package com.kubetetris.api.config;

import com.kubetetris.api.store.ClusterRecord;
import com.kubetetris.api.store.ClusterRegistry;
import com.kubetetris.api.store.SnapshotStore;
import com.kubetetris.engine.synth.SnapshotFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Seeds an in-memory 'synth' cluster on startup so the API is usable without a real
 * Kubernetes connection. The synthetic snapshot is re-materialized on every /collect.
 */
@Component
public class SyntheticSeeder {

    public static final String SYNTH_ID = "synth";
    public static final String SYNTH_NAME = "synthetic";

    private final ClusterRegistry registry;
    private final SnapshotStore snapshots;
    private final Clock clock;

    public SyntheticSeeder(ClusterRegistry registry, SnapshotStore snapshots, Clock clock) {
        this.registry = registry;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @PostConstruct
    public void seed() {
        registry.register(new ClusterRecord(SYNTH_ID, SYNTH_NAME, null, "synthetic", null, null, true));
        snapshots.put(SYNTH_ID, SnapshotFactory.uiMockupFixture(), Instant.now(clock));
    }
}
