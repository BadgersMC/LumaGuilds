# Audit Report: interaction/{commands,help,listeners}

**Scope:** 48 files under `src/main/kotlin/net/lumalyte/lg/interaction/`
**Branch:** `fix/audit-ix-commands`
**Methodology:** docs/audit-ix-commands-plan.md (findings-only, no source modified)

---

## Findings

### [SEV: high] PartitionsCommand.kt:43 — paginated loop uses `page` as iteration count offset instead of per-page window

**What:** The `for` loop iterates `0..9 + page`, which means for page 2 it loops `0..11`, page 3 loops `0..12`, etc. Each subsequent page shows *more* items than the first, and items from the previous page are not skipped. The `ClaimListCommand` also has the same pattern at line 45 (`val startIndex = page * 10` is computed but never used — the loop hard-codes `0..9 + page`).

**Why it matters:** Page 2 should show items 10–19, but instead shows items 0–11. Page overlap and increasingly wide windows on higher pages. If the claim has > 10 partitions, players cannot navigate to "page 2" correctly. Combined with the guard on line 33 (`page * 10 - 9 > partitions.count()`), the valid page range is also calculated incorrectly (off-by-one against the actual window size).

**Suggested fix:** Replace the loop with a proper `subList((page-1)*10, min(page*10, total))` window, matching `ClaimListCommand`'s pattern (which has the right idea on line 45 but doesn't use it).

**Confidence:** high

---

### [SEV: high] ToolRemovalListener.kt:129-138 — `onPlayerDeath` builds removal list but never removes items from drops

**What:** The handler iterates `event.drops`, identifies key items (claim tools, move tools) and adds them to `itemsToRemove`, but then never calls `event.drops.removeAll(itemsToRemove)`. The list is accumulated and discarded.

**Why it matters:** Players who die while holding a claim tool or move tool will drop it on the ground, where any other player can pick it up. This defeats the entire `ToolRemovalListener` design, which exists specifically to prevent these items from leaving the player's inventory through any channel. An attacker could kill a player to obtain their claim tool.

**Suggested fix:** Add `event.drops.removeAll(itemsToRemove)` after the loop.

**Confidence:** high

---

### [SEV: med] ClaimCommand.kt:54-69 — `isPlayerHasClaimPermission` checks claim ownership but not guild membership for guild-owned claims

**What:** The permission check only verifies that `player.uniqueId == claim.playerId` (the claim owner). For guild-owned claims (where `claim.teamId != null`), it does not check whether the player is a member of the owning guild. Any guild member should be able to manage guild-owned claims, but only the original claim creator can.

**Why it matters:** A guild creates a claim, later has member turnover, and the original owner leaves — the guild loses the ability to manage its own claims unless the owner is still present. This is likely a design gap rather than an intentional restriction, but it breaks the guild-claim workflow.

**Suggested fix:** When `claim.teamId != null`, check guild membership (and optionally the `MANAGE_CLAIMS` permission) instead of requiring `playerId == claim.playerId`.

**Confidence:** med

---

### [SEV: med] InfoCommand.kt:53 — `Bukkit.getOfflinePlayer(player.uniqueId)` uses wrong UUID for owner name lookup

**What:** Line 53 uses `player.uniqueId` (the *command sender's* UUID) to look up the offline player name, then displays it as the "Owner" of the claim. It should use `claim.playerId` (the claim owner's UUID) instead. If player A runs `/claim info` on player B's claim, the owner name shown is A's name, not B's.

**Why it matters:** The info display misattributes ownership. Players checking claim info will see their own name as owner even when inspecting another player's claim — confusing for trust decisions and moderation.

**Suggested fix:** Replace `Bukkit.getOfflinePlayer(player.uniqueId)` with `Bukkit.getOfflinePlayer(claim.playerId)`.

**Confidence:** high

---

### [SEV: med] WorldClaimProtectionListener.kt:191,224,397,411 — unreachable `else -> return` branches in piston/fluid handlers

**What:** In `onBlockPistonExtendEvent` (line 191), `onBlockPistonRetractEvent` (line 224), `onSpongeAbsorb` (line 396-397), and `onLightningStrike` (line 411), the `else -> return` branch inside the `when` block causes the function to return early when `isWorldActionAllowed` returns a non-Denied result. This means only the *first* affected block/location is checked; subsequent locations in the loop are never evaluated.

**Why it matters:** If a piston extends into multiple claims, only the first block's claim is checked. The remaining blocks could be in a claim that disallows pistons, but the event won't be cancelled. Same issue for sponge absorption affecting multiple blocks across claim boundaries — only the first block is validated.

**Suggested fix:** Remove `else -> return` (and the corresponding `else -> continue` in sponge handler) so the loop continues checking all affected locations.

**Confidence:** high

---

### [SEV: med] ChatInputListener.kt:77-119 — three separate fallback event handlers with same priority create duplicate processing

**What:** Three `@EventHandler` methods (`onPlayerChatFallbackHighest`, `onPlayerChatFallbackLowest`, `onPlayerChatFallbackMonitor`) all listen for the deprecated `AsyncPlayerChatEvent` and all call `handleChatInputFallback` if the player is in input mode. With HIGHEST priority the handler always fires; LOWEST fires concurrently; MONITOR fires as well. The HIGHEST handler already cancels the event, so the LOWEST/MONITOR handlers process an already-cancelled event redundantly.

**Why it matters:** `handleChatInputFallback` calls `event.isCancelled = true` and `stopInputMode(player)` on every invocation. With multiple handlers firing for the same event, `stopInputMode` is called multiple times (harmless but noisy). More concerning: if a future refactor changes one handler but not others, the MONITOR handler could process input even when HIGHEST was supposed to block it.

**Suggested fix:** Use a single `@EventHandler` for the fallback. If multiple priorities are needed for compat, gate on `!event.isCancelled`.

**Confidence:** med (could be intentional compat, but the triple-registration is suspicious)

---

### [SEV: med] LumaGuildsCommand.kt:463-469 — filename regex double-escapes hyphen and permits backslash

**What:** `isValidFileName` uses `Regex("^[a-zA-Z0-9_\\\\-.]{1,100}$")`. The `\\\\` in the string literal becomes `\\` in the regex, which matches a literal backslash character. Combined with the hyphen placement, the regex allows filenames containing `\` (backslashes), which on most JVM platforms does not enable path traversal (the file is still resolved under `temp_exports/` by `Path.resolve`), but it's imprecise and could confuse file listing.

**Why it matters:** A filename like `foo\bar.csv` would pass validation. While `Path.resolve` prevents actual directory traversal, the backslash in the filename could cause issues on Windows or with downstream consumers that split on `/`.

**Suggested fix:** Replace `\\\\-` with just `-` at the end of the character class: `Regex("^[a-zA-Z0-9_.-]{1,100}$")`.

**Confidence:** high

---

### [SEV: med] PartitionUpdateListener.kt:59 — `players.add(player)` called unconditionally after `isPlayerVisualising` check

**What:** Line 60 calls `players.add(player)` *after* the `when` block, outside the `Success` branch. Line 57 adds the player only on `IsPlayerVisualisingResult.Success`, but line 60 then unconditionally adds every nearby player regardless of whether they're visualising. This means players who are not visualising also get their visualisation refreshed (which is a no-op) but wastes DB calls.

**Why it matters:** Unnecessary `refreshVisualisation.execute` calls for every player in the chunk, including those not holding a tool. Under load with many nearby players this adds useless work.

**Suggested fix:** Move `players.add(player)` inside the `Success` branch of the `when` block.

**Confidence:** high

---

### [SEV: med] ClaimListCommand.kt:45 — startIndex computed but never used for subList bounds

**What:** `val startIndex = page * 10` is computed on line 45, but line 47 calls `playerClaims.subList(startIndex, endIndex)` — which is correct. However, the validation on line 36 uses `page * 10 - 9 > playerClaims.count()` which is the 1-based first-item-of-current-page check. This formula is off: for page 1 it checks `1 > count()` (fine), for page 2 it checks `11 > count()` (should be `10 >= count()`). The guard allows page 2 when `count() == 11` (showing items 10..11), but rejects page 2 when `count() == 10` (correct).

Actually, re-checking: for page=2, count=10: `20-9=11 > 10` → true → rejects. That's correct (no items on page 2). For page=2, count=11: `20-9=11 > 11` → false → allows, showing items 10..11 (2 items). Also correct. The formula is actually equivalent to `(page-1)*10 >= count()`. This is fine.

**On closer inspection, this is not a bug.** Reverting to "no finding" for this item.

**Confidence:** N/A (false positive)

---

### [SEV: low] BedrockCacheStatsCommand.kt:21 — permission check uses raw string `hasPermission` on `CommandSender`

**What:** `sender.hasPermission("lumalyte.bedrock.cache.stats")` is a Bukkit standard check, but unlike the rest of the codebase which uses ACF's `@CommandPermission` annotation, this command (being a raw `CommandExecutor`) relies entirely on manual checks. Missing the annotation means ACF's tab-completion and help won't auto-detect the permission.

**Why it matters:** Minor inconsistency. The permission string `lumalyte.bedrock.*` also differs from the `lumaguilds.*` convention used by ACF commands — could confuse administrators reading config files.

**Suggested fix:** Consider migrating to ACF annotations or at least documenting the permission node divergence.

**Confidence:** low (style nit)

---

### [SEV: low] LumaGuildsCommand.kt:118-121,252-256,283-286,426-432 — catch-all Exception handlers in command methods swallow stack traces

**What:** Four `catch (e: Exception)` blocks in `handleDownload`, `handleReload`, `handleProgressionReload`, and `handleMigrate` print `e.message` to the player but do not log the full stack trace to the server log. Only `handleMigrate` prints `e.printStackTrace()`.

**Why it matters:** Silent failure modes make debugging harder. A storage error or NPE in reload would show a generic message to the player with no server-side trace.

**Suggested fix:** Add `plugin.logger.severe(...)` or `e.printStackTrace()` in all catch blocks.

**Confidence:** med

---

### [SEV: low] ClaimAnchorListener.kt:152-184 — infinite loop risk in NameAlreadyExists retry

**What:** The retry logic at line 167-173 uses `while (true)` with an unconditional `break` at line 172. The loop body never actually checks if `uniqueName` is available in the database — it just appends a counter and breaks on the first iteration. If the retried `uniqueName` *also* already exists, `createClaim.execute` will return `NameAlreadyExists` again, and the handler sends a generic failure message without further retry.

**Why it matters:** The loop is not infinite (it always breaks), but it provides a false sense of safety. If two claims have the same default name and the first retry also collides, creation fails with an unhelpful message.

**Suggested fix:** Either query the database for the next available name or wrap in a bounded retry loop (e.g., 10 iterations) with a proper uniqueness check.

**Confidence:** med

---

### [SEV: low] ClaimAnchorListener.kt:152,194 — `println` debug statements left in production code

**What:** Lines 153, 179, 194, and 195 use `println("[DEBUG] ...")` and `e.printStackTrace()` for error reporting. These bypass the plugin logger and will appear on stdout with no level control.

**Why it matters:** Debug noise in production. Stack traces sent to stdout are not captured by log aggregators configured for SLF4J.

**Suggested fix:** Replace with `plugin.logger.info/severe(...)` calls.

**Confidence:** high

---

### [SEV: low] PartyChatListener.kt:101,108,126 — misleading comments reference "line 71" for event cancellation

**What:** Comments say "Event already cancelled at line 71" but the cancellation is at line 76 (`event.isCancelled = true`). Line 71 is `val playerId = player.uniqueId`.

**Why it matters:** Misleading comments confuse future maintainers who may look for the cancellation point.

**Suggested fix:** Update comments to reference the correct line numbers.

**Confidence:** high

---

### [SEV: low] PlayerClaimProtectionListener.kt:553,574 — `onPotionSplash` and `onAreaEffectCloudApply` use `return` inside `for` loop

**What:** In both handlers, `if (entity is Monster || entity is Player) return` will *return from the entire function* rather than `continue` to skip the current entity. If the first affected entity is a Monster, all subsequent entities (including non-monster entities in claims) are not filtered.

**Why it matters:** Potion splash protection is incomplete. A splash hitting a zombie first and then animals in a claim-area will not protect the animals.

**Suggested fix:** Replace `return` with `continue` in both handlers.

**Confidence:** high

---

### [SEV: low] ClaimDestructionListener.kt:97-105 — NamespacedKey uses hardcoded plugin ID instead of `PluginKeys` constant

**What:** `NamespacedKey("bellclaims","claim")` is used directly instead of importing and using the existing `PluginKeys` constants. The domain module already uses `"bellclaims"` in other places but the convention in this codebase is to centralize under `PluginKeys`.

**Why it matters:** If the plugin namespace ever changes, this hardcoded string would be missed.

**Suggested fix:** Add a constant to `PluginKeys` and reference it.

**Confidence:** low (style nit)

---

### [SEV: low] DescriptionCommand.kt:49 — uses `name.count().toString()` instead of `description.count().toString()`

**What:** In the `ExceededCharacterLimit` branch, the displayed count uses `name` (a parameter that doesn't exist in this scope — it's a compilation error). Looking more carefully: the variable is `name` at line 49, but the parameter is called `description` (line 24). This won't compile.

**Wait — re-checking:** Line 49: `arrayOf(name.count().toString(), firstError.maxCharacters)`. The parameter is `description: String` on line 24. There is no `name` variable in scope. This is a compilation error.

**Why it matters:** This code path (text exceeding the character limit) would fail to compile. If forced to compile via some IDE quirk, it would throw a `NoSuchFieldError` or `UnresolvedReferenceException`.

Actually wait — let me re-read. Line 49 is inside `DescriptionCommand.kt`. The parameter is `description`. The variable `name` is not defined. This is definitely a compilation error.

**Suggested fix:** Replace `name.count()` with `description.length`.

**Confidence:** high

---

### [SEV: low] RenameCommand.kt:54 — same `name.count()` vs `name.length` issue as DescriptionCommand

**What:** Line 54: `arrayOf(name.count().toString(), firstError.maxCharacters)`. The parameter is `name: String`. `String.count()` returns the number of characters (same as `.length`), so this actually works in Kotlin, just uses a non-idiomatic call. Unlike the DescriptionCommand case, this compiles fine.

**Why it matters:** Nit — `.length` is the Kotlin idiom for string length.

**Suggested fix:** Replace `name.count()` with `name.length`.

**Confidence:** low (style nit, compiles correctly in Kotlin)

---

### [SEV: low] HarvestReplantListener.kt:19 — handler named `onInventoryClose` but handles `PlayerInteractEvent`

**What:** The method is named `onInventoryClose` and takes `PlayerInteractEvent`. The method body handles right-click harvest logic, not inventory closing.

**Why it matters:** Extreme naming confusion. Any developer searching for inventory-close handlers will find this, and vice versa.

**Suggested fix:** Rename to `onPlayerHarvestCrop` or similar.

**Confidence:** high

---

### [SEV: low] EditToolListener.kt:148-155 — variable names swapped in `onToolSwitch`

**What:** Line 148: `val partitionResizer = firstSelectedCornerResize[event.player.uniqueId]` but the comment says "Cancel claim resizing". Line 153: `val partitionBuilder = firstSelectedCornerCreate[event.player.uniqueId]` but the comment says "Cancel claim building". The variables are fetched from the *opposite* maps: `partitionResizer` comes from `firstSelectedCornerResize` (the resize map, correct), but then the *next* block uses `partitionBuilder` from `firstSelectedCornerCreate` for the unequip message line 153. The message on line 153 says "UNEQUIP_RESIZE" which is correct for the resize map. The logic is actually correct but the variable naming in the second block is misleading — `partitionBuilder` from `firstSelectedCornerCreate` correctly handles the build-cancel case.

**Re-checking:** Lines 137-139: check `firstSelectedCornerResize` → if present, remove from `firstSelectedCornerResize` (correct). Lines 148-150: check `firstSelectedCornerCreate` → if present, remove from `firstSelectedCornerCreate` (correct). Variable names are swapped (resizer vs builder) relative to the maps they read, but the logic is symmetric and correct.

**No actual bug — reverting to no finding.**

---

### [SEV: low] GuildCommand.kt:107-145 — guild name validation duplicated between `onCreate` and `onRename`

**What:** The same 4-step validation (MiniMessage tags, blank, length, invalid chars) is copy-pasted. If validation rules change, both must be updated.

**Why it matters:** Maintenance burden and risk of divergence.

**Suggested fix:** Extract to a shared `validateGuildName` helper.

**Confidence:** med

---

## Test Coverage Gaps

| Untested Behavior | Suggested Test |
|---|---|
| `PartitionsCommand` pagination correctness (page 2+, boundary conditions) | Unit test with 15+ partitions, verify page 1 shows items 0-9, page 2 shows items 10-14 |
| `ClaimListCommand` pagination with edge pages | Unit test: exactly 10 claims (page 2 should reject), 11 claims (page 2 should show 1 item) |
| `ToolRemovalListener.onPlayerDeath` actually removes key items from drops | Integration test: player dies with claim tool in inventory, verify `event.drops` is empty of key items |
| `InfoCommand` owner name uses `claim.playerId` not sender | Unit test: player A inspects player B's claim → owner name is B |
| `isPlayerHasClaimPermission` with guild-owned claims | Integration test: non-owner guild member runs `/claim rename` on guild-owned claim → succeeds |
| `WorldClaimProtectionListener` multi-block piston checks | Unit test extending both ends different, both disallow → event cancelled |
| `DescriptionCommand` character limit error path | Unit test with description exceeding max → error message contains correct count |
| `ChatInputListener` cancels chat and routes to handler | Mock test: player in input mode types "hello" → chat cancelled, handler called |
| `ClaimAnchorListener` NameAlreadyExists retry | Unit test with pre-existing default name → retry succeeds or fails cleanly |
| `EditToolListener.onToolSwitch` symmetry (resize/build cancel) | Unit test: player holding tool switches slot → correct visualisation cleared |
| `ClaimOverrideCommand` toggle + persistence | Unit test: toggle on → hasOverride=true persists across action execution |

---

## Layer Boundary Check

**Verdict: PASS with one note.**

- `interaction/commands/**` imports only from `application.actions`, `application.results`, `application.utilities`, `domain.entities`, `domain.values`, and `infrastructure.adapters` (adapters are interaction-layer infrastructure, acceptable). No direct Bukkit imports in command handlers except `org.bukkit.entity.Player`, which is the standard ACF command context type and is acceptable at the interaction layer.
- `interaction/help/**` imports only `domain.values` and `interaction.help` — clean.
- `interaction/listeners/**` imports `infrastructure.adapters`, `infrastructure.services`, `application.*`, `domain.*`, and Bukkit API. All Bukkit usage is in event handler code (the interaction layer is allowed to touch Bukkit).
- One minor note: `GuildCommand.kt` imports `net.lumalyte.lg.utils.deserializeToItemStack` and `GuildHomeSafety` from `utils` — these are in the `net.lumalyte.lg.utils` package which is neither `application` nor `domain` but is a cross-cutting utility package. Not a layer violation, but worth monitoring if utils grow application-layer dependencies.
- `EditToolListener.kt` imports `kotlin.concurrent.thread` — this is a stdlib primitive, not a layer violation.
- `LumaGuildsCommand.kt` (raw `CommandExecutor`) imports `net.lumalyte.lg.LumaGuilds` (the plugin class). This is acceptable for lifecycle operations (reload, migrate) that need the plugin instance.

**No hardcore layer violations found.** All interaction-layer files respect the hexagonal boundary: no `domain` code imports from here, and `interaction` code only reaches into `application` (ports/results) and `infrastructure` for adapters/services.
