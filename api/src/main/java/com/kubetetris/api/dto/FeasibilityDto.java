package com.kubetetris.api.dto;

import java.util.List;

public record FeasibilityDto(
        String pendingPodUid,
        String pendingPodName,
        boolean feasible,
        String reason,
        String strategy,
        int moves,
        String targetNode,
        boolean touchesNonreversible,
        DisruptionDto disruption,
        String pdbStatus,
        List<PlanStepDto> plan
) {}
