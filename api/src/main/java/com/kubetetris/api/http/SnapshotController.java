package com.kubetetris.api.http;

import com.kubetetris.api.dto.SnapshotDto;
import com.kubetetris.api.mapper.EngineMapper;
import com.kubetetris.api.store.ClusterRecord;
import com.kubetetris.api.store.ClusterRegistry;
import com.kubetetris.api.store.SnapshotStore;
import com.kubetetris.collector.KubeContext;
import com.kubetetris.collector.KubernetesCollector;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.engine.synth.SnapshotFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clusters/{id}")
public class SnapshotController {

    private final ClusterRegistry registry;
    private final SnapshotStore snapshots;
    private final KubernetesCollector collector;
    private final Clock clock;

    public SnapshotController(ClusterRegistry registry, SnapshotStore snapshots,
                              KubernetesCollector collector, Clock clock) {
        this.registry = registry;
        this.snapshots = snapshots;
        this.collector = collector;
        this.clock = clock;
    }

    @PostMapping("/collect")
    public ResponseEntity<?> collect(@PathVariable String id) {
        ClusterRecord record = registry.find(id).orElse(null);
        if (record == null) return ResponseEntity.notFound().build();

        SnapshotView view;
        if (record.synthetic()) {
            view = SnapshotFactory.uiMockupFixture();
        } else {
            if (record.kubeConfigPath() == null) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", "cluster has no kube_config_path configured"));
            }
            try {
                view = collector.collect(KubeContext.ofFile(record.kubeConfigPath()));
            } catch (RuntimeException e) {
                return ResponseEntity.status(502).body(Map.of(
                        "error", "collection failed: " + e.getMessage()));
            }
        }
        Instant now = Instant.now(clock);
        var entry = snapshots.put(id, view, now);
        return ResponseEntity.ok(EngineMapper.toSnapshotDto(id, entry.view(), entry.takenAt(), false));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<SnapshotDto> snapshot(@PathVariable String id) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        var entry = snapshots.latest(id).orElse(null);
        if (entry == null) return ResponseEntity.notFound().build();
        boolean stale = snapshots.isStale(entry.takenAt(), Instant.now(clock));
        return ResponseEntity.ok(EngineMapper.toSnapshotDto(id, entry.view(), entry.takenAt(), stale));
    }
}
