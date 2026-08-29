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
class AbortSignalTest {

    KubernetesClient client;
    Journal journal;
    Executor executor;

    @BeforeEach
    void setup() {
        journal = new InMemoryJournal();
        executor = new Executor(journal, Clock.systemUTC(), Duration.ofMillis(10), EvictionStrategies.delete());
    }

    @Test
    void abortBeforeFirstMoveResultsInAbortedOutcomeWithNoClusterWrites() {
        client.nodes().resource(ExecutorFixtures.node("n1", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n2", false)).create();
        client.pods().inNamespace(ExecutorFixtures.NS)
                .resource(ExecutorFixtures.runningPod("x", "n1", "ReplicaSet", "x-rs")).create();

        PlanStep move = PlanStep.move(ExecutorFixtures.podSpec("x", "ReplicaSet", "x-rs"), "n1", "n2");
        ExecutionRequest request = new ExecutionRequest("c-test", JournalEntry.Kind.BALANCER,
                "swap", List.of(move), Duration.ofSeconds(1), false);

        AbortSignal signal = new AbortSignal();
        signal.abort();   // pre-abort — should short-circuit before the first move even starts

        ExecutionResult result = executor.execute(client, request, FaultInjectors.none(), signal);
        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.ABORTED);
        // Cluster is untouched.
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("x").get()).isNotNull();
    }

    @Test
    void abortBetweenTwoMovesAbortsSecondAndReportsPartialCommit() {
        // Given a two-move chain where the first move completes normally, we then flip the
        // abort flag before the second move runs by using a fault injector as a hook.
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

        AbortSignal signal = new AbortSignal();
        FaultInjector abortAfterFirst = (i, p) -> {
            if (i == 0 && p == FailurePoint.CLEANUP) signal.abort();
            return null;
        };

        PlanStep moveX = PlanStep.move(ExecutorFixtures.podSpec("x", "ReplicaSet", "x-rs"), "n1", "n2");
        PlanStep moveY = PlanStep.move(ExecutorFixtures.podSpec("y", "ReplicaSet", "y-rs"), "n2", "n1");
        ExecutionRequest request = new ExecutionRequest("c-test", JournalEntry.Kind.BALANCER,
                "swap x ⇄ y", List.of(moveX, moveY), Duration.ofSeconds(2), false);

        ExecutionResult result = executor.execute(client, request, abortAfterFirst, signal);
        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.ABORTED);
        assertThat(result.detail()).contains("1 earlier move(s) already committed");
    }
}
