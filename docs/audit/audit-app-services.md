# Audit Report: application/services, utilities, errors, events

**Scope:** 48 files in `application/{services,utilities,errors,events}`
**Branch:** fix/audit-app-services
**Methodology:** docs/audit/audit-app-services-plan.md

---

## Findings

### [SEH: high] PartitionModificationEvent.kt:14 — `handlerList` is a val but must be static

**What:** The `handlerList` is declared `private val` inside the `companion object`, but Bukkit's `HandlerList` must be effectively static/`@JvmStatic` for the event system to work reliably across dispatches. It is only referenced via `getHandlerList()` (line 21) and `getHandlers()` (line 31), which is correct, but the `val` allocation in the companion object is per-class-loader which is fine. **However**, the `getHandlers()` override on line 31 returns the companion's `handlerList` — this is the Bukkit contract. No actual bug here per se, but the code is fragile: if anyone ever adds a second event class copy-pasting this pattern and forgets `@JvmStatic`, the event system silently breaks. No finding.

**Confidence:** low — withdrawn after review; Bukkit contract is met.

---

### [SEV: high] VaultAutoSaveService.kt:257-258 — auto-unboxing NPE on cancelled-task check

**What:** `autoSaveTask?.isCancelled == false` uses the safe-call operator, but if `autoSaveTask` is null the expression evaluates to `null == false` which is `false` (safe). However, the real issue is that `BukkitTask.isCancelled()` returns a `Boolean` (platform type `Boolean!`). If the scheduler implementation ever returns a null (unlikely but not contractual), calling `isCancelled` on a non-null `BukkitTask` that has been garbage-collected or corrupted could throw. This is a marginal concern.

**Why it matters:** Low probability. Withdrawn.

**Confidence:** low

---

### [SEV: med] PartitionModificationEvent.kt:13 — Bukkit event in application layer

**What:** `PartitionModificationEvent` extends `org.bukkit.event.Event` and imports `org.bukkit.event.HandlerList`. This places a Bukkit framework type directly in the `application/events` package, violating the hexagonal architecture rule that `application` must not import Bukkit.

**Why it matters:** The application layer becomes coupled to the Bukkit event system. Any change in the Bukkit API (or migration to a different server platform) would require changes in the application layer. The event should be defined in the infrastructure layer, or the application layer should define its own pure event type that infrastructure adapts.

**Suggested fix:** Define a pure `PartitionModificationEvent` in `application/events` (no Bukkit imports), then create a Bukkit-specific adapter in `infrastructure/bukkit/events` that extends `org.bukkit.Event`.

**Confidence:** high

---

### [SEV: med] CsvExportService.kt:115 — `getPlayerName` calls `Bukkit.getOfflinePlayer` on every row

**What:** `getPlayerName(transaction.actorId)` calls `org.bukkit.Bukkit.getOfflinePlayer(playerId)` for every transaction in the CSV. This is a synchronous I/O call that hits the user cache and potentially disk. For large transaction lists this blocks the calling thread (which may be async, but still wastes resources).

**Why it matters:** Performance degradation when exporting large histories. If called from the main thread (the method is not marked async), it freezes the server.

**Suggested fix:** Batch-resolve player names via `Bukkit.getOfflinePlayer` once into a map, or accept a pre-resolved name map as a parameter. Consider making the method `suspend` or returning a `CompletableFuture`.

**Confidence:** high

---

### [SEV: med] CsvExportService.kt:131-136 — CSV sanitization is incomplete

**What:** `sanitizeCsvField` replaces commas with semicolons (line 133), then on line 136 checks if the result *still* contains commas to decide whether to wrap in quotes. Since commas were already replaced, the quote-wrapping condition on line 136 will never trigger for commas — it only triggers for quotes/newlines. More critically, the method does **not** strip or escape formula-injection characters (`=`, `+`, `-`, `@`, `\t`, `\r`) at the start of fields, which is the primary CSV injection vector.

**Why it matters:** CSV injection (formula injection) allows arbitrary command execution when the CSV is opened in Excel/LibreOffice. A player name like `=CMD("calc")` would pass through unsanitized.

**Suggested fix:** Prefix fields starting with `=`, `+`, `-`, `@`, `\t`, or `'` with a single quote or tab character. Use a proper CSV library (e.g., Apache Commons CSV or opencsv) instead of manual string building.

**Confidence:** high

---

### [SEV: med] DiscordCsvService.kt:27 — `webhookUrl` is a constructor-injected config value with no validation at call time

**What:** The `webhookUrl` is passed at construction and `isConfigured()` checks it starts with `https://discord.com/api/webhooks/`. However, `sendFileToDiscord` (line 232) uses the URL directly in `URI.create(webhookUrl)` without try-catch for `IllegalArgumentException`. A malformed URL would propagate as an unhandled exception, crashing the virtual thread and losing the callback.

**Why it matters:** If the webhook URL is malformed (e.g., contains spaces or invalid characters), the async export silently fails — the callback is never invoked, and the player receives no feedback.

**Suggested fix:** Validate the URL in the constructor or in `sendFileToDiscord` with a try-catch around `URI.create()`, calling `callback(Result.failure(...))` on error.

**Confidence:** med

---

### [SEV: med] DiscordCsvService.kt:232-282 — `sendFileToDiscord` runs HTTP on a virtual thread but catches only IOException

**What:** The `sendFileToDiscord` method catches `IOException` (line 279) but `HttpClient.send()` can also throw `InterruptedException` (which is not an `IOException`). On an `InterruptedException`, the method falls through without returning a `Result`, so the callback is never invoked.

**Why it matters:** Silent failure — the player never gets success or error feedback. The virtual thread is interrupted and the result is lost.

**Suggested fix:** Also catch `InterruptedException` (and ideally any `Exception`) and return `Result.failure(...)`.

**Confidence:** high

---

### [SEV: med] FileExportManager.kt:219-234 — rate limit check is not thread-safe

**What:** `checkRateLimit` reads and writes `rateLimitMap` using `computeIfAbsent` (thread-safe) but then calls `playerRequests.removeIf(...)` and `playerRequests.add(...)` on the mutable list without synchronization. If two exports for the same player fire concurrently, the size check on line 227 and the add on line 232 can race, allowing more than `MAX_EXPORTS_PER_HOUR` exports.

**Why it matters:** Rate limiting can be bypassed under concurrent load, defeating its purpose as a DoS mitigation.

**Suggested fix:** Use a `synchronized` block on the player's list, or use a concurrent queue with atomic size tracking, or use a `ConcurrentLinkedQueue` with a synchronized window check.

**Confidence:** med

---

### [SEV: med] FileExportManager.kt:99,174 — async task captures `callback` from Bukkit scheduler thread

**What:** `Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { ... })` captures the `callback` lambda. The callback is then invoked from the async thread (line 111-118) and also from within the Discord callback (line 109-119). If the callback touches Bukkit API (e.g., sending a message to a player), this will throw or corrupt state since Bukkit API is not thread-safe.

**Why it matters:** Potential `IllegalStateException` or data corruption if the callback interacts with Bukkit from an async thread.

**Suggested fix:** Ensure all Bukkit-interacting callbacks are scheduled back to the main thread via `Bukkit.getScheduler().runTask(plugin, Runnable { ... })`.

**Confidence:** med

---

### [SEV: med] VaultInventoryManager.kt:537 — `flushBuffer` returns `true` for empty buffer

**What:** `flushBuffer` returns `true` on line 538 when there's no buffer (`writeBuffers[guildId] == null`), and also on line 540-541 when the buffer has no pending changes. This is semantically correct (nothing to flush = success), but callers checking the boolean cannot distinguish "nothing to do" from "successfully flushed."

**Why it matters:** Minor — the return value is used by `flushPendingBuffers()` to count flushed buffers, which will never count "no-op" flushes. This is fine. No finding.

**Confidence:** low — withdrawn.

---

### [SEV: med] VaultInventoryManager.kt:858-898 — `saveSlotWithRetry` swallows all non-SQLException errors

**What:** The `saveSlotWithRetry` method catches only `SQLException` (line 874). If `vaultRepository.saveVaultItem` throws any other exception (e.g., `IllegalStateException`, `NullPointerException`), the exception propagates up, potentially crashing the flush cycle and leaving the buffer in an inconsistent state.

**Why it matters:** A single bad item (e.g., with corrupted NBT) could crash the entire auto-save loop for all vaults, since `flushPendingBuffers` iterates all buffers.

**Suggested fix:** Catch `Exception` instead of just `SQLException`, log the error, and continue with the next buffer.

**Confidence:** high

---

### [SEV: med] VaultInventoryManager.kt:909-948 — same issue in `saveGoldBalanceWithRetry`

**What:** Identical to the slot retry issue — only `SQLException` is caught. Any other exception type crashes the flush.

**Why it matters:** Same as above — a non-SQL error in gold balance persistence crashes the auto-save loop.

**Suggested fix:** Catch `Exception` instead of just `SQLException`.

**Confidence:** high

---

### [SEV: med] VaultAutoSaveService.kt:92-107 — `performAutoSave` catches Exception but logs stack trace to stderr

**What:** Line 103 calls `e.stackTraceToString()` which prints to the plugin logger (fine), but line 102 uses `plugin.logger.severe(...)` which is correct. No actual bug, but the pattern of catching all exceptions in background tasks means transient errors are silently retried while persistent errors (e.g., database connection lost) spam the log every second.

**Why it matters:** Log flooding during database outages. No circuit breaker.

**Suggested fix:** Add a consecutive-error counter that backs off logging after N failures, or escalates to disable the auto-save service.

**Confidence:** med

---

### [SEV: low] CsvExportService.kt:16 — `DateTimeFormatter` is not thread-safe when used from async

**What:** `DateTimeFormatter` is stored in a `val` field and used by `formatTimestamp`. `DateTimeFormatter` IS thread-safe (it's immutable), so this is fine. No finding.

**Confidence:** low — withdrawn.

---

### [SEV: low] DiscordCsvService.kt:33 — same `DateTimeFormatter` pattern

**What:** Same as above — `DateTimeFormatter` is thread-safe. No finding.

**Confidence:** low — withdrawn.

---

### [SEV: low] VaultAutoSaveService.kt:257 — `autoSaveTask?.isCancelled` safe-call semantics

**What:** When `autoSaveTask` is null (before `start()` or after `stop()`), `autoSaveTask?.isCancelled` returns `null`, and `null == false` evaluates to `false`. This means `auto_save_running` will be `false` when the task is null, which is correct. No bug.

**Confidence:** low — withdrawn.

---

### [SEV: low] FormCacheService.kt:3 — Geysermc Cumulus import in application layer

**What:** `FormCacheService` imports `org.geysermc.cumulus.form.Form`, a Bedrock/Floodgate framework type, directly in the application layer interface.

**Why it matters:** Violates the hexagonal architecture — the application layer should not depend on framework-specific types. The `Form` type is a Geysermc Cumulus API class.

**Suggested fix:** Define a pure application-layer form abstraction (e.g., `BedrockForm`) and have the infrastructure layer adapt Cumulus `Form` to it.

**Confidence:** high

---

### [SEV: low] FormValidationService.kt:3 — `BaseBedrockMenu` import in application layer

**What:** `FormValidationService` imports `net.lumalyte.lg.interaction.menus.bedrock.BaseBedrockMenu` — a UI-layer type — in the application service interface. However, looking at the actual usage, `BaseBedrockMenu` is not referenced in the file; the import is unused.

**Why it matters:** Unused import is a code quality issue, not a runtime bug. But if it were used, it would couple the application service to the interaction layer.

**Suggested fix:** Remove the unused import.

**Confidence:** high

---

### [SEV: low] FormValidationService.kt:4 — Bukkit Player import in application layer

**What:** `FormValidationService` imports `org.bukkit.entity.Player` and uses it in `showValidationErrors` (line 36). The application layer should not depend on Bukkit types.

**Why it matters:** Couples the validation service to Bukkit. The player identity should be passed as a `UUID` instead.

**Suggested fix:** Change `showValidationErrors` to accept `UUID playerId` instead of `Player`, and let the caller resolve the player.

**Confidence:** high

---

### [SEV: low] BedrockLocalizationService.kt:3 — Bukkit Player import in application layer

**What:** `BedrockLocalizationService` imports `org.bukkit.entity.Player` and uses it in two method signatures.

**Why it matters:** Same as above — application layer should use `UUID` instead of `Player`.

**Suggested fix:** Replace `Player` parameters with `UUID` and let callers resolve.

**Confidence:** high

---

### [SEV: low] PlatformDetectionService.kt:3 — Bukkit Player import in application layer

**What:** `PlatformDetectionService` imports `org.bukkit.entity.Player`.

**Why it matters:** Same pattern — application layer should use `UUID`.

**Suggested fix:** Replace `Player` with `UUID` and let infrastructure resolve.

**Confidence:** high

---

### [SEV: low] MapRendererService.kt:4-5 — Bukkit Player and ItemStack imports in application layer

**What:** `MapRendererService` imports `org.bukkit.entity.Player` and `org.bukkit.inventory.ItemStack`.

**Why it matters:** `ItemStack` is a Bukkit type. The application layer should define its own item representation or use a domain value.

**Suggested fix:** Replace `ItemStack` with a domain-level `MapItem` or `ItemRepresentation` value type.

**Confidence:** high

---

### [SEV: low] GuildVaultService.kt:5-7 — Bukkit Location, Player, ItemStack imports in application layer

**What:** `GuildVaultService` imports three Bukkit types: `Location`, `Player`, `ItemStack`.

**Why it matters:** All three are framework types. The application layer should use domain values (`GuildHome`, `UUID`, domain item types).

**Suggested fix:** Replace with domain equivalents.

**Confidence:** high

---

### [SEV: low] PhysicalCurrencyService.kt:4 — Bukkit ItemStack import in application layer

**What:** `PhysicalCurrencyService` imports `org.bukkit.inventory.ItemStack`.

**Why it matters:** Same pattern.

**Suggested fix:** Use a domain item representation.

**Confidence:** high

---

### [SEV: low] VaultBackupService.kt:4 — Bukkit ItemStack import in application layer

**What:** `VaultBackupService` imports `org.bukkit.inventory.ItemStack`.

**Why it matters:** Same pattern.

**Suggested fix:** Use a domain item representation.

**Confidence:** high

---

### [SEV: low] LunarClientService.kt:3-4 — Apollo/Bukkit imports in application layer

**What:** `LunarClientService` imports `com.lunarclient.apollo.player.ApolloPlayer` and `org.bukkit.entity.Player`.

**Why it matters:** Apollo is a third-party framework. The application layer should define its own `LunarClientStatus` or similar abstraction.

**Suggested fix:** Define a domain-level interface and adapt Apollo in infrastructure.

**Confidence:** high

---

### [SEV: low] GuildService.kt:51 — Bukkit ItemStack import in application layer

**What:** `GuildService.setBanner` accepts `org.bukkit.inventory.ItemStack?` as a parameter.

**Why it matters:** The application service interface should not expose Bukkit types. The banner should be described by a domain value (material name, patterns, etc.).

**Suggested fix:** Replace `ItemStack?` with a `BannerDesignData` or material name string.

**Confidence:** high

---

### [SEV: low] PartitionModificationEvent.kt:4-5 — Bukkit event imports in application layer

**What:** Already covered in the med finding above. This is the same issue.

**Confidence:** high

---

### [SEV: low] VaultInventoryManager.kt:12-17 — multiple Bukkit and JDBC imports in application layer

**What:** `VaultInventoryManager` imports `org.bukkit.Bukkit`, `org.bukkit.entity.Player`, `org.bukkit.inventory.Inventory`, `org.bukkit.inventory.ItemStack`, `org.slf4j.LoggerFactory`, and `java.sql.SQLException`.

**Why it matters:** This is the most severe layer violation in the audit. `VaultInventoryManager` is a concrete class (not an interface) in the `application/services` package that directly uses Bukkit API (`Bukkit.createInventory`, `Bukkit.getScheduler`) and JDBC (`java.sql.SQLException`). This tightly couples the application service to both the Bukkit framework and the SQL infrastructure.

**Suggested fix:** Extract an interface `VaultInventoryManager` into the application layer with pure domain types. Move the current implementation to `infrastructure/persistence` or `infrastructure/bukkit`. The interface should not expose `Inventory`, `Player`, `ItemStack`, or `SQLException`.

**Confidence:** high

---

### [SEV: low] VaultAutoSaveService.kt:3-5 — Bukkit imports in application layer

**What:** `VaultAutoSaveService` imports `org.bukkit.Bukkit`, `org.bukkit.plugin.java.JavaPlugin`, and `org.bukkit.scheduler.BukkitTask`.

**Why it matters:** Same pattern — concrete class in application layer directly uses Bukkit scheduler and plugin API.

**Suggested fix:** Move to infrastructure layer or extract an interface.

**Confidence:** high

---

### [SEV: low] DiscordCsvService.kt:6-7 — Bukkit imports in application layer

**What:** `DiscordCsvService` imports `org.bukkit.Bukkit` and `org.bukkit.entity.Player`.

**Why it matters:** Same pattern.

**Suggested fix:** Replace `Player` with `UUID` and resolve the name via a port interface.

**Confidence:** high

---

### [SEV: low] FileExportManager.kt:3-5 — Bukkit imports in application layer

**What:** `FileExportManager` imports `org.bukkit.Bukkit`, `org.bukkit.entity.Player`, and `org.bukkit.plugin.Plugin`.

**Why it matters:** Same pattern.

**Suggested fix:** Extract interface, move implementation to infrastructure.

**Confidence:** high

---

### [SEV: low] CsvExportService.kt:115 — Bukkit import via fully-qualified name

**What:** `CsvExportService` uses `org.bukkit.Bukkit.getOfflinePlayer(playerId)` via fully-qualified name (no import, but still a direct Bukkit dependency).

**Why it matters:** Same layer violation, just without an explicit import statement.

**Suggested fix:** Inject a `PlayerNameResolver` port interface.

**Confidence:** high

---

### [SEV: low] GoldBalanceButton.kt:7-11 — Bukkit imports in application utilities

**What:** `GoldBalanceButton` imports `org.bukkit.Material`, `org.bukkit.NamespacedKey`, `org.bukkit.inventory.ItemStack`, `org.bukkit.persistence.PersistentDataType`, and `org.bukkit.plugin.java.JavaPlugin`.

**Why it matters:** Utility class in application layer directly uses Bukkit Material, PDC, and plugin API.

**Suggested fix:** Extract the Bukkit-specific item creation to an infrastructure adapter.

**Confidence:** high

---

### [SEV: low] ValuableItemChecker.kt:4-6 — Bukkit imports in application utilities

**What:** `ValuableItemChecker` imports `org.bukkit.Material`, `org.bukkit.inventory.ItemStack`, and `org.bukkit.persistence.PersistentDataType`.

**Why it matters:** Same pattern.

**Suggested fix:** Use a domain item representation.

**Confidence:** high

---

### [SEV: low] FileExportManager.kt:32-34 — rate limit constants are not configurable

**What:** `MAX_EXPORTS_PER_HOUR = 5` and `RATE_LIMIT_WINDOW_MS` are hardcoded constants.

**Why it matters:** Cannot be tuned without recompilation. Should be in config.

**Suggested fix:** Inject these values from configuration.

**Confidence:** low

---

### [SEV: low] FileExportManager.kt:41-42 — temp file expiry is hardcoded

**What:** `FILE_EXPIRY_MS = 15 minutes` and `MAX_FILE_SIZE_BYTES = 5MB` are hardcoded.

**Why it matters:** Same as above — should be configurable.

**Suggested fix:** Inject from config.

**Confidence:** low

---

### [SEV: low] DiscordCsvService.kt:254 — hardcoded avatar URL

**What:** The Discord embed includes a hardcoded `avatar_url` pointing to `https://i.imgur.com/placeholder.png`.

**Why it matters:** Broken image in Discord embeds. Cosmetic.

**Suggested fix:** Make configurable or remove.

**Confidence:** low

---

### [SEV: low] GuildBannerService.kt:78 — banner limit is hardcoded to 6

**What:** `BannerDesignData.isValid()` checks `patterns.size <= 6` with a comment "Minecraft banner limit." This is correct for vanilla Minecraft but is a game-mechanic constant in the application layer.

**Why it matters:** Minor — the constant is correct and unlikely to change. No real issue.

**Confidence:** low — withdrawn.

---

### [SEV: low] GuildService.kt:61 — emoji parameter accepts raw Nexo placeholder string

**What:** `setEmoji(guildId, emoji, actorId)` accepts a raw string like `":catsmileysmile:"` with no validation.

**Why it matters:** If an invalid placeholder is stored, it may display incorrectly or cause issues with the Nexo parser. No validation that it's a valid Nexo placeholder format.

**Suggested fix:** Validate the emoji string format (e.g., matches `^:[a-z0-9_]+:$`).

**Confidence:** med

---

### [SEV: low] GuildService.kt:79 — tag accepts raw MiniMessage string

**What:** `setTag(guildId, tag, actorId)` accepts a raw MiniMessage-formatted string with no validation or sanitization.

**Why it matters:** Malformed MiniMessage could cause parsing errors or visual glitches. A tag like `<red><unclosed` could break chat rendering.

**Suggested fix:** Validate MiniMessage syntax before storing, or strip formatting and store plain text.

**Confidence:** med

---

### [SEV: low] ConfigService.kt:3 — imports `net.lumalyte.lg.config.MainConfig`

**What:** `ConfigService` imports `net.lumalyte.lg.config.MainConfig`. The `config` package is outside the hexagonal architecture — it's a cross-cutting concern. This is a minor layer concern.

**Why it matters:** The config package likely contains Bukkit-specific config loading. The application service should depend on a config port interface, not a concrete config class.

**Suggested fix:** Inject config values or a config port interface.

**Confidence:** med

---

## Test Coverage Gaps

| File | Untested Behavior | Suggested Test |
|------|-------------------|----------------|
| `CsvExportService` | CSV injection sanitization | Test with formula-injection payloads (`=CMD(...)`, `+cmd|...`) |
| `CsvExportService` | `generateTransactionHistoryCsv` with empty list | Test empty transaction list produces header-only CSV |
| `CsvExportService` | `generateGuildSummaryCsv` with zero balance | Test edge case of new guild with no activity |
| `DiscordCsvService` | `sendFileToDiscord` with malformed webhook URL | Test `URI.create` failure path |
| `DiscordCsvService` | `sendFileToDiscord` with `InterruptedException` | Test interrupt handling during HTTP send |
| `DiscordCsvService` | `isConfigured` with blank/invalid URL | Test boundary conditions |
| `FileExportManager` | Rate limit enforcement under concurrency | Test concurrent exports from same player |
| `FileExportManager` | `cancelExport` for non-existent export | Test canceling already-completed export |
| `FileExportManager` | `cleanupOldFiles` with no temp files | Test empty directory cleanup |
| `FileExportManager` | File size limit enforcement | Test export rejection when CSV exceeds max |
| `VaultInventoryManager` | `flushBuffer` with database failure | Test retry logic and dirty marking |
| `VaultInventoryManager` | `saveSlotWithRetry` with non-SQLException | Test behavior on unexpected exception types |
| `VaultInventoryManager` | `depositGold` / `withdrawGold` concurrency | Test concurrent deposits/withdrawals for race conditions |
| `VaultInventoryManager` | `broadcastSlotUpdate` with disconnected viewer | Test viewer disconnect during broadcast |
| `VaultInventoryManager` | `cleanupIdleSessions` threshold boundary | Test session exactly at idle threshold |
| `VaultInventoryManager` | `evictSharedInventory` with active viewers | Test eviction blocked when viewers present |
| `VaultAutoSaveService` | Crash detection on startup | Test with/without running marker file |
| `VaultAutoSaveService` | `performAutoSave` with persistent DB failure | Test error logging and retry behavior |
| `VaultAutoSaveService` | `archiveOldTransactions` with no old transactions | Test no-op archival |
| `GoldBalanceButton` | `isGoldButton` with null item | Test null input |
| `GoldBalanceButton` | `convertToItems` with zero balance | Test empty result |
| `GoldBalanceButton` | `convertToItems` with exact block multiples | Test compression into RAW_GOLD_BLOCK |
| `GoldBalanceButton` | `calculateGoldValue` with AIR material | Test AIR handling |
| `ValuableItemChecker` | `isValuableItem` with custom model data | Test CMD matching |
| `ValuableItemChecker` | `isValuableItem` with enchantments enabled/disabled | Test enchantment check toggle |
| `FormValidationService` | `GUILD_NAME` regex validation | Test boundary names (1 char, 24 chars, invalid chars) |
| `FormValidationService` | `PLAYER_NAME` regex vs actual Minecraft rules | Test names with special characters |
| All 38 interface files | No unit tests exist for any application service interface | Integration tests needed for all service implementations |

---

## Layer Boundary Check

**Verdict: FAIL — widespread Bukkit/framework coupling in application layer.**

The following files violate the hexagonal architecture rule ("application must not import Bukkit/JDBC/frameworks"):

| File | Violation |
|------|-----------|
| `PartitionModificationEvent.kt` | `org.bukkit.event.Event`, `org.bukkit.event.HandlerList` |
| `BedrockLocalizationService.kt` | `org.bukkit.entity.Player` |
| `FormCacheService.kt` | `org.geysermc.cumulus.form.Form` |
| `FormValidationService.kt` | `org.bukkit.entity.Player` |
| `GuildService.kt` | `org.bukkit.inventory.ItemStack` |
| `GuildVaultService.kt` | `org.bukkit.Location`, `org.bukkit.entity.Player`, `org.bukkit.inventory.ItemStack` |
| `MapRendererService.kt` | `org.bukkit.entity.Player`, `org.bukkit.inventory.ItemStack` |
| `PhysicalCurrencyService.kt` | `org.bukkit.inventory.ItemStack` |
| `PlatformDetectionService.kt` | `org.bukkit.entity.Player` |
| `VaultBackupService.kt` | `org.bukkit.inventory.ItemStack` |
| `apollo/LunarClientService.kt` | `com.lunarclient.apollo.player.ApolloPlayer`, `org.bukkit.entity.Player` |
| `VaultInventoryManager.kt` | `org.bukkit.Bukkit`, `org.bukkit.entity.Player`, `org.bukkit.inventory.Inventory`, `org.bukkit.inventory.ItemStack`, `java.sql.SQLException` |
| `VaultAutoSaveService.kt` | `org.bukkit.Bukkit`, `org.bukkit.plugin.java.JavaPlugin`, `org.bukkit.scheduler.BukkitTask` |
| `DiscordCsvService.kt` | `org.bukkit.Bukkit`, `org.bukkit.entity.Player` |
| `FileExportManager.kt` | `org.bukkit.Bukkit`, `org.bukkit.entity.Player`, `org.bukkit.plugin.Plugin` |
| `CsvExportService.kt` | `org.bukkit.Bukkit` (via FQN, no import) |
| `GoldBalanceButton.kt` | `org.bukkit.Material`, `org.bukkit.NamespacedKey`, `org.bukkit.inventory.ItemStack`, `org.bukkit.persistence.PersistentDataType`, `org.bukkit.plugin.java.JavaPlugin` |
| `ValuableItemChecker.kt` | `org.bukkit.Material`, `org.bukkit.inventory.ItemStack`, `org.bukkit.persistence.PersistentDataType` |

**18 of 48 files** (37.5%) violate the layer boundary. The most severe violations are in the concrete classes (`VaultInventoryManager`, `VaultAutoSaveService`, `FileExportManager`, `DiscordCsvService`, `CsvExportService`) which directly instantiate Bukkit objects. The interface files mostly violate by accepting `Player` or `ItemStack` parameters instead of domain types.

**Recommended remediation:** Define domain-level value types (`PlayerId`, `ItemRepresentation`, `BedrockForm`) in the `domain/values` package and use them in all application service interfaces. Move concrete implementations that require Bukkit to the infrastructure layer.
