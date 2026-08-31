# 0013 — Operaton as the embedded process engine

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

This blueprint automates one BPMN process on an **embedded Camunda-7-lineage engine**, driven through
the bpm-crafters **`process-engine-api`** abstraction (start / correlate / complete, plus
`@ProcessEngineWorker` beans for the external service tasks). It originally ran on **CIB seven**, one
community continuation of Camunda 7 Community Edition. **Operaton** is the other — same Camunda 7
heritage, same BPMN/DMN semantics, same `camunda:` model namespace, same `/engine-rest` API.

The whole point of routing every engine interaction through `process-engine-api` (ADR-0002) is that the
*integration* of the engine is decoupled from the *choice* of engine: bpm-crafters ships a parallel
adapter per engine (`process-engine-adapter-cib-seven-*`, `process-engine-adapter-operaton-*`). That
makes a port a bounded change rather than a rewrite, and switching demonstrates the abstraction actually
pays off.

## Decision

Run the example on **Operaton 2.1.3**, embedded, with **no CIB seven dependencies**.

- **Engine + webapps:** `org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter-webapp` (Cockpit /
  Tasklist / Admin) and `…-starter-rest` (`/engine-rest`), version-aligned by the `operaton-only-bom`
  platform. Operaton 2.x *is* the Spring Boot 4 line, so there is no `-4` starter variant and no
  webclient — the classic webapps serve at **`/operaton`** (admin/admin).
- **process-engine-api adapter:** swap the bpm-crafters adapter to
  `process-engine-adapter-operaton-embedded-spring-boot-starter` (+ `…-operaton-bom`), keeping
  `process-engine-api` 1.7 and `process-engine-worker` 0.8.4. The only concrete-adapter symbol in app
  code, `EngineCommandExecutor`, moves package `…adapter.cibseven.embedded.shared` →
  `…adapter.operaton.embedded.shared`; the workers and the start/correlate/complete driver are
  engine-agnostic and unchanged.
- **Native engine touch-points** (the few places the API has no equivalent): the direct task/runtime
  reads (`TaskInboxAdapter`, `ProcessEngineExtensions`), the history-cleanup `ProcessEnginePlugin`, and
  the `MismatchingMessageCorrelationException` mapped to `409` — all move `org.cibseven.bpm.engine.*` →
  `org.operaton.bpm.engine.*` (a straight package rename; Operaton kept the Camunda 7 API shape).
- **Adapter packages renamed** `adapter/{inbound,outbound}/cibseven` → `…/operaton`, so the naming
  architecture test (`NamingConventionArchitectureTest`) and the folder layout stay honest.
- **Models are left on the `camunda:` namespace.** Operaton reads Camunda 7 BPMN/DMN/Forms natively, so
  the `.bpmn` / `.dmn` / `.form` files, the `ServiceTaskExternalTopicRule` (which asserts
  `camunda:type="external"` + topic), and the bpmnlint config (`camunda-platform-7-24`) are untouched.
  Consequently `bpmn-to-code` generation stays on **`ProcessEngine.CAMUNDA_7`**: its `OPERATON` target
  demands `operaton:`-namespaced models and rejects `camunda:` ones, and the generated constants are
  engine-agnostic BPMN strings either way. The generated `*ProcessApi` is byte-identical before/after.
- **Serialization: Jackson 3, no Spin.** Every process variable this blueprint stores is a primitive
  (id/amount/flag), so JSON object serialization (Operaton Spin, which pins the Jackson 2 ecosystem) is
  unnecessary and would clash with the app's Spring Boot 4 / Jackson 3 baseline. This follows the
  adapter's `java-operaton-embedded-jackson3` reference.

## Consequences

- **Positive:** the port is almost entirely mechanical — a dependency swap, one adapter package, a
  handful of `org.cibseven`→`org.operaton` imports, and config-key renames (`camunda.bpm.*` →
  `operaton.bpm.*`, adapter key `cib-seven-embedded` → `operaton-embedded`). The BPMN models, the REST
  contract (`openapi/openapi.json`), the `/engine-rest`-based Bruno suite and its CI readiness probe,
  and the bpmn lint tooling all carry over unchanged. It is a working demonstration that the
  `process-engine-api` layer makes the engine a swappable detail.
- **Trade-off — Spring Boot version:** the app tracks the latest Spring Boot (4.1.x, ADR-0008) while
  Operaton 2.1.x is *tested* against Spring Boot 4.0.x (same 4.x major). This mirrors how the CIB seven
  adapter was already run a minor ahead of its stated target; the full build and process tests pass. If
  a future Spring Boot minor breaks the engine, pin Operaton to the newest 2.x or align Spring Boot to
  the engine's tested minor.
- **Neutral:** the `camunda:` namespace remains in the models. It is the *shared* Camunda 7 namespace
  both forks read — not a CIB seven artifact — so keeping it does not reintroduce a CIB seven
  dependency. A future "fully Operaton-native" pass could rewrite the models to the `operaton:`
  namespace and flip generation to `ProcessEngine.OPERATON`, but that would also require an
  Operaton-aware bpmnlint setup and an updated `ServiceTaskExternalTopicRule`, for a cosmetic gain.
