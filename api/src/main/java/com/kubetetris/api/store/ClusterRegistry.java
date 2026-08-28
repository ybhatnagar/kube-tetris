package com.kubetetris.api.store;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory cluster registry. Persistence will land in a later milestone; the shape is
 * kept minimal so a file- or DB-backed implementation can slot in behind the same API.
 */
@Component
public class ClusterRegistry {

    private final Map<String, ClusterRecord> byId = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1);

    public ClusterRecord register(ClusterRecord record) {
        byId.put(record.id(), record);
        return record;
    }

    public ClusterRecord create(String name, String apiUrl, String authMethod, String credentialRef) {
        String id = "c" + seq.getAndIncrement();
        return register(new ClusterRecord(id, name, apiUrl, authMethod, credentialRef, false));
    }

    public Optional<ClusterRecord> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<ClusterRecord> list() {
        return List.copyOf(byId.values());
    }

    public boolean remove(String id) {
        return byId.remove(id) != null;
    }
}
