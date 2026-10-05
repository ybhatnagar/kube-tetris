package com.kubetetris.api.journal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kubetetris.executor.ExecutionOutcome;
import com.kubetetris.executor.Journal;
import com.kubetetris.executor.JournalEntry;
import com.kubetetris.executor.JournalStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Append-only {@link Journal} that persists each entry as a JSON file under a
 * configurable directory. Reads are served from an in-memory cache populated on
 * startup; writes update the cache first, then atomically replace the on-disk file.
 *
 * <p>The storage model is deliberately simple — one file per journal, named
 * {@code {journalId}.json} — so an operator can browse, back up, or hand-prune the
 * directory with ordinary tools. A sharded or database-backed implementation can slot
 * in behind the same interface later.
 */
public final class FileJournal implements Journal {

    private static final Logger log = LoggerFactory.getLogger(FileJournal.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Path baseDir;
    private final Map<String, JournalEntry> cache = new ConcurrentHashMap<>();

    public FileJournal(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create journal directory " + baseDir, e);
        }
        loadFromDisk();
    }

    private void loadFromDisk() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.json")) {
            for (Path file : stream) {
                try {
                    JournalEntry entry = MAPPER.readValue(file.toFile(), JournalEntry.class);
                    cache.put(entry.journalId(), entry);
                } catch (IOException e) {
                    log.warn("could not read journal file {}: {}", file, e.getMessage());
                }
            }
            log.info("loaded {} journal entries from {}", cache.size(), baseDir);
        } catch (IOException e) {
            log.warn("could not list journal directory {}: {}", baseDir, e.getMessage());
        }
    }

    @Override
    public JournalEntry create(JournalEntry entry) {
        cache.put(entry.journalId(), entry);
        write(entry);
        return entry;
    }

    @Override
    public Optional<JournalEntry> get(String journalId) {
        return Optional.ofNullable(cache.get(journalId));
    }

    @Override
    public List<JournalEntry> listForCluster(String clusterId) {
        List<JournalEntry> out = new ArrayList<>();
        for (JournalEntry e : cache.values()) {
            if (clusterId.equals(e.clusterId())) out.add(e);
        }
        out.sort(Comparator.comparing(JournalEntry::startedAt).reversed());
        return out;
    }

    @Override
    public JournalEntry updateSteps(String journalId, List<JournalStep> steps) {
        JournalEntry updated = cache.computeIfPresent(journalId, (k, existing) -> existing.withSteps(steps));
        if (updated == null) throw new IllegalStateException("No journal entry with id " + journalId);
        write(updated);
        return updated;
    }

    @Override
    public JournalEntry updateOutcome(String journalId, ExecutionOutcome outcome, Instant endedAt) {
        JournalEntry updated = cache.computeIfPresent(journalId, (k, existing) -> existing.withOutcome(outcome, endedAt));
        if (updated == null) throw new IllegalStateException("No journal entry with id " + journalId);
        write(updated);
        return updated;
    }

    private void write(JournalEntry entry) {
        Path file = baseDir.resolve(entry.journalId() + ".json");
        Path tmp = baseDir.resolve(entry.journalId() + ".json.tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), entry);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("could not persist journal entry {}: {}", entry.journalId(), e.getMessage());
        }
    }
}
