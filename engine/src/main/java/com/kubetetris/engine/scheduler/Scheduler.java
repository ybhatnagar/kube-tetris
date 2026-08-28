package com.kubetetris.engine.scheduler;

import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.domain.FeasibilityResult;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.engine.domain.WorkingCluster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure entry point over a {@link SnapshotView}. Both the single-pod recursion and the
 * multi-pod DP recursion are computed; the plan with fewer moves wins (ties → fewer
 * non-reversible touches, then smaller total request magnitude). The executor path is
 * out of scope: this class only computes plans.
 */
public final class Scheduler {

    private final EngineConfig config;

    public Scheduler(EngineConfig config) {
        this.config = config;
    }

    public FeasibilityResult plan(SnapshotView snapshot, PodSpec pending) {
        return plan(snapshot, pending, false);
    }

    public FeasibilityResult plan(SnapshotView snapshot, PodSpec pending, boolean optInNonReversible) {
        if (pending.request().isZero() && config.safety().requireRequests()) {
            return FeasibilityResult.infeasible(pending.uid(),
                    "Pending pod has no resource requests; footprint is unknown.");
        }
        long totalFreeCpu = 0L, totalFreeMem = 0L;
        for (NodeState n : snapshot.nodes()) {
            totalFreeCpu += n.free().cpuMillicore();
            totalFreeMem += n.free().memoryMB();
        }
        if (totalFreeCpu < pending.request().cpuMillicore()
                || totalFreeMem < pending.request().memoryMB()) {
            return FeasibilityResult.infeasible(pending.uid(),
                    "Σ free capacity < request; add a node (no migration can help).");
        }

        // Exhaustively explore each top-level candidate node and keep the minimum-moves plan
        // (ties broken by fewer non-reversible touches, then smaller total disruption).
        // Inner recursion (for displaced pods) remains greedy — first success wins there.
        Attempt bestSingle = exploreTopLevel(snapshot, pending, optInNonReversible, false);
        Attempt bestMulti = exploreTopLevel(snapshot, pending, optInNonReversible, true);

        return pickBetter(pending, bestSingle, bestMulti);
    }

    private Attempt exploreTopLevel(SnapshotView snapshot, PodSpec pending,
                                    boolean optInNonReversible, boolean multi) {
        // Try direct fit first (0 moves) — cheapest possible.
        for (NodeState n : snapshot.nodes()) {
            if (n.fits(pending.request())) {
                Attempt a = new Attempt(config, optInNonReversible);
                WorkingCluster wc = WorkingCluster.from(snapshot);
                a.emitPlacement(wc, pending, wc.node(n.name()));
                return a;
            }
        }
        // Otherwise iterate priority-ordered top-level candidates and keep the best.
        Attempt best = null;
        for (int nodeIdx : Priority.nodeOrderByDeficit(pending.request(), snapshot.nodes())) {
            String target = snapshot.nodes().get(nodeIdx).name();
            Attempt a = new Attempt(config, optInNonReversible);
            WorkingCluster wc = WorkingCluster.from(snapshot);
            boolean ok = multi
                    ? a.placeOnTargetMulti(wc, pending, target)
                    : a.placeOnTargetSingle(wc, pending, target);
            if (!ok) continue;
            if (best == null || compareAttempts(a, best) < 0) best = a;
        }
        return best;
    }

    private FeasibilityResult pickBetter(PodSpec pending, Attempt single, Attempt multi) {
        if (single == null && multi == null) {
            return FeasibilityResult.infeasible(pending.uid(),
                    "No feasible placement within recursion/iteration caps.");
        }
        Attempt winner;
        FeasibilityResult.Strategy strategy;
        if (single != null && multi != null) {
            int cmp = compareAttempts(single, multi);
            winner = cmp <= 0 ? single : multi;
            strategy = winner == single ? FeasibilityResult.Strategy.SINGLE : FeasibilityResult.Strategy.MULTI;
        } else if (single != null) {
            winner = single;
            strategy = FeasibilityResult.Strategy.SINGLE;
        } else {
            winner = multi;
            strategy = FeasibilityResult.Strategy.MULTI;
        }
        List<PlanStep> finalizedPlan = finalizePlan(winner.plan);
        int moves = (int) finalizedPlan.stream().filter(s -> s.kind() == PlanStep.Kind.MOVE).count();
        String target = finalizedPlan.stream()
                .filter(s -> s.kind() == PlanStep.Kind.PLACE && s.pod().uid().equals(pending.uid()))
                .map(PlanStep::toNode).findFirst().orElse(null);
        boolean touchesNonReversible = finalizedPlan.stream()
                .filter(s -> s.kind() == PlanStep.Kind.MOVE)
                .anyMatch(s -> !s.pod().reversible());
        return new FeasibilityResult(pending.uid(), true, null, strategy,
                moves, target, touchesNonReversible, List.copyOf(finalizedPlan));
    }

    /**
     * The raw plan built during recursion emits (MOVE-off from N, PLACE-land on M) as two
     * steps per human "move". The public plan uses atomic {@code MOVE(from, to)} steps.
     * Finalize pairs the raw ops and sequences deepest-chain moves first, which is the
     * order the executor must apply them in to preserve capacity invariants.
     */
    static List<PlanStep> finalizePlan(List<PlanStep> raw) {
        Map<String, String> evictedFrom = new HashMap<>();
        List<PlanStep> atomicMoves = new ArrayList<>();
        List<PlanStep> placements = new ArrayList<>();
        for (PlanStep s : raw) {
            if (s.kind() == PlanStep.Kind.MOVE && s.toNode() == null) {
                evictedFrom.put(s.pod().uid(), s.fromNode());
            } else if (s.kind() == PlanStep.Kind.PLACE) {
                String from = evictedFrom.remove(s.pod().uid());
                if (from != null) {
                    atomicMoves.add(PlanStep.move(s.pod(), from, s.toNode()));
                } else {
                    placements.add(s);
                }
            }
        }
        List<PlanStep> out = new ArrayList<>(atomicMoves.size() + placements.size());
        for (int i = atomicMoves.size() - 1; i >= 0; i--) out.add(atomicMoves.get(i));
        out.addAll(placements);
        return out;
    }

    private int compareAttempts(Attempt a, Attempt b) {
        int moveDelta = a.moveCount() - b.moveCount();
        if (moveDelta != 0) return moveDelta;
        if (config.scheduler().preferFewerNonReversible()) {
            int nrDelta = a.nonReversibleTouches() - b.nonReversibleTouches();
            if (nrDelta != 0) return nrDelta;
        }
        return Long.compare(a.disruptionMagnitude(), b.disruptionMagnitude());
    }

    /** Encapsulates one recursion attempt (single-pod or multi-pod) and its resulting plan. */
    static final class Attempt {

        private final EngineConfig config;
        private final boolean optInNonReversible;
        final List<PlanStep> plan = new ArrayList<>();
        private final Set<String> visited = new HashSet<>();
        private final Set<String> newlyPlaced = new HashSet<>();
        private int iterations;

        Attempt(EngineConfig config, boolean optInNonReversible) {
            this.config = config;
            this.optInNonReversible = optInNonReversible;
        }

        int moveCount() {
            return (int) plan.stream().filter(s -> s.kind() == PlanStep.Kind.MOVE).count();
        }

        int nonReversibleTouches() {
            return (int) plan.stream()
                    .filter(s -> s.kind() == PlanStep.Kind.MOVE)
                    .filter(s -> !s.pod().reversible())
                    .count();
        }

        long disruptionMagnitude() {
            return plan.stream()
                    .filter(s -> s.kind() == PlanStep.Kind.MOVE)
                    .mapToLong(s -> s.pod().request().cpuMillicore() + s.pod().request().memoryMB())
                    .sum();
        }

        boolean placeOnTargetSingle(WorkingCluster wc, PodSpec placePod, String targetNodeName) {
            NodeState node = wc.node(targetNodeName);
            if (node == null) return false;
            if (!incrementIter()) return false;
            if (node.fits(placePod.request())) {
                emitPlacement(wc, placePod, node);
                return true;
            }
            ResourceReq deficit = deficit(placePod.request(), node.free());
            List<PodSpec> eligible = eligibleForSingle(node, deficit);
            if (eligible.isEmpty()) return false;
            PodSpec evict = pickMinimumMigratable(eligible);
            if (evict == null) return false;
            String key = visitedKey(evict.uid(), node.name());
            if (!visited.add(key)) return false;
            node.removePod(evict.uid());
            plan.add(PlanStep.move(evict, node.name(), null));
            plan.add(PlanStep.place(placePod, node.name()));
            node.addPod(placePod);
            newlyPlaced.add(placePod.uid());
            if (recurseSingle(wc, evict, 1)) {
                return true;
            }
            node.removePod(placePod.uid());
            newlyPlaced.remove(placePod.uid());
            node.addPod(evict);
            plan.remove(plan.size() - 1);
            plan.remove(plan.size() - 1);
            visited.remove(key);
            return false;
        }

        boolean placeOnTargetMulti(WorkingCluster wc, PodSpec placePod, String targetNodeName) {
            NodeState node = wc.node(targetNodeName);
            if (node == null) return false;
            if (!incrementIter()) return false;
            if (node.fits(placePod.request())) {
                emitPlacement(wc, placePod, node);
                return true;
            }
            ResourceReq deficit = deficit(placePod.request(), node.free());
            List<PodSpec> pool = allMovableExcludingPlaced(node);
            List<PodSpec> chosen = minimumSubsetCovering(pool, deficit);
            if (chosen.isEmpty()) return false;
            for (PodSpec p : chosen) {
                if (!visited.add(visitedKey(p.uid(), node.name()))) {
                    for (PodSpec q : chosen) visited.remove(visitedKey(q.uid(), node.name()));
                    return false;
                }
            }
            List<PodSpec> removed = new ArrayList<>();
            for (PodSpec p : chosen) {
                node.removePod(p.uid());
                plan.add(PlanStep.move(p, node.name(), null));
                removed.add(p);
            }
            plan.add(PlanStep.place(placePod, node.name()));
            node.addPod(placePod);
            newlyPlaced.add(placePod.uid());

            boolean allPlaced = true;
            for (PodSpec p : new ArrayList<>(removed)) {
                if (!recurseMulti(wc, p, 1)) {
                    allPlaced = false;
                    break;
                }
            }
            if (allPlaced) return true;

            node.removePod(placePod.uid());
            newlyPlaced.remove(placePod.uid());
            for (PodSpec p : removed) node.addPod(p);
            plan.remove(plan.size() - 1);
            for (int i = 0; i < removed.size(); i++) plan.remove(plan.size() - 1);
            for (PodSpec p : chosen) visited.remove(visitedKey(p.uid(), node.name()));
            return false;
        }

        private boolean incrementIter() {
            iterations++;
            return iterations <= config.scheduler().maxIterations();
        }

        private boolean recurseSingle(WorkingCluster wc, PodSpec placePod, int depth) {
            if (!incrementIter()) return false;
            if (depth > config.scheduler().maxRecursionDepth()) return false;
            NodeState direct = firstFit(wc, placePod);
            if (direct != null) {
                emitPlacement(wc, placePod, direct);
                return true;
            }
            for (int nodeIdx : Priority.nodeOrderByDeficit(placePod.request(), wc.nodes())) {
                NodeState node = wc.nodes().get(nodeIdx);
                ResourceReq deficit = deficit(placePod.request(), node.free());
                List<PodSpec> eligible = eligibleForSingle(node, deficit);
                if (eligible.isEmpty()) continue;
                PodSpec evict = pickMinimumMigratable(eligible);
                if (evict == null) continue;
                String key = visitedKey(evict.uid(), node.name());
                if (!visited.add(key)) continue;
                node.removePod(evict.uid());
                plan.add(PlanStep.move(evict, node.name(), null));
                plan.add(PlanStep.place(placePod, node.name()));
                node.addPod(placePod);
                newlyPlaced.add(placePod.uid());
                if (recurseSingle(wc, evict, depth + 1)) {
                    return true;
                }
                // undo
                node.removePod(placePod.uid());
                newlyPlaced.remove(placePod.uid());
                node.addPod(evict);
                plan.remove(plan.size() - 1);
                plan.remove(plan.size() - 1);
                visited.remove(key);
            }
            return false;
        }

        private boolean recurseMulti(WorkingCluster wc, PodSpec placePod, int depth) {
            if (!incrementIter()) return false;
            if (depth > config.scheduler().maxRecursionDepth()) return false;
            NodeState direct = firstFit(wc, placePod);
            if (direct != null) {
                emitPlacement(wc, placePod, direct);
                return true;
            }
            for (int nodeIdx : Priority.nodeOrderByDeficit(placePod.request(), wc.nodes())) {
                NodeState node = wc.nodes().get(nodeIdx);
                ResourceReq deficit = deficit(placePod.request(), node.free());
                List<PodSpec> pool = allMovableExcludingPlaced(node);
                List<PodSpec> chosen = minimumSubsetCovering(pool, deficit);
                if (chosen.isEmpty()) continue;
                for (PodSpec p : chosen) {
                    String key = visitedKey(p.uid(), node.name());
                    if (!visited.add(key)) {
                        // undo any adds we made this iteration and skip this node
                        rollbackVisitedForIter(chosen, node.name());
                        chosen = List.of();
                        break;
                    }
                }
                if (chosen.isEmpty()) continue;

                List<PodSpec> removed = new ArrayList<>();
                for (PodSpec p : chosen) {
                    node.removePod(p.uid());
                    plan.add(PlanStep.move(p, node.name(), null));
                    removed.add(p);
                }
                plan.add(PlanStep.place(placePod, node.name()));
                node.addPod(placePod);
                newlyPlaced.add(placePod.uid());

                boolean allPlaced = true;
                List<PodSpec> stillToPlace = new ArrayList<>(removed);
                for (PodSpec p : stillToPlace) {
                    if (!recurseMulti(wc, p, depth + 1)) {
                        allPlaced = false;
                        break;
                    }
                }
                if (allPlaced) return true;

                // rollback plan additions and cluster state
                node.removePod(placePod.uid());
                newlyPlaced.remove(placePod.uid());
                for (PodSpec p : removed) node.addPod(p);
                plan.remove(plan.size() - 1); // PLACE
                for (int i = 0; i < removed.size(); i++) plan.remove(plan.size() - 1);
                for (PodSpec p : chosen) visited.remove(visitedKey(p.uid(), node.name()));
            }
            return false;
        }

        private void rollbackVisitedForIter(List<PodSpec> chosen, String nodeName) {
            for (PodSpec p : chosen) visited.remove(visitedKey(p.uid(), nodeName));
        }

        void emitPlacement(WorkingCluster wc, PodSpec placePod, NodeState target) {
            target.addPod(placePod);
            plan.add(PlanStep.place(placePod, target.name()));
            newlyPlaced.add(placePod.uid());
        }

        private NodeState firstFit(WorkingCluster wc, PodSpec pod) {
            for (NodeState n : wc.nodes()) if (n.fits(pod.request())) return n;
            return null;
        }

        private ResourceReq deficit(ResourceReq need, ResourceReq free) {
            return new ResourceReq(
                    Math.max(0L, need.cpuMillicore() - free.cpuMillicore()),
                    Math.max(0L, need.memoryMB() - free.memoryMB())
            );
        }

        private List<PodSpec> eligibleForSingle(NodeState node, ResourceReq deficit) {
            List<PodSpec> out = new ArrayList<>();
            for (PodSpec p : node.pods()) {
                if (!isMovable(p)) continue;
                if (newlyPlaced.contains(p.uid())) continue; // never re-evict what we just placed
                if (p.request().covers(deficit)) out.add(p);
            }
            return out;
        }

        private List<PodSpec> allMovableExcludingPlaced(NodeState node) {
            List<PodSpec> out = new ArrayList<>();
            for (PodSpec p : node.pods()) {
                if (!isMovable(p)) continue;
                if (newlyPlaced.contains(p.uid())) continue;
                out.add(p);
            }
            return out;
        }

        private boolean isMovable(PodSpec p) {
            if (!p.movable()) return false;
            if (!p.reversible() && !optInNonReversible) return false;
            if (config.safety().excludedNamespaces().contains(p.namespace())) return false;
            return true;
        }

        /**
         * DP over an eligible pool: find the minimum-size subset whose summed request
         * covers the deficit in both dimensions. Ported from
         * {@code CapacityPlacementServiceImpl#computeMinimumMigrateablePods} with the
         * mem-branch/cpu-branch copy-paste bug corrected and List-key hashing avoided.
         */
        static List<PodSpec> minimumSubsetCovering(List<PodSpec> pool, ResourceReq deficit) {
            if (pool.isEmpty()) return List.of();
            if (deficit.isZero()) return List.of();
            // sort by (cpu+mem) desc so we cover deficits fast
            List<PodSpec> sorted = new ArrayList<>(pool);
            sorted.sort(Comparator.<PodSpec>comparingLong(p -> -(p.request().cpuMillicore() + p.request().memoryMB())));
            int[] bestSize = {Integer.MAX_VALUE};
            List<PodSpec> bestPick = new ArrayList<>();
            List<PodSpec> current = new ArrayList<>();
            search(sorted, 0, deficit, 0, 0, current, bestSize, bestPick);
            return bestPick.isEmpty() ? List.of() : List.copyOf(bestPick);
        }

        private static void search(List<PodSpec> pool, int idx, ResourceReq deficit,
                                   long cpuSum, long memSum,
                                   List<PodSpec> current, int[] bestSize, List<PodSpec> bestPick) {
            if (cpuSum >= deficit.cpuMillicore() && memSum >= deficit.memoryMB()) {
                if (current.size() < bestSize[0]) {
                    bestSize[0] = current.size();
                    bestPick.clear();
                    bestPick.addAll(current);
                }
                return;
            }
            if (idx == pool.size()) return;
            if (current.size() + 1 >= bestSize[0]) return; // prune
            PodSpec p = pool.get(idx);
            current.add(p);
            search(pool, idx + 1, deficit,
                    cpuSum + p.request().cpuMillicore(),
                    memSum + p.request().memoryMB(),
                    current, bestSize, bestPick);
            current.remove(current.size() - 1);
            search(pool, idx + 1, deficit, cpuSum, memSum, current, bestSize, bestPick);
        }

        /**
         * From an eligible list (each individually covers the deficit), pick the pod
         * whose eviction disturbs the cluster least, using (cpu + mem) sum as the
         * disruption proxy and uid as a deterministic tiebreak.
         */
        static PodSpec pickMinimumMigratable(List<PodSpec> eligible) {
            if (eligible.isEmpty()) return null;
            return eligible.stream()
                    .min(Comparator.<PodSpec>comparingLong(p -> p.request().cpuMillicore() + p.request().memoryMB())
                            .thenComparing(PodSpec::uid))
                    .orElse(null);
        }

        private static String visitedKey(String podUid, String nodeName) {
            return podUid + "@" + nodeName;
        }
    }
}
