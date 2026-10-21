# Dialog acceptance followups

Base: PR241 aa35e0da; canonical main freshly fetched at 283d75f1.
Continue the isolated existing worktree. No production or merge. Shared TEST
window 796 was released by receipt 797; no current live resource claim.

## Spec

- MF-001: WHILE the same authorized menu remains open THE SYSTEM SHALL keep its
  controls usable beyond two minutes, with one shared bounded twelve-hour
  callback/session lifetime. Navigation replacement, disconnect, revoked
  membership, foreign actors and replay SHALL still fail closed. Independent
  purchase quotes and confirmation deadlines SHALL remain unchanged.
- MF-002: WHEN viewing Statistics achievements THE SYSTEM SHALL use the same
  eight current-stat predicates in its count and detail, and link the personal
  achievement tree with a separate guild combat milestone view. Guild combat
  milestones SHALL show exact conditions, retained-war-history scope and no
  extra XP or permanent historical award claim.
- MF-003: WHEN a matching optional inventory-label plate is present THE SYSTEM
  SHALL draw it behind vanilla Inventory text with zero net title advance,
  without changing any inventory slots or actions. Missing plates and Vanilla
  style SHALL retain the existing rendering.

## Prove and implementation

Baseline evidence: native two-minute idle callbacks stopped responding;
Statistics count has six different predicates while detail has eight; native
Inventory text overlaps the patterned divider. Add behavioral regressions for
idle boundaries, shared predicate counts, native scope navigation and optional
zero-advance title composition before implementation. No local Gradle execution
under an unacknowledged build-capacity claim; use hosted exact-head CI.

Project EARS/state helpers remain absent. Manual requirement/evidence tracking
is used. Economy mutation and exhaustive live theme matrix require a new
acknowledged resource window and fixture scope; no live acceptance is implied.

Hosted baseline 9584f534 ran 2,232 tests: three failures, 68 skips. Two locale
contracts rejected the new dynamic conditional key expression; use literal
calls rather than weakening the baseline. The new scope-navigation fixture
also needs the same LangService.msg stub as the existing personal-tree fixture.
This is recorded failure evidence; a fresh hosted run must pass the refinement.
