package com.kubetetris.executor;

/** Factory for common {@link FaultInjector} strategies. */
public final class FaultInjectors {

    private FaultInjectors() {}

    /** Never inject a failure — the production default. */
    public static FaultInjector none() {
        return (moveIndex, point) -> null;
    }

    /** Fail the specified leg of the specified move exactly once. */
    public static FaultInjector at(int moveIndex, FailurePoint point, String message) {
        return (i, p) -> (i == moveIndex && p == point) ? message : null;
    }
}
