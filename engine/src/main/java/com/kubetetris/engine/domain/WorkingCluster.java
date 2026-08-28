package com.kubetetris.engine.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable per-plan scratchpad. The engine's public entry points build a {@code
 * WorkingCluster} from a {@link SnapshotView} on entry and mutate only the scratchpad,
 * so the snapshot itself stays immutable across a planning session.
 */
public final class WorkingCluster {

    private final List<NodeState> nodes;
    private final Map<String, NodeState> byName;
    private final Map<String, NodeState> podHome;

    public WorkingCluster(List<NodeState> nodes) {
        this.nodes = new ArrayList<>(nodes.size());
        this.byName = new LinkedHashMap<>();
        this.podHome = new LinkedHashMap<>();
        for (NodeState src : nodes) {
            NodeState copy = src.copy();
            this.nodes.add(copy);
            this.byName.put(copy.name(), copy);
            for (PodSpec p : copy.pods()) podHome.put(p.uid(), copy);
        }
    }

    public static WorkingCluster from(SnapshotView view) {
        return new WorkingCluster(view.nodes());
    }

    public List<NodeState> nodes() { return List.copyOf(nodes); }

    public NodeState node(String name) { return byName.get(name); }

    public NodeState homeOf(String podUid) { return podHome.get(podUid); }

    public boolean movePod(PodSpec pod, NodeState target) {
        NodeState home = podHome.get(pod.uid());
        if (home == null || home == target) return false;
        if (!target.fits(pod.request())) return false;
        if (!home.removePod(pod.uid())) return false;
        if (!target.addPod(pod)) {
            home.addPod(pod);
            return false;
        }
        podHome.put(pod.uid(), target);
        return true;
    }

    public boolean placeNewPod(PodSpec pod, NodeState target) {
        if (podHome.containsKey(pod.uid())) return false;
        if (!target.addPod(pod)) return false;
        podHome.put(pod.uid(), target);
        return true;
    }

    public void evictPod(PodSpec pod) {
        NodeState home = podHome.get(pod.uid());
        if (home != null && home.removePod(pod.uid())) {
            podHome.remove(pod.uid());
        }
    }

    public WorkingCluster copy() {
        return new WorkingCluster(nodes);
    }
}
