# Audit: infrastructure/persistence

**Branch:** fix/audit-infra-persistence
**Scope:** 37 files under src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/
**Date:** 2026-06-09
**Method:** Manual line-by-line review + targeted grep for SQL-string patterns

---

## Summary

| Severity | Count |
|----------|-------|
| crit     | 2     |
| high     | 6     |
| med      | 10    |
| low      | 6     |

---

## Findings

### [SEV: crit] migrations/SQLiteMigrations.kt:427 — SQL injection in tableExists()
**What:** `tableExists()` at line 427 builds a query by string interpolation:
```kotlin
stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'")
```
`tableName` is an internal string, but one call site passes it from loop variables over known table names. More critically, the same pattern appears in `columnExists()` at line 442 where `tableName` and `columnName` are interpolated directly into a `PRAGMA table_info($tableName)` query. While the current call sites use hardcoded names, the helper is public (private to the class) and the pattern is an injection vector if ever refactored to accept user input.
**Why it matters:** If any caller ever passes a table/column name derived from user input (e.g., a guild name used as a dynamic table suffix), it would be exploitable SQL injection. The `PRAGMA table_info($tableName)` pattern is particularly dangerous because PRAGMA does not support parameterized queries in SQLite.
**Suggested fix:** Validate `tableName` and `columnName` against a strict `[A-Za-z0-9_]` whitelist before interpolation. This makes the helpers safe regardless of future callers.
**Confidence:** med

### [SEV: crit] guilds/VaultTransactionLogger.kt:167,227,311,388 — LIMIT/OFFSET SQL injection via integer-to-string interpolation
**What:** Three methods interpolate `limit` and `offset` (or just `limit`) directly into SQL strings:
- line 167: `sql += " LIMIT $limit OFFSET $offset"` in `getTransactions()`
- line 227: `sql += " LIMIT $limit OFFSET $offset"` in `getPlayerTransactions()`
- line 311: `LIMIT $limit` in `getRecentTransactions()`
- line 388: `LIMIT $limit` in `getTransactionsByType()`
While these are `Int` parameters today (so injection via content is impossible today), the pattern is fragile: if signature ever changes to `String` for raw SQL composition, or if the values come from a nullable `Int?` without a null guard, unparameterized LIMIT is a well-known SQL injection vector class.
**Why it matters:** Integer-to-string interpolation in SQL is an anti-pattern that static analyzers flag. If any caller passes a crafted value, it could manipulate query execution.
**Suggested fix:** Use `LIMIT ? OFFSET ?` with `PreparedStatement` parameters for the dynamic-limit queries. For fixed-limit cases (lines 311, 388), keep as-is but add a bounds check or keep the parameterized form for consistency.
**Confidence:** med

### [SEV: high] storage/VirtualThreadSQLiteStorage.kt:30 — Excessive connection pool size (50) with no backpressure
**What:** `VirtualThreadSQLiteStorage` creates a HikariCP pool with `maxConnections(50)`. SQLite is a single-writer database — having 50 concurrent connections means 49 threads will block waiting for the write lock. Combined with virtual threads (which are cheap to create), this can lead to massive contention and OOM under load.
**Why it matters:** Virtual threads + SQLite + large pool = likely deadlock or severe degradation under concurrent load. The class javadoc claims "handles thousands of concurrent database queries" but SQLite can only serialize writes.
**Suggested fix:** Reduce `maxConnections` to a small number (e.g., 5-10) or use a single-writer queue pattern. The pool should reflect SQLite's actual concurrency model.
**Confidence:** high

### [SEV: high] guilds/GuildRepositorySQLite.kt:464-467,740-742,892-893 — Debug println left in production
**What:** Three `println("DEBUG [GuildRepositorySQLite] ...")` blocks at lines 464-467, 740-742, and 892-893 log internal vault state to stdout. These were clearly developer debugging aids.
**Why it matters:** Information disclosure — guild names, vault status, chest locations, and row-update counts are dumped to the server console on every guild load and update. Any player with console access (or log access) can read this data.
**Suggested fix:** Remove all debug println blocks. Replace with `logger.debug(...)` if the information is needed for troubleshooting.
**Confidence:** high

### [SEV: high] guilds/GuildRepositorySQLite.kt:97-120 — checkColumnExists runs on every repository init
**What:** `checkColumnExists()` performs a JDBC query (INFORMATION_SCHEMA or PRAGMA) for each of 6 columns. These are wrapped in `lazy` delegates, so they run once per column per repository instance. More critically, the INFORMATION_SCHEMA query at line 97-103 uses string interpolation for `tableName` and `columnName` parameters:
```kotlin
"WHERE TABLE_NAME = ? AND COLUMN_NAME = ?"  // uses params — OK
```
But the fallback SQLite path at line 107 uses `PRAGMA table_info($tableName)` with interpolation. If `tableName` were ever non-hardcoded, this would be injection (same class of issue as SQLiteMigrations.tableExists).
**Why it matters:** The PRAGMA path is only taken when INFORMATION_SCHEMA fails (i.e., on SQLite). The tableName is hardcoded "guilds" today, so not exploitable, but the pattern is fragile.
**Suggested fix:** Hardcode the PRAGMA query or validate tableName against a whitelist before interpolation.
**Confidence:** med

### [SEV: high] claims/ClaimPermissionRepositorySQLite.kt:84,102-103 — Silent error swallowing via printStackTrace()
**What:** `createTable()` (line 84) catches `SQLException` and calls `error.printStackTrace()`. `preload()` (line 102-103 in the same class) catches `IllegalArgumentException` but the outer `createTable()` failure means the table doesn't exist, yet the class continues to operate with an empty in-memory cache.
**Why it matters:** If table creation fails, all subsequent DB operations will also fail silently (catching and returning null/empty). The plugin starts "successfully" but no data is persisted, leading to data loss.
**Suggested fix:** Throw `DatabaseOperationException` like all other claim repositories do, rather than silently swallowing.
**Confidence:** high

### [SEV: high] claims/ClaimRepositorySQLite.kt:105-106 — Silent error swallowing in createClaimTable()
**What:** Same pattern as ClaimPermissionRepositorySQLite — `createClaimTable()` catches `SQLException` and calls `error.printStackTrace()`, allowing the repository to start with a non-existent table.
**Why it matters:** Data loss — all claim persistence silently fails. The in-memory cache works until server restart, then all claims are gone.
**Suggested fix:** Throw `DatabaseOperationException` (as `ClaimFlagRepositorySQLite` and `PlayerAccessRepositorySQLite` correctly do).
**Confidence:** high

### [SEV: high] guilds/GuildVaultRepositorySQLite.kt:266-280 — Unsafe Java ObjectInputStream deserialization fallback
**What:** `deserializeItem()` at line 267-280 falls back to `ObjectInputStream.readObject()` on arbitrary byte data from the database. While this is a legacy format handler, it means the application accepts Java-serialized objects from the DB and deserializes them without any class filtering.
**Why it matters:** If an attacker can write to the (local) SQLite file (e.g., via a separate file-write vulnerability, or if the DB file is exposed), they could inject a Java deserialization gadget chain. This is a well-known remote code execution vector. The `deserializeItem()` caller gets results from the DB, which is populated by Bukkit's `ItemStack.serializeAsBytes()` — so the data path is internally controlled. But the fallback path accepts arbitrary bytes.
**Suggested fix:** Replace `ObjectInputStream` with `ObjectInputFilter` (Java 9+) restricting deserialization to `ItemStack` and `Map<String,?>` classes only. Or better, remove the legacy path entirely after a migration window.
**Confidence:** med

### [SEV: med] migrations/GuildBalanceConsolidator.kt:63-64 — Non-idempotent migration: ledger sum can double-count
**What:** The consolidation is documented as "NOT safe to run twice" and guarded by schema version v21->v22. However, if the schema version is manually reset (e.g., a DBA fixes a migration issue), or if the version bump at `SQLiteMigrations.updateDatabaseVersion(22)` fails after `consolidate()` has already run, re-running would add ledger amounts a second time on top of already-folded balances.
**Why it matters:** Double-counting inflates guild gold balances. This is a data-integrity bug that would be very hard to detect after the fact.
**Suggested fix:** Add a counter-guard: after consolidation, set a flag column (e.g., `balance_consolidated INTEGER DEFAULT 1` on `guilds`) and check it before folding. Or, after folding, truncate the `bank_transactions` table (though this loses audit history).
**Confidence:** high

### [SEV: med] guilds/GuildInvitationRepositorySQLite.kt:80,102,115 — Silent catch returning false hides DB failures
**What:** Three catch blocks in `add()`, `remove()`, and `removeAllForPlayer()` catch `SQLException` and return `false` without logging. This makes debugging DB issues very difficult.
**Why it matters:** Failed operations are indistinguishable from "no rows affected" failures. No audit trail for debugging.
**Suggested fix:** Log the exception at warn/error level before returning false, or throw `DatabaseOperationException` as other repositories do.
**Confidence:** high

### [SEV: med] guilds/MemberRepositorySQLite.kt:107,129,143,157 — Silent catch returning false hides DB failures
**What:** Same pattern as GuildInvitationRepository — 4 catch blocks silently return false on SQLException.
**Why it matters:** Data inconsistency between in-memory cache and DB is hard to diagnose.
**Suggested fix:** Log exceptions or throw DatabaseOperationException.
**Confidence:** high

### [SEV: med] players/PlayerStateRepositoryMemory.kt:13-62 — Memory-only persistence with no DB fallback
**What:** This is an in-memory-only implementation of `PlayerStateRepository`. The class javadoc at line 11 explicitly states "player states are lost on server restart." There is no corresponding DB implementation in the persistence tier.
**Why it matters:** Any player state (location, preferences, etc.) tracked by this repository is ephemeral. If the feature is meant to persist, this is a correctness gap. If it's intended to be ephemeral, the class name/repository pattern is misleading and should be documented at the port level.
**Suggested fix:** Either implement a DB-backed version (PlayerStateRepositorySQLite) or clearly document at the application port interface that this is intentionally ephemeral.
**Confidence:** high

### [SEV: med] guilds/GuildVaultRepositorySQLite.kt:186-215 — TOCTOU race in addGoldBalance/subtractGoldBalance
**What:** `addGoldBalance()` reads balance (line 189), computes new balance in memory (line 190), then writes (line 192). Between the read and write, another thread can modify the balance. No optimistic locking or atomic UPDATE is used.
**Why it matters:** Concurrent deposits/withdrawals can lose updates. Two concurrent reads of balance=100, one adds 50 (->150), one subtracts 30 (->70). Last writer wins: final balance is either 150 or 70, not 120.
**Suggested fix:** Use an atomic `UPDATE vault_gold SET balance = balance + ?, last_modified = ? WHERE guild_id = ?` query. For subtract, add a `balance >= ?` guard in the WHERE clause and check rows affected.
**Confidence:** high

### [SEV: med] guilds/LeaderboardRepositorySQLite.kt:364 — getLeaderboardSnapshots uses hardcoded KILLS filter instead of parameter
**What:** Line 364: `storage.connection.getResults(sql, LeaderboardType.KILLS.name, period.name, limit)` — the `type` parameter from the function signature (`type: ExtendedLeaderboardType`) is ignored; `LeaderboardType.KILLS.name` is used instead.
**Why it matters:** The method ignores its `type` argument and always queries KILLS type snapshots. Other leaderboard type snapshots are unreachable.
**Suggested fix:** Use `type.name` instead of `LeaderboardType.KILLS.name`.
**Confidence:** high

### [SEV: med] guilds/ProgressionRepositorySQLite.kt:285,399 — Operator precedence bug in createDefaultProgressionIfNotExists
**What:** Line 285: `val exists = result?.getInt("count") ?: 0 > 0` — due to Kotlin operator precedence, `?: 0 > 0` is parsed as `?: (0 > 0)` which is `?: false`. So `exists` is always `true` when `result` is non-null, regardless of the actual count.
**Why it matters:** Non-existent progressions are never auto-created from DB. The method returns `null` when the guild doesn't have progression data.
**Suggested fix:** Add parentheses: `val exists = (result?.getInt("count") ?: 0) > 0`. Same bug at line 399.
**Confidence:** high

### [SEV: med] migrations/MigrationVerifier.kt:81 — Uses PRAGMA for SQLite but class is named MigrationVerifier (MariaDB-specific naming)
**What:** `MigrationVerifier.columnExists()` at line 81 runs `PRAGMA table_info(guilds)`. If this is used against MariaDB (as the name "MigrationVerifier" suggests), PRAGMA is not valid MySQL syntax. The `autoRepairSchema()` method at line 146 runs raw ALTER TABLE via `execute()` which is fine for both, but the verification itself would always fail on MariaDB.
**Why it matters:** On MariaDB, `verifyGuildsTableSchema()` would always report all columns missing and attempt auto-repair every startup, adding unnecessary ALTER TABLE statements to the migration flow.
**Suggested fix:** Detect DB type (SQLite vs MariaDB) and use the appropriate column-existence check for each.
**Confidence:** med

### [SEV: low] guilds/GuildBannerRepositorySQLite.kt:208,333,405,416 — catch(e: SQLException) on non-SQL exceptions
**What:** Multiple catch blocks in `GuildBannerRepositorySQLite` and `ProgressionRepositorySQLite` catch `SQLException` on operations that cannot throw it (e.g., `UUID.fromString()`, `Instant.parse()`, `PerkType.valueOf()`). These should catch `IllegalArgumentException` or a general `Exception`.
**Why it matters:** If UUID parsing fails with `IllegalArgumentException`, the exception propagates up uncaught instead of being handled gracefully. The `SQLException` catch block gives a false impression of what's being handled.
**Suggested fix:** Catch the specific exception types that can actually be thrown by the wrapped code.
**Confidence:** high

### [SEV: low] guilds/PartyRepositorySQLite.kt:252,271 — catch(e: SQLException) on Gson parsing
**What:** `parseMutedPlayersJson()` and `parseBannedPlayersJson()` at lines 252 and 271 catch `SQLException` but the wrapped code uses `gson.fromJson()` which throws `JsonSyntaxException` (an `IllegalArgumentException` subclass).
**Why it matters:** JSON parse failures propagate uncaught instead of returning the graceful empty map.
**Suggested fix:** Catch `IllegalArgumentException` or `JsonSyntaxException` instead of `SQLException`.
**Confidence:** high

### [SEV: low] guilds/RankRepositorySQLite.kt:186-187 — swapPriorities uses Int.MAX_VALUE sentinel in UNIQUE column
**What:** The `swapPriorities` method temporarily sets a rank's priority to `Int.MAX_VALUE` as a sentinel. If the process crashes between the first and second UPDATE, the rank is left with `Int.MAX_VALUE` priority, which could break `getHighestRank()`/`getDefaultRank()` semantics.
**Why it matters:** Crash corruption — a crash during the 3-step swap leaves the DB in an inconsistent state.
**Suggested fix:** Use a two-phase swap without sentinel: read both priorities in memory, then execute both UPDATEs in a single transaction and check all rows affected.
**Confidence:** med

### [SEV: low] guilds/KillRepositorySQLite.kt:186-191 — columnExists via SELECT swallows errors
**What:** `columnExists()` at line 186 tries `SELECT $columnName FROM kills LIMIT 1`. If the column doesn't exist, this throws `SQLException` which is caught and returns `false`. But the exception message is lost. This is a wasteful way to check column existence.
**Why it matters:** Not a bug per se, but the pattern is fragile — any error (including transient ones like locked DB) is interpreted as "column doesn't exist."
**Suggested fix:** Use `PRAGMA table_info(kills)` like other repositories do for SQLite.
**Confidence:** low

### [SEV: low] storage/MariaDBStorage.kt:50 — Credentials passed through constructor with no encryption
**What:** `MariaDBStorage` takes a plaintext `password: String` in its constructor. This is standard for JDBC connection pooling, but worth noting that the credential lifecycle is entirely in-memory with no protection.
**Why it matters:** If the process memory is dumped (e.g., via a heap dump or /proc/pid/mem), the password is visible in plaintext.
**Suggested fix:** Consider using a configuration provider that supports encrypted values or short-lived tokens. This is a defense-in-depth suggestion, not a critical issue.
**Confidence:** low

---

## Test Coverage Gaps

| Repository | Method | Risk |
|---|---|---|
| `GuildRepositorySQLite` | `add()` / `update()` — 4-branch SQL variants based on column flags | Very high: each branch produces a different SQL statement. Only one path is exercised per run, and manually testing all 4 requires specific migration states. |
| `GuildBalanceConsolidator` | `consolidate()` | High: the only data-migration orchestrator. Needs unit tests for: already-consolidated re-run guard, vault_gold missing, bank_transactions empty. |
| `RankRepositorySQLite` | `swapPriorities()` | High: 3-step transaction with crash-recovery semantics. |
| `GuildVaultRepositorySQLite` | `addGoldBalance()` / `subtractGoldBalance()` | High: TOCTOU race, but no concurrent-access test exists. |
| `ClaimFlagRepositorySQLite` | `preload()` with mixed valid/invalid enum values | Medium: catch(IllegalArgumentException) silently skips bad rows. |
| `KillRepositorySQLite` | `parseRecentKills()` / `serializeRecentKills()` | Medium: custom JSON parsing without a real JSON library; edge cases untested. |
| `LeaderboardRepositorySQLite` | `getLeaderboardSnapshots()` | High: the `type` parameter bug means this returns wrong results unless tested. |
| `ProgressionRepositorySQLite` | `createDefaultProgressionIfNotExists()` / `createDefaultActivityMetricsIfNotExists()` | High: the `?: 0 > 0` precedence bug means this method never returns a default. |
| `MigrationVerifier` | `verifyGuildsTableSchema()` against MariaDB | Medium: PRAGMA is invalid MySQL syntax; would fail silently. |
| `SQLiteMigrations` | `migrateToVersion2()` — 10-step schema rebuild | Very high: complex multi-step migration with no rollback test. |

---

## Layer Boundary Check

No business/domain logic leaked into the persistence layer. All 37 files strictly use JDBC (via IDB) and implement application-defined port interfaces. The `VaultTransactionLogger` defines its own `VaultTransaction` data class and `VaultTransactionType` enum in the same file, but these are infrastructure-level DTOs, not domain entities — acceptable.

---

## Static Analysis

`detekt` is not configured as a Gradle plugin in this project (no `io.gitlab.arturbosch.detekt` plugin declaration, no `detekt` task available). No detekt findings to fold in.
