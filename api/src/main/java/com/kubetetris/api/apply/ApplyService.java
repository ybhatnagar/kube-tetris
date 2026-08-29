package com.kubetetris.api.apply;

import com.kubetetris.api.dto.ApplyRequestDto;
import com.kubetetris.api.store.ClusterRecord;
import com.kubetetris.api.store.ClusterRegistry;
import com.kubetetris.api.store.SnapshotStore;
import com.kubetetris.engine.balancer.Balancer;
import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.domain.BalancePlanResult;
import com.kubetetris.engine.domain.FeasibilityResult;
import com.kubetetris.engine.domain.PlanStep;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.SnapshotView;
import com.kubetetris.engine.domain.SwapStep;
import com.kubetetris.engine.scheduler.Scheduler;
import com.kubetetris.executor.AbortSignal;
import com.kubetetris.executor.ExecutionOutcome;
import com.kubetetris.executor.ExecutionRequest;
import com.kubetetris.executor.ExecutionResult;
import com.kubetetris.executor.Executor;
import com.kubetetris.executor.FaultInjectors;
import com.kubetetris.executor.Journal;
import com.kubetetris.executor.JournalEntry;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Config;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Orchestrates the write path. For each apply request:
 * <ol>
 *   <li>Enforce a per-cluster single-in-flight lock.</li>
 *   <li>Resolve the pending pod / swap on the latest snapshot into a plan.</li>
 *   <li>Route to {@link SyntheticApplier} for the synth cluster or to the real
 *       {@link Executor} for anything else.</li>
 *   <li>Run the execution on a background thread and return the initial
 *       {@link ExecutionResult} (status {@code running}) immediately. The client polls
 *       {@code GET /apply/:journalId} for updates and can call
 *       {@code POST /apply/:journalId:abort} to stop mid-flight.</li>
 * </ol>
 */
@Service
public class ApplyService {

    private static final Logger log = LoggerFactory.getLogger(ApplyService.class);

    private final ClusterRegistry registry;
    private final SnapshotStore snapshots;
    private final Scheduler scheduler;
    private final EngineConfig engineConfig;
    private final Journal journal;
    private final Executor executor;
    private final Clock clock;
    private final SyntheticApplier synthetic;

    private final Map<String, ReentrantLock> clusterLocks = new ConcurrentHashMap<>();
    private final Map<String, AbortSignal> abortSignals = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "kt-executor");
        t.setDaemon(true);
        return t;
    });

    public ApplyService(ClusterRegistry registry, SnapshotStore snapshots,
                        Scheduler scheduler, EngineConfig engineConfig,
                        Journal journal, Executor executor, Clock clock) {
        this.registry = registry;
        this.snapshots = snapshots;
        this.scheduler = scheduler;
        this.engineConfig = engineConfig;
        this.journal = journal;
        this.executor = executor;
        this.clock = clock;
        this.synthetic = new SyntheticApplier(snapshots, journal, clock);
    }

    @PreDestroy
    void shutdown() {
        threadPool.shutdownNow();
        try { threadPool.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts an apply and returns as soon as the journal entry is written. The caller
     * polls {@code GET /apply/:journalId} for progress.
     */
    public ExecutionResult apply(String clusterId, ApplyRequestDto req) {
        if (!req.ack()) throw new IllegalArgumentException("ack must be true to apply");

        ClusterRecord record = registry.find(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("no cluster " + clusterId));
        SnapshotStore.Entry snap = snapshots.latest(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("no snapshot for " + clusterId));

        ExecutionRequest execRequest = buildRequest(clusterId, req, snap.view());

        ReentrantLock lock = clusterLocks.computeIfAbsent(clusterId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new IllegalStateException("another apply is already in flight for " + clusterId);
        }

        String journalId = "j-" + UUID.randomUUID().toString().substring(0, 8);
        AbortSignal abort = new AbortSignal();
        abortSignals.put(journalId, abort);

        // Pre-create the journal so the abort endpoint can find it before the background
        // thread even schedules.
        journal.create(new JournalEntry(journalId, clusterId, execRequest.kind(), execRequest.summary(),
                Instant.now(clock), null, ExecutionOutcome.RUNNING, List.of()));

        threadPool.submit(() -> {
            try {
                if (record.synthetic()) {
                    synthetic.applyWithId(journalId, clusterId, execRequest);
                } else {
                    try (KubernetesClient client = kubeClient(record)) {
                        executor.executeWithId(journalId, client, execRequest, FaultInjectors.none(), abort);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("apply {} failed: {}", journalId, e.getMessage(), e);
                journal.updateOutcome(journalId, ExecutionOutcome.NEEDS_ATTENTION, Instant.now(clock));
            } finally {
                abortSignals.remove(journalId);
                lock.unlock();
            }
        });

        return new ExecutionResult(journalId, ExecutionOutcome.RUNNING, "Execution started.");
    }

    /**
     * Flips the abort signal for a running apply. Returns {@code true} if a matching
     * running apply was found and told to stop; {@code false} otherwise.
     */
    public boolean abort(String journalId) {
        AbortSignal signal = abortSignals.get(journalId);
        if (signal == null) return false;
        signal.abort();
        return true;
    }

    private ExecutionRequest buildRequest(String clusterId, ApplyRequestDto req, SnapshotView view) {
        Duration readyTimeout = Duration.ofSeconds(
                req.opts() != null && req.opts().readyTimeoutS() != null ? req.opts().readyTimeoutS() : 60);
        boolean dryRun = req.opts() == null || req.opts().dryRun() == null || req.opts().dryRun();
        boolean optIn = req.opts() != null && Boolean.TRUE.equals(req.opts().optInNonReversible());

        if (req.pendingPodUid() != null) {
            PodSpec pending = null;
            for (PodSpec p : view.pending()) if (p.uid().equals(req.pendingPodUid())) { pending = p; break; }
            if (pending == null) throw new IllegalArgumentException("no pending pod " + req.pendingPodUid());
            FeasibilityResult result = scheduler.plan(view, pending, optIn);
            if (!result.feasible()) throw new IllegalArgumentException("plan is infeasible: " + result.reason());
            return new ExecutionRequest(clusterId, JournalEntry.Kind.SCHEDULER,
                    "PLACE " + pending.name(), result.plan(), readyTimeout, dryRun);
        }
        if (req.swapRef() != null) {
            Balancer balancer = new Balancer(engineConfig);
            BalancePlanResult plan = balancer.plan(view);
            int idx;
            try {
                idx = Integer.parseInt(req.swapRef().startsWith("s") ? req.swapRef().substring(1) : req.swapRef());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad swap ref: " + req.swapRef());
            }
            if (idx < 0 || idx >= plan.swaps().size()) throw new IllegalArgumentException("no swap " + req.swapRef());
            SwapStep swap = plan.swaps().get(idx);
            PlanStep moveA = PlanStep.move(swap.podA(), swap.nodeA(), swap.nodeB());
            PlanStep moveB = PlanStep.move(swap.podB(), swap.nodeB(), swap.nodeA());
            return new ExecutionRequest(clusterId, JournalEntry.Kind.BALANCER,
                    "SWAP " + swap.podA().name() + " ⇄ " + swap.podB().name(),
                    List.of(moveA, moveB), readyTimeout, dryRun);
        }
        throw new IllegalArgumentException("apply request must include pending_pod_uid or swap_ref");
    }

    private KubernetesClient kubeClient(ClusterRecord record) {
        if (record.kubeConfigPath() == null) {
            // No file path — fall back to auto-configuration (KUBECONFIG env, ~/.kube/config,
            // or in-cluster ServiceAccount when running inside a Kubernetes pod).
            return new KubernetesClientBuilder().withConfig(Config.autoConfigure(null)).build();
        }
        try {
            String yaml = Files.readString(Path.of(record.kubeConfigPath()));
            Config cfg = Config.fromKubeconfig(null, yaml, record.kubeConfigPath());
            return new KubernetesClientBuilder().withConfig(cfg).build();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read kubeconfig " + record.kubeConfigPath(), e);
        }
    }
}
