# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it.

## Project Overview

A headless **MiraVelo bike-leasing** example: one complete, runnable BPMN process automated with an
Operaton embedded engine, driven through the **process-engine-api** (bpm-crafters) abstraction, with
an architecture enforced by tests.

- **Backend** (`service/app`) — Java 21 / Spring Boot 4, hexagonal, Operaton 2.1.3 embedded engine.
  Package root `io.miragon.blueprint`.
- **Process-engine-api, not embedded delegates** — BPMN service tasks are `camunda:type="external"`
  topics consumed by `@ProcessEngineWorker` beans in `adapter/inbound/operaton`; the engine is driven
  (start / correlate / complete) through `dev.bpmcrafters.processengineapi` in `adapter/outbound/operaton`.
  There are **no** `JavaDelegate`/`ExecutionListener`/`TaskListener` and no `camunda:delegateExpression`.
- **The API contract** is `openapi/openapi.json`: springdoc generates it from the controllers, it is
  **committed and drift-gated** in CI so any REST change is caught. See ADR-0003.

## Development Setup

Two commands to a running backend:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
./mvnw -pl service/app spring-boot:run             # backend + embedded engine on :8080
```

On a **fresh clone** (or after changing `common-architecture-tests`), run `./mvnw -DskipTests
install` once first — module-scoped commands resolve the arch-tests jar from the local Maven
repository.

### Ports (one source of truth — keep README, this file and `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + engine-rest) | 8080 |
| Operaton Cockpit / webapps | 8080/operaton (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8080/actuator |

## Build Commands

| Area | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing (gate 80) | `./mvnw -DskipTests install && ./mvnw -pl service/app org.pitest:pitest-maven:mutationCoverage` — report under `service/app/target/pit-reports` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./mvnw -pl service/app generate-sources` (also runs automatically on every build) |
| Regenerate + verify the OpenAPI contract | `./mvnw -pl service/app -am test -Dtest=OpenApiSpecExportTest` then `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` |
| BPMN lint | `npm ci && npm run lint:bpmn` |
| Backend OCI image | `./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:build-image -DskipTests` — [ADR-0011](docs/adr/0011-build-and-deployment-approach.md), CONTRIBUTING "Run it in containers" |

## Architecture — the rules are machine-enforced

The hexagonal rules live in `service/common-architecture-tests` (ArchUnit, bytecode) and **fail the
build**; the source-level rules (one top-level class per file, no star imports) are enforced by
Checkstyle (`checkstyle.xml` at the repo root, runs at the `validate` phase inside `./mvnw verify`) —
see ADR-0014. Read `HexagonalArchitectureTest.java` and `NamingConventionArchitectureTest.java`
before writing code. The hard rules:

- **One inbound port per controller.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1. An inbox listing + a completion are two controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi.java`; edit the `.bpmn` and re-run
  `./mvnw -pl service/app generate-sources`.
- **Service tasks are external topics.** A custom model rule (`ServiceTaskExternalTopicRule`) requires
  every service task to be an external task with a topic — no embedded delegates. The trade-offs
  behind this are written up in [`docs/execution-and-task-listeners.md`](docs/execution-and-task-listeners.md).
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; a custom model
  test requires every service task to be an external task with a topic.
- `bpmnlint` tooling lives at the **repo root** (`package.json`, `.bpmnlintrc`): `npm ci && npm run
  lint:bpmn`. It also runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | Mockito (BDD style) unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + `@MockitoBean` |
| `adapter.outbound.db` | `@DataJpaTest` |
| process end-to-end | Operaton process tests (the real `@ProcessEngineWorker` beans consume the external service tasks) |

**Mutation testing gates PRs at 80** (`./mvnw -pl service/app org.pitest:pitest-maven:mutationCoverage`): a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See ADR-0004.

## Verify After Each Task (targeted, not a full build)

- Backend service/controller: `./mvnw -pl service/app -am test "-Dtest=*<Name>Test" -Dsurefire.failIfNoSpecifiedTests=false`
- Architecture only: `./mvnw -pl service/app -am test "-Dtest=io.miragon.blueprint.architecture.*"`
- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## ADRs

Architecture decisions are recorded in `docs/adr/` (0001–0014). Read them to understand *why* the
repo is shaped this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
