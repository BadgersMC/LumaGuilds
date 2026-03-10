# Paper 1.21.11 Migration — Implementation Plan

**Date:** 2026-03-10
**Design doc:** `docs/plans/2026-03-10-paper-1-21-11-migration-design.md`
**Branch:** `feature/paper-1-21-11` off `main`

---

## Audit Summary

A full static analysis of `main` was run against every deprecated API listed in the design doc.

| Deprecated Pattern | Occurrences | Status |
|---|---|---|
| `ItemStack(Material)` / `ItemStack(Material, Int)` constructor | ~500 across 70 files | **Needs fixing** |
| `InventoryView.getTitle()` / `setTitle()` / `getOriginalTitle()` | 0 | Clean |
| `AttributeModifier(UUID, ...)` constructor | 0 | Clean |
| `AttributeInstance.getModifier(UUID)` / `removeModifier(UUID)` | 0 | Clean |
| `Attribute.valueOf(String)` | 0 | Clean |
| `ItemStack.setType(Material)` | 0 | Clean |
| `ItemStack.getRarity()` | 0 | Clean |
| `ItemMeta` destroyable/placeable key methods | 0 | Clean |
| `Server.spigot()` | 0 | Clean |
| `UnsafeValues` serialize/deserialize | 0 | Clean — already uses `serializeAsBytes()` |
| Potion meta deprecated | 0 | Clean |

**Phase 2 is one mechanical change:** replace `ItemStack(Material` with `ItemStack.of(Material` across 70 files.

---

## Branch Setup

```bash
git checkout main
git pull
git checkout -b feature/paper-1-21-11
```

---

## Phase 1 — Dependency Bump

**Commit message:** `chore: bump Paper to 1.21.11 and update all dependent libraries`

### Task 1.1 — Update `build.gradle.kts`

**File:** `build.gradle.kts`

Changes:

```kotlin
// Version
version = "2.0.0"   // was "1.1.1"

// compileOnly
compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")   // was 1.21.8

// testImplementation — paper-api
testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")   // was 1.21.10

// testImplementation — MockBukkit
// Check https://central.sonatype.com/artifact/org.mockbukkit.mockbukkit/mockbukkit-v1.21/versions
// for latest version targeting 1.21.11, replace 4.98.0
testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:<LATEST>")

// IF
implementation("com.github.stefvanschie.inventoryframework:IF:0.11.6")   // was 0.11.3

// Geyser/Floodgate — check https://repo.opencollab.dev for latest snapshots
compileOnly("org.geysermc.geyser:api:<LATEST-SNAPSHOT>")
compileOnly("org.geysermc.floodgate:api:<LATEST-SNAPSHOT>")
compileOnly("org.geysermc.cumulus:cumulus:<LATEST-SNAPSHOT>")
```

> **Note on MockBukkit/Geyser versions:** Resolve the exact latest versions from Maven Central / OpenCollab repo before committing. The `<LATEST>` placeholders must be filled in.

### Task 1.2 — Update `plugin.yml`

**File:** `src/main/resources/plugin.yml`

```yaml
api-version: '1.21.4'   # was '1.21'
```

### Task 1.3 — Compile gate

```bash
./gradlew compileKotlin
```

Fix any compile errors introduced by:
- IF 0.11.6 API changes (check IF release notes for 0.11.4–0.11.6 breaking changes)
- Geyser/Floodgate API changes if any

Then run tests:

```bash
./gradlew test
```

All tests must pass before committing Phase 1.

---

## Phase 2 — Deprecation Cleanup

**Commit message:** `refactor: replace deprecated ItemStack constructors with ItemStack.of()`

### Task 2.1 — Global find-and-replace

The only deprecated API in use is the `ItemStack(Material)` / `ItemStack(Material, Int)` constructor.

**Replace pattern:** `ItemStack(Material.` → `ItemStack.of(Material.`

This covers both the 1-arg and 2-arg forms since `ItemStack.of(Material, Int)` has the same signature.

**Method:** Use IDE global find-and-replace (IntelliJ: `Ctrl+Shift+R`) or `sed`:

```bash
# Dry run first
grep -r "ItemStack(Material\." src/main/kotlin/ | wc -l

# Apply
find src/main/kotlin/ -name "*.kt" -exec sed -i 's/ItemStack(Material\./ItemStack.of(Material./g' {} +
```

### Task 2.2 — Verify no regressions

```bash
./gradlew compileKotlin
./gradlew test
```

### Task 2.3 — Spot-check 5 representative files

Manually verify the replacement is correct in:
- `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildControlPanelMenu.kt`
- `src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimFlagMenu.kt`
- `src/main/kotlin/net/lumalyte/lg/utils/ClaimPermissionUtils.kt`
- `src/main/kotlin/net/lumalyte/lg/utils/FlagUtils.kt`
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ToolItemServiceBukkit.kt`

### Affected files (70 total)

```
src/main/kotlin/net/lumalyte/lg/application/utilities/GoldBalanceButton.kt
src/main/kotlin/net/lumalyte/lg/infrastructure/services/MapRendererServiceBukkit.kt
src/main/kotlin/net/lumalyte/lg/infrastructure/services/ToolItemServiceBukkit.kt
src/main/kotlin/net/lumalyte/lg/interaction/listeners/BannerSelectionListener.kt
src/main/kotlin/net/lumalyte/lg/interaction/listeners/ClaimDestructionListener.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/common/ConfirmationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/AllianceRequestMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/AlliesListMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/DescriptionEditorMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/EnemiesListMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/EnemyDeclarationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldDepositMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldWithdrawMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBannerMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildControlPanelMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildEmojiMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildHomeMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInfoMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInviteConfirmationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInviteMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildKickConfirmationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildKickMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildMemberListMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildMemberManagementMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildMemberRankConfirmationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildMemberRankMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildModeMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildPartyManagementMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildRankManagementMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildRelationsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildSelectionMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildSettingsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildStatisticsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildWarAcceptanceMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildWarDeclarationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildWarManagementMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/IncomingRequestsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/JoinRequirementsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/LfgBrowserMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/OutgoingRequestsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/PartyCreationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/PartyModerationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/PeaceAgreementMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/PermissionCategoryMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/PlayerModerationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankCreationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankEditMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/TagEditorMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/TruceRequestMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/WarGuildSelectionMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/WarObjectivesSelectionMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimCreationMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimFlagMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimIconMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimManagementMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimNamingMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimPlayerMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimPlayerPermissionsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimPlayerSearchMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimRenamingMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimTransferNamingMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimTrustMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/management/ClaimWidePermissionsMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/misc/ClaimListMenu.kt
src/main/kotlin/net/lumalyte/lg/interaction/menus/misc/EditToolMenu.kt
src/main/kotlin/net/lumalyte/lg/utils/ClaimPermissionUtils.kt
src/main/kotlin/net/lumalyte/lg/utils/FlagUtils.kt
src/main/kotlin/net/lumalyte/lg/utils/PlayerHeads.kt
```

---

## Phase 3 — New API Adoption

**Commit message:** `feat: adopt Paper 1.21.11 DataComponent and ItemStack.of APIs`

### Task 3.1 — Verify `.name()` / `.lore()` extension functions

**File:** `src/main/kotlin/net/lumalyte/lg/utils/` (find the extension functions file)

Verify that the `.name(Component)` and `.lore(Component)` Kotlin extension functions used throughout the menu layer set display name and lore via `DataComponentTypes.ITEM_NAME` and `DataComponentTypes.LORE` rather than via the legacy `ItemMeta.setDisplayName()` / `ItemMeta.setLore()` path. If they use the legacy path, migrate them to DataComponent setters.

### Task 3.2 — Adopt `ItemStack.of()` as canonical factory everywhere

The Phase 2 sed pass replaces `ItemStack(Material.X` patterns. Additionally verify:
- Any `ItemStack(itemStack)` copy constructor usages → `itemStack.clone()` or `itemStack.copy()` as appropriate
- Any remaining `new ItemStack(...)` patterns not caught by the sed pattern

### Task 3.3 — Verify IF 0.11.6 MenuType integration

Open and spot-test the following GUI classes to confirm they compile and render correctly under IF 0.11.6:
- `GuildControlPanelMenu.kt` (largest, most complex)
- `ClaimManagementMenu.kt`
- `ConfirmationMenu.kt`

IF 0.11.6 uses `MenuType` internally — no API call-site changes are expected, but confirm no constructor signatures changed.

### Task 3.4 — Final clean build + full test run

```bash
./gradlew clean shadowJar
./gradlew test
```

Zero deprecation warnings, all tests green.

---

## Success Criteria

- [ ] `./gradlew shadowJar` produces zero deprecation warnings against Paper 1.21.11
- [ ] `./gradlew test` fully green with MockBukkit targeting 1.21.11
- [ ] `plugin.yml` declares `api-version: '1.21.4'`
- [ ] Plugin version is `2.0.0`
- [ ] All 70 files use `ItemStack.of()` — zero `ItemStack(Material.` constructor calls remain
- [ ] IF version is `0.11.6`
- [ ] Plugin loads on a local 1.21.11 Paper test server without errors

---

## PR Checklist

- [ ] Branch: `feature/paper-1-21-11` → `main`
- [ ] Three clean commits (one per phase)
- [ ] Design doc linked in PR description
- [ ] Tested on local 1.21.11 Paper server
