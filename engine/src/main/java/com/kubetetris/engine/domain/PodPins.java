package com.kubetetris.engine.domain;

import java.util.List;

/**
 * Placement constraints attached to a pod that can pin or restrict where it can run.
 * All fields are optional; unknown or absent constraints stay {@code null} / empty.
 */
public record PodPins(
        String nodeSelector,
        String affinity,
        String topologySpread,
        String hostPath,
        List<String> pvc
) {
    public static final PodPins EMPTY = new PodPins(null, null, null, null, List.of());

    public PodPins {
        pvc = pvc == null ? List.of() : List.copyOf(pvc);
    }
}
