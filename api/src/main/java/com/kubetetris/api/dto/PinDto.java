package com.kubetetris.api.dto;

import java.util.List;

public record PinDto(
        String nodeSelector,
        String affinity,
        String topologySpread,
        String hostPath,
        List<String> pvc
) {
    public static PinDto empty() { return new PinDto(null, null, null, null, List.of()); }
}
