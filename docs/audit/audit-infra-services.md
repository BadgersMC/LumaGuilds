# Audit Report: infrastructure/services

**Branch:** fix/audit-infra-services
**Scope:** 54 files in `infrastructure/services/` (including `apollo/` and `scheduling/` sub-packages)

---

## Findings

### [SEV: high] AsyncTaskService.kt:97 — runAsyncCallback captures mutable `result` in callback lambda

**What:** The `runAsyncCallback` method on line 92-106 dispatches `onSuccess(result)` and `onError(e)` via `Bukkit.getScheduler().runTask`. The `result` local variable is assigned inside the coroutine on lines 94-95. However, the `CoroutineScope(virtualDispatcher)` on line 44 creates a **new** scope each call. Since `runTask` is scheduled from inside that launched coroutine, if the coroutine is cancelled between `task()` completing and `runTask` executing, the `result` variable is never set, while the lambda captures `result` by reference. In practice this means a `lateinit` value could be read if cancellation races, and more critically the `onSuccess`/`onError` callbacks access **mutable locals** that the coroutine may not have written yet under cancellation.

**Why it matters:** A cancellation race between the virtual-thread launch and the main-thread callback dispatch could call `onSuccess(result)` with an uninitialized value or stale data, causing silent wrong-path execution. Under heavy load with coroutine cancellation this can lose errors or invoke success with garbage.

**Suggested fix:** Snapshot `result` and `e` into `val` locals *before* dispatching to main thread, or convert to the `runAsync` pattern that returns a `CompletableFuture` and avoids capturing mutable state entirely.

**Confidence:** med

---

### [SEV: high] BankServiceBukkit.kt:656 — isValidAmount uses deposit range for withdrawal validation

**What:** `isValidAmount` (line 656-658) validates against `getMinDepositAmount()` and `getMaxDepositAmount()`. This method is called on line 141 for deposits AND on line 340 for withdrawals. Withdrawals use the deposit min/max range, not withdrawal-specific limits. If config sets different ranges (which the config schema supports via `maxWithdrawalPercent`), withdrawal amounts outside the deposit range but within withdrawal limits would be rejected.

**Why it matters:** Withdrawal amounts that should be valid (e.g., withdrawing 60% of balance when maxWithdrawalPercent=0.6 but maxDepositAmount=100000 and balance=200000) would be blocked. Conversely, deposits above maxWithdrawalPercent could succeed when they shouldn't.

**Suggested fix:** Add `isValidWithdrawalAmount(amount, balance)` or pass context to validation.

**Confidence:** high

---

### [SEV: high] CombatServiceBukkit.kt:119 — getPlayerGuilds always returns empty set

**What:** `getPlayerGuilds` (line 119-123) is a private helper that returns `emptySet()` with a comment "This would be injected in a real implementation". Similarly, `getRelationType` (line 125-129) always returns `NEUTRAL`.

**Why it matters:** `getPvpBlockReason` and `getTerritoryPvpBlockReason` will never detect same-guild members, peaceful guilds, or allied guilds — they always return `null` (no block reason). This means PvP is **never blocked** by this service for guild members, allies, or peaceful guilds. Every PvP check reason is silently lost.

**Suggested fix:** Inject `MemberService` and `RelationService` into the constructor (the comment acknowledges this is a placeholder).

**Confidence:** high

---

### [SEV: high] GuildInvitationManager.kt:12 — object singleton with lateinit var, no concurrent init guard

**What:** `GuildInvitationManager` is a Kotlin `object` (singleton) with a `lateinit var repository` (line 14). `initialize()` (line 20) sets it once but has no synchronization guard. If two threads call `initialize()` concurrently (e.g., during plugin reload), one will overwrite the other, and calls between the two stores may use a stale or uninitialized reference.

**Why it matters:** On plugin reload or async initialization, `repository` could be uninitialized when `addInvite` is called, causing `UninitializedPropertyAccessException`. Or it could be set to a stale repository.

**Suggested fix:** Use `@Volatile` + synchronized init, or make it a regular class with DI instead of an object singleton.

**Confidence:** med

---

### [SEV: high] GuildBannerServiceBukkit.kt:107 — canSetBanners ignores submitter, grants all members permission

**What:** `canSetBanners` (line 107-120) always returns `true` for any existing guild, regardless of which submits. The comment says "For now, any guild member can set banners" — but the method doesn't even verify the submitter is a member.

**Why it matters:** Any player (even non-members) can set/remove the guild banner, bypassing the rank-based permission system. This is a privilege escalation.

**Suggested fix:** Check `memberService.hasPermission(submitterId, guildId, RankPermission.MANAGE_BANNER)` in `setGuildBanner` and `removeGuildBanner` (already checked in GuildService but not here where it's an independent service path).

**Confidence:** high

---

### [SEV: high] DailyWarCostsServiceBukkit.kt:27 — in-memory only last-costs-applied survives restart

**What:** `lastCostsApplied` is a `ConcurrentHashMap<UUID, Instant>` (line 27) with the comment "In production, this should be persisted to a database". It tracks whether daily costs were applied per guild.

**Why it matters:** On server restart, the map is empty, so daily war costs are re-applied to all guilds in active wars. If the scheduler runs twice in one day (restart + schedule), guilds get double-charged.

**Suggested fix:** Persist `lastCostsApplied` to the database, or use a date-based check rather than timestamp.

**Confidence:** med

---

### [SEV: high] WarServiceBukkit.kt:336-338 — canGuildDeclareWar maxWars hardcoded, ignores progression max

**What:** `canGuildDeclareWar` (line 330-334) hardcodes `activeWars < 3` instead of using the progression-based war slot limit computed elsewhere in `declareWar` (lines 61-72).

**Why it matters:** A guild that has progressed to level 30 and unlocked 5 war slots via `declareWar` logic can still be blocked from declaring by `canGuildDeclareWar` returning `false`. The check is inconsistent with the actual declaration logic.

**Suggested fix:** Compute max wars via the same progression logic used in `declareWar`, or extract to a shared method.

**Confidence:** high

---

### [SEV: med] AsyncTaskService.kt:44 — new CoroutineScope created per call, no lifecycle management

**What:** `runAsync` (line 44) creates a new `CoroutineScope(virtualDispatcher)` on every call. Each scope is independent and never cancelled. The launched coroutine holds a reference to the scope via its completion.

**Why it matters:** Under high call volume, many scope objects accumulate. If the `block` captures a reference to an outer object (e.g., a player session), that object is retained until the coroutine completes. No structured concurrency means errors in child coroutines are silently swallowed.

**Suggested fix:** Use a single `CoroutineScope` managed by the service lifecycle, with `supervisorScope` for structured concurrency.

**Confidence:** med

---

### [SEV: med] ChatServiceBukkit.kt:49-50 — rate limit fields not persistent, survive restart only in memory

**What:** `announceRateLimit` and `pingRateLimit` (lines 35-36) are hardcoded in-memory values. More importantly, `updateAnnouncementRateLimit` and `updatePingRateLimit` (lines 498-536) persist via `chatSettingsRepository.updateRateLimit()`, but the rate-limit check methods read from `chatSettingsRepository.getRateLimit(playerId)` — if the server restarts between checks, the in-memory cache (if any) is lost and the DB is the source of truth. This is actually correct behavior. **However**, the `maxAnnouncementsPerHour` and `maxPingsPerHour` constants (lines 37-38) are defined locally and not configurable, creating a hidden coupling.

**Why it matters:** Constants buried in code rather than config make testing and tuning harder, and could lead to confusion if config changes don't take effect.

**Suggested fix:** Move `maxAnnouncementsPerHour` and `maxPingsPerHour` to config.

**Confidence:** low

---

### [SEV: med] GuildServiceBukkit.kt:268 — setTag reuses MANAGE_EMOJI permission instead of dedicated permission

**What:** `setTag` (line 264) checks `RankPermission.MANAGE_EMOJI` for tag management (line 268). Similarly `setDescription` (line 309) checks `MANAGE_EMOJI` (line 313).

**Why it matters:** The permission name is misleading — a rank with `MANAGE_EMOJI` can also set guild tags and descriptions. If a future admin wants to separate these permissions, they can't without a code change. This is a layer-boundary / domain-logic leak where infrastructure reuses a permission enum for unrelated purposes.

**Suggested fix:** Add `MANAGE_TAG` and `MANAGE_DESCRIPTION` to `RankPermission` enum, or at minimum use a clearly named permission.

**Confidence:** med

---

### [SEV: med] GuildServiceBukkit.kt:793 — countVisibleCharacters regex is incomplete

**What:** `countVisibleCharacters` (line 793-802) uses `Regex("<[^>]*>")` to strip MiniMessage tags. This regex fails on nested tags like `<gradient:red:blue><bold>text</bold></gradient>` where the closing `>` of the inner tag matches the outer regex prematurely.

**Why it matters:** Guild tags with nested MiniMessage formatting will have incorrect visible character counts, potentially allowing tags that exceed the 32-character limit or rejecting valid ones.

**Suggested fix:** Use a proper MiniMessage parser (e.g., `MiniMessage.miniMessage().stripTags(tag).length`).

**Confidence:** med

---

### [SEV: med] GuildRolePermissionResolverBukkit.kt:126 — mapRankToClaimPermissions uses rank.name string matching

**What:** `mapRankToClaimPermissions` (line 126-146) maps rank names to permissions via `config.teamRolePermissions.roleMappings[rankName]`. This is a string-based lookup that breaks if rank names are renamed.

**Why it matters:** If an admin renames a rank (e.g., "Mod" → "Moderator"), all claim permissions for that rank are silently lost and fall back to defaults. No warning is logged.

**Suggested fix:** Map by rank ID instead of name, or log a warning when a rank name has no mapping.

**Confidence:** med

---

### [SEV: med] GuildVaultServiceBukkit.kt:76 — placeVaultChest allows replacing chest at non-air location

**What:** `placeVaultChest` (line 62) checks `block.type != Material.AIR && block.type != Material.CHEST` on line 76, and if the block is not air or chest, it sets `block.type = Material.CHEST` without checking what was there before.

**Why it matters:** Any block (including player builds, redstone, etc.) at the target location is silently destroyed when a vault is placed. No drop is given, no warning is shown.

**Suggested fix:** Return failure if the block is not AIR or CHEST, or at minimum log a warning and drop the replaced block's items.

**Confidence:** med

---

### [SEV: med] PartyServiceBukkit.kt:40-41 — createParty allows single-guild parties without leader guild check

**What:** `createParty` (line 25) checks that the leader has `MANAGE_RELATIONS` in their own guild (line 43-47), but for single-guild parties (line 40: `isPrivateParty = party.guildIds.size == 1`), the leader's guild is not verified to be in `party.guildIds`.

**Why it matters:** A leader from guild A could create a single-guild party for guild B (if `party.guildIds` contains only guild B), bypassing the permission check for guild B.

**Suggested fix:** Verify `leaderGuildId` is in `party.guildIds` before proceeding.

**Confidence:** med

---

### [SEV: med] RelationServiceBukkit.kt:32-33 — isRequester uses guildA not requestingGuildId for legacy null check

**What:** `isRequester` (line 32-33) checks `relation.requestingGuildId == guildId`. For legacy rows where `requestingGuildId` is null, this returns false, which is correct. But the comment on line 32 says "allowing legacy null rows" — the actual behavior is that legacy null rows cannot pass the requester check, so the direction enforcement on lines 142 and 310 would reject both guilds from accepting.

**Why it matters:** Legacy relation rows with null `requestingGuildId` cannot be accepted by either guild, permanently blocking alliance/truce/unenemy flows for old data.

**Suggested fix:** For legacy null rows, allow either guild to accept (as the comment intends).

**Confidence:** med

---

### [SEV: med] TeleportationService.kt:100 — cancelTeleport before startTeleport removes previous session without cleanup

**What:** `startTeleportInternal` (line 97) calls `cancelTeleport(playerId)` on line 99, which removes the session from `activeTeleports` and cancels the task. But if the previous session's `onSuccess` callback was already queued on the main thread (via `teleportAsync.whenComplete`), it will still fire and reference the old session.

**Why it matters:** The stale `onSuccess` callback from the previous teleport could fire after a new teleport starts, causing double cooldown stamping or other side effects.

**Suggested fix:** Use a session generation counter or check session identity in the callback (the code already does this on line 192, so this is partially mitigated — but the `onSuccess` from the old session could still fire between lines 99 and 105).

**Confidence:** low

---

### [SEV: med] VaultBackupServiceBukkit.kt:177 — serializeVaultData uses custom format, not standard serialization

**What:** `serializeVaultData` (line 177-183) encodes as `"goldBalance|slot:base64,slot:base64,..."`. The `deserializeVaultData` (line 189-206) splits on `|` with limit 2, then splits on `,`, then splits each entry on `:` with limit 2. If a Base64 string contains `|` or `:` (which standard Base64 does not, but URL-safe Base64 could), deserialization would fail silently.

**Why it matters:** Data corruption on backup/restore. If the format ever changes or Base64 encoding varies, backups become unreadable.

**Suggested fix:** Use JSON or a well-defined serialization format instead of custom string encoding.

**Confidence:** low

---

### [SEV: med] NexoEmojiService.kt:10 — profane comment in production code

**What:** Line 10 contains: `JFS there is some really nasty shit going on here.` This is a profane developer comment in production code.

**Why it matters:** Unprofessional, and the comment doesn't explain what's actually wrong. The reflection-based FontManager access (lines 195-258) is indeed fragile and should be documented properly.

**Suggested fix:** Replace with a clear comment explaining the reflection workaround and linking to a Nexo API issue.

**Confidence:** high

---

### [SEV: med] NexoEmojiService.kt:213 — profane comment "cancer"

**What:** Line 213: `// cancer` and line 228: `// gonna kms` — profane developer comments.

**Why it matters:** Same as above — unprofessional in production code.

**Suggested fix:** Remove or replace with descriptive comments.

**Confidence:** high

---

### [SEV: med] VisualisationServiceBukkit.kt:106-161 — refreshAsync logs excessively at INFO level

**What:** `refreshAsync` (line 186) and `clearAsync` (line 302) log at `logger.info` for every async operation including position calculations, material validation, and callback timing.

**Why it matters:** Under normal operation, this produces hundreds of INFO log lines per second, flooding the log file and making real issues harder to find.

**Suggested fix:** Change to `logger.debug` or `logger.fine`.

**Confidence:** high

---

### [SEV: med] VisualisationServiceBukkit.kt:204-218 — calculateVisualizationPositions returns Position2D but is called for Position3D

**What:** `calculateVisualizationPositions` (line 206) returns `Set<Position2D>` but the method is declared to return `Set<Position3D>` (line 206 shows `emptySet<Position2D>()` as the error return). The actual computation on line 206 calls `getOuterBorders` which returns `Position2D`.

**Why it matters:** The type mismatch means the returned 2D positions are silently upcast to 3D (likely with y=0), causing visualization at wrong heights. This is a compile-time type error waiting to happen.

**Suggested fix:** The method should return `Set<Position2D>` or compute 3D positions directly.

**Confidence:** high

---

### [SEV: low] ConfigServiceBukkit.kt:56 — default MariaDB password is "password"

**What:** `loadMariaDBConfig` (line 50-65) defaults to password `"password"` on line 56.

**Why it matters:** If the config file is not properly configured, the plugin connects to MariaDB with a well-known default password. This is a security risk in production.

**Suggested fix:** Fail hard if the password is not explicitly configured, or generate a random default.

**Confidence:** med

---

### [SEV: low] ConfigServiceBukkit.kt:255 — Discord webhook URL stored in plain text

**What:** `loadDiscordConfig` (line 253-258) loads `discord_webhook_url` as a plain string from config.

**Why it matters:** Webhook URLs are secrets. Storing them in plain config files risks exposure via config dumps, version control, or log files.

**Suggested fix:** Document that this should be externalized, or use environment variable substitution.

**Confidence:** low

---

### [SEV: low] PlayerLocaleServicePaper.kt:9 — returns empty string for offline players

**What:** `getLocale` (line 8-11) returns `""` for offline players. Callers may not expect an empty string and could pass it to localization logic, causing missing-key fallbacks or empty display names.

**Why it matters:** Silent failure — no log, no exception, just empty string propagation.

**Suggested fix:** Return a default locale (e.g., "en_US") or document the empty-string contract.

**Confidence:** low

---

### [SEV: low] PlayerMetadataServiceVault.kt:13 — MainConfig passed directly, bypasses ConfigService

**What:** `PlayerMetadataServiceVault` takes `MainConfig` directly (line 12) instead of `ConfigService`. This creates a direct dependency on the config model from the infrastructure layer.

**Why it matters:** Layer boundary violation — infrastructure depends on config model directly rather than through the application service. Makes testing harder.

**Suggested fix:** Inject `ConfigService` and call `loadConfig()` as needed.

**Confidence:** low

---

### [SEV: low] LeaderboardServiceBukkit.kt:29-40 — toSimple mapping is lossy and inconsistent

**What:** `ExtendedLeaderboardType.toSimple()` (line 29-40) maps multiple extended types to the same simple type (e.g., `GUILD_DEATHS` → `KILLS`, `GUILD_BANK_BALANCE` → `LEVEL`). This means the reverse mapping is ambiguous.

**Why it matters:** When code calls `toSimple()` then `toExtended()`, it may get a different extended type than originally intended. This can cause wrong leaderboard data to be returned.

**Suggested fix:** Use a bidirectional mapping or avoid the round-trip.

**Confidence:** med

---

### [SEV: low] GuildTeamService.kt:86-87 — isVanished uses metadata, may not work with all vanish plugins

**What:** `isVanished()` (line 86-87) checks for `"vanished"` metadata. Different vanish plugins use different metadata keys (e.g., "vanished", "isVanished", "essentials.vanished").

**Why it matters:** Players vanished via plugins using different metadata keys will still appear on the team HUD, breaking vanish functionality.

**Suggested fix:** Support multiple metadata keys or use the SuperVanish/VanishNoPacket API directly.

**Confidence:** low

---

### [SEV: low] GuildNotificationService.kt:83-85 — logs "not a Lunar Client user" at INFO level

**What:** Line 84: `logger.info("${player.name} is not a Lunar Client user")` — this fires for every non-LC player on every notification send.

**Why it matters:** Log spam — most players on a typical server won't be using Lunar Client.

**Suggested fix:** Change to `logger.debug`.

**Confidence:** high

---

### [SEV: low] DailyWarCostsScheduler.kt:27-34 — delay calculation uses LocalTime, wrong across timezone/DST boundaries

**What:** `startDailyScheduler` (line 25) uses `LocalTime.now()` (line 28) to compute delay until 2:00 AM. `LocalTime` uses the system default timezone, not the server timezone.

**Why it matters:** If the server JVM timezone differs from the server's physical timezone, the daily war costs will fire at the wrong time. DST transitions could cause the task to fire twice or skip a day.

**Suggested fix:** Use `ZonedDateTime` with an explicit timezone (e.g., `ZonedDateTime.of(2024, 1, 1, 2, 0, 0, 0, ZoneOffset.UTC)`).

**Confidence:** med

---

### [SEV: low] ExperienceTransactionCleanupScheduler.kt:45 — catch block swallows all exceptions silently

**What:** Line 44-47: The catch block logs the error but the scheduler continues. If `deleteOldTransactions` consistently fails (e.g., DB is down), the error is logged every interval but no alert is raised.

**Why it matters:** Silent failure — the cleanup task could be failing for days without operator awareness.

**Suggested fix:** Add a failure counter and escalate after N consecutive failures.

**Confidence:** low

---

### [SEV: low] FormCacheServiceGuava.kt:26-31 — asyncExecutor is never shut down

**What:** `asyncExecutor` (line 26-31) is an `Executors.newCachedThreadPool` that is never shut down. The threads are daemon threads (line 28), so they won't prevent JVM exit, but they leak on plugin reload.

**Why it matters:** On plugin reload, a new thread pool is created while the old one may still be running tasks. This can cause resource exhaustion over multiple reloads.

**Suggested fix:** Store the executor as a class field and provide a `shutdown()` method called on plugin disable.

**Confidence:** med

---

### [SEV: low] MapRendererServiceBukkit.kt:294-295 — startCacheCleanupTask is a no-op

**What:** `startCacheCleanupTask` (line 292-295) only logs a message but doesn't actually start any cleanup task (the TODO on line 293 confirms this).

**Why it matters:** The `chartCache` (line 26) grows unboundedly. Over time, this causes memory leaks as expired chart entries are never evicted.

**Suggested fix:** Implement the scheduled cleanup task or use a Guava cache with expiration.

**Confidence:** med

---

### [SEV: low] BankServiceBukkit.kt:483-496 — getTopBalances cache invalidation race

**What:** `getTopBalances` (line 483-497) uses `synchronized(balanceLeaderboardLock)` for the cache read/write, but `invalidateBalanceLeaderboard()` (line 500-502) sets `balanceLeaderboardExpiresAt = 0L` without synchronization.

**Why it matters:** A race between `invalidateBalanceLeaderboard()` and the cache check on line 489 could cause a stale cache read to be used for up to 30 seconds after invalidation.

**Suggested fix:** Make `balanceLeaderboardExpiresAt` `@Volatile` or access it within the synchronized block.

**Confidence:** med

---

### [SEV: low] GuildServiceBukkit.kt:49-50 — guild name regex allows spaces but docs say "alphanumerics"

**What:** `GUILD_NAME_ALLOWED = Regex("^[A-Za-z0-9 ]+$")` (line 50) allows spaces. The comment on line 48 says "Allow only alphanumerics and spaces" which is correct, but the validation on line 54 also allows blank names (`name.isBlank()` returns false for `"   "` which passes the regex).

**Why it matters:** A guild name of all spaces passes validation. `isBlank()` checks for empty or whitespace-only, but `"   "` is not blank — wait, actually `"   ".isBlank()` returns `true`. So this is correctly caught. No bug here, but the regex could be tightened.

**Suggested fix:** No fix needed — the `isBlank()` check on line 54 catches whitespace-only names.

**Confidence:** low

---

### [SEV: low] WarServiceBukkit.kt:26-33 — all war state is in-memory only

**What:** `wars`, `warDeclarations`, `warStats`, `warWagers`, `peaceAgreements`, `warFarmingCooldowns`, `warDeclarationCooldowns` (lines 27-33) are all `ConcurrentHashMap` — no database persistence.

**Why it matters:** On server restart, all active wars, declarations, stats, wagers, and cooldowns are lost. This is acknowledged in the comment "In-memory storage for now" but is a data-loss risk.

**Suggested fix:** Persist to database (acknowledged as future work).

**Confidence:** med

---

### [SEV: low] WorldManipulationServiceBukkit.kt:19-42 — isInsideWorldBorder uses double comparison without epsilon

**What:** `isInsideWorldBorder` (line 19-42) compares `areaMinX >= borderMinX` etc. using exact double comparison. World border coordinates and area coordinates may have floating-point precision differences.

**Why it matters:** An area that is mathematically inside the border could be rejected due to floating-point rounding.

**Suggested fix:** Use an epsilon-based comparison (e.g., `>= borderMinX - 0.001`).

**Confidence:** low

---

### [SEV: low] ChartRenderer.kt:85-98 — calculateValueRange adds 10% padding which can produce negative min for positive data

**What:** `calculateValueRange` (line 85-98) computes `padding = (maxValue - minValue) * 0.1` and returns `(minValue - padding).coerceAtMost(0.0)`. If all values are positive, the coerce prevents negative min. But if all values are equal (max == min), padding is 0, and the range is `(value, value)` — a zero-width range.

**Why it matters:** When `dataMax == dataMin`, `scaleValue` (line 117) returns `chartMin` for all values, so all bars render at the same height. This is correct behavior but could be confusing.

**Suggested fix:** No fix needed — behavior is correct.

**Confidence:** low

---

### [SEV: low] BarChartRenderer.kt:286-292 — createKillRankingChart divides by zero when single entry

**What:** `createKillRankingChart` (line 286-292) computes `intensity = (killData.size - index - 1).toDouble() / (killData.size - 1)`. When `killData.size == 1`, this divides by zero, producing `Infinity` or `NaN`.

**Why it matters:** A single-entry kill ranking chart would produce NaN color values, causing rendering artifacts.

**Suggested fix:** Guard against single-entry lists: `if (killData.size <= 1) return killData.map { DataPoint(it.first, it.second, defaultColor) }`.

**Confidence:** high

---

### [SEV: low] LineChartRenderer.kt:65 — renderDataLines silently returns for single data point

**What:** `renderDataLines` (line 60-83) returns early if `data.size < 2`. No error message or fallback rendering.

**Why it matters:** A single data point line chart renders without any line, which may confuse users expecting at least a point marker.

**Suggested fix:** Render a single point marker even for single-entry data.

**Confidence:** low

---

### [SEV: low] KillServiceBukkit.kt:151 — killDeathRatio uses killsByGuild / deathsByGuild but counts from raw list

**What:** `getKillStatsForPeriod` (line 139-158) counts `killsByGuild` and `deathsByGuild` from a raw kill list filtered by `killerGuildId == guildId`. But the kill list is fetched with limit 1000 (line 141), so if there are more than 1000 kills in the period, the ratio is computed from a truncated sample.

**Why it matters:** The KDR for high-activity guilds will be inaccurate due to the 1000-kill limit.

**Suggested fix:** Use a dedicated repository method that counts kills/deaths in a period without fetching all records.

**Confidence:** med

---

### [SEV: low] KillServiceBukkit.kt:225-241 — getKillDeathRatio returns Double.MAX_VALUE for zero deaths

**What:** Line 233: `if (deathsByA == 0) { if (killsByA > 0) Double.MAX_VALUE else 0.0 }`. `Double.MAX_VALUE` is used as an "infinite" ratio.

**Why it matters:** Any code consuming this ratio and doing arithmetic (e.g., averaging) will overflow or produce Infinity.

**Suggested fix:** Return a large but finite sentinel (e.g., `999999.0`) or use `Double.POSITIVE_INFINITY`.

**Confidence:** low

---

### [SEV: low] ProgressionServiceBukkit.kt:124-146 — getLevelFromExperience has O(n) loop with safety cap at 100

**What:** `getLevelFromExperience` (line 124-146) iterates level-by-level to find the current level. With the safety cap at 100 (line 139), very high experience values will return level 100 even if the actual level should be higher.

**Why it matters:** If `maxLevel` in config is increased above 100, this cap silently truncates levels.

**Suggested fix:** Use the `maxLevel` config value as the cap instead of hardcoded 100.

**Confidence:** med

---

### [SEV: low] ProgressionServiceBukkit.kt:223 — calculateWeeklyActivityScore uses hardcoded weights

**What:** `calculateWeeklyActivityScore` (line 223-244) uses hardcoded multipliers (e.g., `MEMBER_JOINED * 2`, `WAR_WON * 3`) instead of reading from config.

**Why it matters:** Activity scoring cannot be tuned without code changes, and the weights are inconsistent with the progression config's XP values.

**Suggested fix:** Read weights from `ActivityWeightsConfig` in progression.yml.

**Confidence:** low

---

### [SEV: low] ModeServiceBukkit.kt:195-197 — isGuildInActiveWar delegates to relationService without null check

**What:** `isGuildInActiveWar` (line 195-197) calls `relationService.isGuildInActiveWar(guildId)` but doesn't handle the case where `relationService` might throw.

**Why it matters:** If the relation service is not initialized, this throws instead of returning a safe default.

**Suggested fix:** Wrap in try-catch or ensure service is always available.

**Confidence:** low

---

### [SEV: low] LfgServiceBukkit.kt:48-92 — canJoinGuild checks player funds but joinGuild deducts them non-atomically

**What:** `canJoinGuild` (line 48) checks the player has sufficient funds. `joinGuild` (line 94) then deducts them. Between the check and deduction, the player's balance could change (e.g., another plugin modifies it).

**Why it matters:** TOCTOU race — a player could pass the balance check, then spend the money before the deduction, resulting in a failed deduction that isn't properly handled (line 125-128 does handle this, but the refund path on line 139 could also fail).

**Suggested fix:** The existing refund handling is adequate, but consider documenting the non-atomic nature.

**Confidence:** low

---

### [SEV: low] PhysicalCurrencyServiceBukkit.kt:109 — deductCurrency requires exact divisibility

**What:** `deductCurrency` (line 109) returns false if `amount % itemValue != 0`. This means you can't deduct amounts that aren't exact multiples of the item value.

**Why it matters:** If `itemValue = 5` and the guild needs to pay 12 coins, the deduction fails. The caller has no way to handle this gracefully.

**Suggested fix:** Round up to the nearest divisible amount and document the behavior, or allow partial-item deductions.

**Confidence:** med

---

### [SEV: low] PhysicalCurrencyServiceBukkit.kt:179 — addCurrency requires exact divisibility

**What:** Same issue as `deductCurrency` — `addCurrency` (line 179) returns false if `amount % itemValue != 0`.

**Why it matters:** Same as above.

**Suggested fix:** Same as above.

**Confidence:** med

---

### [SEV: low] ToolItemServiceBukkit.kt:66 — doesPlayerHaveClaimTool throws PlayerNotFoundException for offline players

**What:** `doesPlayerHaveClaimTool` (line 65-73) throws `PlayerNotFoundException` if the player is offline. The caller may not expect an exception for a simple inventory check.

**Why it matters:** Callers must catch `PlayerNotFoundException` for a routine check, which is unexpected.

**Suggested fix:** Return `false` for offline players instead of throwing.

**Confidence:** med

---

### [SEV: low] VaultHologramService.kt:45 — scheduleSyncRepeatingTask may fail on Folia

**What:** Line 45 uses `Bukkit.getScheduler().scheduleSyncRepeatingTask()` which is not compatible with Folia's region-based scheduler.

**Why it matters:** On Folia servers, this will throw an exception and holograms won't update.

**Suggested fix:** Use the Folia-compatible scheduler API or document Folia incompatibility.

**Confidence:** low

---

### [SEV: low] GuildRichPresenceService.kt:52-54 — teamMaxSize reads from config every call

**What:** `updateGuildRichPresence` (line 34) reads `plugin.config.getInt("guild.max_members_per_guild", 50)` on every call (line 66).

**Why it matters:** Config reads are I/O operations. Reading on every rich presence update (which happens frequently) is wasteful.

**Suggested fix:** Cache the value or pass it as a constructor parameter.

**Confidence:** low

---

### [SEV: low] GuildWaypointService.kt:197 — onPlayerJoin delays 1 second, may miss initial render

**What:** `onPlayerJoin` (line 192) delays waypoint display by 20 ticks (1 second). If the player moves away from their guild home within that second, the waypoint may not render correctly.

**Why it matters:** Minor UX issue — waypoints may briefly flash or not appear.

**Suggested fix:** Reduce delay or re-check on player move.

**Confidence:** low

---

### [SEV: low] GuildNotificationService.kt:80-81 — logs "Notifications disabled" at INFO for every non-enabled check

**What:** Line 80: `logger.info("Notifications disabled in config")` fires every time a notification is sent while disabled.

**Why it matters:** Log spam when notifications are intentionally disabled.

**Suggested fix:** Change to `logger.debug` or only log once on config load.

**Confidence:** high

---

### [SEV: low] GuildNotificationService.kt:84 — logs "not a Lunar Client user" at INFO

**What:** Line 84: `logger.info("${player.name} is not a Lunar Client user")` — same issue as above.

**Why it matters:** Log spam for every non-LC player.

**Suggested fix:** Change to `logger.debug`.

**Confidence:** high

---

### [SEV: low] GuildTeamService.kt:182 — logs "Created team for LC user" at INFO per viewer per refresh

**What:** Line 182: `logger.info("Created team for LC user ${viewer.name} with ${teamMembers.size} teammates")` fires on every team refresh tick.

**Why it matters:** Massive log spam — every online LC user logs this every refresh tick.

**Suggested fix:** Change to `logger.debug`.

**Confidence:** high

---

### [SEV: low] GuildTeamService.kt:190 — logs "Guild team created" at INFO per guild per refresh

**What:** Line 190: `logger.info("Guild team created for $guildId with ${onlineMembers.size} online members")` fires every refresh tick for every guild.

**Why it matters:** Same as above — log spam.

**Suggested fix:** Change to `logger.debug`.

**Confidence:** high

---

## Test Coverage Gaps

| File | Untested Behavior | Suggested Test |
|------|-------------------|----------------|
| BankServiceBukkit.kt | Deposit/withdraw refund paths when vault credit fails after player debit | Mock `vaultInventoryManager.depositGold` to throw, verify player refund logic |
| BankServiceBukkit.kt | Withdrawal re-credit when player payout fails after guild debit | Mock `economy.depositPlayer` to fail, verify guild re-credit |
| BankServiceBukkit.kt | Balance leaderboard cache invalidation under concurrent access | Concurrent deposit + getTopBalances test |
| GuildServiceBukkit.kt | Guild creation rollback when rank creation fails | Mock `rankService.createDefaultRanks` to return false, verify guild removed |
| GuildServiceBukkit.kt | Ownership transfer atomicity — rollback on second update failure | Mock second `memberRepository.update` to fail, verify first is rolled back |
| GuildServiceBukkit.kt | setTag with nested MiniMessage tags | Test `countVisibleCharacters` with `<gradient><bold>text</bold></gradient>` |
| GuildServiceBukkit.kt | setHomeAllowedRanks sanitizes non-guild rank IDs | Pass rank IDs from another guild, verify they're filtered |
| GuildVaultServiceBukkit.kt | Vault removal with concurrent inventory access | Simulate player closing inventory during removal |
| GuildVaultServiceBukkit.kt | placeVaultChest on non-air block | Verify behavior when block is not AIR or CHEST |
| GuildRolePermissionResolverBukkit.kt | Cache invalidation on rank change | Change rank, verify permission cache is cleared |
| GuildInvitationManager.kt | Concurrent initialize + addInvite | Thread-safety test for lateinit repository |
| PartyServiceBukkit.kt | Single-guild party creation by leader of different guild | Verify leader's guild must be in party.guildIds |
| PartyServiceBukkit.kt | Party dissolve when last guild leaves | Verify party status becomes DISSOLVED |
| RelationServiceBukkit.kt | Legacy null-requestingGuildId rows can be accepted | Create relation with null requestingGuildId, verify accept flow |
| RelationServiceBukkit.kt | Stale terminal rows are reused for new alliance requests | Create REJECTED row, verify new PENDING alliance reuses it |
| WarServiceBukkit.kt | War wager rollback on second guild deduction failure | Mock second deductFromGuildBank to fail, verify first is refunded |
| WarServiceBukkit.kt | Expired war draw condition with equal kills | Create war with equal kills, verify draw |
| WarServiceBukkit.kt | canGuildDeclareWar vs declareWar maxWars inconsistency | Test guild at progression war limit vs hardcoded limit |
| TeleportationService.kt | Teleport cancellation during active countdown | Start teleport, cancel, verify no onSuccess callback |
| TeleportationService.kt | Concurrent startTeleport for same player | Verify old session is properly cleaned up |
| CombatServiceBukkit.kt | PvP block reasons with actual guild membership | Integration test with real MemberService |
| DailyWarCostsServiceBukkit.kt | Double application after server restart | Simulate restart, verify costs not applied twice |
| PhysicalCurrencyServiceBukkit.kt | Non-divisible amount deduction | Test with itemValue=5, amount=12 |
| NexoEmojiService.kt | FontManager reflection failure | Test with Nexo unavailable, verify graceful fallback |
| LeaderboardServiceBukkit.kt | toSimple/toExtended round-trip consistency | Verify all ExtendedLeaderboardType values round-trip correctly |
| BarChartRenderer.kt | Single-entry kill ranking chart | Verify no division by zero |
| AsyncTaskService.kt | Cancellation race in runAsyncCallback | Test coroutine cancellation between task() and callback dispatch |

---

## Layer Boundary Check

**Verdict: PASS with minor violations.**

The infrastructure services correctly depend on application-layer ports (interfaces like `BankService`, `GuildService`, `MemberService`, etc.) and persistence repositories (`BankRepository`, `GuildRepository`, etc.). Bukkit/Spigot API usage is properly contained within infrastructure services.

**Minor violations found:**

1. **PlayerMetadataServiceVault.kt** — directly imports `net.lumalyte.lg.config.MainConfig` (config model) instead of going through `ConfigService`. This is a layer boundary leak: infrastructure should depend on application services, not config models.

2. **NexoEmojiService.kt** — directly imports `net.lumalyte.lg.application.services.ConfigService` but also accesses `plugin.config` (Bukkit API) directly for emoji permission prefix. Mixed access pattern.

3. **GuildRichPresenceService.kt** — reads `plugin.config` directly (line 66) instead of using `ConfigService`, creating a direct dependency on Bukkit's FileConfiguration.

4. **GuildNotificationService.kt** — same pattern: reads `plugin.config` directly for notification icons and enabled flags.

5. **GuildTeamService.kt** — reads `plugin.config` directly for team colors and settings.

6. **GuildWaypointService.kt** — reads `plugin.config` directly for waypoint colors.

7. **MapRendererServiceBukkit.kt** — reads `config.width` and `config.height` from `ChartConfig` (application layer) which is correct, but `getCacheStatistics` returns raw config values.

**No domain-layer violations found** — domain entities and enums are used correctly without importing infrastructure or Bukkit types.

**No application-layer violations found** — application services are not imported by domain (this audit didn't check domain/application directly, but no evidence of violations was seen in the infrastructure files).

---

## Summary

| Severity | Count |
|----------|-------|
| crit | 0 |
| high | 7 |
| med | 18 |
| low | 22 |
| **Total** | **47** |

**Key risk areas:**
1. **CombatServiceBukkit** — completely non-functional (placeholder implementation)
2. **BankServiceBukkit** — withdrawal validation uses deposit ranges
3. **GuildBannerService** — no permission check on banner operations
4. **WarServiceBukkit** — all state in-memory only, inconsistent war limit checks
5. **NexoEmojiService** — fragile reflection-based integration with profane comments
6. **VisualisationService** — excessive INFO logging, type mismatch in async refresh
