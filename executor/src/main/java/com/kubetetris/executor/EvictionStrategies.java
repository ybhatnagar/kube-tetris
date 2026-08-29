package com.kubetetris.executor;

import com.kubetetris.executor.internal.LegFailedException;
import io.fabric8.kubernetes.client.KubernetesClientException;

/**
 * Two implementations of {@link EvictionStrategy}:
 *
 * <ul>
 *   <li>{@link #policyV1Eviction()} — production default. Uses the
 *       {@code policy/v1} Eviction subresource, which lets the API server enforce the
 *       pod's PodDisruptionBudget. A 429 from the API server is surfaced as an explicit
 *       PDB-blocked failure so the executor can roll back cleanly.</li>
 *   <li>{@link #delete()} — plain {@code DELETE} on the pod. No PDB check. Used by
 *       tests against Fabric8's mock server (which currently does not model the
 *       Eviction subresource) and available for environments where eviction is
 *       intentionally disabled.</li>
 * </ul>
 */
public final class EvictionStrategies {

    private EvictionStrategies() {}

    public static EvictionStrategy policyV1Eviction() {
        return (client, spec) -> {
            try {
                boolean evicted = client.pods().inNamespace(spec.namespace()).withName(spec.name()).evict();
                if (!evicted) {
                    // Fabric8's evict() returns false for 429 (PDB blocked) as well as for
                    // "pod already gone". Both signal that we must not proceed with the leg.
                    throw new LegFailedException(FailurePoint.EVICT,
                            "eviction refused for " + spec.name() +
                            " — likely blocked by its PodDisruptionBudget or the pod is already gone");
                }
            } catch (KubernetesClientException e) {
                if (e.getCode() == 429) {
                    throw new LegFailedException(FailurePoint.EVICT,
                            "eviction blocked by PodDisruptionBudget for " + spec.name());
                }
                throw new LegFailedException(FailurePoint.EVICT,
                        "eviction failed for " + spec.name() + ": " + e.getMessage());
            }
        };
    }

    public static EvictionStrategy delete() {
        return (client, spec) -> client.pods().inNamespace(spec.namespace()).withName(spec.name()).delete();
    }
}
