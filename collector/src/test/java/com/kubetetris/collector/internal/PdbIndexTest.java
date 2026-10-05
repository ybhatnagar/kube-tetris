package com.kubetetris.collector.internal;

import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class PdbIndexTest {

    KubernetesClient client;

    @Test
    void podWithoutMatchingPdbIsAllowed() {
        givenPdb("web", "web-pdb", Map.of("app", "web"), 0);
        PdbIndex idx = PdbIndex.from(client);

        Pod unmatched = podWithLabels("payments", "p1", Map.of("app", "ledger"));
        assertThat(idx.allowsEviction(unmatched)).isTrue();
    }

    @Test
    void podMatchingAFullBudgetIsBlocked() {
        givenPdb("web", "web-pdb", Map.of("app", "web"), 0);
        PdbIndex idx = PdbIndex.from(client);

        Pod matched = podWithLabels("web", "api", Map.of("app", "web"));
        assertThat(idx.allowsEviction(matched)).isFalse();
    }

    @Test
    void podMatchingAnOpenBudgetIsAllowed() {
        givenPdb("web", "web-pdb", Map.of("app", "web"), 1);
        PdbIndex idx = PdbIndex.from(client);

        Pod matched = podWithLabels("web", "api", Map.of("app", "web"));
        assertThat(idx.allowsEviction(matched)).isTrue();
    }

    @Test
    void matchExpressionsAreHonored() {
        PodDisruptionBudget pdb = new PodDisruptionBudgetBuilder()
                .withNewMetadata().withName("tier-pdb").withNamespace("web").endMetadata()
                .withNewSpec()
                    .withSelector(new LabelSelectorBuilder()
                            .withMatchExpressions(new LabelSelectorRequirementBuilder()
                                    .withKey("tier").withOperator("In").withValues("frontend").build())
                            .build())
                .endSpec()
                .withNewStatus().withDisruptionsAllowed(0).endStatus()
                .build();
        client.policy().v1().podDisruptionBudget().inNamespace("web").resource(pdb).create();

        PdbIndex idx = PdbIndex.from(client);
        assertThat(idx.allowsEviction(podWithLabels("web", "a", Map.of("tier", "frontend")))).isFalse();
        assertThat(idx.allowsEviction(podWithLabels("web", "b", Map.of("tier", "backend")))).isTrue();
    }

    @Test
    void emptyIndexAllowsEverything() {
        PdbIndex idx = PdbIndex.empty();
        assertThat(idx.allowsEviction(podWithLabels("web", "x", Map.of("app", "web")))).isTrue();
    }

    private void givenPdb(String ns, String name, Map<String, String> labels, int disruptionsAllowed) {
        PodDisruptionBudget pdb = new PodDisruptionBudgetBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).endMetadata()
                .withNewSpec()
                    .withSelector(new LabelSelectorBuilder().withMatchLabels(labels).build())
                .endSpec()
                .withNewStatus().withDisruptionsAllowed(disruptionsAllowed).endStatus()
                .build();
        client.policy().v1().podDisruptionBudget().inNamespace(ns).resource(pdb).create();
    }

    private Pod podWithLabels(String ns, String name, Map<String, String> labels) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).withLabels(labels).endMetadata()
                .build();
    }
}
