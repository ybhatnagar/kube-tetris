package com.kubetetris.executor;

import java.time.Instant;
import java.util.List;

/**
 * A single execution's durable audit + rollback record. Every apply produces exactly one
 * entry regardless of outcome, and successful, rolled-back, and needs-attention entries
 * are all preserved so operators can audit what happened.
 */
public record JournalEntry(
        String journalId,
        String clusterId,
        Kind kind,
        String summary,
        Instant startedAt,
        Instant endedAt,
        ExecutionOutcome outcome,
        List<JournalStep> steps
) {
    public enum Kind { SCHEDULER, BALANCER }

    public JournalEntry withSteps(List<JournalStep> newSteps) {
        return new JournalEntry(journalId, clusterId, kind, summary, startedAt, endedAt, outcome, newSteps);
    }

    public JournalEntry withOutcome(ExecutionOutcome outcome, Instant endedAt) {
        return new JournalEntry(journalId, clusterId, kind, summary, startedAt, endedAt, outcome, steps);
    }
}
