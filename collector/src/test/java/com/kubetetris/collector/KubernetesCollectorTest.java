package com.kubetetris.collector;

import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.SnapshotView;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class KubernetesCollectorTest {

    KubernetesClient client;

    @Test
    void collectsNodesAndPodsFromTheClusterAndClassifiesReversibility() {
        client.nodes().resource(node("worker-a", "4", "8Gi", "us-east-1a", "m5.large", false, true)).create();
        client.nodes().resource(node("worker-b", "2", "4Gi", "us-east-1b", "m5.medium", true, true)).create();

        // running deployment pod on worker-a
        client.pods().inNamespace("web")
                .resource(deploymentPod("web-a", "worker-a", "500m", "512Mi", "web-rs")).create();
        // stateful pod on worker-a
        client.pods().inNamespace("db")
                .resource(statefulPod("db-0", "worker-a", "1", "1Gi", "db")).create();
        // pending pod
        client.pods().inNamespace("web")
                .resource(pendingDeploymentPod("checkout", "300m", "600Mi", "checkout-rs")).create();
        // succeeded pod — must be ignored
        client.pods().inNamespace("jobs")
                .resource(succeededPod("done-1", "worker-a")).create();

        SnapshotView view = new KubernetesCollector().collect(client);

        assertThat(view.nodes()).hasSize(2);
        NodeState wa = view.nodes().stream().filter(n -> n.name().equals("worker-a")).findFirst().orElseThrow();
        NodeState wb = view.nodes().stream().filter(n -> n.name().equals("worker-b")).findFirst().orElseThrow();

        assertThat(wa.spec().allocatable().cpuMillicore()).isEqualTo(4000L);
        assertThat(wa.spec().allocatable().memoryMB()).isEqualTo(8192L);
        assertThat(wa.spec().zone()).isEqualTo("us-east-1a");
        assertThat(wa.spec().instanceType()).isEqualTo("m5.large");
        assertThat(wa.spec().cordoned()).isFalse();
        assertThat(wa.spec().ready()).isTrue();
        assertThat(wa.pods()).hasSize(2);

        assertThat(wb.spec().cordoned()).isTrue();
        assertThat(wb.pods()).isEmpty();

        PodSpec web = wa.pods().stream().filter(p -> p.name().equals("web-a")).findFirst().orElseThrow();
        assertThat(web.reversible()).isTrue();
        assertThat(web.ownerKind()).isEqualTo("ReplicaSet");
        assertThat(web.ownerName()).isEqualTo("web-rs");
        assertThat(web.request().cpuMillicore()).isEqualTo(500L);
        assertThat(web.request().memoryMB()).isEqualTo(512L);

        PodSpec db = wa.pods().stream().filter(p -> p.name().equals("db-0")).findFirst().orElseThrow();
        assertThat(db.reversible()).isFalse();
        assertThat(db.ownerKind()).isEqualTo("StatefulSet");

        assertThat(view.pending()).hasSize(1);
        PodSpec checkout = view.pending().get(0);
        assertThat(checkout.name()).isEqualTo("checkout");
        assertThat(checkout.request().cpuMillicore()).isEqualTo(300L);
        assertThat(checkout.request().memoryMB()).isEqualTo(600L);
    }

    @Test
    void emptyClusterProducesEmptySnapshot() {
        SnapshotView view = new KubernetesCollector().collect(client);
        assertThat(view.nodes()).isEmpty();
        assertThat(view.pending()).isEmpty();
    }

    // ---------- fixture helpers ----------

    private static io.fabric8.kubernetes.api.model.Node node(String name, String cpu, String mem,
                                                             String zone, String instanceType,
                                                             boolean cordoned, boolean ready) {
        return new NodeBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withLabels(Map.of(
                            "topology.kubernetes.io/zone", zone,
                            "node.kubernetes.io/instance-type", instanceType))
                .endMetadata()
                .withNewSpec().withUnschedulable(cordoned).endSpec()
                .withStatus(new NodeStatusBuilder()
                        .addToAllocatable("cpu", new Quantity(cpu))
                        .addToAllocatable("memory", new Quantity(mem))
                        .addNewCondition().withType("Ready").withStatus(ready ? "True" : "False").endCondition()
                        .build())
                .build();
    }

    private static Pod deploymentPod(String name, String nodeName, String cpu, String mem, String ownerName) {
        return new PodBuilder()
                .withNewMetadata()
                    .withName(name).withNamespace("web").withUid("uid-" + name)
                    .addNewOwnerReference()
                        .withKind("ReplicaSet").withName(ownerName).withApiVersion("apps/v1")
                        .withController(true).withUid("rs-uid-" + ownerName)
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec().withNodeName(nodeName)
                    .addNewContainer().withName("app")
                        .withResources(new ResourceRequirementsBuilder()
                                .addToRequests("cpu", new Quantity(cpu))
                                .addToRequests("memory", new Quantity(mem)).build())
                    .endContainer()
                .endSpec()
                .withNewStatus().withPhase("Running").withQosClass("Burstable").endStatus()
                .build();
    }

    private static Pod statefulPod(String name, String nodeName, String cpu, String mem, String ownerName) {
        return new PodBuilder(deploymentPod(name, nodeName, cpu, mem, ownerName))
                .editMetadata()
                    .withOwnerReferences(new io.fabric8.kubernetes.api.model.OwnerReferenceBuilder()
                            .withKind("StatefulSet").withName(ownerName).withApiVersion("apps/v1")
                            .withController(true).withUid("sts-uid-" + ownerName).build())
                .endMetadata()
                .build();
    }

    private static Pod pendingDeploymentPod(String name, String cpu, String mem, String ownerName) {
        return new PodBuilder(deploymentPod(name, null, cpu, mem, ownerName))
                .editSpec().withNodeName(null).endSpec()
                .withNewStatus().withPhase("Pending").endStatus()
                .build();
    }

    private static Pod succeededPod(String name, String nodeName) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace("jobs").withUid("uid-" + name).endMetadata()
                .withNewSpec().withNodeName(nodeName).endSpec()
                .withNewStatus().withPhase("Succeeded").endStatus()
                .build();
    }
}
