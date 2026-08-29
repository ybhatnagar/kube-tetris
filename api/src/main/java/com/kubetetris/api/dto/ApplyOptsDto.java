package com.kubetetris.api.dto;

public record ApplyOptsDto(
        Boolean dryRun,
        Integer readyTimeoutS,
        Boolean optInNonReversible
) {}
