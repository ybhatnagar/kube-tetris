package com.kubetetris.engine.domain;

/**
 * Immutable pod description consumed by the engine. {@code uid} is the Kubernetes UID
 * and is the stable join key across snapshot / plan / journal — never join on
 * {@code name}. {@code reversible}, {@code qos}, {@code systemCritical}, {@code namespace},
 * {@code pdbOk}, and {@code pins} are all set upstream by the caller (the collector for
 * real clusters, the synthetic factory for tests).
 */
public record PodSpec(
        String uid,
        String name,
        String namespace,
        String ownerKind,
        String ownerName,
        ResourceReq request,
        Qos qos,
        boolean reversible,
        boolean systemCritical,
        boolean pdbOk,
        PodPins pins
) {
    public enum Qos { GUARANTEED, BURSTABLE, BEST_EFFORT }

    public PodSpec {
        pins = pins == null ? PodPins.EMPTY : pins;
    }

    /** Backwards-compatible convenience: no owner name, PDB allowed, no pins. */
    public PodSpec(
            String uid, String name, String namespace, String ownerKind,
            ResourceReq request, Qos qos, boolean reversible, boolean systemCritical) {
        this(uid, name, namespace, ownerKind, null, request, qos, reversible, systemCritical,
                true, PodPins.EMPTY);
    }

    public boolean movable() {
        return !systemCritical && !request.isZero();
    }
}
