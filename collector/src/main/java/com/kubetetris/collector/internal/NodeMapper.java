package com.kubetetris.collector.internal;

import com.kubetetris.engine.domain.NodeSpec;
import com.kubetetris.engine.domain.ResourceReq;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.Taint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes Node → engine {@link NodeSpec}. Emits allocatable capacity plus operational
 * metadata (zone, instance type, taints, cordoned/ready flags).
 */
public final class NodeMapper {

    public static final String ZONE_LABEL = "topology.kubernetes.io/zone";
    public static final String INSTANCE_TYPE_LABEL = "node.kubernetes.io/instance-type";

    private NodeMapper() {}

    public static NodeSpec toSpec(Node node) {
        String name = node.getMetadata() == null ? null : node.getMetadata().getName();
        ResourceReq allocatable = allocatable(node);
        String zone = label(node, ZONE_LABEL);
        String instanceType = label(node, INSTANCE_TYPE_LABEL);
        boolean cordoned = node.getSpec() != null && Boolean.TRUE.equals(node.getSpec().getUnschedulable());
        boolean ready = isReady(node);
        List<String> taints = taints(node);
        return new NodeSpec(name, allocatable, zone, instanceType, cordoned, ready, taints);
    }

    static ResourceReq allocatable(Node node) {
        if (node.getStatus() == null || node.getStatus().getAllocatable() == null) {
            return ResourceReq.ZERO;
        }
        var allocatable = node.getStatus().getAllocatable();
        long cpu = Quantities.cpuMillicores(allocatable.get("cpu"));
        long mem = Quantities.memoryMib(allocatable.get("memory"));
        return new ResourceReq(cpu, mem);
    }

    static String label(Node node, String key) {
        if (node.getMetadata() == null) return null;
        Map<String, String> labels = node.getMetadata().getLabels();
        return labels == null ? null : labels.get(key);
    }

    static boolean isReady(Node node) {
        if (node.getStatus() == null || node.getStatus().getConditions() == null) return false;
        for (NodeCondition c : node.getStatus().getConditions()) {
            if ("Ready".equals(c.getType())) return "True".equals(c.getStatus());
        }
        return false;
    }

    static List<String> taints(Node node) {
        if (node.getSpec() == null || node.getSpec().getTaints() == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Taint t : node.getSpec().getTaints()) {
            StringBuilder sb = new StringBuilder();
            sb.append(t.getKey() == null ? "" : t.getKey());
            if (t.getValue() != null && !t.getValue().isEmpty()) sb.append('=').append(t.getValue());
            if (t.getEffect() != null && !t.getEffect().isEmpty()) sb.append(':').append(t.getEffect());
            out.add(sb.toString());
        }
        return out;
    }
}
