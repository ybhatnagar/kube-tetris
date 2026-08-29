package com.kubetetris.executor;

import java.time.Instant;

/**
 * One step in the execution journal. Steps are append-only: they are updated by replacing
 * their state and detail as the leg progresses (pending → running → done | failed | reverted).
 */
public record JournalStep(
        int seq,
        String label,
        State state,
        String detail,
        Instant updatedAt
) {
    public enum State { PENDING, RUNNING, DONE, FAILED, REVERTED }

    public JournalStep withState(State state, String detail, Instant now) {
        return new JournalStep(seq, label, state, detail, now);
    }
}
