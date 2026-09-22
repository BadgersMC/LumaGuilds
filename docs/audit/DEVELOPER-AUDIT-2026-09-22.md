# LumaGuilds Developer Audit — Reported Findings (2026-09-22)

This document records findings supplied by the project developers from a **separate audit**.
It is intentionally kept distinct from the September 2026 ChatGPT/codebase re-audit and
from the June mass audit. Overlap between audits does not change the provenance of a finding.

## Status legend

- **◻️ DEV-REPORTED** — reported by the developer audit; not independently re-verified in this track yet.
- **🛠 REMEDIATED IN #168** — independently matched to code corrected by pending PR #168
  (`fix(claims): repair audited claim workflows`). This means the fix exists on that PR branch;
  it does not imply the PR has merged.
- **🛠 REMEDIATED IN #171** — independently source-confirmed and corrected by pending PR #171
  (`fix(audit): make chapter and quest rewards crash safe`).
- **🛠 REMEDIATED IN #172** — independently source-confirmed and corrected by pending PR #172
  (`fix(quests): harden generated quest progress`).
- **🔎 NOT REPRODUCED / HARDENED IN #172** — the exact reported failure mode was not present
  in the current runtime, but adjacent state handling was strengthened to preserve the intended invariant.

**Inventory:** 37 reported findings total; 10 remediated in #168; 4 remediated in #171;
4 remediated in #172; 1 not reproduced/hardened in #172; 18 still awaiting independent
verification and/or remediation.

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
### ◻️ DEV-10 — Concurrent role syncs can create duplicate guild roles
Two role synchronization operations running at nearly the same time can both decide a role must
be created.

### ◻️ DEV-11 — Join/leave role updates can complete out of order
A rapid guild join/leave sequence can leave a stale async role update completing last, causing a
player to retain a guild role they should no longer have.

---

## War lifecycle, objectives, and limits

### ◻️ DEV-12 — Expired-draw wars skip the normal war-end lifecycle
Wars that expire as draws reportedly do not fire the standard war-end event/notifications.

### ◻️ DEV-13 — Rank renames can break claim permissions
Claim permissions are tied to rank names rather than stable rank IDs, so renaming a rank can
disconnect the permission mapping.

### ◻️ DEV-14 — A war-ending kill can end the war before kill processing completes
The war can terminate before the kill event, XP award, and kill messages finish processing.

### ◻️ DEV-28 — `War.isExpired` cannot become true at zero remaining time
The reported logic clamps remaining duration to zero and then checks whether zero is negative.

### ◻️ DEV-29 — Peace agreements skip the normal war-end lifecycle
Peace completion can bypass the same standard war-end event/notification path.

### ◻️ DEV-30 — War declaration cooldown is recorded but not enforced
A guild can reportedly create another declaration without the stored declaration cooldown
actually blocking it.
### ◻️ DEV-31 — War farming cooldown is stored but not enforced
The anti-farming cooldown reportedly does not prevent another war.

### ◻️ DEV-32 — War cooldowns are memory-only
Declaration/farming cooldown state disappears across a restart even if enforcement is added.

### ◻️ DEV-33 — Max simultaneous war limit is checked only for the declaring guild
The defending guild can be pushed above its own simultaneous-war cap.

### ◻️ DEV-34 — Non-kill war objectives do not update real progress
`addObjectiveProgress()` reportedly logs the progress and returns success without changing the
objective state.

### ◻️ DEV-35 — Bedrock 100-kill objective conflicts with the 25-kill global win target
Bedrock can select a 100-kill objective while the default global kill target ends the war at 25,
making that objective unreachable under the default configuration.

### ◻️ DEV-36 — Java can target peaceful guilds for war
Bedrock filters peaceful guilds, but the Java menu/service path reportedly does not fully enforce
the same restriction.

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
### ◻️ DEV-25 — Some multi-block protections stop after the first allowed block
Reported examples include pistons crossing claim boundaries. An early allowed block can prevent
later affected blocks from being checked.

### ◻️ DEV-26 — Splash/lingering potion protection can stop checking affected entities early
An early allowed entity can reportedly end validation before later animals/entities in the same
splash are checked.

### ◻️ DEV-27 — One block-explosion protection path listens to the wrong event
A handler intended for block explosions reportedly listens to `EntityExplodeEvent` rather than
the block explosion event, allowing some block explosions to miss that protection path.

### ◻️ DEV-37 — Guild-owned claim management still relies on original owner checks in places
Some management paths reportedly validate the original player owner instead of the guild/member
permission model, which can strand a guild-owned claim when that original player is gone.

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
- The remaining **18 DEV-REPORTED findings** should be source-verified before implementation.
  If confirmed, preserve these IDs in future PR descriptions so fixes can be traced back to this
  audit without conflating it with the June or September ChatGPT audit tracks.