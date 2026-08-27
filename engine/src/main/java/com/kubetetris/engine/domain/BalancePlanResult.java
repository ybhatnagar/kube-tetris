package com.kubetetris.engine.domain;

import java.util.List;

/** Engine-side mirror of BalancePlanDTO (doc 04). Swaps are ordered best-first. */
public record BalancePlanResult(
        double baseEntropy,
        double projectedEntropy,
        double improvementPct,
        List<SwapStep> swaps
) {
    public static BalancePlanResult empty(double baseEntropy) {
        return new BalancePlanResult(baseEntropy, baseEntropy, 0d, List.of());
    }
}
