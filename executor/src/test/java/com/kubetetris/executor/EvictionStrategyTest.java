package com.kubetetris.executor;

import com.kubetetris.engine.domain.PodPins;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import com.kubetetris.executor.internal.LegFailedException;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mock server does not model the {@code policy/v1} Eviction subresource end-to-end,
 * so these tests use canned responses via {@link KubernetesMockServer#expect()} to
 * assert the strategy translates each HTTP outcome into the right leg failure.
 */
@EnableKubernetesMockClient
class EvictionStrategyTest {

    KubernetesMockServer server;
    KubernetesClient client;

    @Test
    void pdbBlockedEvictionSurfacesAsLegFailure() {
        PodSpec pod = new PodSpec("uid-x", "x", "web", "ReplicaSet", "x-rs",
                new ResourceReq(100, 100), PodSpec.Qos.BURSTABLE, true, false, true, PodPins.EMPTY);

        // Fabric8's evict() first GETs the pod. Provide it, then answer 429 on the eviction POST.
        server.expect()
                .get()
                .withPath("/api/v1/namespaces/web/pods/x")
                .andReturn(200, new PodBuilder().withNewMetadata()
                        .withName("x").withNamespace("web").withUid("uid-x").endMetadata().build())
                .always();
        server.expect()
                .post()
                .withPath("/api/v1/namespaces/web/pods/x/eviction")
                .andReturn(429, new StatusBuilder()
                        .withCode(429).withMessage("PDB denies eviction").build())
                .always();

        assertThatThrownBy(() -> EvictionStrategies.policyV1Eviction().evict(client, pod))
                .isInstanceOf(LegFailedException.class)
                .hasMessageContaining("PodDisruptionBudget");
    }

    @Test
    void generalKubernetesErrorSurfacesAsLegFailure() {
        PodSpec pod = new PodSpec("uid-x", "x", "web", "ReplicaSet", "x-rs",
                new ResourceReq(100, 100), PodSpec.Qos.BURSTABLE, true, false, true, PodPins.EMPTY);

        server.expect()
                .get()
                .withPath("/api/v1/namespaces/web/pods/x")
                .andReturn(200, new PodBuilder().withNewMetadata()
                        .withName("x").withNamespace("web").withUid("uid-x").endMetadata().build())
                .always();
        server.expect()
                .post()
                .withPath("/api/v1/namespaces/web/pods/x/eviction")
                .andReturn(500, new StatusBuilder()
                        .withCode(500).withMessage("apiserver on fire").build())
                .always();

        assertThatThrownBy(() -> EvictionStrategies.policyV1Eviction().evict(client, pod))
                .isInstanceOf(LegFailedException.class)
                .hasMessageContaining("eviction failed");
    }

    @Test
    void deleteStrategyRoutesToPlainDelete() {
        PodSpec pod = new PodSpec("uid-x", "x", "web", "ReplicaSet", "x-rs",
                new ResourceReq(100, 100), PodSpec.Qos.BURSTABLE, true, false, true, PodPins.EMPTY);
        // Fabric8's delete() returns silently even if the pod doesn't exist — the strategy
        // just calls it and doesn't throw. This asserts that shape.
        EvictionStrategies.delete().evict(client, pod);
    }
}
