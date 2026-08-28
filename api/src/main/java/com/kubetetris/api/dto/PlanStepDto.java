package com.kubetetris.api.dto;

public record PlanStepDto(
        String action,
        String podUid,
        String podName,
        String fromNode,
        String toNode,
        String note
) {}
