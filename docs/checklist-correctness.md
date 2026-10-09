# Guild checklist correctness

Current sanitized base: `bfa6931e` (merged PR #226). This review rebuild contains only the rank-authority, atomic ownership-transfer, accessible-home completion, and relation-aware disband delta from old #213. The old stacked reconciliation commits are excluded because they reintroduced pre-sanitization dependency history and the removed proprietary stall-art generator.

## SPEAR

**Spec.** REQ-128/129/131 govern service-boundary rank authority, atomic ownership transfer, and accessible home completion. Ordinary rank mutation may neither assign nor remove ownership; that boundary applies even to admin override. Disband must remain one transaction across the relation layouts seen in current and historical MariaDB schemas while preserving current reward/cosmetic cleanup behavior.

**Engine.** `RankServiceBukkit` enforces relative priority and delegated-permission boundaries. `MemberServiceBukkit.changeMemberRank` rejects every direct transition to or from the priority-0 owner rank; ownership moves only through `transferOwnership`. `MemberOwnershipTransferSQL` compare-and-set updates both members inside one transaction and publishes cache/event success only after commit. `/guild home` completion filters through the same current access decision used by teleportation. Relation deletion discovers the supported column layout without destructive migration and executes inside the existing disband transaction.

**Current proof.** On the sanitized post-#226 base, focused Java 25/Paper 26.2 validation passed 21 tests with zero skips, failures, or errors. Coverage includes the new admin-override owner-demotion regression, rank authority boundaries, ownership transfer rollback/stale-state behavior, accessible-home completion, and disband atomicity including cosmetic ownership cleanup. The single-commit publish tree then passed clean `test shadowJar`: 1,659 tests, zero failures/errors, 16 optional/environment skips; Shadow JAR produced. `git diff --check` is clean. Hosted exact-head checks remain required before merge.

**Historical evidence.** The original feature branch previously exercised native MariaDB ownership-transfer and historical relation-schema scenarios successfully. Those runs remain useful design evidence, but they were not rerun for this sanitized rebuild and are not claimed as exact-head proof.

## Release boundaries

This branch is source review only. It performs no production database repair, deployment, restart, or live mutation. An already ownerless guild requires read-only evidence and a separately approved recovery operation; this change prevents ordinary/admin rank APIs from creating a new ownerless guild.

Shop-XP durable receipts introduced by #226 are intentionally not deleted during disband: retained receipt identity lets delayed Market delivery resolve against the missing guild as a terminal stale run. Existing reward-ownership and cosmetic-ledger cleanup remain part of the guild deletion transaction.
