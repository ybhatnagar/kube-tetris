package com.kubetetris.executor;

import java.util.List;
import java.util.Optional;

/**
 * Append-only execution log. Reads are safe from any thread while an execution is in
 * flight; writes happen only from the executor thread that owns the entry. Implementations
 * that persist to a file or database must uphold the same guarantees.
 */
public interface Journal {

    JournalEntry create(JournalEntry entry);

    Optional<JournalEntry> get(String journalId);

    List<JournalEntry> listForCluster(String clusterId);

    /** Replaces the entry's steps (used to append a new step or update an existing one). */
    JournalEntry updateSteps(String journalId, List<JournalStep> steps);

    JournalEntry updateOutcome(String journalId, ExecutionOutcome outcome, java.time.Instant endedAt);
}
