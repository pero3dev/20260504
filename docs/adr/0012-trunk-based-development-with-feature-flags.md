# ADR-0012: Trunk-Based Development with feature flags

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Engineering management, Tech leads

## Context

Fifty engineers across multiple sub-teams. Weekly-or-faster release cadence is a stated requirement. Long-lived release branches in a 13-service codebase create coordination overhead that we cannot afford.

## Decision

Adopt **Trunk-Based Development**. Rules:

- Branches are short-lived (under one day target).
- All work merges into `main`. There is no `develop` branch.
- Incomplete features land on `main` behind a **feature flag** — the user-visible behavior is gated.
- Feature flags are managed by **Unleash (OSS)** running inside our cluster.
- Production deploys from `main` use **Argo Rollouts canary** (1% → 5% → 25% → 100% with auto-rollback on SLO regression — see ADR-0013).
- Commit messages follow **Conventional Commits**.
- Pull requests require ≥2 reviewers (one from a designated core team), are limited to roughly 600 lines of diff, and pass all automated checks (Lint, Format, Unit, ArchUnit, SAST, image scan).
- Merges are squash-merges so `main`'s history reads as one commit per PR.

## Consequences

**Positive.** Branch lifetime is measured in hours; merge conflicts stay small. Release cadence is set by ArgoCD sync + canary, not by branch coordination. Feature flags decouple "merged" from "released," giving us the freedom to revert behavior without reverting code.

**Negative.** Flag debt accumulates. Mitigation: every flag has an owner and an expiry date (enforced by a CI check), and stale flags are removed in scheduled cleanup PRs. Trunk-Based requires high test coverage on `main` because everyone is sharing it — see ADR (test strategy) and the 80% unit-test coverage floor.

**Neutral.** Conventional Commits drive automatic CHANGELOG generation per service.

## Alternatives considered

### Option 1: GitHub Flow (long-lived feature branches)
Easier to learn. Rejected because long-lived branches at 50 engineers create merge-conflict pile-ups; we measured this in past projects.

### Option 2: GitFlow
Most ceremonial. Rejected outright — release/develop/feature/hotfix branches are incompatible with weekly+ releases at this team size.

## References

- `memory/design_implementation.md` — F3 Git strategy
- ADR-0013 (canary rollout)
