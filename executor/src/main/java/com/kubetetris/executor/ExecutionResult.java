package com.kubetetris.executor;

/** Outcome the executor returns from a synchronous run. */
public record ExecutionResult(String journalId, ExecutionOutcome outcome, String detail) {
}
