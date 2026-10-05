package com.kubetetris.collector;

import com.kubetetris.collector.internal.NodeMapper;
import com.kubetetris.collector.internal.PdbIndex;
import com.kubetetris.collector.internal.PodMapper;
import com.kubetetris.engine.domain.NodeSpec;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.SnapshotView;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only collector: reads Nodes + Pods from the target cluster and produces a
 * {@link SnapshotView} the engine can plan against. Nothing writes to the cluster.
 */
public class KubernetesCollector {

    private static final Logger log = LoggerFactory.getLogger(KubernetesCollector.class);

    /** Collect using kubeconfig / context described by {@code ctx}. */
    public SnapshotView collect(KubeContext ctx) {
        Config config = loadConfig(ctx);
        try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
            return collect(client);
        }
    }

    /**
     * Collect using a caller-supplied client. Handy for tests using
     * {@code KubernetesMockServer}; the collector doesn't close the client in this form.
     */
    public SnapshotView collect(KubernetesClient client) {
        List<Node> k8sNodes;
        List<Pod> allPods;
        try {
            k8sNodes = client.nodes().list().getItems();
            allPods = client.pods().inAnyNamespace().list().getItems();
        } catch (KubernetesClientException e) {
            log.warn("Kubernetes list call failed: {}", e.getMessage());
            throw e;
        }
        PdbIndex pdbs = PdbIndex.from(client);

        Map<String, List<PodSpec>> podsByNode = new HashMap<>();
        List<PodSpec> pending = new ArrayList<>();
        for (Pod pod : allPods) {
            String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
            if ("Succeeded".equals(phase) || "Failed".equals(phase)) continue;

            PodSpec spec = PodMapper.toSpec(pod, pdbs);
            if (spec == null) continue;

            String nodeName = pod.getSpec() == null ? null : pod.getSpec().getNodeName();
            if (nodeName == null || nodeName.isEmpty()) {
                if ("Pending".equals(phase)) pending.add(spec);
                continue;
            }
            podsByNode.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(spec);
        }

        List<NodeState> nodes = new ArrayList<>(k8sNodes.size());
        for (Node k8sNode : k8sNodes) {
            NodeSpec spec = NodeMapper.toSpec(k8sNode);
            if (spec == null || spec.name() == null) continue;
            List<PodSpec> onNode = podsByNode.getOrDefault(spec.name(), List.of());
            nodes.add(new NodeState(spec, onNode));
        }

        return new SnapshotView(nodes, pending);
    }

    private static Config loadConfig(KubeContext ctx) {
        if (ctx == null || ctx.kubeConfigPath() == null) {
            return Config.autoConfigure(ctx == null ? null : ctx.contextName());
        }
        try {
            String yaml = Files.readString(Path.of(ctx.kubeConfigPath()));
            return Config.fromKubeconfig(ctx.contextName(), yaml, ctx.kubeConfigPath());
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Could not read kubeconfig at " + ctx.kubeConfigPath() + ": " + e.getMessage(), e);
        }
    }
}
