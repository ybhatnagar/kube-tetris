package com.kubetetris.api.dto;

import java.time.Instant;

public record HistoryEntryDto(
        String journalId,
        Instant ts,
        String kind,
        String summary,
        String effect,
        String outcome
) {}
