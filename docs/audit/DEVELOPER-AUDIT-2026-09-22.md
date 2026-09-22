# LumaGuilds Developer Audit — Reported Findings (2026-09-22)

This document records findings supplied by the project developers from a **separate audit**.
It is intentionally kept distinct from the September 2026 ChatGPT/codebase re-audit and
from the June mass audit. Overlap between audits does not change the provenance of a finding.

## Status legend

- **◻️ DEV-REPORTED** — reported by the developer audit; not independently re-verified in this track yet.
- **🛠 REMEDIATED IN #168** — independently matched to code corrected by pending PR #168
  (`fix(claims): repair audited claim workflows`). This means the fix exists on that PR branch;
  it does not imply the PR has merged.

**Inventory:** 37 reported findings total; 10 remediated in #168; 27 still awaiting
independent verification and/or remediation.

---

## Chapter lifecycle / crash consistency

### ◻️ DEV-01 — Chapter rollover can become non-idempotent after a crash
Archive/reset writes can persist before chapter state advances. A restart can retry the same
rollover and attempt duplicate inserts, leaving rollover stuck.

### ◻️ DEV-02 — Chapter backup file/database state can diverge
A backup file can be created before its database record. If the DB write fails, the next attempt
finds the existing file and refuses to proceed, leaving backup creation stuck.

### ◻️ DEV-03 — Weekly quest rewards can be lost across a crashThe quest can be marked claimed before XP/items are delivered. A crash in that window can
persist the claimed state without the reward.

### ◻️ DEV-04 — Weekly leaderboard rewards can be paid twice across a crash
XP can be granted before the persistent "already paid" state is saved. A crash in that window
can cause the reward to be issued again after restart.

---

## Quest generation and progress tracking

### ◻️ DEV-05 — Natural-block quests can count old player-placed blocks
Blocks placed before a relevant quest became active may not be tracked as player-placed and can
later be counted as natural blocks.

### ◻️ DEV-06 — Player-placed block markers are not always cleared on normal break
A location can remain marked player-placed after the original block is gone, corrupting future
natural-block checks for that location.

### ◻️ DEV-07 — Quest generator can generate impossible natural-block targets
Example reported: mining naturally generated netherite blocks.

### ◻️ DEV-08 — Some crafting quests can target recipes outside the normal crafting event
Those recipes may never emit the event used for quest progress, making the generated quest
impossible to complete through the tracked path.

### ◻️ DEV-09 — Shift-click crafting can undercount quest progress
Progress can count a single recipe result rather than the total amount crafted by the shift-click.

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
- The remaining **27 DEV-REPORTED findings** should be source-verified before implementation.
  If confirmed, preserve these IDs in future PR descriptions so fixes can be traced back to this
  audit without conflating it with the June or September ChatGPT audit tracks.