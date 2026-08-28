package com.kubetetris.api.http;

import com.kubetetris.api.dto.BalanceControlsDto;
import com.kubetetris.api.dto.BalancePlanDto;
import com.kubetetris.api.dto.WhyDto;
import com.kubetetris.api.mapper.EngineMapper;
import com.kubetetris.api.store.ClusterRegistry;
import com.kubetetris.api.store.SnapshotStore;
import com.kubetetris.engine.balancer.Balancer;
import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.domain.BalancePlanResult;
import com.kubetetris.engine.domain.SwapStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clusters/{id}/balance")
public class BalanceController {

    private final ClusterRegistry registry;
    private final SnapshotStore snapshots;
    private final EngineConfig defaults;

    public BalanceController(ClusterRegistry registry, SnapshotStore snapshots, EngineConfig defaults) {
        this.registry = registry;
        this.snapshots = snapshots;
        this.defaults = defaults;
    }

    @PostMapping("/plan")
    public ResponseEntity<BalancePlanDto> plan(@PathVariable String id,
                                               @RequestBody(required = false) BalanceControlsDto controls) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        var entry = snapshots.latest(id).orElse(null);
        if (entry == null) return ResponseEntity.notFound().build();
        EngineConfig cfg = mergeControls(defaults, controls);
        Balancer balancer = new Balancer(cfg);
        BalancePlanResult result = balancer.plan(entry.view());
        return ResponseEntity.ok(EngineMapper.toBalancePlanDto(result));
    }

    @GetMapping("/{swapRef}/why")
    public ResponseEntity<WhyDto> why(@PathVariable String id, @PathVariable String swapRef) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        var entry = snapshots.latest(id).orElse(null);
        if (entry == null) return ResponseEntity.notFound().build();
        int idx;
        try {
            idx = Integer.parseInt(swapRef.startsWith("s") ? swapRef.substring(1) : swapRef);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        BalancePlanResult result = new Balancer(defaults).plan(entry.view());
        if (idx < 0 || idx >= result.swaps().size()) return ResponseEntity.notFound().build();
        SwapStep s = result.swaps().get(idx);
        String evidence = "Swap " + s.podA().name() + " (" + s.nodeA() + ") ⇄ "
                + s.podB().name() + " (" + s.nodeB() + ") drops entropy "
                + fmt(s.entropyBefore()) + " → " + fmt(s.entropyAfter())
                + " (" + Math.round(s.improvementPct()) + "%).";
        return ResponseEntity.ok(new WhyDto(evidence));
    }

    private static String fmt(double d) { return String.format("%.2f", d); }

    private static EngineConfig mergeControls(EngineConfig defaults, BalanceControlsDto c) {
        if (c == null) return defaults;
        EngineConfig.Balancer b = defaults.balancer();
        int maxSwaps = c.maxSwaps() != null ? c.maxSwaps() : b.maxSwaps();
        double minImp = c.minImprovementPct() != null ? c.minImprovementPct() : b.minImprovementPct();
        boolean exclNonRev = c.excludeNonReversible() != null ? c.excludeNonReversible() : b.excludeNonReversible();
        String ns = c.namespaceFilter() != null ? c.namespaceFilter() : b.namespaceFilter();
        EngineConfig.Balancer merged = new EngineConfig.Balancer(maxSwaps, minImp, b.epsilon(), exclNonRev, ns);
        return new EngineConfig(defaults.scheduler(), merged, defaults.safety());
    }
}
