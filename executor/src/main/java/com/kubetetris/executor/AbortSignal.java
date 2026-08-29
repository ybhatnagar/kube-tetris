package com.kubetetris.executor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative kill switch checked by the executor between legs. Flipping {@link #abort()}
 * from another thread causes the current apply to roll back with outcome
 * {@link ExecutionOutcome#ABORTED}. Between-leg checks mean an in-flight leg completes
 * or fails on its own terms before the abort takes effect; the executor never leaves the
 * cluster in an unknown state to satisfy an abort.
 */
public final class AbortSignal {

    private final AtomicBoolean aborted = new AtomicBoolean(false);

    public void abort() { aborted.set(true); }

    public boolean isAborted() { return aborted.get(); }

    /** No-op signal for callers that don't need abort support. */
    public static AbortSignal never() { return new AbortSignal(); }
}
