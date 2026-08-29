package com.kubetetris.executor;

import com.kubetetris.engine.domain.PlanStep;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ExecutorMultiMoveTest {

    KubernetesClient client;
    Journal journal;
    Executor executor;

    @BeforeEach
    void setup() {
        journal = new InMemoryJournal();
        executor = new Executor(journal, Clock.systemUTC(), Duration.ofMillis(10), EvictionStrategies.delete());
    }

    @Test
    void swapChainRunsTwoMovesBackToBack() {
        // Balancer swap: X on n1 ⇄ Y on n2. Two MOVEs, one after the other.
        client.nodes().resource(ExecutorFixtures.node("n1", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n2", false)).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("x", "n1", "ReplicaSet", "x-rs")).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("y", "n2", "ReplicaSet", "y-rs")).create();
        // Pre-created replacements at each target.
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("x-2", "n2", "ReplicaSet", "x-rs")).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("y-2", "n1", "ReplicaSet", "y-rs")).create();

        PlanStep moveX = PlanStep.move(ExecutorFixtures.podSpec("x", "ReplicaSet", "x-rs"), "n1", "n2");
        PlanStep moveY = PlanStep.move(ExecutorFixtures.podSpec("y", "ReplicaSet", "y-rs"), "n2", "n1");
        ExecutionRequest request = new ExecutionRequest("c-test", JournalEntry.Kind.BALANCER,
                "swap x ⇄ y", List.of(moveX, moveY), Duration.ofSeconds(2), false);

        ExecutionResult result = executor.execute(client, request, FaultInjectors.none());

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.DONE);
        var steps = journal.get(result.journalId()).orElseThrow().steps();
        assertThat(steps).hasSize(12);
        assertThat(steps).allMatch(s -> s.state() == JournalStep.State.DONE);
    }

    @Test
    void failureInSecondMoveAfterFirstCommittedMarksNeedsAttention() {
        client.nodes().resource(ExecutorFixtures.node("n1", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n2", false)).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("x", "n1", "ReplicaSet", "x-rs")).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("y", "n2", "ReplicaSet", "y-rs")).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("x-2", "n2", "ReplicaSet", "x-rs")).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("y-2", "n1", "ReplicaSet", "y-rs")).create();

        PlanStep moveX = PlanStep.move(ExecutorFixtures.podSpec("x", "ReplicaSet", "x-rs"), "n1", "n2");
        PlanStep moveY = PlanStep.move(ExecutorFixtures.podSpec("y", "ReplicaSet", "y-rs"), "n2", "n1");
        ExecutionRequest request = new ExecutionRequest("c-test", JournalEntry.Kind.BALANCER,
                "swap x ⇄ y", List.of(moveX, moveY), Duration.ofSeconds(2), false);

        // Fail move index 1's EVICT leg.
        ExecutionResult result = executor.execute(client, request,
                FaultInjectors.at(1, FailurePoint.EVICT, "second-evict-failure"));

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.NEEDS_ATTENTION);
        assertThat(result.detail()).contains("already committed");
    }
}
