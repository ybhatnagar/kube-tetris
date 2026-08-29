package com.kubetetris.executor;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ExecutorSingleMoveTest {

    KubernetesClient client;
    Journal journal;
    Executor executor;

    @BeforeEach
    void setup() {
        journal = new InMemoryJournal();
        executor = new Executor(journal, Clock.systemUTC(), Duration.ofMillis(20));
    }

    @Test
    void happyPathRunsAllSixLegsToDone() {
        // Given three nodes and a running deployment pod on n1, plus a pre-created
        // "replacement" already ready on n2 (simulating what a controller would do
        // after eviction). The executor should evict from n1, find the replacement,
        // and finish DONE with the cluster uncordoned.
        client.nodes().resource(ExecutorFixtures.node("n1", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n2", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n3", false)).create();

        var original = ExecutorFixtures.runningPod("ranker", "n1", "ReplicaSet", "ranker-rs");
        var replacement = ExecutorFixtures.runningPod("ranker-2", "n2", "ReplicaSet", "ranker-rs");
        client.pods().inNamespace(ExecutorFixtures.NS).resource(original).create();
        client.pods().inNamespace(ExecutorFixtures.NS).resource(replacement).create();

        var request = ExecutorFixtures.singleMoveRequest("c-test",
                ExecutorFixtures.podSpec("ranker", "ReplicaSet", "ranker-rs"),
                "n1", "n2", Duration.ofSeconds(2), false);

        ExecutionResult result = executor.executeMove(client, request, FaultInjectors.none());

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.DONE);
        assertJournalStates(result.journalId(),
                JournalStep.State.DONE, JournalStep.State.DONE, JournalStep.State.DONE,
                JournalStep.State.DONE, JournalStep.State.DONE, JournalStep.State.DONE);

        // Original pod was evicted.
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNull();
        // Nodes were uncordoned after the move.
        for (String node : List.of("n1", "n3")) {
            assertThat(client.nodes().withName(node).get().getSpec().getUnschedulable())
                    .as("uncordoned after apply: " + node).isFalse();
        }
    }

    @Test
    void dryRunJournalsButMakesNoClusterWrites() {
        client.nodes().resource(ExecutorFixtures.node("n1", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n2", false)).create();
        var pod = ExecutorFixtures.runningPod("ranker", "n1", "ReplicaSet", "ranker-rs");
        client.pods().inNamespace(ExecutorFixtures.NS).resource(pod).create();

        var request = ExecutorFixtures.singleMoveRequest("c-test",
                ExecutorFixtures.podSpec("ranker", "ReplicaSet", "ranker-rs"),
                "n1", "n2", Duration.ofSeconds(1), true);

        ExecutionResult result = executor.executeMove(client, request, FaultInjectors.none());

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.DONE);
        // Cluster untouched.
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNotNull();
        assertThat(client.nodes().withName("n1").get().getSpec().getUnschedulable()).isFalse();
        assertThat(client.nodes().withName("n2").get().getSpec().getUnschedulable()).isFalse();
    }

    private void assertJournalStates(String journalId, JournalStep.State... expected) {
        var steps = journal.get(journalId).orElseThrow().steps();
        assertThat(steps).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(steps.get(i).state()).as("step " + i).isEqualTo(expected[i]);
        }
    }
}
