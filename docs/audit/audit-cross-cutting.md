# Cross-Cutting Audit Report

**Scope:** `config`, `di`, `common`, `utils`, `integrations` — 23 files
**Branch:** `fix/audit-cross-cutting`

---

## Findings

### [SEH: high] ItemStackExtensions.kt:133 — getBooleanMeta return type is String? but setter writes BOOLEAN

**What:** `getBooleanMeta()` declares return type `String?` and reads with `PersistentDataType.STRING`, while `setBooleanMeta()` writes with `PersistentDataType.BOOLEAN`. The getter will always return `null` (or a stale STRING value) for data written by the setter, because the PDC key types are different.

**Why it matters:** Any code that sets a boolean meta and later reads it back will get `null` instead of the stored value. This is a silent correctness bug — no crash, just broken state. If any caller relies on `getBooleanMeta` to check a flag set by `setBooleanMeta`, the check always fails.

**Suggested fix:** Change `getBooleanMeta` to use `PersistentDataType.BOOLEAN` and return `Boolean?`, or rename the getter to `getStringMeta` if a string read is truly intended. Ensure getter and setter use the same `PersistentDataType`.

**Confidence:** high

---

### [SEH: high] GuildHomeSafety.kt:64 — Duplicate LAVA check after already catching it in DAMAGING set

**What:** Line 61 checks `DAMAGING.contains(feet.type)` which already includes `Material.LAVA`. Line 64 then checks `feet.type == Material.LAVA` redundantly. The line 64 check is dead code — it can never be reached because LAVA at feet is already caught by the DAMAGING check on line 61.

**Why it matters:** Not a runtime bug per se, but it reveals incomplete reasoning: the author likely intended the LAVA check to cover a different case (e.g., the block *below* being lava while feet are in a non-damaging block). As written, line 64 is unreachable and the specific "Lava at feet" reason message is dead code.

**Suggested fix:** Remove line 64, or if the intent was to check the block two below (standing on a transparent block above lava), add that check explicitly with a separate head-room safety check.

**Confidence:** high

---

### [SEH: med] GuildHomeSafety.kt:48 — evaluateSafety does not check the head block (eye level)

**What:** `evaluateSafety` checks `feet` and `below` but never checks the block at eye level (~1.62m above feet). A player teleporting into a location where the head block is lava, fire, cactus, or magma block would take damage despite the safety check passing.

**Why it matters:** The safety check is used by `checkOrAskConfirm` before teleporting players to guild homes. A player could teleport into a ceiling of lava/fire and take damage or die, defeating the purpose of the safety system.

**Suggested fix:** Add a `headLoc = base.clone().add(0.0, 1.0, 0.0)` check and test it against DAMAGING materials.

**Confidence:** high

---

### [SEH: med] ColorCodeUtils.kt:23 — convertLegacyToMiniMessage heuristic misidentifies plain text with angle brackets as MiniMessage

**What:** The check `input.contains(Regex("<[^>]+>"))` returns `true` for any string containing `<anything>`, including plain text like `"a < b > c"` or `"use <item>"`. Such input would be returned unconverted instead of being processed through the legacy serializer.

**Why it matters:** If a guild tag or user input contains angle brackets that are NOT MiniMessage tags, the function silently skips conversion. This is a minor correctness issue since the fallback path would also handle it, but the heuristic is fragile and could cause confusion with edge-case inputs.

**Suggested fix:** Use a more specific regex that matches known MiniMessage tag patterns (e.g., `<[a-z][a-z0-9_-]*>` or `</[a-z]>`), or attempt MiniMessage parsing first and fall back to legacy.

**Confidence:** med

---

### [SEH: med] ColorCodeUtils.kt:56 — renderTagForDisplay drops legacy & codes when MiniMessage tags are also present

**What:** The condition `tag.contains('&') && !tag.contains(Regex("<[^>]+>"))` means that if a tag has BOTH `&` codes AND MiniMessage tags, it falls to the else branch which tries MiniMessage parsing. The `&c` prefix would be treated as literal text by MiniMessage, producing garbled output. The catch block then silently returns the original tag.

**Why it matters:** Mixed-format tags (possible from manual edits or data migration) silently produce unformatted output with no warning. This is a minor display issue.

**Suggested fix:** Strip legacy `&` codes before MiniMessage parsing, or normalize the input to one format first.

**Confidence:** med

---

### [SEH: med] ConfigValidator.kt:20 — validateAndCorrect is never called; entire validator is dead code

**What:** `ConfigValidator.validateAndCorrect()` and `logConfigSummary()` are public functions with zero callers in the entire codebase. The `config/MainConfig.kt` data class is loaded via `ConfigServiceBukkit` which returns the raw config without validation.

**Why it matters:** The validator contains useful safety bounds (e.g., `menuSize` rounding, `maxWithdrawalPercent` clamping, material name validation) that are never applied. Invalid config values from `config.yml` are used as-is, potentially causing runtime errors or unexpected behavior.

**Suggested fix:** Either wire `validateAndCorrect` into the config loading path (e.g., in `ConfigServiceBukkit.loadConfig()`), or remove the dead code to avoid confusion.

**Confidence:** high

---

### [SEH: med] ConfigValidator.kt:152 — brewingXp is missing from validateProgressionConfig

**What:** `validateProgressionConfig` validates `craftingXp`, `smeltingXp`, `fishingXp`, and `enchantingXp` but does NOT validate `brewingXp` (line 347 of MainConfig.kt). A negative `brewingXp` value from config would be used as-is.

**Why it matters:** Inconsistent validation — all other XP source values are coerced to non-negative except `brewingXp`. A misconfigured negative value would subtract XP for brewing activities.

**Suggested fix:** Add `brewingXp = config.brewingXp.coerceAtLeast(0)` to the validation chain.

**Confidence:** high

---

### [SEH: med] ItemStackExtensions.kt:136 — PDC namespace "bellclaims" is a leftover from a different plugin

**What:** All four PDC extension functions (`getBooleanMeta`, `setBooleanMeta`, `getStringMeta`, `setStringMeta`) hardcode `NamespacedKey("bellclaims", key)` — "bellclaims" is a different Minecraft plugin entirely. This means LumaGuilds PDC data shares a namespace with BellClaims, risking key collisions if both plugins are installed.

**Why it matters:** If a server runs both LumaGuilds and BellClaims, PDC data could collide — one plugin reading/writing the other's data. This is a namespace pollution issue that could cause subtle data corruption.

**Suggested fix:** Change the namespace to `"lumaguilds"` (using the plugin instance for proper namespacing, as `PluginKeys` does for `NamespacedKey`).

**Confidence:** high

---

### [SEH: med] CombatUtil.kt:16 — Unchecked cast to ICombatLogX without instanceof check

**What:** `getAPI()` casts the plugin instance directly: `plugin as ICombatLogX?`. While the null-safe cast (`as?`) prevents ClassCastException, there is no `instanceof` check. If another plugin named "CombatLogX" is present but doesn't implement `ICombatLogX`, this returns null silently.

**Why it matters:** Low risk in practice since the CombatLogX plugin is well-known, but the pattern is fragile. A misbehaving or renamed plugin could cause silent failures.

**Suggested fix:** Add an `instanceof ICombatLogX` check before casting, or catch the cast more explicitly.

**Confidence:** low

---

### [SEH: low] LumaGuildsHook.kt:83 — getTeamMembers calls getAllGuilds() on every invocation

**What:** `getTeamMembers` calls `guildService.getAllGuilds()` (which likely hits the database or iterates all cached guilds) every time AxKoth queries team members. This is called frequently during KOTH events.

**Why it matters:** Performance issue under load — each call iterates all guilds to find one by name. If the guild list is large, this adds unnecessary overhead on a hot path.

**Suggested fix:** Use a direct lookup by name (e.g., `guildService.getGuildByName(teamName)`) instead of iterating all guilds.

**Confidence:** med

---

### [SEH: low] LumaGuildsHook.kt:121 — getTeamByName also calls getAllGuilds()

**What:** Same issue as `getTeamMembers` — `guildService.getAllGuilds()` is called for a simple name lookup.

**Why it matters:** Unnecessary full scan for a single guild lookup.

**Suggested fix:** Use `guildService.getGuildByName(teamName)` directly.

**Confidence:** med

---

### [SEH: low] GuildDisplayUtils.kt:116 — isValidEmojiFormat only checks colon-wrapped format

**What:** `isValidEmojiFormat` validates that emoji starts with `:` and ends with `:` and has length > 2. This is a very loose check — it would accept `":x:"` or `":   :"` as valid emoji. Meanwhile, the actual emoji resolution (elsewhere) may expect specific formats.

**Why it matters:** Loose validation could let malformed emoji strings through, causing display issues downstream. Not a security risk, just a data quality nit.

**Suggested fix:** Add a regex check for valid emoji name characters between the colons.

**Confidence:** low

---

### [SEH: low] Modules.kt:247 — Unchecked cast in coreModule storage binding

**What:** `@Suppress("UNCHECKED_CAST") single<Storage<Database>> { storage as Storage<Database> }` performs an unchecked cast. If the `storage` parameter is not actually `Storage<Database>`, this will fail at runtime with a ClassCastException when the binding is resolved.

**Why it matters:** The cast is safe as long as all callers pass the correct type, but there is no compile-time guarantee. A wrong storage type would cause a runtime crash during DI initialization.

**Suggested fix:** Consider making `coreModule` generic or passing `Storage<Database>` directly to avoid the cast.

**Confidence:** low

---

### [SEH: low] EmojiUtils.kt / PermissionDisplay.kt — Empty stub files

**What:** Both files are 0 bytes. They are listed in the audit scope but contain no code.

**Why it matters:** Dead files clutter the codebase and may confuse future developers. If they are placeholders, they should be noted or removed.

**Suggested fix:** Remove the files or add a comment explaining why they exist.

**Confidence:** high

---

## Test Coverage Gaps

| File | Untested Behavior | Suggested Test |
|------|-------------------|----------------|
| `GuildTagValidator.kt` | Regex matching for disallowed tags (click/hover/insertion) with various casings and edge cases | Unit test: verify `rejectionReason` rejects `<click:...>`, `<hover:...>`, `<insertion>` and allows `<red>`, `<bold>`, `<gradient>` |
| `GuildHomeSafety.kt` | `evaluateSafety` with various block configurations (lava ceiling, cactus wall, safe location) | Unit test (mocked): verify safety result for feet/below/head block combinations |
| `ColorCodeUtils.kt` | `convertLegacyToMiniMessage` with mixed-format input, plain text with angle brackets | Unit test: verify conversion of `&cRed`, `<red>Red</red>`, and `"a < b"` inputs |
| `ColorCodeUtils.kt` | `stripAllFormatting` with nested MiniMessage tags, mixed legacy+MM | Unit test: verify plain text extraction from complex formatted strings |
| `ConfigValidator.kt` | All validation bounds (negative values, out-of-range menu sizes, invalid materials) | Unit test: verify each validation function coerces out-of-range values correctly |
| `GuildResolver.kt` | `normalize` with color codes, mixed case, special characters | Unit test: verify normalization strips formatting and produces consistent keys |
| `GuildResolver.kt` | `resolveGuildByName` ambiguity handling (multiple guilds with same normalized name) | Unit test: verify null is returned for ambiguous matches |
| `ItemStackExtensions.kt` | `serializeToString` / `deserializeToItemStack` round-trip with complex items (banners, enchanted) | Integration test: verify serialization round-trip preserves NBT data |
| `ItemStackExtensions.kt` | `getBooleanMeta` / `setBooleanMeta` round-trip | Unit test: verify getter returns what setter wrote (currently broken) |
| `GuildDisplayUtils.kt` | `getFormattedGuildName` with null tag, blank tag, formatting-only tag | Unit test: verify fallback to guild name when tag is blank after stripping |
| `CombatUtil.kt` | `isInCombat` when CombatLogX is not installed | Unit test (mocked): verify graceful handling when plugin is null |

---

## Layer Boundary Check

**Verdict: PASS with minor notes.**

- `config/` (MainConfig.kt, ProgressionConfig.kt): Contain only data classes and pure functions. No imports from `application/` or `infrastructure/` except `ProgressionConfig.kt` which imports `PerkType` from `application/services/` and `Material` from Bukkit. The `Material` import in `MilestoneItemRewardConfig` is a domain-value concern and acceptable. The `PerkType` import is an application-layer type used as a config value — this is a minor layer blur but not a hard violation.

- `di/` (Modules.kt): Correctly imports from both `application/` and `infrastructure/` — this is the expected composition root. No domain imports.

- `common/` (PluginKeys.kt): Only imports from `org.bukkit` — acceptable for a key registry.

- `utils/` (all files): Import from `org.bukkit` and `domain/` — utilities are infrastructure-adjacent and this is expected. `ConfigValidator.kt` imports from `config/` and `org.bukkit.Material` — acceptable.

- `integrations/` (LumaGuildsHook.kt): Imports from `application/services/` (ports) and `org.koin` — correct for an integration adapter.

**No hard layer boundary violations detected.** The hexagonal architecture is respected: domain types are used as imports by config/utils, but domain code does not import upward.
