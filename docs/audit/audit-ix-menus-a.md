# Audit Report: audit-ix-menus-a

**Batch:** interaction/menus (first 69 files)
**Branch:** fix/audit-ix-menus-a
**Scope:** `interaction/menus` — Menu.kt, MenuFactory.kt, MenuNavigator.kt, bedrock/*, common/ConfirmationMenu.kt, guild/AllianceRequestMenu.kt, guild/AlliesListMenu.kt, guild/AllyHomeAccessMenu.kt, guild/DescriptionEditorMenu.kt

---

## Findings

### [SEV: high] BedrockClaimManagementMenu.kt:61 — Button index mismatch for guild±claim convert vs. transfer

**What:** When `claim.teamId == null`, button 4 triggers `handleConvertToGuild()` and button 5 triggers `createClaimTransferMenu`. But when `claim.teamId != null`, button 4 falls through to `createClaimTransferMenu` and button 5 falls through to `goBack()`. The `when` block at lines 61-76 maps button indices 4 and 5 to *different* actions depending on `teamId`, yet the button labels at lines 48-53 always show "convert" at index 4 and "transfer" at index 5 regardless of `teamId`. When the claim IS guild-owned, pressing the "convert" button (index 4) actually triggers transfer, and pressing "transfer" (index 5) goes back — the user sees misleading labels.

**Why it matters:** A player attempting to convert a guild-owned claim will unexpectedly be sent to the transfer menu, and a player attempting to transfer will silently go back. This is a logic bug that breaks the feature for guild-owned claims.

**Suggested fix:** Reorder the `when` branches so button indices match the dynamically-added buttons. Either always add the convert button conditionally and adjust indices, or use a mapping from button-list index to action.

**Confidence:** high

---

### [SEV: high] BedrockGuildBankMenu.kt:189 — Slider max value of 100f ignores actual balance

**What:** The deposit and withdraw sliders at lines 46-67 use a hardcoded max of `100f` for the slider range, even when `playerBalance` or `guildBalance` is much larger. At line 189, `parseAmount` falls back to `slider.toInt()` when the custom input is blank — capping the usable slider amount at 100 regardless of actual balance.

**Why it matters:** Players with balances > 100 cannot use the slider to deposit/withdraw their full amount. They must type a custom amount, which defeats the purpose of the slider UI. For guilds with large treasuries this makes the withdraw slider useless.

**Suggested fix:** Set the slider max to `playerBalance.toFloat()` / `guildBalance.toFloat()` (capped at a reasonable upper limit). Update `parseAmount` to use the actual slider max.

**Confidence:** high

---

### [SEV: high] BedrockClaimTransferMenu.kt:62 — Transfer request not persisted — mutation on in-memory copy

**What:** Line 62 mutates `claim.transferRequests[targetPlayer.uniqueId]` directly on the in-memory `Claim` object, but never calls `claimRepository.update(claim)` to persist the change. The `transferRequests` map is modified in memory only.

**Why it matters:** The transfer request is lost on server restart or when the claim object is reloaded from the database. The recipient will never see the transfer offer. This is a data-loss bug.

**Suggested fix:** After mutating `claim.transferRequests`, call `claimRepository.update(claim)` to persist. Or better, add a `addTransferRequest` method to the claim repository.

**Confidence:** high

---

### [SEV: med] BedrockClaimCreationMenu.kt:37 — Form element index 2 used for first input (off-by-one)

**What:** The form is built with a title (index 0), a label (index 1), then two inputs. At line 37, `response.asInput(2)` reads the first input, and line 38 reads `asInput(3)` for the second. Cumulus form indices are 0-based and include all elements (title is not counted, but labels and inputs are). The first input is at index 2 (after title + label), second at index 3. This appears correct for Cumulus where `asInput(n)` uses the nth input slot index. However, if Cumulus `asInput` is 0-based among inputs only, these indices should be 0 and 1.

**Why it matters:** If the indices are wrong, the menu reads null/empty values, causing validation to always fail or read the wrong field. The same pattern appears in many other files (BedrockClaimNamingMenu, BedrockClaimPlayerMenu, etc.) — if wrong, it's a systemic bug across all Bedrock menus.

**Suggested fix:** Verify Cumulus `asInput`/`asDropdown`/`asToggle` indexing semantics. If 0-based among all form elements, the current code may be correct. If 0-based among component type, adjust all indices.

**Confidence:** med

---

### [SEV: med] BedrockGuildModeMenu.kt:115 — handleModeSwitch button index logic is broken for PEACEFUL mode

**What:** At line 116-118, `targetMode` is computed as the *opposite* of current mode. At line 122-128, when `guild.mode == PEACEFUL`, `actualButtonIndex = buttonIndex` (maps to hostile button at index 0). But when `guild.mode == HOSTILE`, `actualButtonIndex = buttonIndex` maps to peaceful button at index 0. The `when` block at 130-143 then checks `actualButtonIndex == 0` for the target mode, but the logic at line 139 checks `targetMode == HOSTILE && guild.mode == PEACEFUL` which can never be true when `actualButtonIndex == 0` in the HOSTILE branch (because `targetMode` would be PEACEFUL).

**Why it matters:** When a guild is in HOSTILE mode and the user clicks button 0 (peaceful), the `when` block enters branch 0 at line 131, calls `switchToPeaceful` — which is correct. But the nested `if` at line 139 (`targetMode == HOSTILE && guild.mode == PEACEFUL`) is dead code that never fires. The logic works by accident for the first button but the second button (index 1) is never handled — if both buttons are present, clicking button 1 does nothing.

**Suggested fix:** Simplify the mode switch handler. When mode is PEACEFUL, button 0 = switch to HOSTILE. When mode is HOSTILE, button 0 = switch to PEACEFUL. Remove the dead code branch.

**Confidence:** med

---

### [SEV: med] BedrockGuildSettingsMenu.kt:248 — Blank description check uses isNotBlank() but empty string is valid

**What:** Line 248 checks `newDescription.isNotBlank()` before saving. If the user clears the description (sets it to `""`), the condition is false and the description is not updated. But line 228 checks `newDescription != (guild.description ?: "")` — if the current description is `null` and the user sets it to `""`, the equality check passes and no change is detected. If the current description is `"some text"` and the user clears it to `""`, `isNotBlank()` is false and the clear is silently ignored.

**Why it matters:** Users cannot clear their guild description through the Bedrock settings menu. The description will remain set even after the user explicitly empties the input field.

**Suggested fix:** Change `isNotBlank()` to `isNotEmpty()` or remove the check entirely, allowing empty string to clear the description.

**Confidence:** high

---

### [SEV: med] BedrockGuildLeaveConfirmationMenu.kt:57 — createFallbackJavaMenu uses wrong constructor signature

**What:** Line 57-60 attempts to instantiate `GuildLeaveConfirmationMenu` with `(MenuNavigator, Player)` but the Java `GuildLeaveConfirmationMenu` constructor requires `(MenuNavigator, Player, Guild)`. This will throw a `NoSuchMethodException` at runtime, caught silently by the catch block at line 63.

**Why it matters:** Bedrock players who trigger the fallback (e.g., Floodgate unavailable) will never see a leave confirmation menu — the fallback silently fails and returns null.

**Suggested fix:** Add `guild` as the third constructor argument.

**Confidence:** high

---

### [SEV: med] BedrockGuildControlPanelMenu.kt:179 — createFallbackJavaMenu uses wrong constructor signature

**What:** Line 179-182 attempts to instantiate `GuildControlPanelMenu` with `(MenuNavigator, Player, Guild)` — but the actual Java `GuildControlPanelMenu` constructor requires additional service parameters (guildService, rankService, memberService, etc.). This will throw `NoSuchMethodException`.

**Why it matters:** Same as above — fallback silently fails.

**Suggested fix:** Either inject the required services into the Bedrock menu or use `menuFactory.createGuildControlPanelMenu()` instead of reflection.

**Confidence:** high

---

### [SEV: med] BedrockGuildMemberRankConfirmationMenu.kt:17 — Uses GUILD_MEMBER_RANK_CHANGE permission check via memberService.hasPermission but should use RankPermission.MANAGE_RANKS

**What:** The `changeMemberRank` call at line 83 passes `player.uniqueId` as the actor, but there is no permission check before the call. The BedrockGuildPromotionMenu (line 125) correctly checks `RankPermission.MANAGE_RANKS`, but BedrockGuildMemberRankConfirmationMenu does not check any permission before calling `changeMemberRank`.

**Why it matters:** Any player who can open the rank confirmation menu can change ranks without the MANAGE_RANKS permission. The permission check should happen before the confirmation dialog is shown, not after.

**Suggested fix:** Add a `guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)` check before showing the confirmation form.

**Confidence:** med

---

### [SEV: med] BedrockGuildInviteConfirmationMenu.kt:59 — Opens GuildMemberManagementMenu (Java) instead of BedrockGuildMemberManagementMenu

**What:** Line 72 opens `GuildMemberManagementMenu(menuNavigator, player, guild)` — the Java inventory GUI class — after sending a Bedrock invite. A Bedrock player will not be able to interact with a ChestGui-based menu.

**Why it matters:** After sending an invite, Bedrock players are shown a Java inventory GUI they cannot use, breaking the navigation flow.

**Suggested fix:** Open `BedrockGuildMemberManagementMenu` instead.

**Confidence:** high

---

### [SEV: med] BedrockGuildKickConfirmationMenu.kt:72 — Opens GuildMemberManagementMenu (Java) instead of BedrockGuildMemberManagementMenu

**What:** Same issue as above — line 72 opens the Java `GuildMemberManagementMenu` after a Bedrock kick confirmation.

**Why it matters:** Bedrock players see a Java GUI they cannot interact with.

**Suggested fix:** Open `BedrockGuildMemberManagementMenu` instead.

**Confidence:** high

---

### [SEV: low] BedrockFormUtils.kt:63 — println instead of logger for form image errors

**What:** Lines 63 and 76 use `println()` for error logging instead of a proper logger instance. This bypasses the plugin's logging configuration and appears on stdout rather than the server log.

**Why it matters:** Errors in production are invisible to server administrators who monitor log files rather than stdout.

**Suggested fix:** Accept a `Logger` parameter or use the plugin logger.

**Confidence:** high

---

### [SEV: low] BedrockGuildBankMenu.kt:220 — Redundant bedrockMenusEnabled check in showTransactionConfirmation

**What:** Line 220 checks `config.bedrockMenusEnabled` to decide between ModalForm and message confirmation. But this menu is already only shown to Bedrock players (the `shouldUseBedrockMenus` check in MenuFactory gates this). The config check is redundant and if `bedrockMenusEnabled` is toggled off at runtime, the fallback sends a chat message and immediately executes transactions without confirmation (line 251).

**Why it matters:** Transactions could be executed without confirmation if the config is toggled mid-session.

**Suggested fix:** Always use the ModalForm confirmation path in this menu, since it's already gated by the Bedrock platform check.

**Confidence:** med

---

### [SEV: low] BedrockGuildMemberListMenu.kt:101 — createMemberListContent counts online members but button 0 opens member list (not a specific member)

**What:** Button 0 at line 101 is labeled "Member list" and calls `handleMemberSelection(members)`, which shows `BedrockGuildMemberManagementMenu`. But the button text at line 101 says "Member list" — it's confusing because the content already shows the member list. The button doesn't actually show individual member details.

**Why it matters:** UX confusion — the button labeled as the member list opens a different menu.

**Suggested fix:** Either make button 0 open a detailed member browser, or relabel it to "Manage Members".

**Confidence:** low

---

### [SEV: low] BedrockGuildSelectionMenu.kt:199 — Direct FloodgateApi.sendForm call bypasses BaseBedrockMenu.open()

**What:** Line 199-200 calls `FloodgateApi.getInstance().sendForm()` directly instead of using `menuNavigator.openMenu()`. This bypasses the navigation stack, timeout handling, and error handling in `BaseBedrockMenu.open()`.

**Why it matters:** The summary form won't have timeout tracking, and if the form fails to send, there's no error handling. It also won't appear in the navigation stack for back-button handling.

**Suggested fix:** Wrap the summary form in a proper `BaseBedrockMenu` subclass and use `menuNavigator.openMenu()`.

**Confidence:** med

---

### [SEV: low] BedrockGuildRelationsMenu.kt:127 — Multiple anonymous BaseBedrockMenu subclasses for sub-menus

**What:** Throughout the file (lines 173, 232, 292, 365, 454, 518, 606, 663, 722, 832), anonymous `BaseBedrockMenu` instances are created to wrap forms with handlers. These anonymous classes don't implement `getFormCached()`, `shouldCacheForm()`, or `createCacheKey()`, so caching is never enabled for sub-menus.

**Why it matters:** Minor — sub-menus don't benefit from form caching. Not a bug but a missed optimization.

**Suggested fix:** Extract named classes or use a shared wrapper that delegates caching.

**Confidence:** low

---

### [SEV: low] FormStateManager.kt:33 — ScheduledExecutorService cleanup task has no error handling

**What:** The `cleanupExpiredStates` method at line 86 iterates `stateStorage` entries and removes expired ones. If `remove()` throws (e.g., concurrent modification), the scheduled task will stop running silently.

**Why it matters:** After a concurrent modification exception, expired states are never cleaned up, causing memory leaks.

**Suggested fix:** Wrap the cleanup loop in a try-catch.

**Confidence:** med

---

### [SEV: low] BedrockGuildWarDeclarationMenu.kt:238 — Unsafe cast to WarServiceBukkit

**What:** Line 238 casts `warService` to `WarServiceBukkit` using `as?`. If the service is not a `WarServiceBukkit` instance (e.g., in tests or with a different implementation), `declaration` is null and the code falls through to the `else` branch which calls `declareWar` without wager/terms support.

**Why it matters:** The war declaration feature silently degrades if the service implementation changes.

**Suggested fix:** Add the `createWarDeclaration` method to the `WarService` interface or handle the null case explicitly.

**Confidence:** med

---

### [SEV: low] BedrockGuildModeMenu.kt:90 — addPeacefulButton/addHostileButton are extension functions that always add a button

**What:** The `addPeacefulButton` method at line 90 always calls `button(buttonText)` regardless of `canSwitch`. When the player cannot switch, the button is still added but is greyed out. This means the form always has both buttons (one greyed, one active), which is correct UX. However, the `handleModeSwitch` method at line 115 doesn't account for the case where only one button is present — it always assumes both buttons exist.

**Why it matters:** If the form builder is refactored to conditionally add buttons, the handler indices will be wrong.

**Suggested fix:** Document the invariant or make the handler dynamically determine which button was clicked.

**Confidence:** low

---

### [SEV: low] BedrockTagEditorMenu.kt:245 — giveQRCodeMap uses com.google.zxing (QR code library) in a menu handler

**What:** The `giveQRCodeMap` method generates QR codes using zxing and creates map items with custom renderers. This is a significant side effect (item generation, map creation) triggered from a menu handler. If the library is missing or the map creation fails, the exception is caught and re-thrown at line 304, which will propagate up and potentially crash the form handler.

**Why it matters:** A missing dependency or Bukkit map rendering issue could cause the entire tag editor form to fail.

**Suggested fix:** Add a feature flag for the QR code functionality and handle errors gracefully without re-throwing.

**Confidence:** med

---

### [SEV: low] MenuFactory.kt:310 — Koin GlobalContext.get() direct service resolution in createPartyCreationMenu

**What:** Line 310-314 uses `org.koin.core.context.GlobalContext.get().get<T>()` to resolve services directly from the Koin container, bypassing the factory's constructor-injected dependencies. This is inconsistent with the rest of the factory which uses constructor injection.

**Why it matters:** If the Koin context is not initialized or the services are not registered, this will throw at runtime. It also makes testing harder.

**Suggested fix:** Inject `GuildService`, `PartyService`, `RankService`, `MemberService`, `ChatInputListener` into the `MenuFactory` constructor.

**Confidence:** high

---

### [SEV: low] MenuFactory.kt:345 — Same Koin GlobalContext.get() pattern in createGuildControlPanelMenu

**What:** Lines 345-351 use the same direct Koin resolution pattern for `GuildService`, `RankService`, `MemberService`, `GuildVaultService`, `ProgressionService`, `ProgressionRepository`.

**Why it matters:** Same as above.

**Suggested fix:** Inject these services into the constructor.

**Confidence:** high

---

### [SEV: low] BedrockGuildMemberRankConfirmationMenu.kt:83 — changeMemberRank called without permission check in menu

**What:** The `changeMemberRank` call at line 83 is the actual state-changing operation. While the Java `GuildMemberRankConfirmationMenu` may check permissions before opening, the Bedrock version does not verify `MANAGE_RANKS` before showing the confirmation form. A player without the permission can open the confirmation and click "Change Rank", which will call `changeMemberRank` — the permission check happens inside the service, but the user experience is confusing (showing a confirmation for an action that will fail).

**Why it matters:** Poor UX and potential for confusion. The service will reject the change, but the user sees a success/failure message only after confirming.

**Suggested fix:** Check `MANAGE_RANKS` permission before building the form, similar to `BedrockGuildPromotionMenu`.

**Confidence:** med

---

## Test Coverage Gaps

| # | Untested Behavior | File | Test That Should Exist |
|---|-------------------|------|------------------------|
| 1 | Bedrock menu form response handling (all menus) | All Bedrock menu files | Unit tests verifying form button indices map to correct actions |
| 2 | Claim transfer request persistence | BedrockClaimTransferMenu | Test that transfer request is persisted to repository |
| 3 | Guild mode switch cooldown enforcement | BedrockGuildModeMenu | Test that cooldown prevents mode switch and shows correct message |
| 4 | Guild settings description clearing | BedrockGuildSettingsMenu | Test that empty description string clears the guild description |
| 5 | Bank transaction slider max values | BedrockGuildBankMenu | Test that slider max reflects actual player/guild balance |
| 6 | Claim management button index mapping | BedrockClaimManagementMenu | Test that convert/transfer buttons work for both guild-owned and non-guild-owned claims |
| 7 | Fallback Java menu constructor signatures | BedrockGuildLeaveConfirmationMenu, BedrockGuildControlPanelMenu | Test that fallback menu instantiation succeeds |
| 8 | FormStateManager expiration and cleanup | FormStateManager | Test that expired states are cleaned up and concurrent access is safe |
| 9 | BedrockGuildSelectionMenu pagination | BedrockGuildSelectionMenu | Test that pagination correctly calculates page boundaries |
| 10 | War declaration wager escrow | BedrockGuildWarDeclarationMenu | Test that wager is escrowed and refunded on failure |
| 11 | MenuFactory Koin direct resolution | MenuFactory | Test that all factory methods resolve services correctly |
| 12 | BedrockDescriptionEditorMenu profanity filter | BedrockDescriptionEditorMenu | Test that inappropriate words are rejected |
| 13 | BedrockGuildPromotionMenu permission check | BedrockGuildPromotionMenu | Test that players without MANAGE_RANKS cannot promote |
| 14 | BedrockGuildMemberRankMenu rank dropdown indices | BedrockGuildMemberRankMenu | Test that dropdown selection maps to correct rank |
| 15 | BedrockEmojiSelectionMenu emoji format validation | BedrockEmojiSelectionMenu | Test valid and invalid emoji format inputs |

---

## Layer Boundary Check

**Verdict: PASS with minor notes.**

All 69 files are in `interaction/menus` (the interaction layer), which is the correct location for UI code. The files correctly:

- Import from `application.services.*` for business logic (not from `domain` directly for operations)
- Import from `domain.entities.*` for data types
- Use `org.bukkit.entity.Player` (acceptable in interaction layer)
- Use `org.koin.core.component.inject` for dependency injection

**Minor notes (not violations):**
- `MenuFactory.kt` uses `org.koin.core.context.GlobalContext.get()` direct resolution instead of constructor injection — this is a code quality issue, not a layer violation.
- `BedrockGuildPartyManagementMenu.kt` imports `application.persistence.GuildRepository` directly — this is a layer boundary concern (interaction → persistence), but it's used only for reading guild data. Consider using a service instead.
- `BedrockGuildProgressionInfoMenu.kt` imports `application.persistence.ProgressionRepository` directly — same concern as above.
- `BedrockGuildWarDeclarationMenu.kt` imports `application.persistence.GuildRepository` directly — same concern.
- `BedrockClaimIconMenu.kt` and `BedrockClaimNamingMenu.kt` import `application.persistence.ClaimRepository` directly — same concern.

These are **low-severity hardening nits** — the repositories are application-layer interfaces (ports), not infrastructure implementations, so they don't technically violate hexagonal architecture. But using application services would be cleaner.

---

## Summary

| Severity | Count |
|----------|-------|
| crit | 0 |
| high | 3 |
| med | 8 |
| low | 10 |
| **Total** | **21** |

**Key themes:**
1. **Button index management** in dynamic Bedrock forms is error-prone (3 findings)
2. **Persistence gaps** — in-memory mutations not saved to repository (1 finding)
3. **Hardcoded limits** — slider max of 100 ignores actual balances (1 finding)
4. **Fallback menu constructor mismatches** — reflection-based fallbacks use wrong signatures (2 findings)
5. **Java/Bedrock menu mixing** — Bedrock menus opening Java GUIs (2 findings)
6. **Missing permission checks** — rank changes without MANAGE_RANKS verification (2 findings)
