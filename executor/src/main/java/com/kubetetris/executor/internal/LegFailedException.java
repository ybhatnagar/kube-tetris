package com.kubetetris.executor.internal;

import com.kubetetris.executor.FailurePoint;

/** Internal signal that a specific execution leg failed. */
public final class LegFailedException extends RuntimeException {

    private final FailurePoint point;

    public LegFailedException(FailurePoint point, String message) {
        super(message);
        this.point = point;
    }

    public FailurePoint point() { return point; }
}
