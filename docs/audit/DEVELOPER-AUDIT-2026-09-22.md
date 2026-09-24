# LumaGuilds Developer Audit — Reported Findings (2026-09-22)

This document records findings supplied by the project developers from a **separate audit**.
It is intentionally kept distinct from the September 2026 ChatGPT/codebase re-audit and
from the June mass audit. Overlap between audits does not change the provenance of a finding.

## Status legend

- **◻️ DEV-REPORTED** — reported by the developer audit; not independently re-verified in this track yet.
- **🛠 REMEDIATED IN #168** — independently matched to code corrected by merged PR #168
  (`fix(claims): repair audited claim workflows`).
- **🛠 REMEDIATED IN #171** — independently source-confirmed and corrected by merged PR #171
  (`fix(audit): make chapter and quest rewards crash safe`).
- **🛠 REMEDIATED IN #172** — independently source-confirmed and corrected by merged PR #172
  (`fix(quests): harden generated quest progress`).
- **🛠 REMEDIATED IN #173** — independently source-confirmed and corrected by merged PR #173
  (`fix(discord): serialize guild role synchronization`).
- **🛠 REMEDIATED IN #174** — independently source-confirmed and corrected by merged PR #174
  (`fix(wars): harden terminal lifecycle resolution`).
- **🛠 REMEDIATED IN #175** — independently source-confirmed and corrected by merged PR #175
  (`fix(claims): preserve rank permission identity`).
- **🛠 REMEDIATED IN #176** — independently source-confirmed and corrected by merged PR #176
  (`fix(wars): enforce durable war policy`).
- **🛠 REMEDIATED IN #177** — independently source-confirmed and corrected by merged PR #177
  (`fix(claims): close protection and guild management gaps`).
- **🔎 NOT REPRODUCED / HARDENED IN #172** — the exact reported failure mode was not present
  in the current runtime, but adjacent state handling was strengthened to preserve the intended invariant.

**Inventory:** 37 reported findings total; 10 remediated in #168; 4 remediated in #171;
4 remediated in #172; 2 remediated in #173; 4 remediated in #174; 1 remediated in #175;
7 remediated in #176; 4 remediated in #177; 1 not reproduced/hardened in #172;
**0 findings remain awaiting independent verification or remediation.**

---

## Chapter lifecycle / crash consistency

### 🛠 DEV-01 — Chapter rollover can become non-idempotent after a crash — #171
Source-confirmed. PR #171 makes archive/reset work transactional with the lifecycle phase
advance, so a failed phase write rolls the associated chapter mutation back instead of leaving
replayable partial state.

### 🛠 DEV-02 — Chapter backup file/database state can diverge — #171
Source-confirmed. PR #171 can adopt an orphaned backup file only after integrity/restore
verification and confirmation that it captured the expected frozen chapter state, then commits
its evidence and lifecycle transition.

### 🛠 DEV-03 — Weekly quest rewards can be lost across a crash — #171
Source-confirmed. PR #171 persists claim actor/delivery state, uses deterministic XP transaction
IDs, reconciles pending claims on the weekly coordinator, and retains undelivered rows through
week cleanup. Existing historical claimed rows migrate as already delivered.

### 🛠 DEV-04 — Weekly leaderboard rewards can be paid twice across a crash — #171
Source-confirmed. PR #171 uses deterministic payout transaction IDs and refuses to clear/advance
the weekly reset until the durable paid marker is confirmed. Retrying therefore reuses the same
XP transaction instead of paying twice.

---

## Quest generation and progress tracking

### 🔎 DEV-05 — Natural-block quests can count old player-placed blocks — #172
The reported **quest-activation gap was not reproduced** in the current runtime: the always-
registered progression listener already recorded eligible player placements independently of
which quests were active. PR #172 nevertheless hardens provenance as a world-state invariant by
recording all player placements, including Creative/Spectator placements that are not XP-eligible.
Blocks placed before provenance tracking existed remain outside what the current database can
retroactively identify.

### 🛠 DEV-06 — Player-placed block markers are not always cleared on normal break — #172
Source-confirmed. PR #172 clears provenance on ordinary breaks even when the breaker has no guild
and synchronizes cleanup with pending async placement writes, closing the fast place/break race
that could recreate a stale marker after deletion.

### 🛠 DEV-07 — Quest generator can generate impossible natural-block targets — #172
Source-confirmed. PR #172 rejects player-producible blocks from `NATURAL_ONLY` mining targets
unless they are explicitly known to also world-generate, and always excludes netherite blocks.

### 🛠 DEV-08 — Some crafting quests can target recipes outside the normal crafting event — #172
Source-confirmed against Paper 1.21.11 behavior. PR #172 limits generated crafting quests and the
craft-progress handler to crafting-matrix `CraftingRecipe` paths, excluding smithing,
stonecutting, and other recipe types that `CraftItemEvent` does not represent.

### 🛠 DEV-09 — Shift-click crafting can undercount quest progress — #172
Source-confirmed. PR #172 calculates the total shift-click output from both available matrix
ingredients and destination inventory capacity rather than counting one recipe result.

---

## Discord role synchronization
### 🛠 DEV-10 — Concurrent role syncs can create duplicate guild roles — #173
Source-confirmed in the earlier Discord-role implementation. The duplicate-creation race was already
closed by #158 before this audit-tail PR: role creation publishes a placeholder future with
`putIfAbsent` before invoking the external gateway, so only one caller can create the role. PR #173
retains that safer single-flight design and adds/keeps concurrency coverage while serializing the
remaining per-member synchronization paths.

### 🛠 DEV-11 — Join/leave role updates can complete out of order — #173
Source-confirmed. Join and removal calls previously launched independent Discord futures with no
ordering guarantee. PR #173 serializes grant/revoke operations per `(guildId, playerId)` and uses
the same queue for reconciliation and account unlink paths, so rapid join/leave/rejoin updates
complete in invocation order and the latest requested state wins.

---

## War lifecycle, objectives, and limits

### 🛠 DEV-12 — Expired-draw wars skip the normal war-end lifecycle — #174
Source-confirmed. Expired draws used a persistence-only path that skipped the standard
`GuildWarEndEvent` and war-ended notification lifecycle, and `processExpiredWars()` had no
production caller at all. PR #174 routes expiration through the shared terminal lifecycle and
schedules war maintenance once per minute after the war listeners are registered.

### 🛠 DEV-13 — Rank renames can break claim permissions — #175
Source-confirmed. Claim permission lookup used the mutable rank display name as the
`team_role_permissions.roles.*` key, so a rename could silently fall back to default claim
permissions. PR #175 persists an immutable rank-UUID -> legacy permission-profile name mapping,
establishes that profile before any rename/update, compensates failed rank/profile creation, and
adds SQLite/MariaDB schema v40 plus regression coverage proving permissions survive rename,
cache invalidation, and repository reload.

### 🛠 DEV-14 — A war-ending kill can end the war before kill processing completes — #174
Source-confirmed. The kill counter previously ended the war inside the durable counter update,
before the listener fired the kill event, awarded XP, and sent killer/victim messages. PR #174
persists the decisive kill first, completes those kill-side effects, then revalidates and resolves
the terminal state. A restart reconciliation pass recovers the persisted decisive-kill crash gap.

### 🛠 DEV-28 — `War.isExpired` cannot become true at zero remaining time — #174
Source-confirmed. `remainingDuration` intentionally clamps elapsed wars to `Duration.ZERO`, while
`isExpired` then tested whether that value was negative. PR #174 computes expiration directly
from `startedAt + duration`, so elapsed wars report expired without changing the display-friendly
clamped remaining duration.

### 🛠 DEV-29 — Peace agreements skip the normal war-end lifecycle — #174
Source-confirmed. Peace acceptance manually persisted `ENDED` state and settled the wager rather
than traversing the normal terminal lifecycle. PR #174 routes accepted peace through the same
rating, wager settlement, end-event, cooldown, and terminal notification hook as other endings.
The current notification model only persists VICTORY/DEFEAT recipients, so no-winner peace/draw
outcomes intentionally do not fabricate a durable victory/defeat notification.

### 🛠 DEV-30 — War declaration cooldown is recorded but not enforced — #176
Source-confirmed. The service recorded declaration cooldowns but never consulted them during
admission. PR #176 enforces the cooldown before creating another declaration and persists the
exact deadline on the durable declaration record.

### 🛠 DEV-31 — War farming cooldown is stored but not enforced — #176
Source-confirmed. The anti-farming cooldown had query/update methods but was not part of war
admission. PR #176 checks both participants for an active farming cooldown at declaration and
again at acceptance before any new escrow is funded.

### 🛠 DEV-32 — War cooldowns are memory-only — #176
Source-confirmed. Both declaration and farming cooldowns previously lived in process-local maps.
PR #176 stores exact cooldown deadlines in the existing revision-checked durable war record,
upgrades the war-record codec to v4, and keeps v1-v3 records readable via persisted timestamp
fallback. SQL restart tests verify both cooldown classes survive service/repository restart.

### 🛠 DEV-33 — Max simultaneous war limit is checked only for the declaring guild — #176
Source-confirmed. Admission counted active wars only for the declaring guild. PR #176 applies each
guild's effective slot limit to both participants at declaration and rechecks both at acceptance,
covering the pending-declaration race where a target can fill its final slot before accepting.

### 🛠 DEV-34 — Non-kill war objectives do not update real progress — #176
Source-confirmed. `addObjectiveProgress()` previously only logged and returned success. PR #176
revision-writes objective progress into the durable war record, clamps progress at the target,
persists completion metadata, and verifies progress/completion across repository restart.

### 🛠 DEV-35 — Bedrock 100-kill objective conflicts with the 25-kill global win target — #176
Source-confirmed. Bedrock hard-coded a 100-kill objective while the global default ends wars at 25,
and Java exposed fixed kill targets independently of the configured cap. PR #176 makes the global
kill target authoritative in the service, Bedrock, and Java objective selectors, including Java's
initial default objective.

### 🛠 DEV-36 — Java can target peaceful guilds for war — #176
Source-confirmed. The Java target menu did not filter peaceful guilds and the service boundary did
not enforce mode eligibility. PR #176 hides peaceful targets in Java and requires both guilds to
remain HOSTILE at declaration and acceptance, with service-level regression coverage.

---

## Claim transfers and claim command correctness

### 🛠 DEV-15 — Grant-all claim permissions called remove instead of add — #168
`GrantAllPlayerClaimPermissions` was backwards and could revoke all permissions. PR #168 changes
the operation to grant the permissions and adds regression coverage.

### 🛠 DEV-16 — Claim transfer requests were not durable — #168
Transfer offers are now stored through a persistent transfer-request repository instead of only
living in transient claim state.
### 🛠 DEV-17 — Transfer withdrawal existence check was inverted — #168
PR #168 corrects the withdrawal path so an existing request can actually be withdrawn.

### 🛠 DEV-18 — Transfer expiration was stored but not enforced — #168
Acceptance now queries an active request using the current epoch time, so expired offers no
longer qualify as active.

### 🛠 DEV-19 — Transfer name collision was checked against the old owner — #168
Acceptance now checks `getByName(playerId, newName)` against the receiving player.

### 🛠 DEV-20 — Accepted transfer did not apply the requested new name — #168
The accepted claim copy now persists both the new owner and `name = newName`.

### 🛠 DEV-21 — `DoesClaimHaveFlag` continued after a missing claim — #168
The action now returns `ClaimNotFound` immediately when the claim lookup fails.

### 🛠 DEV-22 — `/partitions` pagination reused the beginning of the list — #168
PR #168 replaces the expanding-from-zero range with proper page bounds.

### 🛠 DEV-23 — Claim/move tools could remain in player death drops — #168
The death listener now actually removes the identified tools from the drop collection.

### 🛠 DEV-24 — `/claim info` could display the command sender as owner — #168
The display path now resolves the actual claim owner instead of substituting the viewer.

---

## Claim protection event coverage
### 🛠 DEV-25 — Some multi-block protections stop after the first allowed block — #177
Source-confirmed. Piston and sponge protection traversals could stop after an earlier allowed
entry and skip a later protected target. PR #177 makes the affected traversals continue through
allowed entries and adds regression coverage proving a later denied target is still evaluated.

### 🛠 DEV-26 — Splash/lingering potion protection can stop checking affected entities early — #177
Source-confirmed. Splash and lingering potion loops returned from the whole handler when an exempt
Monster or Player appeared first. PR #177 skips only that exempt entity and continues evaluating
later passive entities, with ordered traversal regression coverage.

### 🛠 DEV-27 — One block-explosion protection path listens to the wrong event — #177
Source-confirmed. The handler named for block explosions accepted `EntityExplodeEvent`, leaving
`BlockExplodeEvent` without that protection path. PR #177 binds the handler to the correct event
and includes a signature regression test.

### 🛠 DEV-37 — Guild-owned claim management still relies on original owner checks in places — #177
Source-confirmed across command, menu, edit-tool, anchor movement, and claim-destruction paths.
PR #177 centralizes actor-authoritative management for guild-owned claims using current rank
permissions (`MANAGE_CLAIMS`, `MANAGE_FLAGS`, `MANAGE_PERMISSIONS`, and `DELETE_CLAIMS`) while
preserving original-owner and explicit-override compatibility. Regression coverage verifies a
non-owner guild manager can administer and move a converted claim, and that permission sharing
includes the authorized guild manager while preserving implicit access for the historical owner.

---

## Remediation tracking

- **PR #168:** DEV-15 through DEV-24 are represented by source changes and tests on the pending
  claim-correctness PR.
- **PR #169:** separate September re-audit remediation for threading, vault write-buffer races,
  and owned executor lifecycle; it is not counted as resolving any of the 37 developer-audit
  findings above.
- **PR #171:** DEV-01 through DEV-04 were independently source-confirmed and remediated with
  chapter-transition atomicity, orphan-backup recovery, durable quest reward reconciliation,
  and deterministic weekly payout transaction IDs.
- **PR #172:** DEV-06 through DEV-09 were independently source-confirmed and remediated across
  provenance cleanup, target generation, recipe eligibility, and shift-click progress counting.
  DEV-05's exact activation-gap report was not reproduced, but provenance tracking was hardened
  so all player placements are recorded regardless of XP eligibility.
- **PR #173:** DEV-10 and DEV-11 were independently source-confirmed. The role-creation single-flight
  was already present from #158 and preserved/verified in this audit tail; #173 adds serialized
  per-member role mutations and reconciliation repair so asynchronous updates cannot finish out of order.
- **PR #174:** DEV-12, DEV-14, DEV-28, and DEV-29 were independently source-confirmed and
  remediated through a shared terminal war lifecycle, post-kill terminal resolution, corrected
  expiration semantics, restart reconciliation, and production scheduling of war maintenance.
- **PR #175:** DEV-13 was independently source-confirmed and remediated by persisting a stable
  rank UUID to legacy claim-permission profile identity, preserving existing name-keyed operator
  configuration across rank renames, with schema v40 and failure-compensation regression coverage.
- **PR #176:** DEV-30 through DEV-36 were independently source-confirmed and remediated with
  durable/enforced war cooldowns, bilateral war-slot admission, persisted objective progress,
  configured kill-target consistency, and authoritative hostile-mode checks.
- **PR #177:** DEV-25, DEV-26, DEV-27, and DEV-37 were independently source-confirmed and remediated
  across multi-target protection traversal, potion/explosion event coverage, and actor-authoritative
  guild-claim management.
- **All 37 developer-audit findings are now independently accounted for:** 36 were remediated or
  verified as already remediated, and DEV-05's exact activation-gap report was not reproduced but
  adjacent provenance handling was hardened. No findings remain awaiting remediation in this track.