# Guild Chronicle completion

Base: authoritative main 283d75f1, refreshed 2026-10-16. Ongoing dialog
integration cd949644 is an unmerged dependency, preserved on this branch.
No server activation is part of this task.

## Specification

- CHR-001: WHEN a member browses Chronicle THEN THE SYSTEM SHALL derive all
  levels and rewards from the injected RewardCatalog and current entitlements,
  including every automatic level benefit, without a separate sample catalog.
- CHR-002: WHEN an authorized member tracks or clears a guild goal THEN THE
  SYSTEM SHALL persist it before acknowledging success and retain it across
  menu reopening, player sessions and process restarts. Any member may read;
  only MANAGE_GUILD_SETTINGS may edit. Goals do not grant or purchase rewards.
- CHR-003: IF storage fails, membership is absent, a reward is unknown or a
  rendered goal revision is stale THEN THE SYSTEM SHALL reject the write and
  preserve the last committed goal. Concurrent edits shall use revision CAS.
- CHR-004: WHEN the guild is deleted THEN THE SYSTEM SHALL delete its goal in
  the same transaction. Existing prestige/rollover behavior is unchanged;
  tracking remains a guild preference, with current reward status recalculated.
- CHR-005: WHEN a reward is reviewed THEN THE SYSTEM SHALL show current status,
  exact configured effect/level/price/retention and route purchases exclusively
  through the existing fresh-quote confirmation service.

## Proof and tasks

- [x] Catalog traversal across 1-100, custom catalog, all branches/effects.
- [x] Real SQLite close/reopen persistence; stale CAS, clear ABA, isolation,
  unavailable storage, authorization revocation, invalid reward and cleanup.
- [x] Native menu wiring, localized output, all-level pagination and return paths.
- [x] Canonical checks/build, reviewable source delivery and interactive preview.

SPEAR: spec above; prove with focused service/SQL tests before implementation;
engine via application read model and persistence port; infrastructure SQL and
dialog adapters; refine through checks and exact-head review. Project-local
EARS/state helpers were not found; this file is the manual state/evidence record.
Native client and shared TEST acceptance remain separate from automated checks.

## Chronicle implementation verification

- CHR-001/005: application reads traverse all 100 catalog levels and all 30
  rewards. Custom catalog names/prices and all five effect types are covered;
  branches partition rewards without adding prerequisites. Native progression
  opens Chronicle; details route only to the existing fresh-quote confirmation.
- CHR-002/003: guild-scoped revisioned SQL goal survives close/reopen. The service
  checks current membership and Manage Guild Settings on every write; read-only
  members can browse. CAS conflicts, clear ABA, concurrent writers, missing guild,
  invalid targets, permission revocation and storage rollback are tested.
- CHR-004: additive schema v46 on both migration chains; SQLite restart/repair
  preserves saved goals. Both transactional guild removal paths use the shared
  goal cleanup hook. Prestige/rollover never grants/removes rewards through goals.
- Clean offline `check shadowJar`: 1962 cases, zero failures/errors, 16 pre-existing
  optional skips (Java 25, Paper API 26.2.build.129-stable).
- Focused service/menu/locale/migration contracts pass. Real disposable loopback
  MariaDB 11.8.3 passes all three goal SQL contracts, including close/reopen,
  concurrent CAS and injected write failure; SQLite runs these in the full suite.
- Initial focused compile failed as expected with missing Chronicle types; the
  first broader run exposed schema-version expectations which were corrected.
  Logs are task evidence outside source control, not fabricated prior regressions.
- No project-local EARS/state tools exist; manual CHR requirements/tasks above.
- Source is based on unmerged dialog parent cd949644. Hosted review, parent stack
  reconciliation, Nexo/client appearance and shared TEST acceptance remain gates.
  No shared TEST/production upload, restart, reload or activation occurred.
