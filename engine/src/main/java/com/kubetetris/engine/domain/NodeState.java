package com.kubetetris.engine.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-plan mutable working state for a node. Never mutate a NodeState that came from the
 * snapshot directly — {@link WorkingCluster#snapshot} clones on entry. Ported from the
 * 2018 Node.addPod/removePod but decoupled from k8s I/O and stripped of the back-pointer
 * to the pod's parent node (that alias was a subtle bug source during recursion).
 */
public final class NodeState {

    private final NodeSpec spec;
    private ResourceReq free;
    private final Map<String, PodSpec> pods = new LinkedHashMap<>();

    public NodeState(NodeSpec spec, List<PodSpec> initialPods) {
        this.spec = spec;
        ResourceReq f = spec.allocatable();
        for (PodSpec p : initialPods) {
            pods.put(p.uid(), p);
            f = f.minus(p.request());
        }
        this.free = f;
    }

    private NodeState(NodeSpec spec, ResourceReq free, Map<String, PodSpec> pods) {
        this.spec = spec;
        this.free = free;
        this.pods.putAll(pods);
    }

    public NodeSpec spec() { return spec; }
    public String name() { return spec.name(); }
    public ResourceReq free() { return free; }

    public List<PodSpec> pods() { return List.copyOf(pods.values()); }

    public boolean fits(ResourceReq request) {
        return free.covers(request);
    }

    public boolean addPod(PodSpec pod) {
        if (!fits(pod.request())) return false;
        pods.put(pod.uid(), pod);
        free = free.minus(pod.request());
        return true;
    }

    public boolean removePod(String podUid) {
        PodSpec removed = pods.remove(podUid);
        if (removed == null) return false;
        free = free.plus(removed.request());
        return true;
    }

    public NodeState copy() {
        return new NodeState(spec, free, pods);
    }

    /** Sort pods by CPU request desc; ties broken by uid for determinism. */
    public List<PodSpec> podsByCpuDesc() {
        List<PodSpec> out = new ArrayList<>(pods.values());
        out.sort((a, b) -> {
            int c = Long.compare(b.request().cpuMillicore(), a.request().cpuMillicore());
            return c != 0 ? c : a.uid().compareTo(b.uid());
        });
        return out;
    }

    /** Sort pods by memory request desc; ties broken by uid. */
    public List<PodSpec> podsByMemDesc() {
        List<PodSpec> out = new ArrayList<>(pods.values());
        out.sort((a, b) -> {
            int c = Long.compare(b.request().memoryMB(), a.request().memoryMB());
            return c != 0 ? c : a.uid().compareTo(b.uid());
        });
        return out;
    }

    public double cpuMemRatio() {
        return free.cpuMemRatio();
    }

    public double distanceFromPivot(double pivot) {
        return Math.abs(pivot - cpuMemRatio());
    }

    @Override
    public String toString() {
        return "NodeState{" + name() + ", free=" + free + ", pods=" + pods.keySet() + "}";
    }

    /** Snapshot as an immutable list of pod uids currently on this node (for asserts). */
    public List<String> podUidsView() {
        return Collections.unmodifiableList(new ArrayList<>(pods.keySet()));
    }
}
