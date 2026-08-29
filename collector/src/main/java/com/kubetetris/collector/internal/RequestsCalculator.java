package com.kubetetris.collector.internal;

import com.kubetetris.engine.domain.ResourceReq;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

import java.util.List;

/**
 * Computes a pod's effective resource footprint from its containers' declared requests.
 *
 * <p>Sums {@code requests.cpu} and {@code requests.memory} across all normal containers.
 * Init containers are handled as the running-time footprint dictates: init containers
 * run sequentially before the workload, so the effective steady-state footprint uses the
 * {@code max} over init containers rather than the sum. Sidecars (init containers with
 * {@code restartPolicy: Always}) run alongside main containers, so their requests are
 * summed instead of maxed. Missing requests are treated as zero.
 */
public final class RequestsCalculator {

    private RequestsCalculator() {}

    public static ResourceReq forPod(Pod pod) {
        if (pod == null || pod.getSpec() == null) return ResourceReq.ZERO;

        long cpuMillis = 0L;
        long memMib = 0L;

        List<Container> containers = pod.getSpec().getContainers();
        if (containers != null) {
            for (Container c : containers) {
                cpuMillis += cpuOf(c);
                memMib += memOf(c);
            }
        }

        // Sidecar init containers (restartPolicy: Always) run alongside main containers; sum them.
        // Regular init containers run once; use the max as their steady-state contribution.
        long maxRegularInitCpu = 0L;
        long maxRegularInitMem = 0L;
        List<Container> initContainers = pod.getSpec().getInitContainers();
        if (initContainers != null) {
            for (Container c : initContainers) {
                if ("Always".equals(c.getRestartPolicy())) {
                    cpuMillis += cpuOf(c);
                    memMib += memOf(c);
                } else {
                    maxRegularInitCpu = Math.max(maxRegularInitCpu, cpuOf(c));
                    maxRegularInitMem = Math.max(maxRegularInitMem, memOf(c));
                }
            }
        }
        cpuMillis = Math.max(cpuMillis, maxRegularInitCpu);
        memMib = Math.max(memMib, maxRegularInitMem);

        return new ResourceReq(cpuMillis, memMib);
    }

    private static long cpuOf(Container c) {
        Quantity q = requests(c) == null ? null : requests(c).get("cpu");
        return Quantities.cpuMillicores(q);
    }

    private static long memOf(Container c) {
        Quantity q = requests(c) == null ? null : requests(c).get("memory");
        return Quantities.memoryMib(q);
    }

    private static java.util.Map<String, Quantity> requests(Container c) {
        ResourceRequirements r = c.getResources();
        return r == null ? null : r.getRequests();
    }
}
