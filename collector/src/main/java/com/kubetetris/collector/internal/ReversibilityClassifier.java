package com.kubetetris.collector.internal;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;

import java.util.List;
import java.util.Set;

/**
 * Decides whether a pod is safe to evict + reschedule elsewhere (reversible) and whether
 * it's a system-critical workload the tool must never touch.
 *
 * <p>The taxonomy (conservative by default; the operator can opt in per plan later):
 * <ul>
 *   <li>{@code Deployment}/{@code ReplicaSet}/{@code ReplicationController} — reversible.</li>
 *   <li>{@code StatefulSet} — non-reversible (stable identity + bound PVC).</li>
 *   <li>{@code DaemonSet} — non-reversible (one-per-node by design).</li>
 *   <li>{@code Job}/{@code CronJob} — non-reversible (eviction may re-run work).</li>
 *   <li>Bare pod, static/mirror pod — non-reversible (no controller to recreate).</li>
 *   <li>{@code system-cluster-critical} / {@code system-node-critical} priority class — never touched.</li>
 *   <li>{@code hostPath} or {@code nodeName}-pinned or has any PVC — non-reversible (state/pinning).</li>
 * </ul>
 */
public final class ReversibilityClassifier {

    private static final Set<String> ALWAYS_MOVABLE_OWNERS = Set.of(
            "Deployment", "ReplicaSet", "ReplicationController");
    private static final Set<String> NEVER_MOVABLE_OWNERS = Set.of(
            "StatefulSet", "DaemonSet", "Job", "CronJob");
    private static final Set<String> CRITICAL_PRIORITY_CLASSES = Set.of(
            "system-cluster-critical", "system-node-critical");

    private ReversibilityClassifier() {}

    public static boolean isSystemCritical(Pod pod) {
        if (pod == null || pod.getSpec() == null) return false;
        String pc = pod.getSpec().getPriorityClassName();
        return pc != null && CRITICAL_PRIORITY_CLASSES.contains(pc);
    }

    public static boolean isReversible(Pod pod) {
        if (pod == null) return false;
        if (isSystemCritical(pod)) return false;
        if (isStaticOrMirrorPod(pod)) return false;

        PodSpec spec = pod.getSpec();
        if (spec == null) return false;

        // Pinned to a specific node by name — moving it means it comes back to the same node.
        if (spec.getNodeName() != null && spec.getNodeSelector() != null
                && spec.getNodeSelector().values().stream().anyMatch(v -> v != null && !v.isEmpty())) {
            // covered by later host-affinity check
        }

        // Bare pods (no controller) — deleting them destroys them; never movable.
        List<OwnerReference> owners = pod.getMetadata() == null ? null : pod.getMetadata().getOwnerReferences();
        if (owners == null || owners.isEmpty()) return false;

        String kind = ownerKind(pod);
        if (NEVER_MOVABLE_OWNERS.contains(kind)) return false;
        if (!ALWAYS_MOVABLE_OWNERS.contains(kind)) return false;

        // hostPath / emptyDir-with-state / PVC → conservatively non-reversible.
        if (hasHostPathVolume(spec)) return false;
        if (hasAnyPvc(spec)) return false;

        return true;
    }

    public static String ownerKind(Pod pod) {
        if (pod == null || pod.getMetadata() == null) return null;
        List<OwnerReference> owners = pod.getMetadata().getOwnerReferences();
        if (owners == null || owners.isEmpty()) return null;
        // Prefer the controller owner if one is flagged.
        for (OwnerReference o : owners) {
            if (Boolean.TRUE.equals(o.getController())) return o.getKind();
        }
        return owners.get(0).getKind();
    }

    public static String ownerName(Pod pod) {
        if (pod == null || pod.getMetadata() == null) return null;
        List<OwnerReference> owners = pod.getMetadata().getOwnerReferences();
        if (owners == null || owners.isEmpty()) return null;
        for (OwnerReference o : owners) {
            if (Boolean.TRUE.equals(o.getController())) return o.getName();
        }
        return owners.get(0).getName();
    }

    static boolean isStaticOrMirrorPod(Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getAnnotations() == null) return false;
        return pod.getMetadata().getAnnotations().containsKey("kubernetes.io/config.mirror")
                || pod.getMetadata().getAnnotations().containsKey("kubernetes.io/config.source");
    }

    static boolean hasHostPathVolume(PodSpec spec) {
        List<Volume> volumes = spec.getVolumes();
        if (volumes == null) return false;
        for (Volume v : volumes) if (v.getHostPath() != null) return true;
        return false;
    }

    static boolean hasAnyPvc(PodSpec spec) {
        List<Volume> volumes = spec.getVolumes();
        if (volumes == null) return false;
        for (Volume v : volumes) if (v.getPersistentVolumeClaim() != null) return true;
        return false;
    }
}
