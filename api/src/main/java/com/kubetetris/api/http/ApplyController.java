package com.kubetetris.api.http;

import com.kubetetris.api.apply.ApplyService;
import com.kubetetris.api.dto.ApplyRequestDto;
import com.kubetetris.api.dto.ExecStepDto;
import com.kubetetris.api.dto.ExecutionDto;
import com.kubetetris.api.dto.HistoryEntryDto;
import com.kubetetris.api.dto.HistoryListDto;
import com.kubetetris.api.store.ClusterRegistry;
import com.kubetetris.executor.ExecutionOutcome;
import com.kubetetris.executor.ExecutionResult;
import com.kubetetris.executor.Journal;
import com.kubetetris.executor.JournalEntry;
import com.kubetetris.executor.JournalStep;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clusters/{id}")
public class ApplyController {

    private final ApplyService applyService;
    private final ClusterRegistry registry;
    private final Journal journal;

    public ApplyController(ApplyService applyService, ClusterRegistry registry, Journal journal) {
        this.applyService = applyService;
        this.registry = registry;
        this.journal = journal;
    }

    @PostMapping("/apply")
    public ResponseEntity<?> apply(@PathVariable String id, @RequestBody ApplyRequestDto body) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        try {
            ExecutionResult result = applyService.apply(id, body);
            JournalEntry entry = journal.get(result.journalId()).orElse(null);
            return ResponseEntity.status(entry == null ? HttpStatus.OK : HttpStatus.OK)
                    .body(toExecutionDto(entry, result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/apply/{journalId}")
    public ResponseEntity<ExecutionDto> pollApply(@PathVariable String id, @PathVariable String journalId) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        JournalEntry entry = journal.get(journalId).orElse(null);
        if (entry == null || !id.equals(entry.clusterId())) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toExecutionDto(entry, null));
    }

    @GetMapping("/history")
    public ResponseEntity<HistoryListDto> history(@PathVariable String id) {
        if (registry.find(id).isEmpty()) return ResponseEntity.notFound().build();
        List<HistoryEntryDto> entries = new ArrayList<>();
        for (JournalEntry e : journal.listForCluster(id)) entries.add(toHistoryEntryDto(e));
        return ResponseEntity.ok(new HistoryListDto(entries));
    }

    private static ExecutionDto toExecutionDto(JournalEntry entry, ExecutionResult result) {
        List<ExecStepDto> steps = new ArrayList<>();
        String status = "running";
        String detail = null;
        String journalId = entry != null ? entry.journalId() : (result != null ? result.journalId() : null);
        if (entry != null) {
            for (JournalStep s : entry.steps()) {
                steps.add(new ExecStepDto(s.seq(), s.label(), stateName(s.state()), s.detail()));
            }
            if (entry.outcome() != null) status = outcomeName(entry.outcome());
        }
        if (result != null) detail = result.detail();
        return new ExecutionDto(journalId, status, detail, steps);
    }

    private static HistoryEntryDto toHistoryEntryDto(JournalEntry e) {
        int evictions = 0;
        for (JournalStep s : e.steps()) {
            if (s.label() != null && s.label().toLowerCase().contains("evict") && s.state() == JournalStep.State.DONE) {
                evictions++;
            }
        }
        String effect = evictions + " evicted";
        return new HistoryEntryDto(
                e.journalId(),
                e.endedAt() != null ? e.endedAt() : e.startedAt(),
                e.kind() == JournalEntry.Kind.SCHEDULER ? "Scheduler" : "Balancer",
                e.summary(),
                effect,
                outcomeName(e.outcome())
        );
    }

    private static String stateName(JournalStep.State s) {
        return switch (s) {
            case PENDING -> "pending";
            case RUNNING -> "running";
            case DONE -> "done";
            case FAILED -> "failed";
            case REVERTED -> "reverted";
        };
    }

    private static String outcomeName(ExecutionOutcome o) {
        if (o == null) return "running";
        return switch (o) {
            case RUNNING -> "running";
            case DONE -> "applied";
            case ROLLED_BACK -> "rolled_back";
            case NEEDS_ATTENTION -> "needs_attention";
            case ABORTED -> "aborted";
        };
    }
}
