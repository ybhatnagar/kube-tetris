package com.kubetetris.engine.entropy;

import com.kubetetris.engine.domain.NodeSpec;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import com.kubetetris.engine.synth.SnapshotFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntropyTest {

    @Test
    void ratioClampsMemoryZeroToLargeConstant() {
        ResourceReq r = new ResourceReq(500, 0);
        assertEquals(1_000_000d, r.cpuMemRatio(), 0d);
    }

    @Test
    void ratioClampsCpuZeroToZero() {
        ResourceReq r = new ResourceReq(0, 500);
        assertEquals(0d, r.cpuMemRatio(), 0d);
    }

    @Test
    void pivotAndEntropyMatchTheSyntheticFixture() {
        var snap = SnapshotFactory.uiMockupFixture();
        double pivot = Entropy.pivot(snap.nodes());
        double entropy = Entropy.systemEntropy(snap.nodes(), pivot);
        assertEquals(0.5454d, pivot, 0.001d);   // 1200/2200
        assertEquals(2.8045d, entropy, 0.001d);
    }

    @Test
    void pivotIsInvariantUnderPureSwapBetweenTwoNodes() {
        var snap = SnapshotFactory.uiMockupFixture();
        double before = Entropy.pivot(snap.nodes());
        // simulate a swap: ranker (n1) <-> thumbnailer (n3) on copies
        NodeState n1 = snap.nodes().get(0).copy();
        NodeState n3 = snap.nodes().get(2).copy();
        PodSpec ranker = n1.pods().stream().filter(p -> p.name().equals("ranker")).findFirst().orElseThrow();
        PodSpec thumb = n3.pods().stream().filter(p -> p.name().equals("thumbnailer")).findFirst().orElseThrow();
        n1.removePod(ranker.uid());
        n3.removePod(thumb.uid());
        assertTrue(n1.addPod(thumb));
        assertTrue(n3.addPod(ranker));
        double after = Entropy.pivot(List.of(n1, snap.nodes().get(1), n3));
        assertEquals(before, after, 1e-9);
    }

    @Test
    void entropyIsZeroWhenAllNodesShareTheSameRatio() {
        NodeState a = new NodeState(new NodeSpec("a", new ResourceReq(1000, 2000)), List.of());
        NodeState b = new NodeState(new NodeSpec("b", new ResourceReq(500, 1000)), List.of());
        double e = Entropy.systemEntropy(List.of(a, b));
        assertEquals(0d, e, 1e-9);
    }
}
