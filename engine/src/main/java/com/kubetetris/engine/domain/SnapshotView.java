package com.kubetetris.engine.domain;

import java.util.List;

/**
 * Immutable input to the engine. Mirrors the shape of SnapshotDTO in
 * {@code design-docs/04-schema-and-api.md} without the JSON layer (M2 wraps this).
 * {@code stale} + {@code takenAt} + {@code pivot}/{@code entropy} are stamped by the
 * collector in production; for engine tests they can be recomputed on demand.
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
