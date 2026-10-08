# Contributing

Thanks for the interest. A few short rules keep the repo honest and reviewable.

## Build & test

```bash
./gradlew test
```

Runs the whole multi-module test suite. CI runs the same command; a PR that fails
`./gradlew test` won't be merged.

Prerequisites: JDK ≥ 21 (tested on JDK 25 and 26). The Gradle wrapper pins Gradle to
9.7.1 and bootstraps itself.

## Commit style

- **Small, logical commits.** Each commit has a clear intent (chore / fix / feat /
  refactor / docs / build / ci) and compiles on its own. If an intermediate state
  can't compile, bundle it with the fix.
- **Mark BREAKING changes.** If you remove or change a public type, a wire DTO field,
  a config key, or an RBAC verb, call it out in the commit body under a `BREAKING:`
  block that names what went away and who needs to migrate.
- **Subject stays short.** Target 70 chars; the body carries detail.

## Tests

- Engine stages are pure functions — unit tests on synthetic fixtures with no cluster.
- Executor ships **fault-injection tests** that fail each named leg and assert
  rollback. Any new write path lands with the same style.
- Spring integration tests use `TestRestTemplate` against a random port. If a test
  needs isolated state across methods, use `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)`.

## Code

- Keep the engine framework-free. No Jackson, no Spring, no Fabric8 inside `engine/`.
- Keep DTOs in `api/com/kubetetris/api/dto/*`. Field names use snake_case over the wire.
- No cluster writes outside `executor/`. The executor is the only module with write
  access; it is transactional (journal + rollback), and the write path is covered by
  mandatory fault-injection tests.
- Every threshold, exclusion, and timeout lives in `EngineConfig` or an application
  property. Nothing is hardcoded in the algorithm layer.

## PRs

- Build + tests green locally.
- One concern per PR. If a change is clearly BREAKING, call it out at the top of the
  PR body as well as in the commit.
- Screenshots when the change is UI-visible — the `docs/img/` directory has the
  existing shots; follow that style.
