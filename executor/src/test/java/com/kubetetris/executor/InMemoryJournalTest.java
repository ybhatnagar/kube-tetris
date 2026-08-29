package com.kubetetris.executor;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryJournalTest {

    @Test
    void createAndReadRoundTrip() {
        InMemoryJournal j = new InMemoryJournal();
        JournalEntry e = newEntry("j1", "c-synth");
        j.create(e);

        assertThat(j.get("j1")).contains(e);
        assertThat(j.listForCluster("c-synth")).containsExactly(e);
    }

    @Test
    void listForClusterOrdersMostRecentFirst() {
        InMemoryJournal j = new InMemoryJournal();
        JournalEntry older = new JournalEntry("j1", "c1", JournalEntry.Kind.SCHEDULER, "s",
                Instant.parse("2026-01-01T00:00:00Z"), null, ExecutionOutcome.DONE, List.of());
        JournalEntry newer = new JournalEntry("j2", "c1", JournalEntry.Kind.SCHEDULER, "s",
                Instant.parse("2026-01-02T00:00:00Z"), null, ExecutionOutcome.DONE, List.of());
        j.create(older);
        j.create(newer);

        assertThat(j.listForCluster("c1")).extracting(JournalEntry::journalId).containsExactly("j2", "j1");
    }

    @Test
    void updateStepsReplacesTheEntrySteps() {
        InMemoryJournal j = new InMemoryJournal();
        j.create(newEntry("j1", "c1"));
        JournalStep step = new JournalStep(0, "Evict", JournalStep.State.RUNNING, null, Instant.parse("2026-01-01T00:00:00Z"));
        JournalEntry updated = j.updateSteps("j1", List.of(step));

        assertThat(updated.steps()).containsExactly(step);
        assertThat(j.get("j1").orElseThrow().steps()).containsExactly(step);
    }

    @Test
    void updateOutcomeStampsEndedAt() {
        InMemoryJournal j = new InMemoryJournal();
        j.create(newEntry("j1", "c1"));
        Instant end = Instant.parse("2026-01-03T00:00:00Z");
        JournalEntry updated = j.updateOutcome("j1", ExecutionOutcome.ROLLED_BACK, end);

        assertThat(updated.outcome()).isEqualTo(ExecutionOutcome.ROLLED_BACK);
        assertThat(updated.endedAt()).isEqualTo(end);
    }

    @Test
    void updatingAnUnknownEntryFails() {
        InMemoryJournal j = new InMemoryJournal();
        assertThatThrownBy(() -> j.updateSteps("missing", List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> j.updateOutcome("missing", ExecutionOutcome.DONE, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JournalEntry newEntry(String id, String clusterId) {
        return new JournalEntry(id, clusterId, JournalEntry.Kind.SCHEDULER, "summary",
                Instant.parse("2026-01-01T00:00:00Z"), null, ExecutionOutcome.RUNNING, List.of());
    }
}
