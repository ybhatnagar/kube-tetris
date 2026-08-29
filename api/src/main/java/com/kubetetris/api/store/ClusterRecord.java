package com.kubetetris.api.store;

import com.kubetetris.api.dto.ClusterDto;

/** In-memory registry entry. */
public record ClusterRecord(String id, String name, String apiUrl, String authMethod,
                            String credentialRef, String kubeConfigPath, boolean synthetic) {

    public ClusterDto toDto() {
        return new ClusterDto(id, name, apiUrl, authMethod, credentialRef, "ready");
    }
}
