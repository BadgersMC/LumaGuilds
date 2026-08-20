# Complete i18n Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move every player-visible LumaGuilds string to Nexus i18n with named MiniMessage placeholders and enforce zero missing, dead, positional, or hardcoded messages.

**Architecture:** `lang/en_US.yml` is the only English source of truth. Interaction and infrastructure adapters render typed application results through one injected `LangService`; domain behavior remains independent of Nexus. Repository contract tests inventory locale keys, references, placeholders, and explicitly classified legacy formatting before each feature batch is migrated.

**Tech Stack:** Kotlin 2.0, Java 21, Paper 1.21, Nexus i18n v2.1.1, JUnit 5, Bukkit `YamlConfiguration`, Gradle Shadow.

**Spec:** `docs/superpowers/specs/2026-08-20-complete-i18n-migration-design.md`

## Global Constraints

- Every player-visible string in commands, Java menus, Bedrock forms, listeners, services, broadcasts, action bars, and item metadata must render through `LangService`.
- Follow LumaTrivia, EnthusiaVotes, and EnthusiaMarket: `msg` for components, `legacy` for string-only APIs, and `raw` only for unparsed values.
- All placeholders are descriptive names; `<0>`, `<1>`, and other numeric placeholders are prohibited.
- `en_US.yml` owns wording and MiniMessage formatting.
- Domain and application result types do not import Nexus.
- Logger output, identifiers, permissions, aliases, persistence values, glyph markup, and pure formatting utilities are out of localization scope.
- Preserve existing English meaning and behavior unless the existing string is malformed.
- Every task follows red-green-refactor and ends with its focused tests passing.

---

### Task 1: Locale Contract Test Harness

**Files:**
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LocaleContractTest.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LocaleSourceScanner.kt`
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LumaGuildsLangResourceTest.kt`
- Modify: `src/main/resources/lang/en_US.yml`

**Interfaces:**
- Consumes: Bukkit `YamlConfiguration`; production Kotlin sources under `src/main/kotlin`.
- Produces: `LocaleSourceScanner.scan(root): LocaleSourceInventory`, containing literal references, dynamic references, hardcoded player text candidates, and placeholder arguments.
- Produces: a deterministic build gate used by every later task.

Implement these test-support types:

```kotlin
data class LocalizationCall(
    val file: Path,
    val line: Int,
    val renderer: String,
    val key: String,
    val placeholderNames: Set<String>,
)

data class PlayerTextCandidate(val file: Path, val line: Int, val source: String)

data class PlaceholderMismatch(
    val key: String,
    val expected: Set<String>,
    val actual: Set<String>,
)

data class LocaleSourceInventory(
    val calls: List<LocalizationCall>,
    val dynamicCalls: List<PlayerTextCandidate>,
    val playerTextCandidates: List<PlayerTextCandidate>,
) {
    val literalKeys: Set<String> get() = calls.mapTo(mutableSetOf()) { it.key }
    fun placeholderMismatches(localeValues: Map<String, String>): List<PlaceholderMismatch>
}

object LocaleSourceScanner {
    fun scan(root: Path): LocaleSourceInventory
}
```

- [ ] **Step 1: Write failing YAML structure tests**

Add tests that load `lang/en_US.yml`, assert mapping keys are strings, assert scalar message values are strings, assert only one root mapping exists for each namespace, and reject numeric MiniMessage placeholders:

```kotlin
@Test
fun `locale contains no positional placeholders`() {
    val positional = flatten(loadLocale()).filterValues { Regex("<\\d+>").containsMatchIn(it) }
    assertEquals(emptyMap<String, String>(), positional)
}

@Test
fun `yaml boolean words remain strings`() {
    val locale = loadLocale()
    assertEquals("No", locale.getString("menu.confirmation.item.no.name"))
    assertEquals("Yes", locale.getString("menu.confirmation.item.yes.name"))
}
```

- [ ] **Step 2: Run the locale tests and verify red**

Run:

```powershell
.\gradlew.bat test --tests 'net.lumalyte.lg.infrastructure.i18n.*'
```

Expected: FAIL because recovered YAML contains `<0>`-style placeholders.

- [ ] **Step 3: Write the source scanner and contract tests**

The scanner must parse literal calls matching `lang.msg("key"`, `lang.legacy("key"`, and `lang.raw("key"`; capture named pairs in the same call; and report dynamic calls separately. Tests must assert:

```kotlin
@Test
fun `all literal localization references resolve`() {
    val inventory = LocaleSourceScanner.scan(projectRoot)
    assertEquals(emptySet<String>(), inventory.literalKeys - localeKeys)
}

@Test
fun `locale has no unreferenced keys`() {
    val referenced = inventory.literalKeys + declaredDynamicKeys
    assertEquals(emptySet<String>(), localeKeys - referenced)
}

@Test
fun `call site placeholder names match locale`() {
    assertEquals(emptyList<PlaceholderMismatch>(), inventory.placeholderMismatches(localeValues))
}
```

Dynamic families must be listed as exact keys derived from `ClaimPermission.entries`, `Flag.entries`, and finite menu-state enums. Do not use prefix-wide exclusions.

- [ ] **Step 4: Run contract tests and record the baseline failures**

Run the `LocaleContractTest` class. Save the failure counts in the task evidence for missing keys, unused keys, positional placeholders, and dynamic calls.

- [ ] **Step 5: Normalize only structural YAML defects**

Merge duplicate mappings, quote YAML boolean words, retain all existing values, and make the file parse deterministically. Do not migrate feature copy in this step.

- [ ] **Step 6: Run focused tests**

Run all infrastructure i18n tests. Structural tests must pass; feature migration counts remain expected failures and guide Tasks 2-6.

- [ ] **Step 7: Commit**

```text
test(i18n): add locale contract gates
```

### Task 2: Named Placeholder Foundation and Recovered Claims Migration

**Files:**
- Modify: `src/main/resources/lang/en_US.yml`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/i18n/LangPlaceholders.kt`
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LangPlaceholdersTest.kt`
- Modify: migrated claim commands under `src/main/kotlin/net/lumalyte/lg/interaction/commands/`
- Modify: migrated claim listeners under `src/main/kotlin/net/lumalyte/lg/interaction/listeners/`
- Modify: claim menus under `src/main/kotlin/net/lumalyte/lg/interaction/menus/management/` and `interaction/menus/misc/`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/ChatInfoBuilder.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ToolItemServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/utils/MenuItemBuilder.kt`

**Interfaces:**
- Consumes: current numeric placeholder pairs.
- Produces: direct named pairs at every call site; no positional adapter remains.

- [ ] **Step 1: Add failing representative rendering tests**

Instantiate `LangService` with a temporary `LangHost` and assert exact rendered output for a one-argument claim message and a four-argument partition location. Expected placeholders must be named `claim`, `permission`, `player`, `lower_x`, `lower_z`, `upper_x`, and `upper_z`.

- [ ] **Step 2: Verify red**

Run the new rendering tests. Expected: FAIL because YAML and callers use numeric names.

- [ ] **Step 3: Rename recovered YAML placeholders**

Replace each numeric placeholder with a semantic name based on the sentence. Examples:

```yaml
command:
  claim:
    add_flag:
      success: "Flag <flag> has been added to claim <claim>."
menu:
  edit_tool:
    item:
      partition:
        lore:
          location: "Lower (<lower_x>, <lower_z>) | Upper (<upper_x>, <upper_z>)"
```

- [ ] **Step 4: Update every recovered call site**

Replace positional pairs and `toLangPlaceholders()` with direct named pairs matching YAML. Delete `LangPlaceholders.kt` after its last consumer is removed. Use `msg` for component-aware send methods and `legacy` only for string APIs.

- [ ] **Step 5: Run claims and locale tests**

```powershell
.\gradlew.bat test --tests 'net.lumalyte.lg.infrastructure.i18n.*' --tests '*Claim*Test' --tests '*EditTool*Test'
```

Expected: named-placeholder tests pass and the locale contains no `<number>` placeholders.

- [ ] **Step 6: Commit**

```text
refactor(i18n): name claim placeholders
```

### Task 3: Commands and Administrative Feedback

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/LumaGuildsCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/PartyChatCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/QuickAllyChatCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/QuickAnnounceCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/QuickGuildChatCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/QuickModChatCommand.kt`
- Modify: every file under `src/main/kotlin/net/lumalyte/lg/interaction/commands/admin/`
- Modify: remaining command files reported by `LocaleSourceScanner`
- Modify: `src/main/resources/lang/en_US.yml`
- Create: `src/test/kotlin/net/lumalyte/lg/interaction/commands/CommandLocalizationTest.kt`

**Interfaces:**
- Consumes: typed command dependencies and one injected `LangService`.
- Produces: component messages using `command.*`, `admin.*`, `guild.*`, `bank.*`, and `war.*` keys.

- [ ] **Step 1: Write failing command behavior tests**

Cover permission denial, player-only denial, successful guild action, bank failure, war failure, and admin result. Mock domain/application dependencies only; use a real `LangService`. Assert rendered plain text and replacement values, not key strings.

- [ ] **Step 2: Verify red**

Run `CommandLocalizationTest`. Expected: at least one assertion sees legacy hardcoded output or a missing locale key.

- [ ] **Step 3: Add command locale entries**

Move complete sentences and formatting into YAML. Reuse existing command keys where semantics match. Introduce named placeholders such as `guild`, `player`, `amount`, `balance`, `reason`, `page`, `count`, and `duration`.

- [ ] **Step 4: Inject and render through `LangService`**

Convert `sendMessage`, `sendActionBar`, broadcasts, help rows, and usage output. Lists may join localized row components with `Component.newline()`; do not concatenate English labels in Kotlin.

- [ ] **Step 5: Run command and locale contracts**

```powershell
.\gradlew.bat test --tests '*CommandLocalizationTest' --tests 'net.lumalyte.lg.infrastructure.i18n.*'
```

Expected: command behavior passes; scanner reports no player-visible command literals.

- [ ] **Step 6: Commit**

```text
refactor(i18n): localize command feedback
```

### Task 4: Java Inventory Menus

**Files:**
- Modify: every file under `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/common/ConfirmationMenu.kt`
- Modify: remaining Java menus under `interaction/menus/management/` and `interaction/menus/misc/`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/utils/MenuItemBuilder.kt`
- Modify: `src/main/resources/lang/en_US.yml`
- Create: `src/test/kotlin/net/lumalyte/lg/interaction/menus/MenuLocalizationTest.kt`

**Interfaces:**
- Consumes: one `LangService` per menu or shared factory dependency.
- Produces: localized titles, names, lore, pagination, confirmation, empty states, and validation messages.

- [ ] **Step 1: Write failing menu tests**

Build representative dashboard, rank, bank, progression, relations, war, settings, and statistics menus with controlled fixtures. Assert visible title/name/lore text after Adventure serialization. Include at least one multi-line lore item and one conditional state.

- [ ] **Step 2: Verify red**

Run `MenuLocalizationTest`. Expected: hardcoded menu text remains or locale lookup is absent.

- [ ] **Step 3: Migrate core and membership menus**

Convert dashboard, control panel, information, selection, settings, member list/management, invite/kick/leave/disband confirmations, ranks, permissions, tag, emoji, banner, description, home, and join-requirement menus.

- [ ] **Step 4: Migrate bank and progression menus**

Convert bank control, automation, budgets, security, statistics, transaction history, contributions, progression levels, rewards, prestige, and leaderboard surfaces.

- [ ] **Step 5: Migrate relations, party, and war menus**

Convert allies, enemies, requests, peace/truce, modes, declarations, objectives, acceptance, history, and management surfaces. Party keys may remain only for functionality still present in production; delete keys for removed party surfaces after consumers are removed.

- [ ] **Step 6: Run menu and locale tests**

```powershell
.\gradlew.bat test --tests '*MenuLocalizationTest' --tests 'net.lumalyte.lg.infrastructure.i18n.*'
```

Expected: representative menu behavior passes and no Java menu player-copy candidates remain.

- [ ] **Step 7: Commit**

```text
refactor(i18n): localize Java menus
```

### Task 5: Bedrock Forms

**Files:**
- Modify: every file under `src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/BedrockLocalizationServiceFloodgate.kt`
- Modify: `src/main/resources/lang/en_US.yml`
- Create: `src/test/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockLocalizationTest.kt`

**Interfaces:**
- Consumes: `LangService`; Cumulus string-only form builders.
- Produces: `bedrock.*` raw or legacy strings and shared feature strings where wording is identical.

- [ ] **Step 1: Write failing form tests**

Build representative guild dashboard, bank, rank, claim, progression, and confirmation forms. Assert title, content, button text, and named replacement output without sending through Floodgate.

- [ ] **Step 2: Verify red**

Run `BedrockLocalizationTest`. Expected: form builders expose hardcoded content.

- [ ] **Step 3: Migrate form copy**

Use `lang.raw()` for titles/buttons requiring literal text and `lang.legacy()` for formatted content. Reuse shared keys only when Java and Bedrock wording is semantically identical.

- [ ] **Step 4: Remove the properties fallback**

Replace `BedrockLocalizationServiceFloodgate` properties fallback paths with Nexus lookups. Preserve locale detection, RTL markers, and Floodgate failure handling; do not keep a second English message store.

- [ ] **Step 5: Run Bedrock and locale tests**

```powershell
.\gradlew.bat test --tests '*BedrockLocalizationTest' --tests 'net.lumalyte.lg.infrastructure.i18n.*'
```

Expected: forms render through Nexus and no Bedrock player-copy literals remain.

- [ ] **Step 6: Commit**

```text
refactor(i18n): localize Bedrock forms
```

### Task 6: Listeners, Services, Broadcasts, and Items

**Files:**
- Modify: player-facing files under `src/main/kotlin/net/lumalyte/lg/interaction/listeners/`
- Modify: player-facing files under `src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/`
- Modify: player-facing files under `src/main/kotlin/net/lumalyte/lg/infrastructure/services/`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/placeholders/LumaGuildsExpansion.kt` only if returned text is human-language copy
- Modify: player-facing item and display utilities under `src/main/kotlin/net/lumalyte/lg/utils/`
- Modify: `src/main/resources/lang/en_US.yml`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/PlayerNotificationLocalizationTest.kt`

**Interfaces:**
- Consumes: typed events/results and injected `LangService` at adapter boundaries.
- Produces: localized chat, action bars, broadcasts, moderation feedback, vault/protection feedback, and item metadata.

- [ ] **Step 1: Write failing notification tests**

Cover one guild chat message, one party/ally message, one protection denial, one teleport result, one progression broadcast, one vault message, and one item display name. Assert semantic output with replacements.

- [ ] **Step 2: Verify red**

Run `PlayerNotificationLocalizationTest`. Expected: representative hardcoded strings remain.

- [ ] **Step 3: Migrate listener and broadcast messages**

Move player-facing sentence templates to YAML and use components end-to-end. Keep logger diagnostics in Kotlin.

- [ ] **Step 4: Migrate service and item messages**

Localize service-produced user feedback and item metadata. Do not localize material names, permission nodes, PAPI identifiers, Nexo IDs, database state, or command aliases.

- [ ] **Step 5: Classify remaining section-sign uses**

For every scanner candidate, either migrate it or add an exact-file classification to `LocaleSourceScanner` with one of: `legacy_serializer_fixture`, `color_code_utility`, `glyph_markup`, or `persistence_literal`. Each classification must be asserted by a dedicated test and may not match directories or prefixes.

- [ ] **Step 6: Run notification and contract tests**

```powershell
.\gradlew.bat test --tests '*PlayerNotificationLocalizationTest' --tests 'net.lumalyte.lg.infrastructure.i18n.*'
```

Expected: zero unclassified player-visible hardcoded strings.

- [ ] **Step 7: Commit**

```text
refactor(i18n): localize player notifications
```

### Task 7: Dead-Key and Legacy Cleanup

**Files:**
- Modify: `src/main/resources/lang/en_US.yml`
- Modify: `src/main/kotlin/net/lumalyte/lg/di/Modules.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt`
- Delete: obsolete localization provider and properties resources if any remain
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LocaleContractTest.kt`

**Interfaces:**
- Consumes: complete set of literal and declared dynamic references.
- Produces: zero dead locale keys and one Nexus localization path.

- [ ] **Step 1: Run dead-key contract and verify red**

Run `LocaleContractTest.locale has no unreferenced keys`. Expected: FAIL with the exact remaining dead-key set.

- [ ] **Step 2: Resolve every dead key**

For each reported key, locate the intended product surface. Wire it if the feature exists; delete it if the feature or wording does not exist. Do not silence it with an allowlist.

- [ ] **Step 3: Remove obsolete plumbing**

Confirm no reference remains to `LocalizationProvider`, `LocalizationProviderProperties`, `LocalizationKeys`, properties resources, override resources, or positional adapters. Remove unused `PlayerLocaleService` wiring only if it has no non-i18n consumers.

- [ ] **Step 4: Run the complete locale contract**

Expected results:

```text
missing keys: 0
unreferenced keys: 0
numeric placeholders: 0
placeholder mismatches: 0
unclassified player literals: 0
```

- [ ] **Step 5: Commit**

```text
refactor(i18n): remove legacy localization
```

### Task 8: Final Verification and SPEAR Evidence

**Files:**
- Modify: `docs/tasks.md`
- Modify: `pr_body.txt` only if this branch will open or update a PR

**Interfaces:**
- Consumes: completed Tasks 1-7.
- Produces: verified shaded JAR and `LG-701` completion evidence.

- [ ] **Step 1: Run diff integrity checks**

```powershell
git diff --check
git status --short
```

Review every changed and deleted file. Preserve unrelated user changes, including `.github/instructions/codacy.instructions.md`.

- [ ] **Step 2: Run clean focused contracts**

```powershell
.\gradlew.bat clean test --tests 'net.lumalyte.lg.infrastructure.i18n.*'
```

Expected: all locale contracts pass from a clean build.

- [ ] **Step 3: Run the full build gate**

```powershell
.\gradlew.bat clean test shadowJar
```

Expected: `BUILD SUCCESSFUL`, zero test failures, and `build/libs/LumaGuilds-2.1.0.jar` produced.

- [ ] **Step 4: Record SPEAR evidence**

Change `LG-701` from `[~]` to `[x]` and record the locale-contract results plus the full build command under Evidence.

- [ ] **Step 5: Commit completion evidence**

```text
docs(i18n): record migration evidence
```

- [ ] **Step 6: Prepare deployment handoff**

Report the branch, commits, JAR path and size, tests executed, and any existing non-i18n warnings. Do not deploy or push unless requested.
