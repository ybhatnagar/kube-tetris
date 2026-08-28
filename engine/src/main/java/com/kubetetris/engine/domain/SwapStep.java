package com.kubetetris.engine.domain;

/** One swap in a balancer plan. */
public record SwapStep(
        PodSpec podA, String nodeA,
        PodSpec podB, String nodeB,
        double entropyBefore, double entropyAfter,
        double improvementPct
) {
    public double entropyDrop() { return entropyBefore - entropyAfter; }
}
