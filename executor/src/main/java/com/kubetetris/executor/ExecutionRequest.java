package com.kubetetris.executor;

import com.kubetetris.engine.domain.PlanStep;

import java.time.Duration;
import java.util.List;

/**
 * What the executor was asked to do, in one immutable value. Callers construct this from
 * a Scheduler or Balancer plan and hand it to the executor.
 *
 * <ul>
 *   <li>{@code clusterId} — cluster the plan applies to; used only for journal tagging.</li>
 *   <li>{@code kind} — SCHEDULER (a MOVE-then-PLACE plan) or BALANCER (a swap).</li>
 *   <li>{@code steps} — atomic MOVE / PLACE steps in the order they must execute.</li>
 *   <li>{@code readyTimeout} — how long to wait for a replacement pod's Ready condition
 *       before treating the leg as failed and rolling back.</li>
 *   <li>{@code dryRun} — when {@code true}, the executor plans and journals every leg but
 *       makes no cluster writes.</li>
 * </ul>
 */
public record ExecutionRequest(
        String clusterId,
        JournalEntry.Kind kind,
        String summary,
        List<PlanStep> steps,
        Duration readyTimeout,
        boolean dryRun
) {
    public ExecutionRequest {
        steps = List.copyOf(steps);
    }
}
