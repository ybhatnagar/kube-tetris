package com.kubetetris.api.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory cluster registry with optional disk persistence. When
 * {@code kubetetris.registry.path} is set, every mutation is also written to a JSON
 * file under that directory (one file per cluster, atomic rename) and the registry
 * reloads from disk on startup. The synthetic cluster is always re-seeded by
 * {@code SyntheticSeeder} so it survives disk clears.
 */
@Component
public class ClusterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClusterRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ClusterRecord> byId = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1);
    private final Path storageDir;

    public ClusterRegistry(@Value("${kubetetris.registry.path:#{null}}") String path) {
        this.storageDir = (path == null || path.isBlank()) ? null : Path.of(path);
    }

    @PostConstruct
    void loadFromDisk() {
        if (storageDir == null) return;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.warn("could not create registry directory {}: {}", storageDir, e.getMessage());
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, "*.json")) {
            for (Path file : stream) {
                try {
                    ClusterRecord r = MAPPER.readValue(file.toFile(), ClusterRecord.class);
                    byId.put(r.id(), r);
                    bumpSeqAbove(r.id());
                } catch (IOException e) {
                    log.warn("could not read cluster file {}: {}", file, e.getMessage());
                }
            }
            log.info("loaded {} cluster records from {}", byId.size(), storageDir);
        } catch (IOException e) {
            log.warn("could not list registry directory {}: {}", storageDir, e.getMessage());
        }
    }

    private void bumpSeqAbove(String id) {
        if (id == null || !id.startsWith("c")) return;
        try {
            int n = Integer.parseInt(id.substring(1));
            seq.updateAndGet(current -> Math.max(current, n + 1));
        } catch (NumberFormatException ignored) {
            // Non-numeric id (synth) — doesn't affect the seq.
        }
    }

    public ClusterRecord register(ClusterRecord record) {
        byId.put(record.id(), record);
        persist(record);
        return record;
    }

    public ClusterRecord create(String name, String apiUrl, String authMethod,
                                String credentialRef, String kubeConfigPath) {
        String id = "c" + seq.getAndIncrement();
        return register(new ClusterRecord(id, name, apiUrl, authMethod, credentialRef,
                kubeConfigPath, false));
    }

    public Optional<ClusterRecord> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<ClusterRecord> list() {
        return List.copyOf(byId.values());
    }

    public boolean remove(String id) {
        ClusterRecord removed = byId.remove(id);
        if (removed != null) deletePersisted(id);
        return removed != null;
    }

    private void persist(ClusterRecord record) {
        if (storageDir == null) return;
        Path file = storageDir.resolve(record.id() + ".json");
        Path tmp = storageDir.resolve(record.id() + ".json.tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), record);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("could not persist cluster {}: {}", record.id(), e.getMessage());
        }
    }

    private void deletePersisted(String id) {
        if (storageDir == null) return;
        try {
            Files.deleteIfExists(storageDir.resolve(id + ".json"));
        } catch (IOException e) {
            log.warn("could not delete cluster file {}: {}", id, e.getMessage());
        }
    }
}
