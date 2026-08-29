package com.kubetetris.executor.internal;

import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.executor.FailurePoint;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Polls for a controller-owned replacement pod on {@code targetNode} to become Ready. */
public final class ReadyWaiter {

    private ReadyWaiter() {}

    public static void waitFor(KubernetesClient client, PodSpec original, String targetNode,
                               Duration timeout, Duration pollInterval, Clock clock) {
        Instant deadline = Instant.now(clock).plus(timeout);
        while (Instant.now(clock).isBefore(deadline)) {
            if (findReadyReplacement(client, original, targetNode) != null) return;
            sleepQuietly(pollInterval);
        }
        throw new LegFailedException(FailurePoint.WAIT_READY,
                "timed out waiting for a Ready replacement of " + original.name() + " on " + targetNode);
    }

    public static Pod findReadyReplacement(KubernetesClient client, PodSpec original, String targetNode) {
        for (Pod pod : client.pods().inNamespace(original.namespace()).list().getItems()) {
            if (original.name().equals(pod.getMetadata().getName())) continue;
            if (!targetNode.equals(PodMatch.nodeOf(pod))) continue;
            if (!PodMatch.sameController(pod, original)) continue;
            if (!PodMatch.isReady(pod)) continue;
            return pod;
        }
        return null;
    }

    private static void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
