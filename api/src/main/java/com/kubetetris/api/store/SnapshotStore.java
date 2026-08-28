package com.kubetetris.api.store;

import com.kubetetris.engine.domain.SnapshotView;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest snapshot per cluster, held in memory. Staleness threshold is a fixed 5 minutes
 * for now — it will move into config once the collector is wired up.
 */
@Component
public class SnapshotStore {

    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final Map<String, Entry> latest = new ConcurrentHashMap<>();

    public Entry put(String clusterId, SnapshotView view, Instant takenAt) {
        Entry entry = new Entry(view, takenAt);
        latest.put(clusterId, entry);
        return entry;
    }

    public Optional<Entry> latest(String clusterId) {
        return Optional.ofNullable(latest.get(clusterId));
    }

    public boolean isStale(Instant takenAt, Instant now) {
        return Duration.between(takenAt, now).compareTo(STALE_AFTER) > 0;
    }

    public record Entry(SnapshotView view, Instant takenAt) {}
}
