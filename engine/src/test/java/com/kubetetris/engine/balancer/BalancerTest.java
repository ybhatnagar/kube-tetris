package com.kubetetris.engine.balancer;

import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.domain.BalancePlanResult;
import com.kubetetris.engine.domain.SwapStep;
import com.kubetetris.engine.synth.SnapshotFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancerTest {

    @Test
    void topSwapIsTheHighestEntropyDropOnTheFixture() {
        var snap = SnapshotFactory.uiMockupFixture();
        Balancer bal = new Balancer(EngineConfig.defaults());
        BalancePlanResult result = bal.plan(snap);

        assertEquals(2.8045d, result.baseEntropy(), 0.005d);
        assertFalse(result.swaps().isEmpty(), "at least one improving swap must be found");

        SwapStep first = result.swaps().get(0);
        assertEquals(0.286d, first.entropyAfter(), 0.005d,
                "top swap should drop entropy to ~0.29");
        Set<String> names = Set.of(first.podA().name(), first.podB().name());
        assertEquals(Set.of("ranker", "thumbnailer"), names,
                "expected top swap ranker⇄thumbnailer; got " + names);
    }

    @Test
    void nonReversibleExcludedByDefault() {
        var snap = SnapshotFactory.uiMockupFixture();
        Balancer bal = new Balancer(EngineConfig.defaults());
        BalancePlanResult result = bal.plan(snap);
        for (SwapStep s : result.swaps()) {
            assertTrue(s.podA().reversible() && s.podB().reversible(),
                    "non-reversible pod appeared in swap: " + s);
        }
    }

    @Test
    void balancedClusterProducesEmptyPlan() {
        var snap = SnapshotFactory.balancedFixture();
        BalancePlanResult r = new Balancer(EngineConfig.defaults()).plan(snap);
        assertTrue(r.swaps().isEmpty());
        assertEquals(r.baseEntropy(), r.projectedEntropy(), 1e-9);
    }

    @Test
    void minImprovementThresholdBlocksMarginalSwaps() {
        var snap = SnapshotFactory.uiMockupFixture();
        EngineConfig strict = new EngineConfig(
                EngineConfig.Scheduler.defaults(),
                new EngineConfig.Balancer(5, 99.9d, 1e-6d, true, null),
                EngineConfig.Safety.defaults());
        BalancePlanResult r = new Balancer(strict).plan(snap);
        // Even the deck swap (95%) is below 99.9% → empty plan.
        assertTrue(r.swaps().isEmpty(),
                "no swap should meet a 99.9% improvement threshold on this fixture");
    }

    @Test
    void terminatesAndDoesNotOscillate() {
        // Run with a high maxSwaps cap; the balancer must terminate on its own.
        var snap = SnapshotFactory.uiMockupFixture();
        EngineConfig high = new EngineConfig(
                EngineConfig.Scheduler.defaults(),
                new EngineConfig.Balancer(50, 1.0d, 1e-6d, true, null),
                EngineConfig.Safety.defaults());
        BalancePlanResult r = new Balancer(high).plan(snap);
        // Assert entropy monotonically decreases across the emitted list.
        double prev = r.baseEntropy();
        for (SwapStep s : r.swaps()) {
            assertTrue(s.entropyAfter() < prev + 1e-9, "entropy must not increase across swaps");
            prev = s.entropyAfter();
        }
    }
}
