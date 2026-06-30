# Audit Report: audit-infra-edge

**Branch:** `fix/audit-infra-edge`
**Scope:** 23 files in `infrastructure/{adapters,bukkit,listeners,web,placeholders,namespaces,hidden,utilities}`
**Generated:** 2026-06-09

---

## Findings

### [SEV: med] infrastructure/web/WebApiServer.kt:89 — Timing side-channel in bearer-token comparison

**What:** `constantTimeEquals` returns `false` immediately when `a.length != b.length`, then uses a non-constant-time loop (`result or ...`) that short-circuits on the first differing character via the `or` accumulator. An attacker can determine token length and perform character-by-character timing attacks.

**Why it matters:** The bearer token is the sole authentication for the web API. A timing side-channel lets an attacker reconstruct the token one character at a time by measuring response latency, especially on a local network where the API binds to `127.0.0.1`.

**Suggested fix:** Use `java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())` or a proper HMAC-based comparison. Always compare all bytes regardless of length mismatch (pad the shorter input to the longer length before comparing).

**Confidence:** high

---

### [SEV: med] infrastructure/web/WebApiServer.kt:104 — `parseQuery` crashes on query strings with `=` in value

**What:** `parseQuery` splits on `=` and takes `pair.substring(idx + 1)` as the value. If a query parameter value itself contains `=` (e.g. `?type=level=1`), only the portion before the second `=` is kept, silently truncating the value. More critically, if a query parameter has no `=` sign (e.g. `?debug`), the pair is silently dropped via `mapNotNull`, which is acceptable but undocumented behavior.

**Why it matters:** While current callers only use simple `type`, `period`, and `limit` parameters, the handler is a public API endpoint. Future consumers may pass values with `=`, causing silent data corruption that is hard to debug.

**Suggested fix:** Use `pair.indexOf('=')` to split into key/value at the *first* `=`, and take everything after it as the value. Alternatively, use a proper URL query parser from the JDK or a library.

**Confidence:** high

---

### [SEV: med] infrastructure/listeners/ProgressionEventListener.kt:79 — `playerGuildCache` is not safely published across threads

**What:** `playerGuildCache` is a `ConcurrentHashMap` but the values are `Collections.unmodifiableSet` snapshots. The `addPlayerGuild` and `removePlayerGuild` methods use `compute`/`computeIfPresent` which are atomic for individual keys, but the cache is read from the hot path of Bukkit event handlers (main thread) and mutated from async coroutine contexts (virtual threads via `AsyncTaskService`). The `rebuildMembershipCache` method clears and repopulates the entire map, which is not atomic with respect to concurrent reads.

**Why it matters:** A race between `rebuildMembershipCache` (called from `refreshCaches`, which can be triggered by `/lumaguilds reload`) and concurrent event processing could cause a player to be missed for XP awards, or worse, a stale guild reference could be used after the player has left.

**Suggested fix:** Use a `ReadWriteLock` or copy-on-write pattern for the cache. Alternatively, make `rebuildMembershipCache` replace the map reference atomically (`@Volatile var`) and let the old references drain naturally.

**Confidence:** med

---

### [SEV: med] infrastructure/listeners/ProgressionEventListener.kt:439 — `onPlayerQuit` drains counters but doesn't flush to DB

**What:** `onPlayerQuit` drains `playerXpCounters` into `pendingGuildXp` via `enqueueGuildExperience`, but does not call `flushPendingGuildXp()`. The pending XP sits in memory until the next 5-second flush cycle. If the server stops shortly after a player quits (e.g., during a restart), that XP is lost.

**Why it matters:** XP earned just before a player disconnects can be silently lost during graceful shutdowns if `shutdown()` is not called, or if the flush cycle hasn't run yet. This is a data-loss edge case.

**Suggested fix:** Call `flushPendingGuildXp()` at the end of `onPlayerQuit`, or ensure `shutdown()` is always called on plugin disable (verify the lifecycle).

**Confidence:** med

---

### [SEV: low] infrastructure/listeners/ProgressionEventListener.kt:431 — `lunarMultiplier` uses `GlobalContext.get()` on every mob kill

**What:** `lunarMultiplier` calls `org.koin.core.context.GlobalContext.get()` on every invocation (mob kill, block break, etc.) to look up `LunarClientService`. This is a map lookup on every event, and `GlobalContext.get()` is not a cheap operation.

**Why it matters:** Mob-kill events can fire at very high rates (mob farms). The repeated `GlobalContext.get()` adds unnecessary overhead on the hot path.

**Suggested fix:** Inject `LunarClientService` (or a nullable wrapper) at construction time, or cache the lookup result in a `@Volatile` field that is refreshed on reload.

**Confidence:** high

---

### [SEV: low] infrastructure/listeners/VaultProtectionListener.kt:293 — Permission check uses `rank.permissions.contains()` instead of `MemberService.hasPermission()`

**What:** `VaultProtectionListener` fetches the player's `Rank` object and checks `rank.permissions.contains(RankPermission.BREAK_VAULT)` directly. The codebase convention (per memory) is to use `MemberService.hasPermission(playerId, guildId, RankPermission.XXX)` because guilds can create custom ranks with custom permission sets.

**Why it matters:** If a guild creates a custom rank that inherits or overrides permissions in a way not captured by the `Rank.permissions` set, this check could bypass the intended permission model. The direct check is not wrong per se, but it's inconsistent with the established pattern and could miss edge cases handled by `hasPermission()`.

**Suggested fix:** Replace with `memberService.hasPermission(player.uniqueId, guild.id, RankPermission.BREAK_VAULT)` for consistency with the rest of the codebase.

**Confidence:** med

---

### [SEV: low] infrastructure/listeners/VaultProtectionListener.kt:529 — `locationToKey` uses `location.world?.uid` which can NPE on unloaded worlds

**What:** `locationToKey` calls `location.world?.uid` which returns `null` if the world is unloaded. The resulting string `"null:x:y:z"` would collide for different unloaded worlds, and the warning system would incorrectly match across worlds.

**Why it matters:** If a player places a vault chest and the world unloads (or the location's world reference becomes null), the break-warning system would produce incorrect location keys, potentially allowing a player to bypass the two-break warning.

**Suggested fix:** Add a null check for `location.world` and return a sentinel value or throw. Use `location.world?.uid ?: "unloaded"` to avoid collisions.

**Confidence:** med

---

### [SEV: low] infrastructure/bukkit/bannerman/BannermanListeners.kt:65 — `deserializeToItemStack()` uses Java `ObjectInputStream` on untrusted data

**What:** `guild.banner?.deserializeToItemStack()` calls into `String.deserializeToItemStack()` which uses Java's `ObjectInputStream.readObject()` to deserialize a `Map<String, Any>`. The banner data is stored in the guild entity and could be set by players via the banner customization menu.

**Why it matters:** Java deserialization of untrusted data is a well-known attack vector (arbitrary code execution via crafted serialized objects). While the data flows from the database (set by the plugin itself), if any bug or SQL injection allows a player to write arbitrary banner data, this becomes a remote code execution vector.

**Suggested fix:** Replace Java serialization with a safe format (e.g., Base64-encoded NBT or JSON). At minimum, validate the deserialized map structure before use.

**Confidence:** med

---

### [SEV: low] infrastructure/bukkit/bannerman/BannermanRenderService.kt:43 — `addPassenger` failure silently drops the display

**What:** If `player.addPassenger(display)` returns `false`, the display entity is removed but the method returns without logging or retrying. The `displays` map is not updated (line 48 is skipped), so `isTracking` returns `false` but the player has no visible banner.

**Why it matters:** In edge cases (e.g., player is in a vehicle, or the entity limit is reached), the banner silently disappears with no log message, making it hard to debug player reports of missing banners.

**Suggested fix:** Add a warning log when `addPassenger` fails. Consider retrying on the next tick.

**Confidence:** high

---

### [SEV: low] infrastructure/namespaces/ItemKeys.kt:13 — Wrong namespace ID `"bell_claims"` instead of `"lumaguilds"`

**What:** `PLUGIN_NAMESPACE_ID` is set to `"bell_claims"`, which is the namespace of a different plugin. This means LumaGuilds' custom item keys (`claim_tool`, `move_tool`) collide with BellClaims' PDC namespace.

**Why it matters:** If both plugins are installed on the same server, items could be misidentified — a BellClaims claim tool would be recognized as a LumaGuilds claim tool and vice versa. This is a correctness bug that could cause item interaction issues.

**Suggested fix:** Change `PLUGIN_NAMESPACE_ID` to `"lumaguilds"`. Note: this is a breaking change for existing items in the world, so a migration strategy may be needed.

**Confidence:** high

---

### [SEV: low] infrastructure/hidden/SystemValidator.kt:47 — Easter egg logs red-text ASCII art on every startup (10% chance)

**What:** `displaySecretMemeText()` logs a large ASCII art banner in red text with `[LumaGuilds]` prefix and "Qwimble is watching" message. This fires with 10% probability on every plugin enable (startup and reload).

**Why it matters:** While not a security vulnerability, this is unprofessional behavior that pollutes server logs with meme content. Server operators may be confused or concerned by the red-text messages. The `SECRET_TRIGGER` constant (`"qwimble_watches"`) is unused dead code.

**Suggested fix:** Remove the easter egg or gate it behind a config flag. Remove the unused `SECRET_TRIGGER` constant and `maintenanceRoutine()` method.

**Confidence:** high

---

### [SEV: low] infrastructure/adapters/bukkit/BukkitLocationAdapter.kt:19 — `Position.toLocation` passes nullable `y` to non-nullable `Location` constructor

**What:** `Position.toLocation` calls `this.y?.toDouble() ?: 0.0` for the Y coordinate. When `y` is `null` (as in `Position2D`), it defaults to `0.0`, which places the location at Y=0 (bedrock level) instead of the player's actual Y position.

**Why it matters:** Any code that converts a `Position2D` to a `Location` will get a location at Y=0, which could cause entities to spawn underground or at the wrong height. The `Position` class declares `y` as `Int?`, but `Location` requires a non-null double.

**Suggested fix:** Require the caller to provide a Y coordinate, or document that `Position2D.toLocation` will always use Y=0. Consider adding a `y` parameter to the function.

**Confidence:** high

---

### [SEV: low] infrastructure/placeholders/LumaGuildsExpansion.kt:443 — `getTop` cache stampede on expiry

**What:** When the 30-second TTL expires, all concurrent PAPI placeholder requests will miss the cache and call `computeTop()` simultaneously, each scanning all guilds from the database.

**Why it matters:** Under high PAPI load (many players, scoreboard updates), this causes a thundering-herd problem where multiple threads recompute the same expensive leaderboard query at the same time.

**Suggested fix:** Use a `computeIfAbsent`-style pattern where only the first thread recomputes and others wait, or use a scheduled refresh that updates the cache before it expires.

**Confidence:** med

---

### [SEV: low] infrastructure/placeholders/LumaGuildsExpansion.kt:446 — `computeTop("level")` calls `getAllGuilds()` and then `getGuildProgression` per guild (N+1 query)

**What:** For the level leaderboard, `getAllGuilds()` fetches all guilds, then for each guild, `progressionRepository.getGuildProgression(g.id)` makes a separate database query. With 1000 guilds, this is 1001 queries every 30 seconds.

**Why it matters:** This is a classic N+1 query problem that can cause significant database load on servers with many guilds.

**Suggested fix:** Add a bulk method to `ProgressionRepository` that returns progression for all guilds in a single query, or join the guild and progression tables.

**Confidence:** high

---

### [SEV: low] infrastructure/listeners/GuildChannelCreationListener.kt:39 — Hardcoded rank name matching breaks with custom ranks

**What:** The listener finds the "Leader" rank by matching `rank.name.equals("Leader", ignoreCase = true) || rank.name.equals("Owner", ignoreCase = true)`. Guilds can create custom rank names, so the leader rank might be called "Guild Master", "Chief", etc.

**Why it matters:** If a guild renames their leader rank, the `Leader_Chat` channel will never be created, and the `Officer_Chat` regex may or may not match depending on the new name. This breaks the default channel creation feature.

**Suggested fix:** Use a flag or property on the `Rank` entity to identify the leader rank (e.g., `rank.isLeader` or `rank.priority`), rather than matching by name.

**Confidence:** high

---

### [SEV: low] infrastructure/listeners/GuildChannelCreationListener.kt:46 — Officer regex matches too broadly

**What:** The officer rank regex `(?i)(officer|admin|moderator|co-?leader|leader|owner)` matches "leader" and "owner", which are already handled by the `leaderRank` variable. This means the leader rank is included in both `officerRanks` and `leaderRank`, so the leader gets both `Officer_Chat` and `Leader_Chat` restrictions.

**Why it matters:** The leader rank is included in the `Officer_Chat` restricted roles, which is redundant but not harmful. However, if a guild has no explicit "officer" rank but does have a "leader" rank, the `Officer_Chat` channel will be created with only the leader rank, which may not be the intended behavior.

**Suggested fix:** Exclude "leader" and "owner" from the officer regex, or document that the leader rank is intentionally included in officer channels.

**Confidence:** med

---

### [SEV: low] infrastructure/utilities/LocalizationProviderProperties.kt:98 — `println` instead of logger for localization load messages

**What:** `loadLayeredProperties` uses `println()` for logging language file load success/failure instead of the plugin logger.

**Why it matters:** `println` writes to stdout, which may not be captured by server log management systems. It also bypasses the plugin's logging configuration and SLF4J logger.

**Suggested fix:** Inject a logger and use it instead of `println`.

**Confidence:** high

---

## Test Coverage Gaps

| # | File | Untested Behavior | Suggested Test |
|---|------|-------------------|----------------|
| 1 | `BannermanVisibility.kt` | Pure function `shouldShow` with all 4 combinations of elytra/invisibility | Unit test parameterized over `(hasElytra, hasInvisibility, expected)` |
| 2 | `BannermanRenderService.kt` | `sweepOrphans` correctly removes only tagged `ItemDisplay` entities | Integration test: spawn tagged/untagged displays, call sweep, verify only tagged removed |
| 3 | `BannermanListeners.kt` | `onGuildBannerChanged` despawns when banner is null, spawns when player is offline | Unit test with mock renderer verifying despawn/spawn calls |
| 4 | `ProgressionEventListener.kt` | XP batching: counter drains correctly when cooldown elapses | Unit test: award XP, advance time past cooldown, verify flush |
| 5 | `ProgressionEventListener.kt` | `onPlayerQuit` drains buffered XP into pending queue | Unit test: award XP, call onPlayerQuit, verify pendingGuildXp contains the amount |
| 6 | `ProgressionEventListener.kt` | Same-guild kill does not award XP | Unit test: killer and victim in same guild, verify no XP awarded |
| 7 | `GuildLeaderboardHandler.kt` | `parseLimit` clamps to `[1, leaderboardLimitMax]` | Unit test: pass 0, -1, null, max+1, verify clamping |
| 8 | `GuildLeaderboardHandler.kt` | `effectivePeriodFor` returns ALL_TIME for non-ACTIVITY categories | Unit test: pass WEEKLY for LEVEL category, verify ALL_TIME returned |
| 9 | `WebApiServer.kt` | `isAuthorized` rejects missing/invalid Authorization header | Unit test: no header, wrong prefix, wrong token, correct token |
| 10 | `WebApiServer.kt` | `parseQuery` handles URL-encoded values, empty values, missing `=` | Unit test: `?a=b=c`, `?a=&b`, `?flag` |
| 11 | `RoseChatCleanupListener.kt` | `shouldLeaveChannel` returns correct boolean for each channel type | Unit test: player in/out of guild, with/without allies, UUID/non-UUID channel IDs |
| 12 | `GuildChannelCreationListener.kt` | Channel creation when no leader rank exists | Unit test: guild with custom rank names, verify graceful handling |
| 13 | `VaultProtectionListener.kt` | Break warning system: first break warns, second break within timeout allows | Integration test: two break attempts, verify first cancelled, second allowed |
| 14 | `VaultProtectionListener.kt` | Piston backfire: piston breaks and drops when pushing vault chest | Integration test: piston extends toward vault, verify piston destroyed |
| 15 | `LumaGuildsExpansion.kt` | Top-N placeholder returns empty string for invalid rank/category | Unit test: rank=0, rank>25, invalid category, verify empty string |
| 16 | `WarKillTrackingListener.kt` | Kill only counted once even if multiple wars exist between guilds | Unit test: two active wars, verify only first match records the kill |

---

## Layer Boundary Check

**PASS** — No domain-layer imports from application/infrastructure detected. All 23 files correctly reside in `infrastructure` and import only from `application` (ports/services), `domain` (entities/values/events), and Bukkit/framework packages. The `BukkitLocationAdapter` correctly converts between Bukkit `Location` and domain `Position*` types. The `deserializeToItemStack` extension function in `utils/` operates on `String` → `ItemStack?` and is called from infrastructure, which is acceptable for an adapter concern.

**Minor note:** `BukkitItemStackAdapter.kt` imports from `org.bukkit.inventory.ItemStack` and `org.bukkit.persistence.PersistentDataType` — this is expected for an adapter in the infrastructure layer. No layer violation.

---

## Summary

| Severity | Count |
|----------|-------|
| crit | 0 |
| high | 0 |
| med | 4 |
| low | 12 |
| **Total** | **16** |

No critical or high-severity findings. The most impactful issues are the timing side-channel in bearer-token comparison (med), the query-string parsing bug (med), the thread-safety concern in `playerGuildCache` (med), and the XP loss on player quit (med). The remaining 12 findings are low-severity: code quality, minor correctness, and defensive-coding nits.
