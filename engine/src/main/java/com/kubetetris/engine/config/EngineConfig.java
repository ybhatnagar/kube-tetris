package com.kubetetris.engine.config;

import java.util.List;
import java.util.Set;

/**
 * All tunables live here. Nothing in the algorithms is hard-coded. Shape mirrors
 * design-docs/06 §4. Defaults chosen for the M1 synthetic fixtures; production
 * defaults will be re-tuned at M6.
 */
public record EngineConfig(Scheduler scheduler, Balancer balancer, Safety safety) {

    public static EngineConfig defaults() {
        return new EngineConfig(Scheduler.defaults(), Balancer.defaults(), Safety.defaults());
    }

    public record Scheduler(
            int maxRecursionDepth,
            int maxIterations,
            boolean preferFewerNonReversible
    ) {
        public static Scheduler defaults() {
            return new Scheduler(16, 256, true);
        }
    }

    public record Balancer(
            int maxSwaps,
            double minImprovementPct,
            double epsilon,
            boolean excludeNonReversible,
            String namespaceFilter
    ) {
        public static Balancer defaults() {
            return new Balancer(5, 3.0d, 1e-6d, true, null);
        }
    }

    public record Safety(
            boolean excludeNonReversible,
            Set<String> excludedNamespaces,
            List<String> protectedPriorityClasses,
            boolean requireRequests
    ) {
        public static Safety defaults() {
            return new Safety(true, Set.of(), List.of("system-cluster-critical", "system-node-critical"), true);
        }
    }
}
