package com.kubetetris.executor;

import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.executor.internal.Evictor;
import com.kubetetris.executor.internal.LegFailedException;
import com.kubetetris.executor.internal.ReadyWaiter;
import com.kubetetris.executor.internal.Steerer;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executes an {@link ExecutionRequest} against a live cluster. Every leg is journaled;
 * a failure at any leg triggers rollback and marks the run either {@code ROLLED_BACK}
 * (no writes reached the cluster or all writes were reversed) or {@code NEEDS_ATTENTION}
 * (the pod was already evicted and can't be safely returned to origin without another
 * cordon cycle — an operator has to intervene).
 *
 * <p>This class runs one move synchronously. Multi-leg plans and the swap primitive are
 * built as sequences on top; see follow-up commits.
 */
public class Executor {

    private static final Logger log = LoggerFactory.getLogger(Executor.class);

    private final Journal journal;
    private final Clock clock;
    private final Duration pollInterval;

    public Executor(Journal journal, Clock clock) {
        this(journal, clock, Duration.ofMillis(200));
    }

    Executor(Journal journal, Clock clock, Duration pollInterval) {
        this.journal = journal;
        this.clock = clock;
        this.pollInterval = pollInterval;
    }

    public ExecutionResult executeMove(KubernetesClient client, ExecutionRequest request,
                                       FaultInjector injector) {
        return execute(client, request, injector);
    }

    /**
     * Run every MOVE in {@code request.steps()} in order. PLACE steps for the pending
     * pod land implicitly — cordoning during a MOVE keeps the scheduler pointed at the
     * intended target node. If any leg of any move fails, all subsequent moves are
     * skipped; the failing move is rolled back per {@link #rollback}. Moves that already
     * committed successfully are left as-is (their placement is the new starting state),
     * and the overall outcome is downgraded to NEEDS_ATTENTION when at least one earlier
     * move committed.
     */
    public ExecutionResult execute(KubernetesClient client, ExecutionRequest request,
                                   FaultInjector injector) {
        String journalId = "j-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now(clock);
        JournalEntry entry = new JournalEntry(journalId, request.clusterId(), request.kind(),
                request.summary(), startedAt, null, ExecutionOutcome.RUNNING, List.of());
        journal.create(entry);

        List<PlanStep> moves = movesOnly(request.steps());
        if (moves.isEmpty()) {
            return finish(journalId, ExecutionOutcome.DONE, "No moves in plan.");
        }

        List<JournalStep> steps = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) steps.addAll(initialStepsFor(i, moves.get(i)));
        journal.updateSteps(journalId, List.copyOf(steps));

        int committedMoves = 0;
        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            PlanStep move = moves.get(moveIndex);
            MoveOutcome outcome = runSingleMove(client, request, moveIndex, moves.size(),
                    move, steps, journalId, injector);
            if (outcome == MoveOutcome.COMMITTED) {
                committedMoves++;
                continue;
            }
            // failure at moveIndex — mark following-move legs as REVERTED so the timeline
            // reads honestly, then decide whether it's ROLLED_BACK or NEEDS_ATTENTION.
            markRemainingAsReverted(steps, journalId, moveIndex + 1);
            if (outcome == MoveOutcome.EVICTED_ROLLBACK_NEEDED || committedMoves > 0) {
                return finish(journalId, ExecutionOutcome.NEEDS_ATTENTION,
                        "Move " + moveIndex + " failed after partial cluster changes; " +
                        (committedMoves > 0 ? committedMoves + " earlier move(s) already committed. " : "") +
                        "Operator action may be required to restore the pre-apply state.");
            }
            return finish(journalId, ExecutionOutcome.ROLLED_BACK,
                    "Move " + moveIndex + " failed; no net change to the cluster.");
        }
        return finish(journalId, ExecutionOutcome.DONE, "Applied.");
    }

    private enum MoveOutcome { COMMITTED, CLEAN_ROLLBACK, EVICTED_ROLLBACK_NEEDED }

    private MoveOutcome runSingleMove(KubernetesClient client, ExecutionRequest request,
                                      int moveIndex, int totalMoves, PlanStep move,
                                      List<JournalStep> steps, String journalId,
                                      FaultInjector injector) {
        int base = moveIndex * LEGS_PER_MOVE;
        Set<String> cordonedNodes = new LinkedHashSet<>();
        boolean evicted = false;

        try {
            runLeg(steps, journalId, base + 0, moveIndex, injector, FailurePoint.PRE_FLIGHT, "ok", () -> {});

            runLeg(steps, journalId, base + 1, moveIndex, injector, FailurePoint.STEER,
                    "steered scheduling toward " + move.toNode(), () -> {
                if (!request.dryRun()) Steerer.cordonAllExcept(client, move.toNode(), cordonedNodes);
            });

            runLeg(steps, journalId, base + 2, moveIndex, injector, FailurePoint.EVICT,
                    "evicted " + move.pod().name() + " (" + move.pod().namespace() + ")", () -> {
                if (!request.dryRun()) Evictor.evict(client, move.pod());
            });
            evicted = !request.dryRun();

            runLeg(steps, journalId, base + 3, moveIndex, injector, FailurePoint.WAIT_READY, "Running", () -> {
                if (!request.dryRun()) {
                    ReadyWaiter.waitFor(client, move.pod(), move.toNode(),
                            request.readyTimeout(), pollInterval, clock);
                }
            });

            runLeg(steps, journalId, base + 4, moveIndex, injector, FailurePoint.VERIFY,
                    "confirmed on " + move.toNode(), () -> {
                if (!request.dryRun()) {
                    if (ReadyWaiter.findReadyReplacement(client, move.pod(), move.toNode()) == null) {
                        throw new LegFailedException(FailurePoint.VERIFY,
                                "no Ready replacement of " + move.pod().name() + " on " + move.toNode());
                    }
                }
            });

            runLeg(steps, journalId, base + 5, moveIndex, injector, FailurePoint.CLEANUP, "restored scheduling", () -> {
                if (!request.dryRun()) Steerer.uncordonAll(client, cordonedNodes);
            });

            return MoveOutcome.COMMITTED;

        } catch (LegFailedException e) {
            return rollbackSingleMove(client, journalId, steps, cordonedNodes, evicted);
        } catch (RuntimeException e) {
            log.warn("Unexpected error mid-execution: {}", e.getMessage(), e);
            return rollbackSingleMove(client, journalId, steps, cordonedNodes, evicted);
        }
    }

    private MoveOutcome rollbackSingleMove(KubernetesClient client, String journalId,
                                           List<JournalStep> steps, Set<String> cordonedNodes,
                                           boolean evicted) {
        rollback(client, journalId, steps, cordonedNodes, evicted, null, "leg failure");
        return evicted ? MoveOutcome.EVICTED_ROLLBACK_NEEDED : MoveOutcome.CLEAN_ROLLBACK;
    }

    // ---------- helpers ----------

    private void runLeg(List<JournalStep> steps, String journalId, int seq, int moveIndex,
                        FaultInjector injector, FailurePoint point,
                        String successDetail, LegAction action) {
        markState(steps, journalId, seq, JournalStep.State.RUNNING, null);
        String failMessage = injector.shouldFail(moveIndex, point);
        if (failMessage != null) {
            markState(steps, journalId, seq, JournalStep.State.FAILED, failMessage);
            throw new LegFailedException(point, failMessage);
        }
        try {
            action.run();
        } catch (LegFailedException e) {
            markState(steps, journalId, seq, JournalStep.State.FAILED, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            markState(steps, journalId, seq, JournalStep.State.FAILED, e.getMessage());
            throw new LegFailedException(point, e.getMessage());
        }
        markState(steps, journalId, seq, JournalStep.State.DONE, successDetail);
    }

    private void markState(List<JournalStep> steps, String journalId, int seq,
                           JournalStep.State state, String detail) {
        steps.set(seq, steps.get(seq).withState(state, detail, Instant.now(clock)));
        journal.updateSteps(journalId, List.copyOf(steps));
    }

    private void rollback(KubernetesClient client, String journalId,
                          List<JournalStep> steps, Set<String> cordonedNodes,
                          boolean evicted, FailurePoint failedAt, String message) {
        try {
            if (!cordonedNodes.isEmpty()) Steerer.uncordonAll(client, cordonedNodes);
        } catch (Exception ex) {
            log.warn("uncordon during rollback failed: {}", ex.getMessage());
        }
        // Only marks legs of the failing move — the outer caller decides what to do with
        // subsequent moves.
        journal.updateSteps(journalId, List.copyOf(steps));
    }

    private void markRemainingAsReverted(List<JournalStep> steps, String journalId, int fromMoveIndex) {
        Instant now = Instant.now(clock);
        int fromSeq = fromMoveIndex * LEGS_PER_MOVE;
        for (int i = fromSeq; i < steps.size(); i++) {
            if (steps.get(i).state() == JournalStep.State.PENDING) {
                steps.set(i, steps.get(i).withState(JournalStep.State.REVERTED, "skipped after failure", now));
            }
        }
        journal.updateSteps(journalId, List.copyOf(steps));
    }

    private ExecutionResult finish(String journalId, ExecutionOutcome outcome, String detail) {
        journal.updateOutcome(journalId, outcome, Instant.now(clock));
        return new ExecutionResult(journalId, outcome, detail);
    }

    private static final int LEGS_PER_MOVE = 6;

    private List<JournalStep> initialStepsFor(int moveIndex, PlanStep move) {
        Instant now = Instant.now(clock);
        int base = moveIndex * LEGS_PER_MOVE;
        String prefix = "[" + (moveIndex + 1) + "] ";
        return List.of(
                pending(base + 0, prefix + "Pre-flight checks", now),
                pending(base + 1, prefix + "Steer scheduling toward " + move.toNode(), now),
                pending(base + 2, prefix + "Evict " + move.pod().name() + " from " + move.fromNode(), now),
                pending(base + 3, prefix + "Wait for replacement Ready on " + move.toNode(), now),
                pending(base + 4, prefix + "Verify placement on " + move.toNode(), now),
                pending(base + 5, prefix + "Restore scheduling on other nodes", now)
        );
    }

    private static JournalStep pending(int seq, String label, Instant now) {
        return new JournalStep(seq, label, JournalStep.State.PENDING, null, now);
    }

    private static List<PlanStep> movesOnly(List<PlanStep> steps) {
        List<PlanStep> out = new ArrayList<>();
        for (PlanStep s : steps) if (s.kind() == PlanStep.Kind.MOVE) out.add(s);
        return out;
    }

    @FunctionalInterface
    private interface LegAction {
        void run() throws LegFailedException;
    }
}
