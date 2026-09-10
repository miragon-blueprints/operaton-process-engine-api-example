# 0014 — Architecture tests after the Java port

- **Status:** Accepted
- **Date:** 2026-09-10
- **Supersedes:** [ADR-0007](0007-two-architecture-test-tools-archunit-and-konsist.md)

## Context

[ADR-0007](0007-two-architecture-test-tools-archunit-and-konsist.md) split the architecture rules
between two tools by what each can see: **ArchUnit** for the compiled **bytecode** (the resolved
dependency graph, naming) and **Konsist** for the Kotlin **source** (the PSI tree — files, imports,
top-level declarations). The port to Java removes Konsist's entire subject matter: Konsist parses
Kotlin PSI, and there is no Kotlin source left to parse. Its half of the split dies with the language,
not by choice — the source-level rules it carried are still worth enforcing.

## Decision

**ArchUnit is the sole architecture-test framework**, in `service/common-architecture-tests`; the
layered-dependency, naming and coding-guideline rules carry over unchanged. `KotlinSourceGuidelinesTest`
is deleted, and its two source rules move to **Checkstyle** (`checkstyle.xml` at the repo root,
`maven-checkstyle-plugin` bound to the `validate` phase, so a violation fails `./mvnw verify`):

- **`OneTopLevelClass`** — at most one top-level type per file. javac enforces this only for *public*
  top-level types; non-public siblings would still compile, so the rule still earns its place.
- **`AvoidStarImport`** — no wildcard imports, now with **no `java.util` exemption**: that carve-out
  existed for Kotlin ergonomics and has no Java justification.

## Consequences

- **Positive:** one JVM-language framework plus one source linter, both in the same build gate; the
  "each tool owns the layer it can see" principle survives with Checkstyle in Konsist's seat.
- **Negative / trade-offs:** the source rules are now XML configuration rather than test code — less
  expressive than Konsist's DSL, which is acceptable for the two simple rules that remain.
- **Neutral:** ADR-0007's two-tools ceiling carries over — a new structural rule goes into ArchUnit or
  Checkstyle, whichever can see it; we do **not** add a third architecture/guardrail framework on top.
