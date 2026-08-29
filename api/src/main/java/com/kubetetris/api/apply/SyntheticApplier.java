package com.kubetetris.api.apply;

import com.kubetetris.api.store.SnapshotStore;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.executor.ExecutionOutcome;
import com.kubetetris.executor.ExecutionRequest;
import com.kubetetris.executor.ExecutionResult;
import com.kubetetris.executor.Journal;
import com.kubetetris.executor.JournalEntry;
import com.kubetetris.executor.JournalStep;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simulated apply for the synth cluster: mutates the in-memory snapshot to reflect the
 * plan's effect, journals every leg as if it ran on a real cluster, and returns DONE.
 * Nothing invasive happens — this is the demo path.
 */
public final class SyntheticApplier {

    private final SnapshotStore snapshots;
    private final Journal journal;
    private final Clock clock;

    public SyntheticApplier(SnapshotStore snapshots, Journal journal, Clock clock) {
        this.snapshots = snapshots;
        this.journal = journal;
        this.clock = clock;
    }

    public ExecutionResult apply(String clusterId, ExecutionRequest request) {
        String journalId = "j-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now(clock);
        JournalEntry entry = new JournalEntry(journalId, clusterId, request.kind(),
                request.summary(), startedAt, null, ExecutionOutcome.RUNNING, List.of());
        journal.create(entry);

        List<JournalStep> steps = new ArrayList<>();
        List<PlanStep> moves = new ArrayList<>();
        List<PlanStep> places = new ArrayList<>();
        for (PlanStep s : request.steps()) {
            if (s.kind() == PlanStep.Kind.MOVE) moves.add(s);
            else places.add(s);
        }

        int seq = 0;
        for (PlanStep m : moves) {
            steps.add(new JournalStep(seq++, "Simulate MOVE " + m.pod().name() + " → " + m.toNode(),
                    JournalStep.State.DONE, "simulated", Instant.now(clock)));
        }
        for (PlanStep p : places) {
            steps.add(new JournalStep(seq++, "Simulate PLACE " + p.pod().name() + " on " + p.toNode(),
                    JournalStep.State.DONE, "simulated", Instant.now(clock)));
        }
        journal.updateSteps(journalId, List.copyOf(steps));

        if (!request.dryRun()) {
            SnapshotStore.Entry current = snapshots.latest(clusterId).orElse(null);
            if (current != null) {
                SnapshotView mutated = applyToSnapshot(current.view(), moves, places);
                snapshots.put(clusterId, mutated, Instant.now(clock));
            }
        }

        journal.updateOutcome(journalId, ExecutionOutcome.DONE, Instant.now(clock));
        return new ExecutionResult(journalId, ExecutionOutcome.DONE, "Applied (synthetic).");
    }

    private SnapshotView applyToSnapshot(SnapshotView view, List<PlanStep> moves, List<PlanStep> places) {
        List<NodeState> nodes = new ArrayList<>(view.nodes().size());
        for (NodeState n : view.nodes()) nodes.add(n.copy());
        List<PodSpec> pending = new ArrayList<>(view.pending());

        for (PlanStep m : moves) {
            NodeState from = findNode(nodes, m.fromNode());
            NodeState to = findNode(nodes, m.toNode());
            if (from == null || to == null) continue;
            PodSpec pod = null;
            for (PodSpec p : from.pods()) if (p.uid().equals(m.pod().uid())) { pod = p; break; }
            if (pod == null) continue;
            from.removePod(pod.uid());
            to.addPod(pod);
        }
        for (PlanStep p : places) {
            NodeState to = findNode(nodes, p.toNode());
            if (to == null) continue;
            PodSpec spec = p.pod();
            pending.removeIf(pod -> pod.uid().equals(spec.uid()));
            to.addPod(spec);
        }
        return new SnapshotView(nodes, pending);
    }

    private static NodeState findNode(List<NodeState> nodes, String name) {
        for (NodeState n : nodes) if (n.name().equals(name)) return n;
        return null;
    }
}
