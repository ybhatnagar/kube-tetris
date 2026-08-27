package com.kubetetris.engine.balancer;

import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.domain.BalancePlanResult;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.engine.domain.SwapStep;
import com.kubetetris.engine.domain.WorkingCluster;
import com.kubetetris.engine.entropy.Entropy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure entry point over a {@link SnapshotView}. Ported from the 2018
 * {@code WorkLoadBalancerImpl} with the following changes:
 *
 *   - ALL I/O stripped. No {@code kubernetesAccessor}, no {@code Thread.sleep}. The
 *     balancer only computes an ordered swap list; the executor (M5) will apply.
 *   - Best-swap enumeration replaces first-improving. Doc 06 §3 asks for a best-first
 *     ordered list, and the UI mockup's {@code bestSwap} enumerates every (i,j,pa,pb).
 *     First-improving picks {@code ranker⇄session-store} (entropy drop 0.43), best-swap
 *     picks {@code ranker⇄thumbnailer} (0.52) — the latter matches the deck example
 *     "entropy ~2.81 → 0.29". Enumeration is O(nodes^2 × pods^2) which is fine for
 *     realistic clusters (a few hundred candidate pods).
 *   - Reversibility filter applied per config; non-reversible pods excluded by default.
 *   - Session-level thrash guard: never re-emit or reverse a swap accepted earlier
 *     in the same planning session.
 *   - minImprovementPct + epsilon threshold applied per swap.
 *   - Static {@code currIterations} in 2018 (persisted across instances) fixed:
 *     iteration state is per-planning-session, held in local vars.
 */
public final class Balancer {

    private final EngineConfig config;

    public Balancer(EngineConfig config) {
        this.config = config;
    }

    public BalancePlanResult plan(SnapshotView snapshot) {
        WorkingCluster wc = WorkingCluster.from(snapshot);
        List<NodeState> nodes = wc.nodes();
        double baseEntropy = Entropy.systemEntropy(nodes);
        if (nodes.size() < 2) return BalancePlanResult.empty(baseEntropy);

        List<SwapStep> swaps = new ArrayList<>();
        Set<String> emittedKeys = new HashSet<>();
        int maxSwaps = config.balancer().maxSwaps();
        double epsilon = config.balancer().epsilon();
        double minImprovementPct = config.balancer().minImprovementPct();
        String nsFilter = config.balancer().namespaceFilter();

        for (int k = 0; k < maxSwaps; k++) {
            double before = Entropy.systemEntropy(nodes);
            if (allOnOneSideOfPivot(nodes)) break;
            Candidate best = findBestSwap(nodes, epsilon, nsFilter, emittedKeys);
            if (best == null) break;
            double after = best.entropyAfter;
            double drop = before - after;
            double pct = before > 0 ? (drop / before) * 100.0 : 0.0;
            if (pct + epsilon < minImprovementPct) break;

            // apply the swap to the working cluster
            NodeState a = nodes.get(best.i);
            NodeState b = nodes.get(best.j);
            a.removePod(best.podA.uid());
            b.removePod(best.podB.uid());
            a.addPod(best.podB);
            b.addPod(best.podA);

            swaps.add(new SwapStep(best.podA, a.name(), best.podB, b.name(), before, after, pct));
            emittedKeys.add(pairKey(best.podA.uid(), best.podB.uid()));
        }

        double projected = swaps.isEmpty() ? baseEntropy : swaps.get(swaps.size() - 1).entropyAfter();
        double improvement = baseEntropy > 0 ? (baseEntropy - projected) / baseEntropy * 100.0 : 0.0;
        return new BalancePlanResult(baseEntropy, projected, improvement, List.copyOf(swaps));
    }

    private boolean allOnOneSideOfPivot(List<NodeState> nodes) {
        double pivot = Entropy.pivot(nodes);
        boolean anyAbove = false, anyBelow = false;
        for (NodeState n : nodes) {
            double delta = pivot - n.cpuMemRatio();
            if (delta > 0) anyBelow = true;
            else if (delta < 0) anyAbove = true;
        }
        return !(anyAbove && anyBelow);
    }

    private Candidate findBestSwap(List<NodeState> nodes, double epsilon, String nsFilter,
                                   Set<String> emittedKeys) {
        double base = Entropy.systemEntropy(nodes);
        Candidate best = null;
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                NodeState a = nodes.get(i);
                NodeState b = nodes.get(j);
                for (PodSpec pa : a.pods()) {
                    if (!candidate(pa)) continue;
                    if (nsFilter != null && !nsFilter.isEmpty()
                            && !pa.namespace().equals(nsFilter)) {
                        // right side must satisfy the filter alternatively
                    }
                    for (PodSpec pb : b.pods()) {
                        if (!candidate(pb)) continue;
                        if (nsFilter != null && !nsFilter.isEmpty()
                                && !pa.namespace().equals(nsFilter)
                                && !pb.namespace().equals(nsFilter)) continue;
                        if (emittedKeys.contains(pairKey(pa.uid(), pb.uid()))) continue;

                        // trial swap on copies
                        NodeState ta = a.copy();
                        NodeState tb = b.copy();
                        ta.removePod(pa.uid());
                        tb.removePod(pb.uid());
                        if (!ta.addPod(pb)) continue;
                        if (!tb.addPod(pa)) continue;

                        double after = trialEntropy(nodes, i, j, ta, tb);
                        double drop = base - after;
                        if (drop <= epsilon) continue;
                        if (best == null || drop > best.drop) {
                            best = new Candidate(i, j, pa, pb, after, drop);
                        }
                    }
                }
            }
        }
        return best;
    }

    private double trialEntropy(List<NodeState> nodes, int i, int j, NodeState ta, NodeState tb) {
        long totalCpu = 0L, totalMem = 0L;
        for (int k = 0; k < nodes.size(); k++) {
            NodeState n = (k == i) ? ta : (k == j) ? tb : nodes.get(k);
            totalCpu += n.free().cpuMillicore();
            totalMem += n.free().memoryMB();
        }
        double pivot = (totalMem == 0) ? 1_000_000d : (double) totalCpu / (double) totalMem;
        double sum = 0d;
        for (int k = 0; k < nodes.size(); k++) {
            NodeState n = (k == i) ? ta : (k == j) ? tb : nodes.get(k);
            sum += Math.abs(pivot - n.cpuMemRatio());
        }
        return sum;
    }

    private boolean candidate(PodSpec pod) {
        if (!pod.movable()) return false;
        if (config.balancer().excludeNonReversible() && !pod.reversible()) return false;
        if (config.safety().excludedNamespaces().contains(pod.namespace())) return false;
        return true;
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private record Candidate(int i, int j, PodSpec podA, PodSpec podB,
                             double entropyAfter, double drop) {}
}
