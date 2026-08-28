package com.kubetetris.api.http;

import com.kubetetris.api.dto.FeasibilityDto;
import com.kubetetris.api.dto.PendingListDto;
import com.kubetetris.api.dto.WhyDto;
import com.kubetetris.api.mapper.EngineMapper;
import com.kubetetris.api.store.ClusterRegistry;
import com.kubetetris.api.store.SnapshotStore;
import com.kubetetris.engine.domain.FeasibilityResult;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.scheduler.Scheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clusters/{id}/pending")
public class PendingController {

    private final ClusterRegistry registry;
    private final SnapshotStore snapshots;
    private final Scheduler scheduler;

    public PendingController(ClusterRegistry registry, SnapshotStore snapshots, Scheduler scheduler) {
        this.registry = registry;
        this.snapshots = snapshots;
        this.scheduler = scheduler;
    }

    @GetMapping
    public ResponseEntity<PendingListDto> listPending(@PathVariable String id) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        var entry = snapshots.latest(id).orElse(null);
        if (entry == null) return ResponseEntity.notFound().build();
        List<FeasibilityDto> out = new ArrayList<>();
        for (PodSpec pending : entry.view().pending()) {
            FeasibilityResult r = scheduler.plan(entry.view(), pending);
            out.add(EngineMapper.toFeasibilityDto(r, pending.name()));
        }
        return ResponseEntity.ok(new PendingListDto(out));
    }

    @GetMapping("/{uid}/why")
    public ResponseEntity<WhyDto> why(@PathVariable String id, @PathVariable String uid) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        var entry = snapshots.latest(id).orElse(null);
        if (entry == null) return ResponseEntity.notFound().build();
        PodSpec pod = entry.view().pending().stream()
                .filter(p -> p.uid().equals(uid))
                .findFirst().orElse(null);
        if (pod == null) return ResponseEntity.notFound().build();
        FeasibilityResult r = scheduler.plan(entry.view(), pod);
        String evidence = r.feasible()
                ? "Feasible via " + r.strategy().name().toLowerCase() + " strategy: " + r.moves() + " move(s), target " + r.targetNode() + "."
                : "Not feasible: " + r.reason();
        return ResponseEntity.ok(new WhyDto(evidence));
    }
}
