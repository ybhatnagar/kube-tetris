package com.kubetetris.engine.domain;

import java.util.List;

/** Engine-side feasibility verdict for a single pending pod. */
public record FeasibilityResult(
        String pendingPodUid,
        boolean feasible,
        String reason,
        Strategy strategy,
        int moves,
        String targetNode,
        boolean touchesNonReversible,
        List<PlanStep> plan
) {
    public enum Strategy { SINGLE, MULTI, NONE }

    public static FeasibilityResult infeasible(String uid, String reason) {
        return new FeasibilityResult(uid, false, reason, Strategy.NONE, 0, null, false, List.of());
    }
}
