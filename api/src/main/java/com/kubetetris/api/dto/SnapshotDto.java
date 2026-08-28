package com.kubetetris.api.dto;

import java.time.Instant;
import java.util.List;

public record SnapshotDto(
        String clusterId,
        Instant takenAt,
        boolean stale,
        double pivot,
        double entropy,
        List<NodeDto> nodes,
        List<PodDto> pending
) {}
