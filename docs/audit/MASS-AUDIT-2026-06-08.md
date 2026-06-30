# LumaGuilds Mass Audit — Consolidated Findings (2026-06-08)

Findings-only audit of the full `net.lumalyte.lg` source tree (573 files) run as 11
Hermes coordinator jobs against `origin/main`. Each batch's raw report is in
`docs/audit/audit-<batch>.md`; this document is the triaged, de-duplicated synthesis.

**No source was modified by the audit.** Every finding below is a recommendation for
the human to action.

## How to read this

Raw total: **~253 findings** (2 crit, 35 high, 76 med, 140 low). After triage the
actionable set is much smaller. Each item is tagged:

- **✅ VERIFIED** — I read the source and confirmed the bug.
- **◻️ REPORTED** — high-confidence coordinator finding, not individually re-verified; treat as a strong lead.
- **❌ CORRECTED** — coordinator over-reported; severity downgraded or dismissed with reason.

Severity here is *my* re-rating, not the coordinator's.

---

## TIER 0 — Economy / item integrity (fix first)

These break the item/gold economy and are exploitable or data-losing in normal play.

### ❌ [DISMISSED] `interaction/menus/guild/GoldWithdrawMenu.kt:152` — reported item dupe is a FALSE POSITIVE
The coordinator claimed `convertToItems`/give-loop run unconditionally past the `-1L`
guard. **They do not** — there is a correct early `return` in the `if (newBalance == -1L)`
branch, so items are only given after a successful withdrawal. Double-click is also safe
(menu clicks are processed serially on the Bukkit main thread). No dupe. (Initially
mis-marked VERIFIED without reading source; corrected on re-review.)

### ✅ [CRIT] `interaction/menus/guild/GuildBankMenu.kt:437-510` — item loss: removes items before deposit confirmed
`handleDeposit()` removes gold items from the player inventory *before* calling
`depositGold()`. Any failure in the deposit pipeline (DB error, guild not found)
destroys the items with no balance increase. Deposit first, remove only on success.

### ✅ [HIGH] `infrastructure/persistence/guilds/GuildVaultRepositorySQLite.kt:186` — TOCTOU on gold balance
`addGoldBalance`/`subtractGoldBalance` read-modify-write the balance non-atomically.
Concurrent deposit+withdraw → lost update. Use atomic
`UPDATE … SET balance = balance + ? WHERE guild_id = ? AND balance >= ?` and check rows affected.

### ◻️ [HIGH] `infrastructure/services/BankServiceBukkit.kt:656` — withdrawals validated against deposit range
`isValidAmount` uses `min/maxDepositAmount` for both deposits (line 141) and
withdrawals (line 340), ignoring `maxWithdrawalPercent`. Valid withdrawals get
rejected and the percent cap is unenforced. Split into a withdrawal-aware validator.

---

## TIER 1 — Confirmed logic bugs (verified against source)

### ✅ [HIGH] `application/actions/claim/permission/GrantAllPlayerClaimPermissions.kt:27` — grant calls `remove()`
The loop commented "Add all permissions" calls `playerAccessRepository.remove(...)`.
**Granting all permissions actually revokes them all.** Change `remove` → `add`.

### ✅ [HIGH] `application/actions/claim/transfer/WithdrawPlayerTransferRequest.kt:10` — inverted guard
`if (playerId in claim.transferRequests.keys) return NoPendingRequest` is backwards:
when a request *exists* it bails out as "no pending request", and only reaches the
`.remove()` when there is nothing to remove. Withdraw never works.

### ✅ [HIGH] `infrastructure/services/CombatServiceBukkit.kt:119` — `getPlayerGuilds()` is a stub
Ships `return emptySet()  // placeholder`. Any combat rule keyed on guild membership
(friendly-fire, war participation) is silently inert.

### ✅ [MED] `infrastructure/persistence/guilds/LeaderboardRepositorySQLite.kt:364` — ignores `type` arg
`getLeaderboardSnapshots(type, …)` hardcodes `LeaderboardType.KILLS.name` instead of
`type.name`; every non-KILLS leaderboard snapshot query returns wrong/empty data.

### ✅ [MED] `infrastructure/persistence/guilds/GuildRepositorySQLite.kt:464,740,892` — debug `println` in prod
Three `println("DEBUG …")` blocks dump guild/vault state to console on every load/update.
Remove or route through `logger.debug`.

---

## TIER 2 — Logic bugs (now source-verified)

All re-read from source. Verdicts: ✅ confirmed real, ❌ false positive (moved to CORRECTIONS), ⬇ over-rated.

| Verdict | Sev | Location | Issue |
|---------|-----|----------|-------|
| ✅ | HIGH | `application/actions/claim/transfer/OfferPlayerTransferRequest.kt:14` | `put()` into `claim.transferRequests` but never `claimRepository.update(claim)` — request lost on restart/re-read |
| ✅ | HIGH | `application/actions/claim/transfer/AcceptTransferRequest.kt:39` | Name-uniqueness checks `getByName(claim.playerId, …)` = old owner's namespace, not the accepting `playerId`'s |
| ✅ | HIGH | `infrastructure/services/GuildBannerServiceBukkit.kt:107` | `canSetBanners(guildId)` takes no player, returns `true` for any existing guild → no per-member check |
| ✅ | HIGH | `infrastructure/services/WarServiceBukkit.kt:336` | `activeWars < 3` hardcoded, ignores progression cap (also `canPlayerManageWars` is a `return true` stub) |
| ✅ | HIGH | `interaction/commands/PartitionsCommand.kt:43` | `for (i in 0..9 + page)` — every page starts at index 0 and the window grows with page; pagination is broken |
| ✅ | HIGH | `interaction/listeners/ToolRemovalListener.kt:129` | `onPlayerDeath` builds `itemsToRemove` then the method ends — never removes from `event.drops`; claim/move tools drop on death |
| ⬇ | LOW | `infrastructure/services/GuildInvitationManager.kt:12` | `object` + `lateinit var`, no concurrent-init guard — real shape but startup-only init, negligible concurrency risk |

---

## TIER 3 — Architectural: hexagonal layer violations (systemic)

The `domain` and `application` layers import Bukkit/framework types — a broad,
consistent violation of the project's own hexagonal rules (`docs/architecture.md`).
Real, but pre-existing and systemic; address as one refactor epic, not 20 hotfixes.

- `domain/entities/Claim.kt`, `VaultInventory.kt`, `ViewerSession.kt` — Bukkit `ItemStack`/`Inventory`/`Player` in domain entities.
- `domain/entities/PlayerState.kt`, `Progression.kt`, `GuildBanner.kt` — import the **application** layer from domain (inward dependency inversion).
- `domain/events/*.kt` (13 files) — extend Bukkit `Event`/`HandlerList` in the domain layer.
- `application/persistence/GuildVaultRepository.kt` — Bukkit `ItemStack` leaked into an application port.

**Recommendation:** introduce domain-level value types (e.g. a serialized-item byte
holder) and map to Bukkit only in infrastructure. Track as a dedicated SPEAR refactor.

---

## TIER 4 — Persistence & service robustness

- ◻️ [HIGH] Inconsistent error handling: `ClaimRepositorySQLite.kt:105` and
  `ClaimPermissionRepositorySQLite.kt:84` swallow table-creation failures via
  `printStackTrace()` while sibling repos throw `DatabaseOperationException` → silent
  data loss on a failed migration. Make them throw, like the siblings.
- ◻️ [MED] Several repos (`GuildInvitationRepositorySQLite`, `MemberRepositorySQLite`)
  `catch (SQLException) { return false }` with no logging — failures indistinguishable
  from "no rows affected".
- ◻️ [MED] `GuildVaultRepositorySQLite.kt:266` — `ObjectInputStream.readObject()` fallback
  on DB bytes; add an `ObjectInputFilter` restricting classes (deserialization hardening).
- ◻️ [MED] `migrations/GuildBalanceConsolidator` — non-idempotent; a crash between
  consolidate and version-bump double-counts balances. Add a consolidated-flag guard.
- ◻️ [HIGH] `storage/VirtualThreadSQLiteStorage.kt:30` — Hikari pool size 50 against a
  single-writer SQLite; large pool + virtual threads = lock contention. Drop to ~5–10.
- ◻️ [LOW×many] Repos `catch (SQLException)` around non-SQL code (`UUID.fromString`,
  Gson, `enum.valueOf`) → the real `IllegalArgumentException` escapes uncaught.

---

## CORRECTIONS — over-reports the triage caught (why the review gate exists)

- ❌ `ProgressionRepositorySQLite.kt:285,399` "`?: 0 > 0` precedence bug" — **false positive.**
  Kotlin elvis binds tighter than comparison; `a ?: 0 > 0` parses as `(a ?: 0) > 0` (correct).
  Wouldn't compile otherwise. Dismissed.
- ❌ Both infra-persistence "crit" **SQL injections** (`SQLiteMigrations.tableExists`,
  `VaultTransactionLogger` LIMIT/OFFSET) — **downgraded to LOW.** Values are `Int`/hardcoded
  table names today; not caller-reachable strings. Worth whitelisting PRAGMA identifiers as
  defense-in-depth, but not crit.
- ❌ `PlayerStateRepositoryMemory` "no DB persistence" — **by design** (documented ephemeral).
  Re-tag as a doc note at the port, not a bug.
- ❌ `AsyncTaskService.kt:97` "captures mutable `result`" — **false.** `val result = task()` is
  immutable and freshly scoped per coroutine launch; no shared state, no race.
- ❌ `VaultAutoSaveService.kt:257` "auto-unboxing NPE" — **false.** `autoSaveTask?.isCancelled == false`
  is the null-safe idiom; `Boolean? == false` cannot NPE.
- ⚠️ `GuildWarDeclarationMenu.kt:489` "race in duplicate check" — **likely false.** Check-then-act,
  but menu clicks are serialized on the Bukkit main thread; not a real race unless `warService` goes async.

**Triage note:** across the high/crit tier, the coordinator's *logic-bug* findings were reliable
(~all confirmed), but its *concurrency / NPE* reasoning was not — it repeatedly ignored Bukkit's
single-threaded main-thread model and Kotlin's null-safe operators. Treat any "race"/"NPE"/"mutable
capture" finding from this run as suspect until verified.

---

## Test-coverage gaps (highest-value first)

The tree has 576 main files but only 32 tests. Priority gaps surfaced across batches:

| Area | Why it needs a test now |
|------|--------------------------|
| Vault deposit/withdraw (menus + service + repo) | The TIER-0 dupe/loss bugs would be caught by a round-trip test |
| `GuildVaultRepositorySQLite.add/subtractGoldBalance` | Concurrent-access test for the TOCTOU |
| `GrantAllPlayerClaimPermissions` / transfer actions | Pure logic, trivially testable, currently wrong |
| `GuildBalanceConsolidator.consolidate` | Re-run / partial-failure idempotency |
| `SQLiteMigrations.migrateToVersion2` | 10-step rebuild, no rollback test |
| `LeaderboardRepositorySQLite.getLeaderboardSnapshots` | Would catch the ignored-`type` bug |

---

## Per-batch raw reports

`docs/audit/audit-{infra-persistence, app-actions, ix-commands, domain, app-services,
infra-services, infra-edge, cross-cutting, app-results-ports, ix-menus-a, ix-menus-b}.md`.
`cross-cutting` returned clean (0 findings).

## Suggested sequencing for the human

1. **TIER 0** — economy/item bugs (dupe + loss), each a small, isolated fix + a test.
2. **TIER 1** — confirmed logic one-liners (`remove`→`add`, inverted guard, stub).
3. **TIER 4 economy-adjacent** — vault TOCTOU, error-swallowing on claim tables.
4. **TIER 2** — verify-then-fix the high-confidence leads.
5. **TIER 3** — schedule the hexagonal-purity refactor as its own SPEAR epic.
