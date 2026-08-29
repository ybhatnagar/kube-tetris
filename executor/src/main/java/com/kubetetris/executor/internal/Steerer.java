package com.kubetetris.executor.internal;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cordon-based steering. Before evicting a pod we cordon every node other than the target
 * so the controller's replacement pod can only land where we want it. After the leg
 * finishes (or fails), the cordoned nodes are restored to their prior state.
 *
 * <p>Node-affinity-based steering is a follow-up: it would be less invasive but needs a
 * controller-template patch to survive the replacement's creation, which requires more
 * care around concurrent writes and rollback.
 */
public final class Steerer {

    private static final Logger log = LoggerFactory.getLogger(Steerer.class);

    private Steerer() {}

    /**
     * Cordon every node other than {@code targetNode} that isn't already cordoned. The
     * names of nodes we touched are added to {@code touched} so they can be uncordoned
     * again on cleanup / rollback.
     */
    public static void cordonAllExcept(KubernetesClient client, String targetNode, Set<String> touched) {
        for (Node node : client.nodes().list().getItems()) {
            String name = node.getMetadata().getName();
            if (targetNode.equals(name)) continue;
            boolean alreadyCordoned = node.getSpec() != null
                    && Boolean.TRUE.equals(node.getSpec().getUnschedulable());
            if (alreadyCordoned) continue;
            client.nodes().withName(name).edit(n -> {
                n.getSpec().setUnschedulable(true);
                return n;
            });
            touched.add(name);
        }
    }

    /** Best-effort uncordon of every node in {@code names}. Errors are logged, not thrown. */
    public static void uncordonAll(KubernetesClient client, Set<String> names) {
        Set<String> pending = new LinkedHashSet<>(names);
        for (String name : pending) {
            try {
                client.nodes().withName(name).edit(n -> {
                    n.getSpec().setUnschedulable(false);
                    return n;
                });
                names.remove(name);
            } catch (Exception e) {
                log.warn("uncordon of {} failed: {}", name, e.getMessage());
            }
        }
    }
}
