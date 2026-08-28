package com.kubetetris.api.mapper;

import com.kubetetris.api.dto.BalancePlanDto;
import com.kubetetris.api.dto.DisruptionDto;
import com.kubetetris.api.dto.FeasibilityDto;
import com.kubetetris.api.dto.NodeDto;
import com.kubetetris.api.dto.OwnerRefDto;
import com.kubetetris.api.dto.PinDto;
import com.kubetetris.api.dto.PlanStepDto;
import com.kubetetris.api.dto.PodDto;
import com.kubetetris.api.dto.SnapshotDto;
import com.kubetetris.api.dto.SwapDto;
import com.kubetetris.engine.domain.BalancePlanResult;
import com.kubetetris.engine.domain.FeasibilityResult;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.engine.domain.SwapStep;
import com.kubetetris.engine.entropy.Entropy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges internal engine values to wire DTOs. Fields that the engine doesn't track
 * yet (node zone/instance-type/cordoned/ready/taints, pod PDB state, pins) are emitted
 * with conservative defaults so the wire schema is complete from day one; when the
 * collector lands with real data those defaults will get replaced.
 */
public final class EngineMapper {

    private EngineMapper() {}

    public static SnapshotDto toSnapshotDto(String clusterId, SnapshotView view, Instant takenAt, boolean stale) {
        double pivot = Entropy.pivot(view.nodes());
        double entropy = Entropy.systemEntropy(view.nodes(), pivot);
        List<NodeDto> nodes = new ArrayList<>(view.nodes().size());
        for (NodeState n : view.nodes()) nodes.add(toNodeDto(n));
        List<PodDto> pending = new ArrayList<>(view.pending().size());
        for (PodSpec p : view.pending()) pending.add(toPodDto(p, null));
        return new SnapshotDto(clusterId, takenAt, stale, pivot, entropy, nodes, pending);
    }

    public static NodeDto toNodeDto(NodeState node) {
        List<PodDto> pods = new ArrayList<>(node.pods().size());
        for (PodSpec p : node.pods()) pods.add(toPodDto(p, node.name()));
        return new NodeDto(
                node.name(),
                null,
                null,
                node.spec().allocatable().cpuMillicore(),
                node.spec().allocatable().memoryMB(),
                node.free().cpuMillicore(),
                node.free().memoryMB(),
                node.cpuMemRatio(),
                false,
                true,
                List.of(),
                pods
        );
    }

    public static PodDto toPodDto(PodSpec p, String nodeName) {
        return new PodDto(
                p.uid(),
                p.name(),
                p.namespace(),
                p.ownerKind(),
                nodeName,
                p.request().cpuMillicore(),
                p.request().memoryMB(),
                p.qos().name(),
                new OwnerRefDto(p.ownerKind(), null),
                p.reversible(),
                true,
                PinDto.empty()
        );
    }

    public static FeasibilityDto toFeasibilityDto(FeasibilityResult r, String podName) {
        List<PlanStepDto> steps = new ArrayList<>(r.plan().size());
        for (PlanStep s : r.plan()) steps.add(toPlanStepDto(s));
        int evictions = r.moves();
        return new FeasibilityDto(
                r.pendingPodUid(),
                podName,
                r.feasible(),
                r.reason(),
                strategyName(r.strategy()),
                r.moves(),
                r.targetNode(),
                r.touchesNonReversible(),
                new DisruptionDto(evictions, evictions),
                "allowed",
                steps
        );
    }

    public static PlanStepDto toPlanStepDto(PlanStep s) {
        return new PlanStepDto(
                s.kind().name(),
                s.pod().uid(),
                s.pod().name(),
                s.fromNode(),
                s.toNode(),
                s.note()
        );
    }

    public static BalancePlanDto toBalancePlanDto(BalancePlanResult r) {
        List<SwapDto> swaps = new ArrayList<>(r.swaps().size());
        for (int i = 0; i < r.swaps().size(); i++) swaps.add(toSwapDto(r.swaps().get(i), i));
        return new BalancePlanDto(r.baseEntropy(), r.projectedEntropy(), r.improvementPct(), swaps);
    }

    public static SwapDto toSwapDto(SwapStep s, int index) {
        boolean reversible = s.podA().reversible() && s.podB().reversible();
        return new SwapDto(
                "s" + index,
                s.podA().uid(), s.podA().name(), s.nodeA(),
                s.podB().uid(), s.podB().name(), s.nodeB(),
                s.entropyBefore(), s.entropyAfter(), s.entropyDrop(), s.improvementPct(),
                new DisruptionDto(2, 2),
                "allowed",
                reversible
        );
    }

    private static String strategyName(FeasibilityResult.Strategy s) {
        return switch (s) {
            case SINGLE -> "single";
            case MULTI -> "multi";
            case NONE -> "none";
        };
    }
}
