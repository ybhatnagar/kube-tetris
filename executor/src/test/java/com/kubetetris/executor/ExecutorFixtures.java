package com.kubetetris.executor;

import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.engine.domain.PodPins;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;

import java.time.Duration;
import java.util.List;

final class ExecutorFixtures {

    static final String NS = "web";

    private ExecutorFixtures() {}

    static Node node(String name, boolean cordoned) {
        return new NodeBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec().withUnschedulable(cordoned).endSpec()
                .withNewStatus()
                    .addNewCondition().withType("Ready").withStatus("True").endCondition()
                .endStatus()
                .build();
    }

    static Pod runningPod(String name, String node, String ownerKind, String ownerName) {
        return new PodBuilder()
                .withNewMetadata()
                    .withName(name).withNamespace(NS).withUid("uid-" + name)
                    .addNewOwnerReference()
                        .withKind(ownerKind).withName(ownerName).withApiVersion("apps/v1")
                        .withController(true).withUid("owner-" + ownerName)
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec().withNodeName(node).endSpec()
                .withNewStatus()
                    .withPhase("Running")
                    .addNewCondition().withType("Ready").withStatus("True").endCondition()
                .endStatus()
                .build();
    }

    static PodSpec podSpec(String name, String ownerKind, String ownerName) {
        return new PodSpec("uid-" + name, name, NS, ownerKind, ownerName,
                new ResourceReq(100, 100), PodSpec.Qos.BURSTABLE,
                true, false, true, PodPins.EMPTY);
    }

    static ExecutionRequest singleMoveRequest(String clusterId, PodSpec pod,
                                              String fromNode, String toNode,
                                              Duration timeout, boolean dryRun) {
        PlanStep move = PlanStep.move(pod, fromNode, toNode);
        return new ExecutionRequest(clusterId, JournalEntry.Kind.BALANCER,
                "MOVE " + pod.name() + " " + fromNode + "→" + toNode,
                List.of(move), timeout, dryRun);
    }
}
