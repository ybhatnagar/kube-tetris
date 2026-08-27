package com.kubetetris.engine.scheduler;

import com.kubetetris.cantor.PairDepair;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.ResourceReq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Port of {@code CapacityPlacementServiceHelper.computePlacementPriority} using the
 * 2018 Cantor pairing function. Semantic diff vs 2018: gaps are clamped at 0 before
 * pairing (surplus in one dimension is not counted as a deficit). Without this fix
 * a node with a huge mem surplus but small cpu deficit ranked WORSE than a node with
 * a small mem deficit and no cpu deficit, which caused the ui-mockup fixture to try
 * the wrong candidate node first.
 */
public final class Priority {

    private Priority() {}

    public static List<Integer> nodeOrderByDeficit(ResourceReq placeRequest, List<NodeState> nodes) {
        List<int[]> paired = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            ResourceReq free = nodes.get(i).free();
            long memGap = Math.max(0L, placeRequest.memoryMB() - free.memoryMB());
            long cpuGap = Math.max(0L, placeRequest.cpuMillicore() - free.cpuMillicore());
            long pair = PairDepair.pair(memGap, cpuGap);
            paired.add(new int[]{i, (int) Math.min(pair, Integer.MAX_VALUE)});
        }
        paired.sort(Comparator.comparingInt(a -> a[1]));
        List<Integer> out = new ArrayList<>(paired.size());
        for (int[] p : paired) out.add(p[0]);
        return out;
    }
}
