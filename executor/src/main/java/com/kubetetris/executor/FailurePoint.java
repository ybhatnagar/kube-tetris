package com.kubetetris.executor;

/**
 * Named legs of a single MOVE execution where a failure can be injected during tests or
 * where a real failure can occur in production. Rollback is the same either way: undo
 * everything already done in reverse order.
 */
public enum FailurePoint {

    /** Pre-flight checks (capacity, taints, reversibility). */
    PRE_FLIGHT,

    /** Applying temporary steering (cordon non-target nodes / patch affinity). */
    STEER,

    /** Calling the Eviction API. */
    EVICT,

    /** Waiting for the replacement pod to become Ready on the intended target. */
    WAIT_READY,

    /** Verifying the replacement is running on the intended target. */
    VERIFY,

    /** Cleaning up temporary steering (uncordon / revert affinity). */
    CLEANUP
}
