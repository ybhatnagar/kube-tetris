package com.kubetetris.api.dto;

public record BalanceControlsDto(
        Integer maxSwaps,
        Double minImprovementPct,
        Boolean excludeNonReversible,
        String namespaceFilter
) {}
