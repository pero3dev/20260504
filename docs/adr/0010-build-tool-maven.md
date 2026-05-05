# ADR-0010: Build tool — Maven

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Tech leads

## Context

The two realistic options for a 13-service Java backend with shared libraries are Maven and Gradle. Gradle is faster and more flexible for complex builds. Maven is more uniform, simpler to read, and has fewer foot-guns at scale. The team has expressed a preference for Maven.

## Decision

Use **Maven** with a multi-module aggregator parent. Java 21, Spring Boot 3.x. A `commons-bom` module pins versions for all platform-internal dependencies; every service POM imports the BOM rather than declaring versions.

## Consequences

**Positive.** Build configuration is uniform across 13 services. New engineers can read a POM without learning a Groovy/Kotlin DSL. Maven's plugin ecosystem covers everything we need (Spotless, Checkstyle, SpotBugs, PMD, Surefire, Failsafe, JaCoCo, Spring Boot, Flyway, OpenAPI generator).

**Negative.** Build is slower than Gradle on incremental compiles. Acceptable — CI cache plus parallel module builds (`-T`) recover most of it.

**Neutral.** Plugin version drift between modules is prevented by `pluginManagement` in the parent POM.

## Alternatives considered

### Option 1: Gradle (Kotlin DSL)
Faster builds, configurable. Rejected because non-uniform Gradle scripts across 50 engineers create cargo-cult patterns and the team prefers Maven's declarative style.

### Option 2: Bazel
Best for very large monorepos. Rejected — the scale doesn't justify Bazel's learning curve for a Java-only stack.

## References

- `memory/design_implementation.md` — E7 Maven choice
