# 0004 — Mutation testing as a blocking PR gate

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

Line coverage answers "was this line executed?", not "would a test notice if the code broke?". That
gap matters most for **AI-assisted tests**, which reliably chase coverage while writing weak assertions
(assert-not-null instead of assert-a-value). A blueprint that asks agents to generate tests needs a
gate that grades *assertion strength*, not just execution. Mutation testing is inherently slower than
unit tests, though, so the gate must stay a guardrail, not a tollbooth — it must not become the thing
that stalls merges.

## Decision

We run **PIT (pitest)** as a **blocking gate** with `mutationThreshold = 80`, configured in
`service/app/pom.xml` (`pitest-maven`) and run via
`./mvnw -pl service/app test-compile pitest:mutationCoverage` (after a one-time
`./mvnw -DskipTests install` so the sibling architecture-test module resolves).

- **On PRs it runs diff-scoped.** `.github/workflows/pre-merge.yml` computes the backend `*.java` files
  the PR changed (from `pull_request.base.sha`), maps them to `io.miragon.blueprint.<pkg>.<File>*`, and
  passes them to pitest via `-DmutationTargetClasses`. It blocks the PR on the changed classes' score
  but stays off the critical path, and is skipped when a PR touches no backend code.
- **Nightly runs the full sweep.** `.github/workflows/nightly.yml` mutates the whole
  `io.miragon.blueprint.*` module with no property override — the authoritative gate-80 run — and
  uploads the HTML report as an artifact.
- Both runs **exclude noise**: the generated `*ProcessApi`, the Spring bootstrap and
  `BikeCatalogueSeeder`, and the `adapter.inbound.operaton.*` workers (thin glue exercised only by the
  slow engine tests).
- The **kill-set** is the fast Mockito / `@WebMvcTest` / `@DataJpaTest` unit tests; the engine
  integration tests (`process.*`) and the ArchUnit tests (`architecture.*`) are excluded from
  the kill-set — they'd make every run slow and non-deterministic without adding mutation signal.

**Why 80 and not 100:** on the Kotlin codebase this repo was ported from, the ceiling was the compiler's
synthetic bytecode — *equivalent mutants* no test could kill. Plain Java emits almost none of that, so
the equivalent-mutant argument is largely gone. 80 is kept anyway: it preserves continuity of the gate
through the port (the bar never moved, so scores stay comparable), and the Java mutant population first
needs a few nightly full sweeps to establish a baseline. Raising the gate (e.g. to 90+) once those
sweeps show consistent headroom is the recorded follow-up.

**What a weak test looks like** (the two patterns the gate caught in this repo's own spike): a test
that asserts too few of a DTO's fields — PIT blanks the unasserted ones (`return ""`) and every test
stays green; and a test that exercises only one branch of a boolean — the *"always return true"*
mutant is then *equivalent* to the original. Both are fixed by asserting **every** mapped field and
**both** outcomes of each branch — one assertion per outcome, not per method.

## Consequences

- **Positive:** AI-generated tests are graded on whether they'd catch a real fault; weak assertions
  surface as surviving mutants with per-mutant, inline feedback — at PR time, on the code the PR
  changed.
- **Negative / trade-offs:** mutation testing is slower than unit tests, hence the diff-scoped PR run
  and the separate nightly full sweep. Gate-80 over a single changed class is stricter granularity than
  over the module, so a PR touching only a small, hard-to-test class can dip below 80 (fix: assert
  every field and both branches, or exclude the class).
- **Neutral:** the threshold is deliberately conservative for the port; the gate-raise (90+) waits on
  the nightly sweeps, not on new tooling.

## Implementation notes

- `service/app/pom.xml` defaults the `mutationTargetClasses` property to `io.miragon.blueprint.*` (the
  full-module scope, used by the nightly sweep and local runs) and sets `failWhenNoMutations = false`
  so a PR whose changed classes are all excluded/non-mutable doesn't fail the build.
- **Engine parity across the port:** `pitest-maven` is pinned to **1.22.1** — the exact PIT engine the
  Gradle plugin resolved before the port — plus `pitest-junit5-plugin` **1.2.2**, so pre- and post-port
  mutation scores stay comparable.
- Do **not** rename the `Mutation testing (PIT, gate 80)` job in `pre-merge.yml` — it is the
  branch-protection required check.
