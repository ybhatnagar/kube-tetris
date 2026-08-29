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
        String journalId = "j-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now(clock);
        JournalEntry entry = new JournalEntry(journalId, request.clusterId(), request.kind(),
                request.summary(), startedAt, null, ExecutionOutcome.RUNNING, List.of());
        journal.create(entry);

        PlanStep move = firstMove(request.steps());
        if (move == null) {
            return finish(journalId, ExecutionOutcome.DONE, "No moves in plan.");
        }

        List<JournalStep> steps = new ArrayList<>(initialSteps(move));
        journal.updateSteps(journalId, steps);

        Set<String> cordonedNodes = new LinkedHashSet<>();
        boolean evicted = false;

        try {
            runLeg(steps, journalId, 0, injector, FailurePoint.PRE_FLIGHT, "ok", () -> {
                // Placeholder — future: capacity + PDB + taints + reversibility check.
            });

            runLeg(steps, journalId, 1, injector, FailurePoint.STEER,
                    "steered scheduling toward " + move.toNode(), () -> {
                if (!request.dryRun()) Steerer.cordonAllExcept(client, move.toNode(), cordonedNodes);
            });

            String podLabel = move.pod().name() + " (" + move.pod().namespace() + ")";
            runLeg(steps, journalId, 2, injector, FailurePoint.EVICT,
                    "evicted " + podLabel, () -> {
                if (!request.dryRun()) Evictor.evict(client, move.pod());
            });
            evicted = !request.dryRun();

            runLeg(steps, journalId, 3, injector, FailurePoint.WAIT_READY, "Running", () -> {
                if (!request.dryRun()) {
                    ReadyWaiter.waitFor(client, move.pod(), move.toNode(),
                            request.readyTimeout(), pollInterval, clock);
                }
            });

            runLeg(steps, journalId, 4, injector, FailurePoint.VERIFY,
                    "confirmed on " + move.toNode(), () -> {
                if (!request.dryRun()) {
                    if (ReadyWaiter.findReadyReplacement(client, move.pod(), move.toNode()) == null) {
                        throw new LegFailedException(FailurePoint.VERIFY,
                                "no Ready replacement of " + move.pod().name() + " on " + move.toNode());
                    }
                }
            });

            runLeg(steps, journalId, 5, injector, FailurePoint.CLEANUP, "restored scheduling", () -> {
                if (!request.dryRun()) Steerer.uncordonAll(client, cordonedNodes);
            });

            return finish(journalId, ExecutionOutcome.DONE, "Applied.");

        } catch (LegFailedException e) {
            return rollback(client, journalId, steps, cordonedNodes, evicted, e.point(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Unexpected error mid-execution: {}", e.getMessage(), e);
            return rollback(client, journalId, steps, cordonedNodes, evicted, null,
                    "unexpected error: " + e.getMessage());
        }
    }

    // ---------- helpers ----------

    private void runLeg(List<JournalStep> steps, String journalId, int seq, FaultInjector injector,
                        FailurePoint point, String successDetail, LegAction action) {
        markState(steps, journalId, seq, JournalStep.State.RUNNING, null);
        String failMessage = injector.shouldFail(0, point);
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

    private ExecutionResult rollback(KubernetesClient client, String journalId,
                                     List<JournalStep> steps, Set<String> cordonedNodes,
                                     boolean evicted, FailurePoint failedAt, String message) {
        try {
            if (!cordonedNodes.isEmpty()) Steerer.uncordonAll(client, cordonedNodes);
        } catch (Exception ex) {
            log.warn("uncordon during rollback failed: {}", ex.getMessage());
        }

        // Mark remaining PENDING steps as REVERTED so the timeline reads honestly.
        Instant now = Instant.now(clock);
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).state() == JournalStep.State.PENDING) {
                steps.set(i, steps.get(i).withState(JournalStep.State.REVERTED,
                        "skipped after " + (failedAt == null ? "error" : failedAt), now));
            }
        }
        journal.updateSteps(journalId, List.copyOf(steps));

        if (evicted) {
            return finish(journalId, ExecutionOutcome.NEEDS_ATTENTION,
                    "The pod was already evicted; the controller will recreate it, but its" +
                    " placement may not match the pre-apply state. " + message);
        }
        return finish(journalId, ExecutionOutcome.ROLLED_BACK,
                "No net change to the cluster. " + message);
    }

    private ExecutionResult finish(String journalId, ExecutionOutcome outcome, String detail) {
        journal.updateOutcome(journalId, outcome, Instant.now(clock));
        return new ExecutionResult(journalId, outcome, detail);
    }

    private List<JournalStep> initialSteps(PlanStep move) {
        Instant now = Instant.now(clock);
        return List.of(
                pending(0, "Pre-flight checks", now),
                pending(1, "Steer scheduling toward " + move.toNode(), now),
                pending(2, "Evict " + move.pod().name() + " from " + move.fromNode(), now),
                pending(3, "Wait for replacement Ready on " + move.toNode(), now),
                pending(4, "Verify placement on " + move.toNode(), now),
                pending(5, "Restore scheduling on other nodes", now)
        );
    }

    private static JournalStep pending(int seq, String label, Instant now) {
        return new JournalStep(seq, label, JournalStep.State.PENDING, null, now);
    }

    private static PlanStep firstMove(List<PlanStep> steps) {
        for (PlanStep s : steps) if (s.kind() == PlanStep.Kind.MOVE) return s;
        return null;
    }

    @FunctionalInterface
    private interface LegAction {
        void run() throws LegFailedException;
    }
}
