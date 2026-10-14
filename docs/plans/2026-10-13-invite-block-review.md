# Invite block PR review

Spec: REQ-150/151 and user-provided invite-block document. Canonical main fetched at283d75f1 and PR #232 fork head db53a274 inspected. Isolated codex/invite-blocks-pr-review contains only invite work. Preserve prior local quest/stall work outside this PR.

Prove: local integration regressions reproduced raw invite counts/presence and malformed UUID loading, then passed after corrections (local source5176e7f). Port those tested refinements plus routing/viewer coverage. Original PR Codacy detail reports136 new findings (summary first100); formatting, effective visibility/public docs, complexity, excessive returns/deep nesting, and migration magic values will be addressed without disabling quality rules.

Engine/arch: SQL repository loads blocks once, publishes cache only after durable write, serializes writes per instance and fails closed on incomplete load. Application ports remain independent of platform; Bukkit listeners/API wiring preserve current contracts and memberships. No GUI changes, so visual preview not applicable.

Verification pending: focused/full Java25 Paper26.2 tests, exact-head GitHub checks and review. No EARS/state helpers or generic verification.md present; manual traceability used. TEST currently runs earlier local combined artifact; later PR refinement is distinct and will not be deployed this turn. No production changes or merge. Multi-server cache invalidation and real player/native MariaDB acceptance remain outside local proof.

## Local validation

Ported correctness/routing/viewer regressions from tested local integration. Original malformed load and invite-count cases failed before correction; no historical red/green claim for inherited tests. Added SQLite trigger-constraint case to verify failed writes cannot be mistaken for duplicate-key success. SQL duplicate acceptance now verifies that the exact block row exists. Repository mutations remain serialized per instance.

Ktlint1.0.1 targeted new-file formatting at120 columns passed using an external operation-only init script; project quality configurations untouched. Full Java25/Paper26.2 offline check shadowJar passed; results recorded in operation evidence. SQLite load/reopen/idempotency/write failure/schema create/repair, real ACF block/unblock/list permission routing, memory-only viewer filtering and JDK API delegation exercised. Native MariaDB and player/cross-server cache acceptance remain separate. LocaleContractTest initially rejected a dynamic message-key refactor; restored literal keys and full contract passes.

Hosted exact-head checks pending after publication. TEST remains on prior local combined5176e7f build; no further server operations or production changes.

## Hosted refinement

Codacy original detail count136 was reduced to51 then17. Follow-up preserves public compatibility service/API signatures while keeping internal persistence/types private, shortens fixture methods, extracts SQL row iteration, and aligns compact parameters and expression wrapping with hosted analyzers. Plain SQL strings avoid contradictory raw-string indentation rules; the table remains owner_id/target_id/created_at with the same composite key and dialect options. No quality-gate configuration changes. Local full suite at bfd2fea passed1785 tests, zero failures/errors,16 optional skips. Final style/helper refinements passed focused invite/schema tests and shadowJar. Exact latest hosted full-suite and Codacy results remain pending.

GitHub connector write scope denied PR metadata changes; signed-in GitHub UI successfully updated description and marked existing PR ready for review. CodeRabbit now reports automatic reviews disabled because repository has fewer than10 stars; manual comment trigger requires user authorization. No external review message posted.

## Manual review and integration refinement

The user authorized remaining review/integration work and TEST staging without restart. Manual CodeRabbit review of1f328df completed with three findings, all verified: log failed SQL writes with IDs/cause, repair a missing MariaDB invite-block table even atv45, and use direct blocked-player membership lookups. These are corrected without changing block semantics. The permanent InviteBlockMigrationSQLTest runs in ordinary SQLite tests and the existing opt-in mariaDbRewardTest profile. An operation-only native MariaDB11.8.3 loopback profile reproduced the missing-table failure before correction and passed three native cases afterward. Focused invite tests passed16 cases with no failures/errors/skips.

Current merged EnthusiaFriends main4c29703 already contains the connector. Its local check and Paper packaging pass234 tests with two optional skips. The actual Friends JAR downloaded from TEST contains GuildBlocks; a compiled bridge harness exercises it against the actual current GuildInviteBlocks API/adapter, covering permission, block, unblock and failed-save propagation. Application storage in that harness is simulated; native SQL is separately exercised. Cross-instance cache proof confirms another instance keeps its startup snapshot until recreated; no immediate cross-server synchronization is claimed.

Canonical main remains283d75f1. Preserve local quest/stall commits in the separate combined TEST branch. Final exact-head hosted checks and later client acceptance remain separate gates. No merge, restart, reload or production modification is authorized by this update.
