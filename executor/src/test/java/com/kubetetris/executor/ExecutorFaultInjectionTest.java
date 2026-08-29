package com.kubetetris.executor;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Injects a failure at each named leg of a single MOVE and asserts the executor rolls
 * back correctly. Legs prior to EVICT (PRE_FLIGHT, STEER, EVICT itself) should end
 * ROLLED_BACK — no writes escaped to the cluster or the writes that did are reversed.
 * Legs after EVICT (WAIT_READY, VERIFY, CLEANUP) leave the cluster in an ambiguous state
 * — the pod is already gone — so the outcome is NEEDS_ATTENTION.
 */
@EnableKubernetesMockClient(crud = true)
class ExecutorFaultInjectionTest {

    KubernetesClient client;
    Journal journal;
    Executor executor;

    @BeforeEach
    void setup() {
        journal = new InMemoryJournal();
        executor = new Executor(journal, Clock.systemUTC(), Duration.ofMillis(10), EvictionStrategies.delete());
    }

    @Test
    void preFlightFailureRollsBackWithoutTouchingTheCluster() {
        var result = runFailingAt(FailurePoint.PRE_FLIGHT);
        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.ROLLED_BACK);
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNotNull();
        assertThat(client.nodes().withName("n2").get().getSpec().getUnschedulable()).isFalse();
    }

    @Test
    void steerFailureRollsBackWithoutTouchingTheCluster() {
        var result = runFailingAt(FailurePoint.STEER);
        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.ROLLED_BACK);
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNotNull();
    }

    @Test
    void evictionFailureUncordonsAndRollsBack() {
        var result = runFailingAt(FailurePoint.EVICT);
        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.ROLLED_BACK);
        // Cordoning was applied then reversed.
        assertThat(client.nodes().withName("n1").get().getSpec().getUnschedulable()).isFalse();
        assertThat(client.nodes().withName("n3").get().getSpec().getUnschedulable()).isFalse();
        // Pod is still on the origin.
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNotNull();
    }

    @Test
    void waitReadyTimeoutMarksNeedsAttention() {
        // No pre-created replacement — the WAIT_READY poll will time out.
        setupCluster(false);
        var request = ExecutorFixtures.singleMoveRequest("c-test",
                ExecutorFixtures.podSpec("ranker", "ReplicaSet", "ranker-rs"),
                "n1", "n2", Duration.ofMillis(50), false);

        var result = executor.executeMove(client, request, FaultInjectors.none());

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.NEEDS_ATTENTION);
        // The pod was evicted; the cluster was uncordoned so the controller can recover.
        assertThat(client.nodes().withName("n1").get().getSpec().getUnschedulable()).isFalse();
        assertThat(client.nodes().withName("n3").get().getSpec().getUnschedulable()).isFalse();
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNull();
    }

    @Test
    void verifyFailureAfterEvictionMarksNeedsAttention() {
        setupCluster(true);
        var request = ExecutorFixtures.singleMoveRequest("c-test",
                ExecutorFixtures.podSpec("ranker", "ReplicaSet", "ranker-rs"),
                "n1", "n2", Duration.ofSeconds(1), false);

        var result = executor.executeMove(client, request,
                FaultInjectors.at(0, FailurePoint.VERIFY, "verify-failure"));

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.NEEDS_ATTENTION);
        assertThat(client.pods().inNamespace(ExecutorFixtures.NS).withName("ranker").get()).isNull();
    }

    @Test
    void cleanupFailureAfterVerifiedSuccessMarksNeedsAttention() {
        setupCluster(true);
        var request = ExecutorFixtures.singleMoveRequest("c-test",
                ExecutorFixtures.podSpec("ranker", "ReplicaSet", "ranker-rs"),
                "n1", "n2", Duration.ofSeconds(1), false);

        var result = executor.executeMove(client, request,
                FaultInjectors.at(0, FailurePoint.CLEANUP, "cleanup-failure"));

        // Move succeeded but cleanup didn't — cluster may still be cordoned.
        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.NEEDS_ATTENTION);
    }

    private ExecutionResult runFailingAt(FailurePoint point) {
        setupCluster(true);
        var request = ExecutorFixtures.singleMoveRequest("c-test",
                ExecutorFixtures.podSpec("ranker", "ReplicaSet", "ranker-rs"),
                "n1", "n2", Duration.ofSeconds(1), false);
        return executor.executeMove(client, request,
                FaultInjectors.at(0, point, "injected-" + point));
    }

    private void setupCluster(boolean withReplacement) {
        client.nodes().resource(ExecutorFixtures.node("n1", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n2", false)).create();
        client.nodes().resource(ExecutorFixtures.node("n3", false)).create();

        var original = ExecutorFixtures.runningPod("ranker", "n1", "ReplicaSet", "ranker-rs");
        client.pods().inNamespace(ExecutorFixtures.NS).resource(original).create();
        if (withReplacement) {
            var replacement = ExecutorFixtures.runningPod("ranker-2", "n2", "ReplicaSet", "ranker-rs");
            client.pods().inNamespace(ExecutorFixtures.NS).resource(replacement).create();
        }
    }
}
