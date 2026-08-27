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
engine/       # M1 — pure JVM engine over SnapshotView (this milestone)
collector/    # M3 — Go client-go collector → SnapshotDTO      (not built)
executor/     # M5 — Eviction + steer + verify + rollback     (not built)
ui/           # M4 — single-file SPA + nginx                   (not built)
deploy/       # M6 — Helm chart + scoped RBAC                  (not built)
design-docs/  # locked design (build-plan doc set)
```

Modules bind only to the cross-module contract in
[design-docs/04-schema-and-api.md](design-docs/04-schema-and-api.md).

## Status

- **M1 — Engine core (pure, tested).** ✅ Shipped. See
  [design-docs/M1-NOTES.md](design-docs/M1-NOTES.md).
- M2 — API + snapshot contract. Not started.
- M3 — Collector. Not started.
- M4 — UI. Not started.
- M5 — Executor + rollback. Not started.
- M6 — Safety hardening + deploy. Not started.

## Design

Start at [CLAUDE.md](CLAUDE.md), then
[design-docs/08-implementation-handoff.md](design-docs/08-implementation-handoff.md).
The full design set is in [`design-docs/`](design-docs/).

## Build (engine)

```bash
cd engine
./gradlew test
```

Requires JDK ≥ 21 (tested on JDK 25/26). The Gradle wrapper is pinned to 9.7.1. The
engine has no cluster/network dependencies — it's a pure library over synthetic
`SnapshotView` fixtures for M1.

## Lineage

The engine ports the 2018 prototype's algorithms (`CapacityPlacementServiceImpl` +
`SystemControllerImpl` + `WorkLoadBalancerImpl`) into pure, I/O-free functions with
several correctness fixes. See [design-docs/M1-NOTES.md](design-docs/M1-NOTES.md) for
the bug list and the semantic changes.
