package com.kubetetris.engine.domain;

/** One swap in a balancer plan. Mirrors SwapDTO (doc 04). */
public record SwapStep(
        PodSpec podA, String nodeA,
        PodSpec podB, String nodeB,
        double entropyBefore, double entropyAfter,
        double improvementPct
) {
    public double entropyDrop() { return entropyBefore - entropyAfter; }
}
