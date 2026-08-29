package com.kubetetris.executor.internal;

import com.kubetetris.engine.domain.PodSpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;

import java.util.List;

/** Helpers for identifying a controller-owned replacement pod and checking readiness. */
public final class PodMatch {

    private PodMatch() {}

    /** Same controller as the evicted pod (same ownerRef name) — proxy for "same workload". */
    public static boolean sameController(Pod candidate, PodSpec original) {
        if (candidate.getMetadata() == null) return false;
        List<OwnerReference> owners = candidate.getMetadata().getOwnerReferences();
        if (owners == null) return false;
        for (OwnerReference o : owners) {
            if (Boolean.TRUE.equals(o.getController()) && matches(o, original)) return true;
        }
        for (OwnerReference o : owners) {
            if (matches(o, original)) return true;
        }
        return false;
    }

    private static boolean matches(OwnerReference o, PodSpec original) {
        return original.ownerKind() != null && original.ownerKind().equals(o.getKind())
                && original.ownerName() != null && original.ownerName().equals(o.getName());
    }

    /** True if the pod has status Running and a Ready condition of True. */
    public static boolean isReady(Pod pod) {
        if (pod.getStatus() == null) return false;
        if (!"Running".equals(pod.getStatus().getPhase())) return false;
        List<PodCondition> conditions = pod.getStatus().getConditions();
        if (conditions == null) return false;
        for (PodCondition c : conditions) {
            if ("Ready".equals(c.getType())) return "True".equals(c.getStatus());
        }
        return false;
    }

    public static String nodeOf(Pod pod) {
        return pod.getSpec() == null ? null : pod.getSpec().getNodeName();
    }
}
