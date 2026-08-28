package com.kubetetris.api.dto;

import java.util.List;

public record NodeDto(
        String name,
        String zone,
        String instanceType,
        long cpuAlloc,
        long memAlloc,
        long cpuFree,
        long memFree,
        double ratio,
        boolean cordoned,
        boolean ready,
        List<String> taints,
        List<PodDto> pods
) {}
