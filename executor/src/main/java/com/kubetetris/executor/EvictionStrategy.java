package com.kubetetris.executor;

import com.kubetetris.engine.domain.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;

/** How the executor should remove a pod. */
@FunctionalInterface
public interface EvictionStrategy {

    /**
     * Remove {@code spec} from the cluster. Should throw
     * {@link com.kubetetris.executor.internal.LegFailedException} — with
     * {@link FailurePoint#EVICT} — when the eviction is refused (e.g. by a PDB) or the
     * pod is unexpectedly missing.
     */
    void evict(KubernetesClient client, PodSpec spec);
}
