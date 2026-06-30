# Audit Report: application/actions

**Branch:** fix/audit-app-actions
**Scope:** 66 files under `src/main/kotlin/net/lumalyte/lg/application/actions/`
**Methodology:** See `/opt/data/audit-app-actions-plan.md`

---

## Findings

### [SEV: high] application/actions/claim/transfer/AcceptTransferRequest.kt:39 — Name-uniqueness check uses wrong playerId

**What:** Line 39 calls `claimRepository.getByName(claim.playerId, newName)` — this checks whether the **old owner** already has a claim named `newName`. It should check whether the **accepting player** (`playerId`) already has a claim with that name, since the claim is being transferred to them and the name must be unique per-player.

**Why it matters:** After transfer, the accepting player could end up with two claims sharing the same name (their existing one + the transferred one), violating the uniqueness invariant enforced by `CreateClaim` and `UpdateClaimName`. Conversely, the transfer could be incorrectly rejected when the old owner happens to have a claim with the same name but the accepting player does not.

**Suggested fix:** Change `claim.playerId` to `playerId` on line 39: `claimRepository.getByName(playerId, newName)`.

**Confidence:** high

---

### [SEV: high] application/actions/claim/transfer/AcceptTransferRequest.kt:43 — Mutable playerId on Claim enables silent data corruption

**What:** `Claim.playerId` is declared as `var` (line 23 of Claim.kt). `AcceptTransferRequest` mutates it directly at line 43 (`claim.playerId = playerId`) and then calls `claimRepository.update(claim)`. Because `Claim` is a data class with a mutable `playerId`, this in-place mutation works but bypasses any domain-level validation or copying semantics. If any other code holds a reference to the same `Claim` instance (e.g., in a cache or the repository's internal store), it sees the mutated `playerId` before `update()` is called, creating a race condition.

**Why it matters:** In a concurrent server environment, another thread reading the same `Claim` object between lines 43 and 44 could see an inconsistent state (new `playerId` but old `teamId`, stale `transferRequests`, etc.). The `update()` call may or may not replace the cached instance depending on repository implementation.

**Suggested fix:** Use `val updatedClaim = claim.copy(playerId = playerId)` and pass `updatedClaim` to `claimRepository.update()`. This requires `playerId` to remain `var` for the copy to work, but the pattern is safer. Ideally, make `playerId` a `val` and treat ownership change as a domain event.

**Confidence:** high

---

### [SEV: high] application/actions/claim/permission/GrantAllPlayerClaimPermissions.kt:27 — Calls remove() instead of add() when granting permissions

**What:** Line 27 calls `playerAccessRepository.remove(claimId, playerId, permission)` inside a loop that is supposed to **grant** all permissions. The method is named `GrantAllPlayerClaimPermissions` and its docstring says "Adds all available permissions" but the implementation calls `remove`.

**Why it matters:** This action revokes all permissions from a player instead of granting them. Any caller relying on this to share a claim with another player will instead strip their access, which is a functional inversion — a silent logic bug that breaks the permission system.

**Suggested fix:** Change `playerAccessRepository.remove(...)` to `playerAccessRepository.add(...)` on line 27.

**Confidence:** high

---

### [SEV: high] application/actions/claim/transfer/OfferPlayerTransferRequest.kt:14 — Transfer request not persisted to storage

**What:** Line 14 adds the transfer request to the in-memory `claim.transferRequests` map, but never calls `claimRepository.update(claim)` to persist the change. The request exists only in the JVM heap.

**Why it matters:** If the server restarts, all pending transfer requests are silently lost. On a single-server setup with no restart, the request works, but any future refactoring that re-reads the claim from the database (e.g., in another action) will not see the request. This is a data-loss bug.

**Suggested fix:** After line 14, call `claimRepository.update(claim)` and handle the potential failure (return a storage error result).

**Confidence:** high

---

### [SEV: high] application/actions/claim/transfer/WithdrawPlayerTransferRequest.kt:10 — Inverted logic: returns NoPendingRequest when request EXISTS

**What:** Line 10 checks `if (playerId in claim.transferRequests.keys)` and returns `NoPendingRequest`. This is backwards: it should return `NoPendingRequest` when the playerId is **NOT** in the map, and proceed to remove it when it **IS** present.

**Why it matters:** A player can never withdraw a transfer request. When a request exists, the action says "no pending request". When no request exists, the code falls through to `claim.transferRequests.remove(playerId)` (a no-op) and returns `Success` — silently doing nothing.

**Suggested fix:** Negate the condition: `if (playerId !in claim.transferRequests.keys) return WithdrawPlayerTransferRequestResult.NoPendingRequest`.

**Confidence:** high

---

### [SEV: med] application/actions/claim/anchor/BreakClaimAnchor.kt:10 — Bukkit (org.bukkit) import in application layer

**What:** Line 10 imports `org.bukkit.plugin.java.JavaPlugin` and line 18 stores it as a constructor parameter. The `Claim.resetBreakCount(plugin)` method (Claim.kt:57) also uses `org.bukkit.Bukkit.getScheduler()` directly.

**Why it matters:** This is a hexagonal architecture layer violation. The application layer must not depend on Bukkit/framework types. The `BreakClaimAnchor` action and the `Claim` domain entity both leak Bukkit dependencies, making them untestable without a Bukkit server and tightly coupling domain logic to the Minecraft scheduler.

**Suggested fix:** Introduce a `SchedulerPort` interface in the application layer, inject it into `BreakClaimAnchor`, and have `Claim.resetBreakCount` accept it instead of `JavaPlugin`. The Bukkit implementation lives in infrastructure.

**Confidence:** high

---

### [SEV: med] application/actions/claim/anchor/MoveClaimAnchor.kt:26 — NoClaimFound treated as valid move target

**What:** Line 26: when `getClaimAtPosition.execute()` returns `NoClaimFound`, the code returns `MoveClaimAnchorResult.InvalidPosition`. But semantically, if there is no claim at the new position, the anchor can be moved there — the position is valid because it's empty. The current logic only allows moving the anchor to a position that already has the same claim, which is circular.

**Why it matters:** Moving a claim anchor to an empty location is rejected, preventing legitimate anchor relocations. The check on line 31 (`claimAtPosition.id != claimId`) already handles the case where a *different* claim occupies the position. The `NoClaimFound` case should be allowed to proceed.

**Suggested fix:** Remove the `NoClaimFound -> return InvalidPosition` branch (line 26) and let execution fall through to the position-update logic.

**Confidence:** med

---

### [SEV: med] application/actions/claim/anchor/MoveClaimAnchor.kt:49 — newWorldId parameter ignored when moving anchor

**What:** The `newWorldId` parameter is accepted at line 19 but never used. Line 49 copies the claim with `existingClaim.copy(position = newPosition)` but does not update `worldId`. The old anchor is broken from `existingClaim.worldId` (line 48), but the claim's `worldId` field remains unchanged.

**Why it matters:** If the anchor is moved to a different world, the claim's `worldId` will be inconsistent with the anchor's actual location. This could cause partition lookups, visualizations, and permission checks to fail or target the wrong world.

**Suggested fix:** Copy with both fields: `existingClaim.copy(position = newPosition, worldId = newWorldId)`. Note: `worldId` is currently `val` in `Claim`, so this requires making it `var` or reconstructing the claim.

**Confidence:** med

---

### [SEV: med] application/actions/claim/flag/DoesClaimHaveFlag.kt:12 — Missing return before ClaimNotFound result

**What:** Line 12: `claimRepository.getById(claimId) ?: DoesClaimHaveFlagResult.ClaimNotFound` — the `return` keyword is missing before the expression. The `ClaimNotFound` result is constructed but discarded, and execution always falls through to line 13.

**Why it matters:** The claim-existence check is completely non-functional. The method will attempt to query `claimFlagRepository.doesClaimHaveFlag(claimId, flag)` with a potentially non-existent claimId, which could return incorrect results or throw an exception depending on the repository implementation.

**Suggested fix:** Add `return` before `DoesClaimHaveFlagResult.ClaimNotFound` on line 12.

**Confidence:** high

---

### [SEV: med] application/actions/claim/partition/ResizePartition.kt:31 — Redundant disconnection check (lines 31 and 57)

**What:** `isResizeResultInAnyDisconnected(newPartition)` is called twice — at line 31 and again at line 57, with identical arguments. The second call is dead code because if the first returns `true`, the method returns `Disconnected` at line 31 and never reaches line 57.

**Why it matters:** Not a bug per se, but the duplicate check suggests a copy-paste error. If the intent was to check something different the second time (e.g., after the block-limit check), the logic is wrong. It also adds unnecessary computational cost (BFS over partitions) for every resize operation.

**Suggested fix:** Remove the duplicate call at line 57 (and its associated comment block). If a different check was intended, clarify the condition.

**Confidence:** high

---

### [SEV: med] application/actions/claim/partition/ResizePartition.kt:52 — Incorrect remaining-blocks calculation

**What:** Line 52: `val requiredExtraBlocks = (playerBlockCount + newArea.getBlockCount()) - playerBlockLimit`. This does not subtract the old partition's block count. The correct formula should be: `(playerBlockCount - partition.area.getBlockCount() + newPartition.area.getBlockCount()) - playerBlockLimit`, which is the same pattern used correctly on line 51 for the comparison.

**Why it matters:** The `InsufficientBlocks` result will report an inflated `requiredExtraBlocks` value (off by `partition.area.getBlockCount()`), confusing the player about how many extra blocks they need. The actual block-limit check on line 51 is correct, so the request is still rejected properly, but the error message is wrong.

**Suggested fix:** Change line 52 to: `val requiredExtraBlocks = (playerBlockCount - partition.area.getBlockCount() + newArea.getBlockCount()) - playerBlockLimit`.

**Confidence:** high

---

### [SEV: med] application/actions/claim/partition/ResizePartition.kt:61 — blocksRemaining calculation double-counts new area

**What:** Line 61: `val blocksRemaining = playerBlockLimit - playerBlockCount - newArea.getBlockCount()`. This subtracts the full new area from the remaining blocks, but `playerBlockCount` already includes the old partition's blocks. The correct formula is: `playerBlockLimit - (playerBlockCount - partition.area.getBlockCount() + newPartition.area.getBlockCount())`.

**Why it matters:** The `blocksRemaining` value reported in the success result will be too low (under-counted by `partition.area.getBlockCount()`). This gives players an incorrect picture of their remaining claim block budget.

**Suggested fix:** `val blocksRemaining = playerBlockLimit - playerBlockCount + partition.area.getBlockCount() - newArea.getBlockCount()`.

**Confidence:** high

---

### [SEV: med] application/actions/claim/partition/CreatePartition.kt:47-53 — Adjacency check only validates same-claim partitions

**What:** Lines 47-53: after passing overlap and distance checks, the code looks for an adjacent partition with the same `claimId`. If found, it adds the partition. If no adjacent same-claim partition exists, it returns `Disconnected`. However, for the **first** partition of a new claim, there are no existing partitions for that claim, so this check always fails and returns `Disconnected`.

**Why it matters:** This means `CreatePartition` can never create the first partition of a claim — only additional partitions attached to an existing one. If claim creation is handled elsewhere (e.g., `CreateClaim` creates the initial partition), this may be intentional, but the naming and result type suggest it should handle both cases. If called for a claim with only one existing partition that doesn't happen to be adjacent to the new area, it also fails.

**Suggested fix:** Clarify the intended scope. If this is only for adding partitions to existing claims, document it. If it should also handle first partitions, add a special case: if the claim has no existing partitions, skip the adjacency check.

**Confidence:** med

---

### [SEV: med] application/actions/claim/IsPlayerActionAllowed.kt:114 — TRIGGER_RAID mapped to both DETONATE and EVENT

**What:** `PlayerActionType.TRIGGER_RAID` appears twice in the `actionToPermissionMapping` map (lines 110 and 114), mapped to `ClaimPermission.DETONATE` and `ClaimPermission.EVENT` respectively. Since Kotlin `mapOf` with duplicate keys keeps the **last** entry, the effective mapping is `TRIGGER_RAID -> EVENT`, not `DETONATE`.

**Why it matters:** Triggering a raid will be checked against the `EVENT` permission instead of `DETONATE`. If a claim has `DETONATE` denied but `EVENT` allowed, raids will incorrectly be permitted (or vice versa). The developer likely intended for `TRIGGER_RAID` to require both permissions, but the map structure cannot express that.

**Suggested fix:** Decide which permission `TRIGGER_RAID` should map to and remove the duplicate. If both are needed, the permission-check logic in `IsPlayerActionAllowed` needs to check multiple permissions per action type (change the map to `Map<PlayerActionType, List<ClaimPermission>>`).

**Confidence:** high

---

### [SEV: med] application/actions/claim/IsPlayerActionAllowed.kt:117 — SLEEP_IN_BED mapped to both DETONATE and SLEEP

**What:** Same issue as above: `PlayerActionType.SLEEP_IN_BED` appears at lines 110 and 117, mapped to `DETONATE` and `SLEEP`. The effective mapping is `SLEEP_IN_BED -> SLEEP` (last wins).

**Why it matters:** Sleeping in a bed will be checked against `SLEEP` permission instead of `DETONATE`. This may be the intended behavior (sleep is more semantically correct), but the duplicate entry is confusing and suggests uncertainty about the design.

**Suggested fix:** Remove the duplicate mapping to `DETONATE` on line 110, or restructure to support multiple permissions per action.

**Confidence:** high

---

### [SEV: med] application/actions/claim/IsPlayerActionAllowed.kt:118 — SET_RESPAWN_POINT mapped to both DETONATE and SLEEP

**What:** Same pattern: `PlayerActionType.SET_RESPAWN_POINT` at lines 111 and 118, mapped to `DETONATE` and `SLEEP`. Effective: `SET_RESPAWN_POINT -> SLEEP`.

**Why it matters:** Same as above — the `DETONATE` mapping is silently dropped.

**Suggested fix:** Remove the duplicate or restructure.

**Confidence:** high

---

### [SEV: med] application/actions/claim/IsPlayerActionAllowed.kt:31 — claimOverride check bypasses all permission checks

**What:** Line 31: `if (playerId == claim.playerId || (playerState != null && playerState.claimOverride))` — when `claimOverride` is true, the player is allowed regardless of any permission settings. This is likely intentional for admin/staff override, but there is no logging or audit trail.

**Why it matters:** If `claimOverride` is toggled on accidentally or by an unauthorized player (depending on who can call `ToggleClaimOverride`), they gain full access to all claims silently. This is more of a design concern than a bug, but worth flagging.

**Suggested fix:** Add logging when `claimOverride` grants access, and ensure `ToggleClaimOverride` has proper authorization checks.

**Confidence:** med

---

### [SEV: med] application/actions/claim/partition/CanRemovePartition.kt:28,35,52,102 — partitionRepository.getByPosition called with Position2D(claim.position)

**What:** Multiple locations (lines 28, 35, 52, 102) call `partitionRepository.getByPosition(Position2D(claim.position))`. The `getByPosition` method expects a `Position` (which has x, y, z), but `Position2D(claim.position)` creates a 2D position from the 3D claim position. This works because `Position2D` extends `Position` with `y = null`, but the `y` being null could cause NPEs in downstream code that expects a non-null y for position-based lookups.

**Why it matters:** If `getByPosition` or any code it calls accesses `position.y`, it will get `null` from a `Position2D`, potentially causing a NullPointerException. This is a latent bug that depends on the repository implementation.

**Suggested fix:** Pass `claim.position` directly (it's already a `Position3D` which extends `Position`), or ensure `getByPosition` handles null y gracefully.

**Confidence:** med

---

### [SEV: low] application/actions/claim/ConvertClaimToGuild.kt:56-62 — Broad catch blocks swallow all exceptions as StorageError

**What:** Lines 56-62 catch `DatabaseOperationException` and then a generic `Exception`, both returning `StorageError`. The broad `Exception` catch will swallow programming errors (NullPointerException, IllegalArgumentException, etc.) and present them as storage failures.

**Why it matters:** Makes debugging difficult. A coding bug in the action (e.g., null dereference) would be silently reported as a storage error, misleading operators.

**Suggested fix:** Catch only the expected exception types. If a broad catch is needed for safety, log the full stack trace at error level.

**Confidence:** med

---

### [SEV: low] application/actions/claim/CreateClaim.kt:64-67 — Claim and partition added without transaction/rollback

**What:** Line 66 calls `claimRepository.add(newClaim)` and line 67 calls `partitionRepository.add(partition)`. If the partition add fails (returns false or throws), the claim is already persisted without its partition, leaving an orphaned claim in the database.

**Why it matters:** An orphaned claim with no partitions would be invisible in the world (no area to protect) but would still count against the player's claim limit and name uniqueness.

**Suggested fix:** Wrap both adds in a transaction, or check the return value of `partitionRepository.add` and roll back the claim creation on failure.

**Confidence:** med

---

### [SEV: low] application/actions/claim/anchor/BreakClaimAnchor.kt:24-28 — Race condition on breakCount mutation

**What:** Lines 24-28: `claim.resetBreakCount(plugin)` is called, then `claim.breakCount` is read and mutated. Since `resetBreakCount` uses the Bukkit scheduler to reset `breakCount` after 200 ticks, and the current code mutates `breakCount` on the same object instance, there is a race between the scheduled reset and the decrement.

**Why it matters:** If two players break the anchor in quick succession, the `breakCount` could be decremented by both before either reset fires, or the reset could overwrite a decrement. This is a classic race condition on shared mutable state.

**Suggested fix:** Use atomic operations or synchronize access to `breakCount`. Consider making the break-count logic server-side only, not domain-side.

**Confidence:** med

---

### [SEV: low] application/actions/claim/partition/ResizePartition.kt:34 — getPrimaryPartition called twice with same args

**What:** `getPrimaryPartition(claim)` is called at line 34 and again at line 72 (via `isResizeResultInAnyDisconnected` which calls it internally). Both operate on the same claim and partitions, producing the same result.

**Why it matters:** Minor performance waste — `getPrimaryPartition` queries the repository each time. Not a bug, but could be cached.

**Suggested fix:** Compute `getPrimaryPartition` once and pass it as a parameter.

**Confidence:** low

---

### [SEV: low] application/actions/claim/partition/ResizePartition.kt:197 — setNewCorner z-defaults to 0 for non-matching corners

**What:** In `setNewCorner` (lines 197-223), when a coordinate does not match the selected corner, the code defaults to `Position2D(x, 0)` or `Position2D(0, z)` with a hardcoded `0` for the non-matching axis. This works because the second assignment (z for x-mismatch, x for z-mismatch) overwrites the 0, but it's fragile and confusing.

**Why it matters:** If the logic is ever refactored to return early or if the two assignment blocks are reordered, the 0 default would produce incorrect areas. The intent is to preserve the opposite corner's coordinate.

**Suggested fix:** Use the actual opposite corner's coordinate as the default instead of 0, e.g., `Position2D(area.upperPosition2D.x, 0)` → `Position2D(area.upperPosition2D.x, area.upperPosition2D.z)` and let the subsequent assignment override the relevant axis.

**Confidence:** low

---

### [SEV: low] application/actions/player/tool/GetClaimIdFromMoveTool.kt:11 — UUID.fromString can throw uncaught IllegalArgumentException

**What:** Line 11: `UUID.fromString(claimIdString)` can throw `IllegalArgumentException` if the string is not a valid UUID. This exception is not caught.

**Why it matters:** If the item data contains a malformed UUID string, the action will throw an unhandled exception that propagates up to the caller, potentially crashing the calling command or listener.

**Suggested fix:** Wrap in a try-catch and return `NotMoveTool` on `IllegalArgumentException`.

**Confidence:** med

---

### [SEV: low] application/actions/player/visualisation/DisplayVisualisation.kt:28-30 — java.util.logging.Logger in application layer

**What:** Line 29 uses `java.util.logging.Logger`, which is a JDK logging framework. While not a Bukkit dependency, it's still a framework-specific logging choice in the application layer.

**Why it matters:** Minor layer purity concern. The application layer should ideally depend on an abstraction for logging.

**Suggested fix:** Inject a logging port/interface. Low priority since JUL is part of the JDK and not a third-party framework.

**Confidence:** low

---

### [SEV: low] application/actions/player/visualisation/DisplayVisualisation.kt:96-100 — Silent catch of all exceptions during player state update

**What:** Lines 96-100 catch all exceptions when updating player state and only log them. The visualization has already been displayed to the player (lines 78-82), but the player state (isVisualisingClaims, lastVisualisationTime) is not updated if this fails.

**Why it matters:** The player sees the visualization but the server doesn't track it, so rate-limiting won't work and the visualization won't be cleared on schedule. This is a minor inconsistency.

**Suggested fix:** Consider whether the visualization should be rolled back if state update fails, or at minimum, ensure the state is eventually consistent.

**Confidence:** med

---

### [SEV: low] application/actions/claim/IsNewClaimLocationValid.kt:53 — getSurroundingPositions uses -1 * radius instead of unary minus

**What:** Line 56: `for (i in -1 * radius..1 * radius)` — this works correctly but is unconventional. The idiomatic Kotlin form is `-radius..radius`.

**Why it matters:** Style/readability only. No functional impact.

**Suggested fix:** Use `-radius..radius`.

**Confidence:** low

---

### [SEV: low] application/actions/claim/IsNewClaimLocationValid.kt:29 — getSurroundingPositions called with radius=1 for chunk padding

**What:** Line 29: `val chunks = area.getChunks().flatMap { getSurroundingPositions(it, 1) }` — this fetches partitions in a 3x3 grid around each chunk of the area. Combined with the boundary padding on lines 34-38, this creates a generous overlap check. However, the variable name `chunks` is misleading since after `flatMap` it contains surrounding positions, not just the area's chunks.

**Why it matters:** Naming confusion only. No functional bug.

**Suggested fix:** Rename to `paddedChunks` or `chunksWithPadding`.

**Confidence:** low

---

### [SEV: low] application/actions/claim/permission/GrantGuildMembersClaimPermissions.kt:62 — alreadyHadAccessCount may be inaccurate

**What:** Line 62: `alreadyHadAccessCount` is incremented when `memberGranted` is false, meaning none of the permissions were newly added. But `memberGranted` is only false if `playerAccessRepository.add()` returned false for ALL permissions — if a player had some permissions but not all, `memberGranted` would be true (because at least one add succeeded) and `alreadyHadAccessCount` would not increment.

**Why it matters:** The `alreadyHadAccessCount` metric is misleading — it only counts players who had ALL permissions already, not players who had SOME permissions. The success result's counts may not sum to the total number of guild members processed.

**Suggested fix:** Rename to `fullyGrantedCount` or change the logic to count members who had at least one permission already.

**Confidence:** med

---

### [SEV: low] application/actions/claim/permission/RevokeAllClaimWidePermissions.kt:7 — Unused import

**What:** Line 7 imports `RevokeAllPlayerClaimPermissionsResult` but it is never used in this file.

**Why it matters:** Dead import, minor code quality issue.

**Suggested fix:** Remove the unused import.

**Confidence:** high

---

### [SEV: low] application/actions/claim/permission/EnableClaimFlag.kt:37 — Logs error.cause instead of error.message

**What:** Line 37: `println("Error has occurred trying to save to the database: ${error.cause}")` — this logs the cause of the exception, not the message. If `error.cause` is null, it prints "null". Other similar catch blocks in the codebase use `error.message`.

**Why it matters:** Inconsistent error logging. The cause may be null or less informative than the message itself.

**Suggested fix:** Change to `error.message` for consistency with other actions.

**Confidence:** med

---

### [SEV: low] application/actions/claim/permission/EnableClaimFlag.kt:24 — KDoc references wrong result type

**What:** Line 24: `@return An [EnableAllClaimFlagsResult]` — should be `EnableClaimFlagResult`. Copy-paste error from `EnableAllClaimFlags`.

**Why it matters:** Documentation only. No functional impact.

**Suggested fix:** Fix the KDoc to reference the correct result type.

**Confidence:** high

---

### [SEV: low] application/actions/claim/permission/RevokeAllClaimWidePermissions.kt:17 — KDoc references wrong result type

**What:** Line 17: `@return An [RevokeAllPlayerClaimPermissionsResult]` — should be `RevokeAllClaimWidePermissionsResult`.

**Why it matters:** Documentation only.

**Suggested fix:** Fix the KDoc.

**Confidence:** high

---

### [SEV: low] application/actions/claim/permission/GrantAllPlayerClaimPermissions.kt:16 — KDoc references "flag" instead of "permission"

**What:** Line 16: `@return An [GrantAllPlayerClaimPermissionsResult] indicating the outcome of the flag addition operation` — should say "permission" not "flag".

**Why it matters:** Documentation clarity.

**Suggested fix:** Change "flag" to "permission" in the KDoc.

**Confidence:** high

---

### [SEV: low] application/actions/claim/permission/RevokeAllPlayerClaimPermissions.kt:16 — KDoc references "flag" instead of "permission"

**What:** Line 16: `@return An [RevokeAllPlayerClaimPermissionsResult] indicating the outcome of the flag addition operation` — should say "permission removal" not "flag addition".

**Why it matters:** Documentation clarity.

**Suggested fix:** Update the KDoc.

**Confidence:** high

---

### [SEV: low] application/actions/claim/permission/GrantAllClaimWidePermissions.kt:16 — KDoc references "flag" instead of "permission"

**What:** Line 16: `@return An [GrantAllClaimWidePermissionsResult] indicating the outcome of the flag addition operation` — should say "permission" not "flag".

**Why it matters:** Documentation clarity.

**Suggested fix:** Update the KDoc.

**Confidence:** high

---

### [SEV: low] application/actions/claim/permission/DisableAllClaimFlags.kt:23 — KDoc references "flag" correctly but variable name is misleading

**What:** Line 30: `var anyFlagEnabled = false` — the variable is named `anyFlagEnabled` but it tracks whether any flag was **disabled** (removed). The return logic on line 38 checks `if (anyFlagEnabled)` to return `Success`, which is correct (a flag was found and removed), but the name suggests enabling.

**Why it matters:** Naming confusion. Could lead to future bugs if someone refactors based on the variable name.

**Suggested fix:** Rename to `anyFlagDisabled` or `anyFlagRemoved`.

**Confidence:** med

---

### [SEV: low] application/actions/claim/permission/EnableAllClaimFlags.kt:30 — Same naming issue

**What:** Line 30: `var anyFlagEnabled = false` — tracks whether any flag was **added**. This is actually correct for enable, but the pattern is inconsistent with `DisableAllClaimFlags`.

**Why it matters:** Consistency.

**Suggested fix:** Keep as-is for enable, but fix the disable counterpart.

**Confidence:** low

---

## Test Coverage Gaps

| # | Untested Behavior | File | Test That Should Exist |
|---|-------------------|------|------------------------|
| 1 | Name-uniqueness check uses correct player (acceptor, not old owner) | `AcceptTransferRequest.kt:39` | `AcceptTransferRequestTest` — verify name collision is checked against the accepting player's claims, not the old owner's |
| 2 | `GrantAllPlayerClaimPermissions` actually grants (not revokes) | `GrantAllPlayerClaimPermissions.kt:27` | `GrantAllPlayerClaimPermissionsTest` — verify permissions are added, not removed |
| 3 | `WithdrawPlayerTransferRequest` correctly identifies pending requests | `WithdrawPlayerTransferRequest.kt:10` | `WithdrawPlayerTransferRequestTest` — verify inverted logic: request found → removed; request not found → error |
| 4 | `DoesClaimHaveFlag` returns ClaimNotFound for missing claims | `DoesClaimHaveFlag.kt:12` | `DoesClaimHaveFlagTest` — verify missing claim returns ClaimNotFound (currently broken due to missing `return`) |
| 5 | `OfferPlayerTransferRequest` persists the transfer request | `OfferPlayerTransferRequest.kt:14` | `OfferPlayerTransferRequestTest` — verify claimRepository.update is called after adding transfer request |
| 6 | `AcceptTransferRequest` uses copy() instead of mutating playerId | `AcceptTransferRequest.kt:43` | `AcceptTransferRequestTest` — verify the original claim object is not mutated |
| 7 | `MoveClaimAnchor` allows moving to empty positions | `MoveClaimAnchor.kt:26` | `MoveClaimAnchorTest` — verify NoClaimFound at new position is treated as valid |
| 8 | `MoveClaimAnchor` updates worldId when moving across worlds | `MoveClaimAnchor.kt:49` | `MoveClaimAnchorTest` — verify worldId is updated in the copied claim |
| 9 | `ResizePartition` blocksRemaining calculation is correct | `ResizePartition.kt:61` | `ResizePartitionTest` — verify blocksRemaining accounts for old partition size |
| 10 | `ResizePartition` requiredExtraBlocks calculation is correct | `ResizePartition.kt:52` | `ResizePartitionTest` — verify InsufficientBlocks reports correct deficit |
| 11 | `CreateClaim` rolls back claim if partition creation fails | `CreateClaim.kt:66-67` | `CreateClaimTest` — verify no orphaned claim when partition add fails |
| 12 | `IsPlayerActionAllowed` TRIGGER_RAID permission resolution | `IsPlayerActionAllowed.kt:110,114` | `IsPlayerActionAllowedTest` — verify which permission (DETONATE or EVENT) is checked for TRIGGER_RAID |
| 13 | `CreatePartition` handles first partition of a new claim | `CreatePartition.kt:47-53` | `CreatePartitionTest` — verify behavior when claim has no existing adjacent partitions |
| 14 | `BreakClaimAnchor` persists break count changes | `BreakClaimAnchor.kt:24-28` | `BreakClaimAnchorTest` — verify break count decrement is persisted |
| 15 | `GetClaimIdFromMoveTool` handles malformed UUID gracefully | `GetClaimIdFromMoveTool.kt:11` | `GetClaimIdFromMoveToolTest` — verify invalid UUID string returns NotMoveTool, not an exception |

---

## Layer Boundary Check

**Verdict: VIOLATED** — `BreakClaimAnchor.kt` imports `org.bukkit.plugin.java.JavaPlugin` (Bukkit framework type) directly in the application layer. Additionally, `Claim.resetBreakCount()` in the domain layer uses `org.bukkit.Bukkit.getScheduler()`, which is a domain-layer violation (domain must not import frameworks). All other 65 files correctly depend only on application/domain ports and domain entities — no other Bukkit/JDBC/framework imports detected.

**Summary:** 2 files with layer violations:
- `BreakClaimAnchor.kt:10` — direct `org.bukkit` import in application layer
- `Claim.kt:57-64` — `org.bukkit.Bukkit` usage in domain entity (domain layer violation, outside the 66-scope but directly caused by the `JavaPlugin` parameter injected into `BreakClaimAnchor`)
