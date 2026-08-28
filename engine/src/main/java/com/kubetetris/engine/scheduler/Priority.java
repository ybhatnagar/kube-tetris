package com.kubetetris.engine.scheduler;

import com.kubetetris.cantor.PairDepair;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.ResourceReq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orders nodes by how close they are to fitting the placement request. Uses the
 * Cantor pairing function over the two-dimensional (mem_gap, cpu_gap) with negative
 * gaps (surplus) clamped at zero — a mem surplus should not count as a mem deficit.
 * Nodes with the smallest paired gap are tried first.
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
