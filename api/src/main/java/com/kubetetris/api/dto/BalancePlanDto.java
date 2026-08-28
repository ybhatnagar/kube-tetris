package com.kubetetris.api.dto;

import java.util.List;

public record BalancePlanDto(
        double baseEntropy,
        double projectedEntropy,
        double improvementPct,
        List<SwapDto> swaps
) {}
