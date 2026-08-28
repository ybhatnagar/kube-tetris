package com.kubetetris.api.dto;

public record SwapDto(
        String swapRef,
        String podAUid,
        String podA,
        String nodeA,
        String podBUid,
        String podB,
        String nodeB,
        double entropyBefore,
        double entropyAfter,
        double entropyDrop,
        double improvementPct,
        DisruptionDto disruption,
        String pdbStatus,
        boolean reversible
) {}
