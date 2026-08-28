# Kube Tetris

Human-in-the-loop pod-migration tool for resource-crunched Kubernetes clusters.
An occasional, invasive-by-consent advisor — **not** an always-on custom scheduler.

Two recommendation engines run over a live cluster snapshot:

- **Scheduler** — when a `Pending` pod can't fit on any single node though the cluster
  has the capacity in total, compute the minimum set of pod moves that frees a node,
  then place.
- **Balancer** — when nodes are CPU/mem imbalanced, compute the swap that reduces the
  system entropy `Σ|pivot − node_cpu/mem_ratio|` most.

Every action is surfaced to the operator with its computed effect; they apply one at a
time, and the tool re-snapshots between actions.

## Repo layout

```
engine/    pure JVM engine over a cluster snapshot (algorithms + tests)
api/       REST wrapper (Spring Boot) exposing /api/v1 over the engine
```

Additional modules (collector, executor, UI, deploy chart) will land in future work.

## Build

The Gradle wrapper lives at the repo root and drives both subprojects. Requires
JDK ≥ 21 (tested on JDK 25 and 26); the wrapper is pinned to Gradle 9.7.1.

```bash
./gradlew test                 # runs engine + api tests
./gradlew :engine:test         # engine only
./gradlew :api:test            # api only
```

## Run the API locally

```bash
./gradlew :api:bootRun
```

By default the server binds to `:8080` and seeds an in-memory `synth` cluster so the
endpoints work without a real Kubernetes connection.

```bash
curl -s http://localhost:8080/healthz
curl -s http://localhost:8080/api/v1/clusters/synth/snapshot
curl -s http://localhost:8080/api/v1/clusters/synth/pending
curl -s -X POST -H 'Content-Type: application/json' -d '{}' \
     http://localhost:8080/api/v1/clusters/synth/balance/plan
```

## Lineage

The engine ports the 2018 prototype's algorithms (`CapacityPlacementServiceImpl` +
`SystemControllerImpl` + `WorkLoadBalancerImpl`) into pure, I/O-free functions with
several correctness fixes to the recursion, priority ordering, and eligibility
filters. The balancer no longer executes swaps inline — it only computes an ordered
swap list. Any cluster-mutating executor is intentionally out of scope for these
modules.
