package com.kubetetris.api.journal;

import com.kubetetris.executor.ExecutionOutcome;
import com.kubetetris.executor.JournalEntry;
import com.kubetetris.executor.JournalStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileJournalTest {

    @Test
    void createsFileOnDiskAndReadsBack(@TempDir Path tmp) {
        FileJournal j = new FileJournal(tmp);
        j.create(entry("j1", "c1"));

        assertThat(tmp.resolve("j1.json")).exists();
        assertThat(j.get("j1")).isPresent();
    }

    @Test
    void outlivesProcessRestart(@TempDir Path tmp) {
        FileJournal first = new FileJournal(tmp);
        first.create(entry("j1", "c1"));
        first.updateSteps("j1", List.of(step("Evict", JournalStep.State.DONE)));
        first.updateOutcome("j1", ExecutionOutcome.DONE, Instant.parse("2026-10-01T00:00:00Z"));

        // Simulate a restart.
        FileJournal second = new FileJournal(tmp);
        JournalEntry recovered = second.get("j1").orElseThrow();
        assertThat(recovered.outcome()).isEqualTo(ExecutionOutcome.DONE);
        assertThat(recovered.steps()).hasSize(1);
        assertThat(recovered.steps().get(0).label()).isEqualTo("Evict");
    }

    @Test
    void listForClusterOrdersByStartTimeDesc(@TempDir Path tmp) {
        FileJournal j = new FileJournal(tmp);
        j.create(new JournalEntry("older", "c1", JournalEntry.Kind.SCHEDULER, "s",
                Instant.parse("2026-01-01T00:00:00Z"), null, ExecutionOutcome.DONE, List.of()));
        j.create(new JournalEntry("newer", "c1", JournalEntry.Kind.SCHEDULER, "s",
                Instant.parse("2026-02-01T00:00:00Z"), null, ExecutionOutcome.DONE, List.of()));

        assertThat(j.listForCluster("c1")).extracting(JournalEntry::journalId)
                .containsExactly("newer", "older");
    }

    private static JournalEntry entry(String id, String clusterId) {
        return new JournalEntry(id, clusterId, JournalEntry.Kind.BALANCER, "summary",
                Instant.parse("2026-01-01T00:00:00Z"), null, ExecutionOutcome.RUNNING, List.of());
    }

    private static JournalStep step(String label, JournalStep.State state) {
        return new JournalStep(0, label, state, null, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
