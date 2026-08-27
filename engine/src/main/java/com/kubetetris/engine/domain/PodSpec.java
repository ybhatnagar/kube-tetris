package com.kubetetris.engine.domain;

/**
 * Immutable pod description as consumed by the engine. `uid` is the k8s UID (the stable
 * join key across snapshot/plan/journal — never join on `name`). `reversible`, `qos`,
 * `systemCritical`, `namespace` are all set upstream (collector in M3; synth in M1).
 */
public record PodSpec(
        String uid,
        String name,
        String namespace,
        String ownerKind,
        ResourceReq request,
        Qos qos,
        boolean reversible,
        boolean systemCritical
) {
    public enum Qos { GUARANTEED, BURSTABLE, BEST_EFFORT }

    public boolean movable() {
        return !systemCritical && !request.isZero();
    }
}
