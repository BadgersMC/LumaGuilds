# Paid guild creation bootstrap

GC-001: WHEN a guild is created, the system SHALL atomically persist the guild,
creator attribution, empty reward account and configured level-one progression
before owner admission reads reward entitlements. Failed bootstrap SHALL leave
no partial rows or cache entry. Other missing reward reads remain fail-closed.

GC-002: WHEN owner setup fails, the system SHALL NOT report success. Gold may be
restored only after proven setup rollback. Uncertain cleanup or existing durable
owner membership SHALL retain the receipt for review rather than risking a refund.

GC-003: WHEN an actor has an unresolved physical reservation, a new paid creation
SHALL stop before another reservation. Uncertain feedback SHALL retain the receipt
reference without falsely claiming creation or suggesting a duplicate name.

GC-004: Command and service creation SHALL share accepted name characters, with
invalid names rejected before reserving gold.

Fresh canonical main283d75f1; isolated codex/guild-creation-bootstrap-20261023.
User authorizes source fix; historical investigation839/841 and intent999 apply.
SPEAR spec -> failing regression -> engine -> transaction/API review -> refine.
Project EARS/state tooling absent: manual plan/task/evidence maintained.
No TEST, client, refund, historical data repair, merge, deployment or restart.

## Prove and engine evidence

The new SQL regression `newGuildCanResolveRewardsBeforeOwnerAdmission` failed on
unchanged main: RewardStateRead was Missing instead of Found (local baseline log,
2026-10-23). The first corrected focused suite passed. A broader run caught the
existing fatal-Error identity contract in rollback handling; preserve that Error
identity while classifying ordinary ambiguous commits/rollbacks for reconciliation.
Final full and isolated MariaDB results are recorded in the PR/delivery evidence.

The initial progression row shares the existing creation/creator/reward transaction
and uses the live configured curve through DI. Cold progression-cache reads load
the committed row. Owner setup rollback removes the guild and new progression;
rank cache eviction handles already-cascaded rows. A durable owner or uncertain
transaction is never treated as a proven rollback eligible for automatic refund.
Pending-receipt lookup is read-only and fails closed; the main-thread physical
adapter serializes command use. It blocks new paid creation for any unresolved
physical receipt, including intermediate phases and receipts predating restart.
It does not resolve or refund old receipts. Public shop XP and companion APIs are
unchanged. Existing constructors retain compatibility overloads.
