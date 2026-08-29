package com.kubetetris.api.dto;

public record ClusterCreateRequestDto(
        String name,
        String apiUrl,
        String authMethod,
        String credentialRef,
        String kubeConfigPath
) {}
