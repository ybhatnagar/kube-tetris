package com.kubetetris.executor;

/**
 * Decides whether a specific leg of a specific move should fail. In production the default
 * ({@link FaultInjectors#none()}) never fails; in tests we can inject a controlled failure
 * at any {@link FailurePoint} to verify rollback.
 */
@FunctionalInterface
public interface FaultInjector {

    /**
     * @param moveIndex   zero-based index of the current move within the plan
     * @param point       the leg being evaluated
     * @return non-null message if the leg should fail; {@code null} to proceed
     */
    String shouldFail(int moveIndex, FailurePoint point);
}
