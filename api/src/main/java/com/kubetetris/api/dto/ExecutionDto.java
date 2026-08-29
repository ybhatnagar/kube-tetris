package com.kubetetris.api.dto;

import java.util.List;

public record ExecutionDto(
        String journalId,
        String status,
        String detail,
        List<ExecStepDto> steps
) {}
