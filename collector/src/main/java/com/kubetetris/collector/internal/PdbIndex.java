package com.kubetetris.collector.internal;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirement;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cluster-wide index of {@code policy/v1 PodDisruptionBudget} status, used by the
 * collector to compute each pod's {@code pdbOk} flag before the engine plans. The real
 * authority is still the apiserver at eviction time; this is a pre-flight hint so the UI
 * can grey out APPLY buttons that would be refused.
 *
 * <p>Semantics:
 * <ul>
 *   <li>No matching PDB → eviction is allowed ({@code pdbOk = true}).</li>
 *   <li>A matching PDB with {@code status.disruptionsAllowed &gt; 0} → allowed.</li>
 *   <li>A matching PDB with {@code status.disruptionsAllowed == 0} → blocked
 *       ({@code pdbOk = false}).</li>
 *   <li>If PDBs can't be listed (older cluster, RBAC denied) the index is empty and
 *       every pod is treated as allowed; the apiserver still enforces at apply time.</li>
 * </ul>
 */
public final class PdbIndex {

    private static final Logger log = LoggerFactory.getLogger(PdbIndex.class);

    private final Map<String, List<PodDisruptionBudget>> byNamespace;

    private PdbIndex(Map<String, List<PodDisruptionBudget>> byNamespace) {
        this.byNamespace = byNamespace;
    }

    public static PdbIndex empty() {
        return new PdbIndex(Map.of());
    }

    public static PdbIndex from(KubernetesClient client) {
        Map<String, List<PodDisruptionBudget>> grouped = new HashMap<>();
        try {
            for (PodDisruptionBudget pdb : client.policy().v1().podDisruptionBudget()
                    .inAnyNamespace().list().getItems()) {
                if (pdb.getMetadata() == null) continue;
                String ns = pdb.getMetadata().getNamespace();
                if (ns == null) continue;
                grouped.computeIfAbsent(ns, k -> new ArrayList<>()).add(pdb);
            }
        } catch (KubernetesClientException e) {
            log.info("Could not list PodDisruptionBudgets: {}. Treating every pod as PDB-allowed; " +
                    "the apiserver still enforces at eviction time.", e.getMessage());
            return empty();
        }
        return new PdbIndex(grouped);
    }

    public boolean allowsEviction(Pod pod) {
        if (pod == null || pod.getMetadata() == null) return true;
        String ns = pod.getMetadata().getNamespace();
        List<PodDisruptionBudget> here = byNamespace.getOrDefault(ns, List.of());
        if (here.isEmpty()) return true;
        Map<String, String> podLabels = pod.getMetadata().getLabels();
        if (podLabels == null) podLabels = Map.of();
        for (PodDisruptionBudget pdb : here) {
            if (!matchesPod(pdb, podLabels)) continue;
            Integer allowed = pdb.getStatus() == null ? null : pdb.getStatus().getDisruptionsAllowed();
            if (allowed != null && allowed <= 0) return false;
        }
        return true;
    }

    private static boolean matchesPod(PodDisruptionBudget pdb, Map<String, String> podLabels) {
        if (pdb.getSpec() == null) return false;
        LabelSelector sel = pdb.getSpec().getSelector();
        if (sel == null) return false;
        Map<String, String> matchLabels = sel.getMatchLabels();
        if (matchLabels != null && !matchLabels.isEmpty()) {
            for (Map.Entry<String, String> e : matchLabels.entrySet()) {
                if (!e.getValue().equals(podLabels.get(e.getKey()))) return false;
            }
        }
        List<LabelSelectorRequirement> exprs = sel.getMatchExpressions();
        if (exprs != null) {
            for (LabelSelectorRequirement req : exprs) {
                if (!matchExpression(req, podLabels)) return false;
            }
        }
        // An empty selector matches everything per the k8s spec.
        return true;
    }

    private static boolean matchExpression(LabelSelectorRequirement req, Map<String, String> labels) {
        String key = req.getKey();
        String op = req.getOperator();
        List<String> values = req.getValues() == null ? List.of() : req.getValues();
        String value = labels.get(key);
        return switch (op) {
            case "In" -> value != null && values.contains(value);
            case "NotIn" -> value == null || !values.contains(value);
            case "Exists" -> labels.containsKey(key);
            case "DoesNotExist" -> !labels.containsKey(key);
            default -> true;
        };
    }
}
