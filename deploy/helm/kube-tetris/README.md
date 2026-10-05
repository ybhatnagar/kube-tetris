# kube-tetris Helm chart

Deploys the kube-tetris api into a Kubernetes cluster. The api runs the collector,
engine, and executor in the same JVM and uses its ServiceAccount to talk to the
cluster it's running in.

## RBAC

The chart creates a `ClusterRole` scoped to what the tool needs and nothing more.

Read (always granted):

- `nodes` — get, list, watch
- `pods` — get, list, watch
- `persistentvolumeclaims` — get, list, watch
- `policy/poddisruptionbudgets` — get, list, watch

Write (granted unless `rbac.readOnly: true`):

- `pods/eviction` — create (the executor calls this via the policy/v1 Eviction
  subresource; the api server enforces the pod's PodDisruptionBudget at this
  point)
- `pods` — patch (temporary affinity / label patches when needed)
- `nodes` — patch (for cordon-based steering)

Nothing in the chart grants delete on pods or write on any workload controller.

## Install

```bash
helm install kt deploy/helm/kube-tetris \
  --namespace kube-tetris --create-namespace
```

## Advisor-only mode

If you want to run without giving the tool any write access:

```bash
helm install kt deploy/helm/kube-tetris \
  --namespace kube-tetris --create-namespace \
  --set rbac.readOnly=true
```

The api will still compute plans; APPLY calls will fail at the API server (RBAC
`Forbidden`).

## Configuration

See `values.yaml` for the full option set. Common overrides:

| key                    | default                     | notes                              |
| ---------------------- | --------------------------- | ---------------------------------- |
| `image.repository`     | `kube-tetris/api`           | your container image               |
| `image.tag`            | `0.1.0`                     | image tag                          |
| `replicaCount`         | `1`                         | keep at 1 until persistence lands  |
| `service.type`         | `ClusterIP`                 | flip to `LoadBalancer` for demos   |
| `rbac.readOnly`        | `false`                     | `true` = advisor-only mode         |
| `persistence.enabled`  | `false`                     | create a PVC and persist to disk   |
| `persistence.size`     | `1Gi`                       | PVC size                           |
| `persistence.mountPath`| `/data`                     | mount point inside the pod         |

## Persistence

With `persistence.enabled=true` the chart creates a `PersistentVolumeClaim` and mounts
it at `/data`. The api is pointed at it via environment variables:

- `KUBETETRIS_JOURNAL_STORAGE=file`
- `KUBETETRIS_JOURNAL_PATH=/data/journal`
- `KUBETETRIS_REGISTRY_PATH=/data/registry`

The journal and cluster registry both survive restarts. With persistence disabled
everything is in-memory and lost on pod replacement — fine for demos.
