package com.kubetetris.engine.domain;

import java.util.List;

/**
 * Immutable node identity + allocatable capacity, plus the operational metadata the wire
 * contract exposes (zone / instance type / cordoned / ready / taints). None of these
 * affect algorithm output; the extra fields are threaded through so downstream callers
 * (HTTP DTOs, execution safety checks) can consume real values without a sidecar map.
 */
public record NodeSpec(
        String name,
        ResourceReq allocatable,
        String zone,
        String instanceType,
        boolean cordoned,
        boolean ready,
        List<String> taints
) {
    public NodeSpec {
        taints = taints == null ? List.of() : List.copyOf(taints);
    }

    /** Backwards-compatible convenience for callers that only care about identity + capacity. */
    public NodeSpec(String name, ResourceReq allocatable) {
        this(name, allocatable, null, null, false, true, List.of());
    }
}
