# ADR-0011: ID strategy — Snowflake-style 64-bit IDs

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture

## Context

Aggregate IDs are generated in many places (web request handlers, Kafka consumers, batch jobs). They must be unique across multiple service instances without round-tripping to a central sequence. They will appear in URLs, logs, and Kafka keys, so size and ordering matter — Kafka topic partition co-location often relies on IDs that are time-clustered.

## Decision

Use **Snowflake-style 64-bit integer IDs** generated in-process by every service, via a generator in `commons-persistence`. Layout (subject to refinement):

- 41 bits: timestamp (milliseconds since custom epoch)
- 10 bits: worker ID (per pod, derived from hostname or env var injected by the deployment)
- 12 bits: per-millisecond sequence

Worker ID is assigned via the Kubernetes downward API (a env var unique within the deployment's replica set), with a fallback hash of the pod name.

## Consequences

**Positive.** No central coordinator, no DB round-trip on insert, time-ordered (B-tree friendly, Kafka partition co-location works), 64-bit fits in `bigint` columns and is faster than UUID v4/v7 in indexes.

**Negative.** Worker-ID assignment is the trickiest part — collision means duplicate IDs. Generator validates worker ID at startup and fails fast if unset or out of range. Time skew between pods (NTP failure) could allow same-millisecond collisions on different workers, which the worker-ID dimension prevents.

**Neutral.** IDs leak time information (privacy: an inventory ID reveals when the inventory was created). Acceptable for inventory/business entities; not used for anything user-confidential.

## Alternatives considered

### Option 1: DB auto-increment
Simple but requires an extra DB round-trip and is a coordination point.

### Option 2: UUID v4
Random 128-bit. Rejected — index locality is poor on B-tree (random inserts cause page splits), 16-byte payload is double the storage of bigint.

### Option 3: UUID v7 (time-ordered UUID)
A reasonable alternative — gives time ordering and full UUID compatibility. Considered close to a tie. Snowflake won on bigint storage efficiency and fewer encoding hops in logs/URLs.

## References

- `memory/design_implementation.md` — E3 ID strategy
- `commons-persistence` `SnowflakeIdGenerator`
