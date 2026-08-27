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
engine/    # pure JVM engine over a cluster snapshot (algorithms + tests)
```

Additional modules (collector, executor, UI, deploy chart) will land in future work.

## Build (engine)

```bash
cd engine
./gradlew test
```

Requires JDK ≥ 21 (tested on JDK 25/26). The Gradle wrapper is pinned to 9.7.1. The
engine has no cluster/network dependencies — it's a pure library over synthetic
snapshot fixtures.

## Lineage

The engine ports the 2018 prototype's algorithms (`CapacityPlacementServiceImpl` +
`SystemControllerImpl` + `WorkLoadBalancerImpl`) into pure, I/O-free functions with
several correctness fixes to the recursion, priority ordering, and eligibility
filters. The balancer no longer executes swaps inline — it only computes an ordered
swap list; the executor path is out of scope for this module.
