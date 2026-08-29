package com.kubetetris.collector.internal;

import com.kubetetris.engine.domain.PodPins;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kubernetes Pod → engine {@link PodSpec}. Runs the reversibility classifier, computes
 * effective requests, and gathers placement pins (nodeSelector, hostPath, PVC list).
 */
public final class PodMapper {

    private PodMapper() {}

    public static PodSpec toSpec(Pod pod) {
        if (pod == null || pod.getMetadata() == null) return null;
        String uid = pod.getMetadata().getUid();
        String name = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();
        String ownerKind = ReversibilityClassifier.ownerKind(pod);
        String ownerName = ReversibilityClassifier.ownerName(pod);
        ResourceReq req = RequestsCalculator.forPod(pod);
        PodSpec.Qos qos = qosOf(pod);
        boolean reversible = ReversibilityClassifier.isReversible(pod);
        boolean systemCritical = ReversibilityClassifier.isSystemCritical(pod);
        PodPins pins = extractPins(pod);
        // pdbOk is defaulted to true; a real PDB check will land alongside the executor.
        return new PodSpec(uid, name, namespace, ownerKind, ownerName, req, qos,
                reversible, systemCritical, true, pins);
    }

    static PodSpec.Qos qosOf(Pod pod) {
        String qos = pod.getStatus() == null ? null : pod.getStatus().getQosClass();
        if (qos == null) return PodSpec.Qos.BURSTABLE;
        return switch (qos) {
            case "Guaranteed" -> PodSpec.Qos.GUARANTEED;
            case "BestEffort" -> PodSpec.Qos.BEST_EFFORT;
            default -> PodSpec.Qos.BURSTABLE;
        };
    }

    static PodPins extractPins(Pod pod) {
        if (pod.getSpec() == null) return PodPins.EMPTY;
        String selector = flattenNodeSelector(pod.getSpec().getNodeSelector());
        String hostPath = firstHostPath(pod);
        List<String> pvcs = pvcClaims(pod);
        return new PodPins(selector, null, null, hostPath, pvcs);
    }

    static String flattenNodeSelector(Map<String, String> selector) {
        if (selector == null || selector.isEmpty()) return null;
        return selector.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(","));
    }

    static String firstHostPath(Pod pod) {
        List<Volume> volumes = pod.getSpec().getVolumes();
        if (volumes == null) return null;
        for (Volume v : volumes) {
            if (v.getHostPath() != null) return v.getHostPath().getPath();
        }
        return null;
    }

    static List<String> pvcClaims(Pod pod) {
        List<Volume> volumes = pod.getSpec().getVolumes();
        if (volumes == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Volume v : volumes) {
            if (v.getPersistentVolumeClaim() != null) {
                out.add(v.getPersistentVolumeClaim().getClaimName());
            }
        }
        return out;
    }
}
