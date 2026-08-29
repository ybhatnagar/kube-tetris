package com.kubetetris.executor;

/** Terminal state of an execution run. */
public enum ExecutionOutcome {
    RUNNING,
    DONE,
    ROLLED_BACK,
    NEEDS_ATTENTION,
    ABORTED
}
