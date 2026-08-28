package com.kubetetris.api.dto;

public record ClusterDto(
        String id,
        String name,
        String apiUrl,
        String authMethod,
        String credentialRef,
        String status
) {
    public static ClusterDto minimal(String id, String name) {
        return new ClusterDto(id, name, null, "synthetic", null, "ready");
    }
}
