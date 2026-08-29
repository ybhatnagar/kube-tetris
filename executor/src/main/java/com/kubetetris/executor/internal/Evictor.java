package com.kubetetris.executor.internal;

import com.kubetetris.engine.domain.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Removes a pod from the cluster. Real production runs will move to the policy/v1
 * Eviction subresource (which respects PDBs); the MVP delete path is enough for the
 * synthetic and mock-server exercise and keeps the surface area small.
 */
public final class Evictor {

    private Evictor() {}

    public static void evict(KubernetesClient client, PodSpec spec) {
        client.pods().inNamespace(spec.namespace()).withName(spec.name()).delete();
    }
}
