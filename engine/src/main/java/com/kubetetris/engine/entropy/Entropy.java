package com.kubetetris.engine.entropy;

import com.kubetetris.engine.domain.NodeState;

import java.util.List;

/**
 * System-balance math.
 *
 * <pre>
 * pivot     = Σ free_cpu / Σ free_mem
 * nodeRatio = node.free_cpu / node.free_mem   (with ResourceReq clamps)
 * entropy   = Σ |pivot − nodeRatio|           (lower is better; 0 = balanced)
 * </pre>
 *
 * Pivot is computed once and passed to {@link #systemEntropy(List, double)}: it is
 * invariant across pure pod swaps between two nodes, so callers can hoist the call.
 */
public final class Entropy {

    private Entropy() {}

    public static double pivot(List<NodeState> nodes) {
        long totalCpu = 0L, totalMem = 0L;
        for (NodeState n : nodes) {
            totalCpu += n.free().cpuMillicore();
            totalMem += n.free().memoryMB();
        }
        if (totalMem == 0) return 1_000_000d;
        return (double) totalCpu / (double) totalMem;
    }

    public static double systemEntropy(List<NodeState> nodes) {
        return systemEntropy(nodes, pivot(nodes));
    }

    public static double systemEntropy(List<NodeState> nodes, double pivot) {
        double sum = 0d;
        for (NodeState n : nodes) {
            sum += Math.abs(pivot - n.cpuMemRatio());
        }
        return sum;
    }
}
