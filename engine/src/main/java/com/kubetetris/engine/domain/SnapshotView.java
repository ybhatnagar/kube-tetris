package com.kubetetris.engine.domain;

import java.util.List;

/**
 * Immutable input to the engine — the domain-level view of a cluster snapshot.
 * Snapshot-level scalars (takenAt, pivot, entropy) are stamped by the collector in
 * production; engine tests recompute them on demand.
 */
public record SnapshotView(
        List<NodeState> nodes,
        List<PodSpec> pending
) {
    public SnapshotView {
        nodes = List.copyOf(nodes);
        pending = List.copyOf(pending);
    }
}
