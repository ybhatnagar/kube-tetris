package com.kubetetris.engine.entropy;

import com.kubetetris.engine.domain.NodeState;

import java.util.List;

/**
 * The 2018 SystemControllerImpl math, made explicit and I/O-free.
 *
 * pivot = Σ free_cpu / Σ free_mem
 * nodeRatio = node.free_cpu / node.free_mem   (with ResourceReq clamps)
 * entropy = Σ |pivot − nodeRatio|             (lower is better; 0 = balanced)
 *
 * Diff vs 2018: pivot is computed once and passed to {@link #systemEntropy(List, double)}
 * rather than recomputed inside the sum loop. Same numeric result; documents that pivot
 * is invariant across pure pod swaps between two nodes.
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
