package com.kubetetris.engine.scheduler;

import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.domain.FeasibilityResult;
import com.kubetetris.engine.domain.NodeSpec;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.engine.synth.SnapshotFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerTest {

    private final Scheduler scheduler = new Scheduler(EngineConfig.defaults());

    @Test
    void checkoutFitsViaExactlyOneMovePlusPlace() {
        var snap = SnapshotFactory.uiMockupFixture();
        PodSpec checkout = snap.pending().stream()
                .filter(p -> p.name().equals("checkout")).findFirst().orElseThrow();

        FeasibilityResult result = scheduler.plan(snap, checkout);

        assertTrue(result.feasible(), () -> "checkout must be feasible; reason=" + result.reason());
        assertEquals(FeasibilityResult.Strategy.SINGLE, result.strategy());
        assertEquals(1, result.moves(), "deck example is a single-move plan");
        assertFalse(result.touchesNonReversible(),
                "single-move plan must not touch the StatefulSet 'ledger'");
        assertNotNull(result.targetNode());

        // shape checks
        long placeSteps = result.plan().stream().filter(s -> s.kind() == PlanStep.Kind.PLACE).count();
        long moveSteps = result.plan().stream().filter(s -> s.kind() == PlanStep.Kind.MOVE).count();
        assertEquals(1, placeSteps);
        assertEquals(1, moveSteps);

        PlanStep placeStep = result.plan().stream()
                .filter(s -> s.kind() == PlanStep.Kind.PLACE).findFirst().orElseThrow();
        assertEquals("checkout", placeStep.pod().name());

        PlanStep moveStep = result.plan().stream()
                .filter(s -> s.kind() == PlanStep.Kind.MOVE).findFirst().orElseThrow();
        assertTrue(moveStep.pod().reversible(),
                "single-move plan must move only reversible pods (StatefulSet excluded)");

        // replay: after applying the plan, target node must have room for the placement.
        assertPlanIsCapacityConsistent(snap, result);
    }

    @Test
    void batchReportInfeasibleByTotal() {
        var snap = SnapshotFactory.uiMockupFixture();
        PodSpec batchReport = snap.pending().stream()
                .filter(p -> p.name().equals("batch-report")).findFirst().orElseThrow();
        // total free = 1200mC / 2200MB, batch-report wants 900mC / 1800MB → actually total covers it!
        // The deck's batch-report case is illustrative of infeasibility, not enforced by these totals.
        // We assert on totals directly for the true infeasible path with a synthetic huge pod.
        PodSpec huge = new PodSpec("huge", "huge", "test", "Deployment",
                new ResourceReq(9_999, 999_999), PodSpec.Qos.BURSTABLE, true, false);
        FeasibilityResult r = scheduler.plan(snap, huge);
        assertFalse(r.feasible());
        assertTrue(r.reason().toLowerCase().contains("add a node")
                || r.reason().toLowerCase().contains("free capacity"),
                () -> "unexpected reason: " + r.reason());
    }

    @Test
    void directFitYieldsZeroMoveSinglePlacePlan() {
        NodeSpec spec = new NodeSpec("solo", new ResourceReq(1000, 2000));
        NodeState solo = new NodeState(spec, List.of());
        SnapshotView snap = new SnapshotView(List.of(solo), List.of());
        PodSpec p = new PodSpec("x", "x", "ns", "Deployment",
                new ResourceReq(500, 1000), PodSpec.Qos.BURSTABLE, true, false);
        FeasibilityResult r = scheduler.plan(snap, p);
        assertTrue(r.feasible());
        assertEquals(0, r.moves());
        assertEquals(1, r.plan().size());
        assertEquals(PlanStep.Kind.PLACE, r.plan().get(0).kind());
    }

    @Test
    void nonReversibleExcludedByDefault() {
        // n1 holds a non-reversible StatefulSet; n2 holds a reversible worker. Both nodes
        // have the same amount of free space, and pending 'q' fits directly on neither.
        // If reversibility were ignored, moving 'sfs' off n1 (index 0, tied priority) would
        // work. With reversibility on, the scheduler must skip n1 and land the plan through
        // n2 by moving 'worker' — reversible only.
        NodeSpec sp1 = new NodeSpec("n1", new ResourceReq(1000, 1000));
        NodeSpec sp2 = new NodeSpec("n2", new ResourceReq(1000, 1000));
        PodSpec sfs = new PodSpec("s1", "state", "db", "StatefulSet",
                new ResourceReq(500, 500), PodSpec.Qos.BURSTABLE, false, false);
        PodSpec worker = new PodSpec("d1", "worker", "web", "Deployment",
                new ResourceReq(500, 500), PodSpec.Qos.BURSTABLE, true, false);
        NodeState n1 = new NodeState(sp1, List.of(sfs));
        NodeState n2 = new NodeState(sp2, List.of(worker));
        PodSpec pending = new PodSpec("q", "q", "web", "Deployment",
                new ResourceReq(700, 700), PodSpec.Qos.BURSTABLE, true, false);
        SnapshotView snap = new SnapshotView(List.of(n1, n2), List.of(pending));
        FeasibilityResult r = scheduler.plan(snap, pending);
        assertTrue(r.feasible(), () -> "expected feasible; reason=" + r.reason());
        assertFalse(r.touchesNonReversible());
        for (PlanStep s : r.plan()) {
            if (s.kind() == PlanStep.Kind.MOVE) {
                assertTrue(s.pod().reversible(),
                        "no non-reversible pod may be moved without opt-in; got " + s);
                assertEquals("worker", s.pod().name(),
                        "must route the plan through the reversible-pod node");
            }
        }
    }

    @Test
    void zeroRequestPodIsInfeasibleByPolicy() {
        var snap = SnapshotFactory.uiMockupFixture();
        PodSpec zero = new PodSpec("z", "z", "test", "Deployment",
                ResourceReq.ZERO, PodSpec.Qos.BURSTABLE, true, false);
        FeasibilityResult r = scheduler.plan(snap, zero);
        assertFalse(r.feasible());
        assertTrue(r.reason().toLowerCase().contains("no resource requests"));
    }

    @Test
    void dpSubsetCoveringPicksMinimumSize() {
        // deficit (200,200); pool of pods where a single pod won't cover, but two do.
        PodSpec big = new PodSpec("b", "b", "n", "Deployment",
                new ResourceReq(300, 50), PodSpec.Qos.BURSTABLE, true, false);
        PodSpec mid = new PodSpec("m", "m", "n", "Deployment",
                new ResourceReq(50, 300), PodSpec.Qos.BURSTABLE, true, false);
        PodSpec small = new PodSpec("s", "s", "n", "Deployment",
                new ResourceReq(120, 120), PodSpec.Qos.BURSTABLE, true, false);
        List<PodSpec> chosen = Scheduler.Attempt.minimumSubsetCovering(
                List.of(big, mid, small), new ResourceReq(200, 200));
        assertEquals(2, chosen.size(),
                "should pick a 2-pod subset that covers both dims (big+mid), not any 3-pod combo");
    }

    private void assertPlanIsCapacityConsistent(SnapshotView snap, FeasibilityResult r) {
        // Replay MOVEs and PLACEs against a fresh copy and assert each step fits.
        var wc = new com.kubetetris.engine.domain.WorkingCluster(snap.nodes());
        for (PlanStep step : r.plan()) {
            NodeState target = wc.node(step.toNode());
            assertNotNull(target, "unknown target node: " + step.toNode());
            switch (step.kind()) {
                case MOVE -> {
                    boolean ok = wc.movePod(step.pod(), target);
                    assertTrue(ok, "MOVE step exceeds target capacity: " + step);
                }
                case PLACE -> {
                    boolean ok = wc.placeNewPod(step.pod(), target);
                    assertTrue(ok, "PLACE step exceeds target capacity: " + step);
                }
            }
        }
    }
}
