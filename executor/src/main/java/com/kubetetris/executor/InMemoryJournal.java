package com.kubetetris.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link Journal}. Suitable for local dev and tests; a persistent
 * implementation (file / database) can slot in behind the same interface.
 */
public final class InMemoryJournal implements Journal {

    private final Map<String, JournalEntry> byId = new ConcurrentHashMap<>();

    @Override
    public JournalEntry create(JournalEntry entry) {
        byId.put(entry.journalId(), entry);
        return entry;
    }

    @Override
    public Optional<JournalEntry> get(String journalId) {
        return Optional.ofNullable(byId.get(journalId));
    }

    @Override
    public List<JournalEntry> listForCluster(String clusterId) {
        List<JournalEntry> out = new ArrayList<>();
        for (JournalEntry e : byId.values()) {
            if (clusterId.equals(e.clusterId())) out.add(e);
        }
        out.sort(Comparator.comparing(JournalEntry::startedAt).reversed());
        return out;
    }

    @Override
    public JournalEntry updateSteps(String journalId, List<JournalStep> steps) {
        JournalEntry updated = byId.computeIfPresent(journalId, (k, existing) -> existing.withSteps(steps));
        if (updated == null) throw new IllegalStateException("No journal entry with id " + journalId);
        return updated;
    }

    @Override
    public JournalEntry updateOutcome(String journalId, ExecutionOutcome outcome, Instant endedAt) {
        JournalEntry updated = byId.computeIfPresent(journalId, (k, existing) -> existing.withOutcome(outcome, endedAt));
        if (updated == null) throw new IllegalStateException("No journal entry with id " + journalId);
        return updated;
    }
}
