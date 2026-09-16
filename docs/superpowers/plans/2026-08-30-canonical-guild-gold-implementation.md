# Canonical Guild Gold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every guild raw-gold credit, debit, personal-account transfer, physical-item exchange, interest award, admin adjustment, and system purchase through one capacity-aware, idempotent, audited application pipeline backed by `vault_gold.balance`.

**Architecture:** Add pure domain policy/result types and an application-owned `GuildGoldService` whose persistence and external-transfer dependencies are ports. A transactional SQL adapter owns canonical balance mutation, idempotency, withdrawal usage, freeze state, and audit writes; Vault Economy and Bukkit inventory remain infrastructure adapters. Existing `BankService` stays as a compatibility facade while all direct `VaultInventoryManager.depositGold`/`withdrawGold` callers are removed.

**Tech Stack:** Kotlin, Java 21, Paper API, Vault Economy API, Koin, Xeflect database abstraction, SQLite/MariaDB migrations, JUnit 5, MockK, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md`

## Global Constraints

- Follow SPEAR in every task: read REQ-009/REQ-054/REQ-092/REQ-093, write the failing test, implement the minimum change, verify architecture, refactor, and rerun.
- `vault_gold.balance` is the only balance authority; `guilds.bank_balance` and ledgers are never read as balances.
- One unit equals one configured base currency item; shipped `RAW_GOLD_BLOCK` contributes nine shipped `RAW_GOLD` units.
- Effective capacity is `min(bank.max_bank_balance, current-run tier capacity + permanent bank-capacity benefits)`.
- Every route enforces amount limits, capacity, permissions, fees, withdrawal percentage/daily limit, suspicious threshold/freeze, idempotency, and audit policy as applicable.
- Player-request `amount` is the net guild credit or player payout: deposits collect `amount + depositFee`, and withdrawals debit `amount + withdrawalFee`.
- Missing Vault Economy disables only personal-account transfers.
- Ordinary `vault_slots` capacity remains independent of numeric guild-gold capacity.
- Domain imports no application, infrastructure, Bukkit, Vault, database, scheduler, menu, or PlaceholderAPI types; application imports only domain plus application ports.
- Preserve the public `BankService` interface during this slice unless a compile-proven caller requires an additive overload.
- Use `Long` inside the new gold domain and persistence boundary; reject values that cannot be represented by legacy `Int` facade methods.

---

## File Structure

### New files

- `src/main/kotlin/net/lumalyte/lg/domain/gold/GuildGold.kt` — amounts, routes, policy, mutation commands, results, rejection reasons, and pure fee/capacity calculations.
- `src/main/kotlin/net/lumalyte/lg/application/persistence/GuildGoldRepository.kt` — atomic canonical-balance mutation/read contract.
- `src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt` — policy validation, transfer ordering, compensation, and application API.
- `src/main/kotlin/net/lumalyte/lg/application/services/PersonalEconomyPort.kt` — personal-account availability/debit/credit port.
- `src/main/kotlin/net/lumalyte/lg/application/services/PhysicalGoldPort.kt` — inventory reservation/commit/restore port using opaque reservation IDs, never `ItemStack`.
- `src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldAuthorizationPort.kt` — rank authorization for player deposits and withdrawals.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildGoldRepositorySQL.kt` — SQLite/MariaDB-compatible transaction adapter for `vault_gold` and gold-operation tables.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/VaultPersonalEconomyAdapter.kt` — Vault Economy adapter.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/BukkitPhysicalGoldAdapter.kt` — Bukkit inventory reservation adapter.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldAuthorizationAdapter.kt` — existing guild-rank permission adapter.
- `src/test/kotlin/net/lumalyte/lg/domain/gold/GuildGoldPolicyTest.kt` — pure policy boundaries.
- `src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldServiceTest.kt` — validation and internal mutations.
- `src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldPersonalTransferTest.kt` — personal transfer ordering/compensation.
- `src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldPhysicalTransferTest.kt` — physical reservation ordering/compensation.
- `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildGoldRepositorySQLTest.kt` — transactional repository contract.

### Modified files

- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkit.kt` — compatibility facade delegating mutations to `GuildGoldService`.
- `src/main/kotlin/net/lumalyte/lg/application/services/BankService.kt` — additive transaction-ID overloads for retry-safe internal credits/debits.
- `src/main/kotlin/net/lumalyte/lg/application/services/BankAutomationService.kt` — interest uses stable period IDs and canonical credit results.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/VaultInventoryManager.kt` — retain reads/cache broadcasts; remove public mutation ownership.
- `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldDepositMenu.kt`, `GoldWithdrawMenu.kt`, `GuildBankMenu.kt` — call the application facade only.
- `src/main/kotlin/net/lumalyte/lg/interaction/listeners/VaultInventoryListener.kt` — reserve physical currency through the application route.
- `src/main/kotlin/net/lumalyte/lg/interaction/commands/admin/BankCreditCommand.kt` — audited idempotent admin credit.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/PhysicalCurrencyServiceBukkit.kt` — system costs use canonical debit/credit rather than direct inventory/balance mutation.
- `src/main/kotlin/net/lumalyte/lg/config/MainConfig.kt`, `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt`, and `src/main/resources/config.yml` — valid BOTH mode, provider-optional behavior, and policy defaults.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt` and `MariaDBMigrations.kt` — operation/idempotency, withdrawal-usage, freeze, and audit schema.
- `src/main/kotlin/net/lumalyte/lg/di/Modules.kt` — bind ports, adapters, repository, and service.

---

### Task 1: Pure guild-gold policy model

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/domain/gold/GuildGold.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/domain/gold/GuildGoldPolicyTest.kt`

**Interfaces:**
- Consumes: no application or infrastructure types.
- Produces: `GuildGoldPolicy`, `GuildGoldRoute`, `GuildGoldDirection`, `GuildGoldMutation`, `GuildGoldResult`, `GuildGoldRejection`, and `GuildGoldCalculator`.

- [ ] **Step 1: Write the failing policy tests**

```kotlin
class GuildGoldPolicyTest {
    private val policy = GuildGoldPolicy(
        minDeposit = 1, maxDeposit = 100_000, withdrawalPercent = 0.5,
        dailyWithdrawalLimit = 50_000, depositFeePercent = 0.01,
        withdrawalFeePercent = 0.02, maxDepositFee = 128,
        maxWithdrawalFee = 15, globalCapacity = 1_000_000,
        suspiciousThreshold = 50_000, autoFreezeSuspicious = false
    )

    @Test fun `effective capacity uses lower tier capacity`() =
        assertEquals(20_000, GuildGoldCalculator.effectiveCapacity(policy, 20_000, 5_000))

    @Test fun `effective capacity includes permanent benefit but never crosses global ceiling`() =
        assertEquals(1_000_000, GuildGoldCalculator.effectiveCapacity(policy, 990_000, 50_000))

    @Test fun `fees round down and respect configured caps`() {
        assertEquals(128, GuildGoldCalculator.depositFee(policy, 100_000))
        assertEquals(15, GuildGoldCalculator.withdrawalFee(policy, 10_000))
    }
}
```

- [ ] **Step 2: Run the focused test and verify red**

Run: `./gradlew test --tests "net.lumalyte.lg.domain.gold.GuildGoldPolicyTest" --no-daemon --console=plain`

Expected: compilation fails because the `domain.gold` types do not exist.

- [ ] **Step 3: Implement the domain API**

```kotlin
@JvmInline value class GuildGoldAmount(val value: Long) { init { require(value >= 0) } }
enum class GuildGoldRoute { PERSONAL_ACCOUNT, PHYSICAL_ITEM, INTEREST, ADMIN, SYSTEM }
enum class GuildGoldDirection { CREDIT, DEBIT }

data class GuildGoldPolicy(
    val minDeposit: Long, val maxDeposit: Long, val withdrawalPercent: Double,
    val dailyWithdrawalLimit: Long, val depositFeePercent: Double,
    val withdrawalFeePercent: Double, val maxDepositFee: Long,
    val maxWithdrawalFee: Long, val globalCapacity: Long,
    val suspiciousThreshold: Long, val autoFreezeSuspicious: Boolean
)

data class GuildGoldMutation(
    val transactionId: UUID, val guildId: UUID, val actorId: UUID,
    val route: GuildGoldRoute, val direction: GuildGoldDirection,
    val amount: Long, val fee: Long, val description: String
)

data class GuildGoldCapacity(val tier: Long, val permanent: Long)
enum class GuildGoldRejection {
    INVALID_AMOUNT, CAPACITY_EXCEEDED, INSUFFICIENT_FUNDS, DAILY_LIMIT,
    WITHDRAWAL_PERCENT, FROZEN, SUSPICIOUS_FROZEN, EXTERNAL_UNAVAILABLE,
    EXTERNAL_REJECTED, DUPLICATE_PENDING
}
enum class GuildGoldOperationStatus { PREPARED, APPLIED, REJECTED, COMPENSATED, FAILED_COMPENSATION }
data class GuildGoldOperationRecord(
    val mutation: GuildGoldMutation, val status: GuildGoldOperationStatus,
    val oldBalance: Long?, val newBalance: Long?
)
sealed interface GuildGoldPreparation {
    data class New(val record: GuildGoldOperationRecord) : GuildGoldPreparation
    data class Existing(val record: GuildGoldOperationRecord) : GuildGoldPreparation
    data object FingerprintMismatch : GuildGoldPreparation
}

sealed interface GuildGoldResult {
    data class Applied(val transactionId: UUID, val oldBalance: Long, val newBalance: Long, val fee: Long) : GuildGoldResult
    data class Rejected(val reason: GuildGoldRejection) : GuildGoldResult
    data class Failed(val transactionId: UUID, val compensationSucceeded: Boolean) : GuildGoldResult
}
```

Implement `GuildGoldCalculator` with overflow-safe `Long` arithmetic, floor-rounded percentage fees, `min(global, tier + permanent)` capacity, and maximum-withdrawal calculation from balance, percentage, and remaining daily allowance.

- [ ] **Step 4: Run domain tests and architecture checks**

Run: `./gradlew test --tests "net.lumalyte.lg.domain.gold.*" --tests "net.lumalyte.lg.architecture.*" --no-daemon --console=plain`

Expected: PASS; no import from `org.bukkit`, `net.milkbowl.vault`, application, or infrastructure exists in `domain/gold`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/domain/gold src/test/kotlin/net/lumalyte/lg/domain/gold
git commit -m "feat(gold): add transaction policy model"
```

### Task 2: Atomic canonical repository and schema

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/persistence/GuildGoldRepository.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildGoldRepositorySQL.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/MariaDBMigrations.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildGoldRepositorySQLTest.kt`

**Interfaces:**
- Consumes: `GuildGoldMutation` from Task 1.
- Produces: `GuildGoldRepository.getBalance`, `getTopBalances`, `apply`, `getDailyWithdrawn`, `isFrozen`, and `setFrozen`.

- [ ] **Step 1: Write repository contract tests for both configured database engines**

```kotlin
interface GuildGoldRepositoryContract {
    fun repository(): GuildGoldRepository

    fun mutation(id: UUID, direction: GuildGoldDirection, amount: Long) = GuildGoldMutation(
        transactionId = id, guildId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        actorId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        route = GuildGoldRoute.SYSTEM, direction = direction,
        amount = amount, fee = 0, description = "contract test"
    )

    @Test fun `duplicate transaction id returns original result without applying twice`() {
        val repo = repository()
        val id = UUID.randomUUID()
        val first = repo.apply(mutation(id, GuildGoldDirection.CREDIT, 600), 1_000, null)
        val second = repo.apply(mutation(id, GuildGoldDirection.CREDIT, 600), 1_000, null)
        assertEquals(first, second)
        assertEquals(600, repo.getBalance(UUID.fromString("00000000-0000-0000-0000-000000000001")))
    }

    @Test fun `concurrent credits cannot cross capacity`() {
        val repo = repository()
        val pool = Executors.newFixedThreadPool(2)
        val results = listOf(UUID.randomUUID(), UUID.randomUUID()).map { id ->
            pool.submit<GuildGoldResult> { repo.apply(mutation(id, GuildGoldDirection.CREDIT, 600), 1_000, null) }
        }.map { it.get(5, TimeUnit.SECONDS) }
        pool.shutdownNow()
        assertEquals(1, results.count { it is GuildGoldResult.Applied })
        assertEquals(1, results.count { it == GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED) })
    }

    @Test fun `debit balance usage and audit commit together`() {
        val repo = repository()
        val guildId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        repo.apply(mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 800), 1_000, null)
        val result = repo.apply(mutation(UUID.randomUUID(), GuildGoldDirection.DEBIT, 200), 1_000, 1_788_048_000_000)
        assertTrue(result is GuildGoldResult.Applied)
        assertEquals(600, repo.getBalance(guildId))
        assertEquals(200, repo.getDailyWithdrawn(guildId, 1_788_048_000_000))
    }

    @Test fun `insufficient debit leaves balance and daily usage unchanged`() {
        val repo = repository()
        val guildId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val result = repo.apply(mutation(UUID.randomUUID(), GuildGoldDirection.DEBIT, 200), 1_000, 1_788_048_000_000)
        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.INSUFFICIENT_FUNDS), result)
        assertEquals(0, repo.getBalance(guildId))
        assertEquals(0, repo.getDailyWithdrawn(guildId, 1_788_048_000_000))
    }
}
```

Create concrete SQLite and MariaDB test classes using the repository's existing test-storage fixtures. MariaDB tests may use the established test-container/profile guard, but must execute in CI where the existing MariaDB contract suite executes.

- [ ] **Step 2: Run the contract test and verify red**

Run: `./gradlew test --tests "*GuildGoldRepositorySQLTest*" --no-daemon --console=plain`

Expected: compilation fails because `GuildGoldRepository` and its SQL adapter do not exist.

- [ ] **Step 3: Define the persistence contract**

```kotlin
interface GuildGoldRepository {
    fun getBalance(guildId: UUID): Long
    fun getTopBalances(limit: Int): List<Pair<UUID, Long>>
    fun getDailyWithdrawn(guildId: UUID, periodStartEpochMs: Long): Long
    fun isFrozen(guildId: UUID): Boolean
    fun setFrozen(guildId: UUID, frozen: Boolean, actorId: UUID, reason: String): Boolean
    fun prepare(mutation: GuildGoldMutation): GuildGoldPreparation
    fun findOperation(transactionId: UUID): GuildGoldOperationRecord?
    fun apply(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long?): GuildGoldResult
    fun recordCompensation(transactionId: UUID, succeeded: Boolean, details: String): Boolean
}
```

`prepare` inserts a fingerprinted `PREPARED` operation or returns the existing record; the same ID with different fields is rejected. `apply` must begin a database transaction, lock/read the prepared operation and `vault_gold`, validate credit capacity or debit sufficiency, mutate `vault_gold`, update withdrawal usage for successful withdrawals, finalize the operation/audit row, and commit. Duplicate finalized IDs return the recorded balances and fee. A leftover `PREPARED` external operation is fail-closed as `DUPLICATE_PENDING` and surfaced for reconciliation; retry never repeats an ambiguous external debit/payout.

- [ ] **Step 4: Add compatible schema and SQL implementation**

Add `guild_gold_operations(transaction_id PK, guild_id, actor_id, route, direction, amount, fee, old_balance, new_balance, status, description, created_at)`, `guild_gold_withdrawal_usage(guild_id, period_start, amount, PRIMARY KEY(guild_id, period_start))`, and `guild_gold_security(guild_id PK, frozen, reason, updated_by, updated_at)`. Use engine-specific upsert/locking SQL behind private adapter methods; do not rename or copy `vault_gold`.

```kotlin
override fun apply(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long?): GuildGoldResult =
    storage.transaction { connection ->
        operationDao.findForUpdate(connection, mutation.transactionId)?.finalResult()?.let { return@transaction it }
        val old = goldDao.balanceForUpdate(connection, mutation.guildId)
        val next = when (mutation.direction) {
            GuildGoldDirection.CREDIT -> Math.addExact(old, mutation.amount).also {
                if (it > capacity) return@transaction GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
            }
            GuildGoldDirection.DEBIT -> Math.subtractExact(old, Math.addExact(mutation.amount, mutation.fee)).also {
                if (it < 0) return@transaction GuildGoldResult.Rejected(GuildGoldRejection.INSUFFICIENT_FUNDS)
            }
        }
        goldDao.setBalance(connection, mutation.guildId, next)
        periodStartEpochMs?.let { usageDao.add(connection, mutation.guildId, it, mutation.amount) }
        operationDao.finish(connection, mutation, old, next)
        GuildGoldResult.Applied(mutation.transactionId, old, next, mutation.fee)
    }
```

- [ ] **Step 5: Run repository, migration, and consolidation tests**

Run: `./gradlew test --tests "*GuildGoldRepositorySQLTest*" --tests "*GuildBalanceConsolidatorTest" --tests "*Migration*Test" --no-daemon --console=plain`

Expected: PASS for atomicity, idempotency, canonical-balance preservation, and both engines.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/persistence/GuildGoldRepository.kt src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildGoldRepositorySQL.kt src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildGoldRepositorySQLTest.kt
git commit -m "feat(gold): add atomic balance repository"
```

### Task 3: Canonical application service for internal mutations

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldServiceTest.kt`

**Interfaces:**
- Consumes: Task 1 policy types, Task 2 repository, existing `ProgressionService`/reward configuration reads, and `Clock` or an injected `() -> Instant`.
- Produces: `balance`, `capacity`, `creditSystem`, `debitSystem`, `depositPersonal`, `withdrawPersonal`, `depositPhysical`, and `withdrawPhysical`.

- [ ] **Step 1: Write failing service tests for internal credit/debit policy**

Test exact rejections for amount below/above limits, capacity, frozen bank, insufficient funds, withdrawal percentage, daily allowance, suspicious auto-freeze, overflow, and duplicate transaction ID. Verify rejected operations never call `repository.apply` and accepted operations pass the calculated fee/capacity/period.

```kotlin
@Test fun `credit above effective capacity is rejected before repository mutation`() {
    every { repository.getBalance(guildId) } returns 950
    every { capacityProvider(guildId) } returns GuildGoldCapacity(tier = 1_000, permanent = 0)
    val result = service.creditSystem(txId, guildId, actorId, 100, GuildGoldRoute.SYSTEM, "refund")
    assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED), result)
    verify(exactly = 0) { repository.apply(any(), any(), any()) }
}

@Test fun `withdrawal uses the lower percentage and daily remainder`() {
    every { repository.getBalance(guildId) } returns 80_000
    every { repository.getDailyWithdrawn(guildId, any()) } returns 45_000
    val result = service.debitSystem(txId, guildId, actorId, 6_000, "purchase")
    assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.DAILY_LIMIT), result)
}
```

- [ ] **Step 2: Run the service test and verify red**

Run: `./gradlew test --tests "net.lumalyte.lg.application.services.GuildGoldServiceTest" --no-daemon --console=plain`

Expected: compilation fails because `GuildGoldService` does not exist.

- [ ] **Step 3: Implement the internal API**

```kotlin
class GuildGoldService(
    private val repository: GuildGoldRepository,
    private val policyProvider: GuildGoldPolicyProvider,
    private val capacityProvider: GuildGoldCapacityProvider,
    private val authorization: GuildGoldAuthorizationPort,
    private val personalEconomy: PersonalEconomyPort,
    private val physicalGold: PhysicalGoldPort,
    private val now: () -> Instant = Instant::now
) {
    fun balance(guildId: UUID): Long
    fun capacity(guildId: UUID): Long
    fun creditSystem(transactionId: UUID, guildId: UUID, actorId: UUID, amount: Long, route: GuildGoldRoute, reason: String): GuildGoldResult
    fun debitSystem(transactionId: UUID, guildId: UUID, actorId: UUID, amount: Long, reason: String): GuildGoldResult
    fun depositPersonal(request: PersonalGoldRequest): GuildGoldResult
    fun withdrawPersonal(request: PersonalGoldRequest): GuildGoldResult
    fun depositPhysical(request: PhysicalGoldRequest): GuildGoldResult
    fun withdrawPhysical(request: PhysicalGoldRequest): GuildGoldResult
}

data class PersonalGoldRequest(
    val transactionId: UUID, val guildId: UUID, val playerId: UUID,
    val amount: Long, val description: String
)

data class PhysicalGoldRequest(
    val transactionId: UUID, val guildId: UUID, val playerId: UUID,
    val amount: Long, val description: String
)

fun interface GuildGoldPolicyProvider { fun policyFor(guildId: UUID): GuildGoldPolicy }
fun interface GuildGoldCapacityProvider { fun capacityFor(guildId: UUID): GuildGoldCapacity }
interface GuildGoldAuthorizationPort {
    fun canDeposit(playerId: UUID, guildId: UUID): Boolean
    fun canWithdraw(playerId: UUID, guildId: UUID): Boolean
}
```

Use one private validation path per direction. System sinks bypass player withdrawal permission and player-facing fee only when their explicit route says so; they still enforce balance, idempotency, audit, and freeze policy. Admin credits enforce capacity unless a separately permissioned force command is introduced by a future requirement.

- [ ] **Step 4: Run service and domain tests**

Run: `./gradlew test --tests "net.lumalyte.lg.application.services.GuildGoldServiceTest" --tests "net.lumalyte.lg.domain.gold.*" --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldServiceTest.kt
git commit -m "feat(gold): centralize balance policy"
```

### Task 4: Personal Vault Economy transfer adapter and compensation

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/PersonalEconomyPort.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/VaultPersonalEconomyAdapter.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldPersonalTransferTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/VaultPersonalEconomyAdapterTest.kt`

**Interfaces:**
- Consumes: `GuildGoldService` and repository from Tasks 2–3.
- Produces: provider-neutral personal-account transfers with explicit unavailable/failed outcomes.

- [ ] **Step 1: Write failing transfer-order and compensation tests**

```kotlin
interface PersonalEconomyPort {
    fun isAvailable(): Boolean
    fun balance(playerId: UUID): Long?
    fun debit(playerId: UUID, amount: Long): ExternalTransferResult
    fun credit(playerId: UUID, amount: Long): ExternalTransferResult
}

sealed interface ExternalTransferResult {
    data object Applied : ExternalTransferResult
    data object Unavailable : ExternalTransferResult
    data class Rejected(val reason: String) : ExternalTransferResult
    data class Failed(val reason: String) : ExternalTransferResult
}
```

Cover: unavailable provider touches neither account nor guild; unauthorized requests touch neither account nor guild; personal deposit debits `amount + depositFee` before guild credit and refunds after repository failure; personal withdrawal debits `amount + withdrawalFee` before player payout and restores guild balance after payout failure; fee is charged once; duplicate transaction ID does not repeat the external leg.

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew test --tests "*GuildGoldPersonalTransferTest" --tests "*VaultPersonalEconomyAdapterTest" --no-daemon --console=plain`

Expected: compilation fails for the missing port/adapter.

- [ ] **Step 3: Implement the adapter and orchestration**

Move Vault service discovery and response translation out of `BankServiceBukkit`. Convert only exact integral amounts; reject negative, fractional, NaN, infinite, or above-`Long` provider values. Use a persisted operation status (`PREPARED`, `APPLIED`, `COMPENSATED`, `FAILED_COMPENSATION`) so restart/retry cannot repeat an external leg silently.

```kotlin
fun depositPersonal(request: PersonalGoldRequest): GuildGoldResult {
    val mutation = validatedPersonalDeposit(request) ?: return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
    return when (val prepared = repository.prepare(mutation)) {
        is GuildGoldPreparation.Existing -> prepared.record.toResultOrPending()
        GuildGoldPreparation.FingerprintMismatch -> GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
        is GuildGoldPreparation.New -> when (personalEconomy.debit(request.playerId, mutation.amount + mutation.fee)) {
            ExternalTransferResult.Applied -> applyOrRefundPersonalDeposit(request, mutation)
            ExternalTransferResult.Unavailable -> rejectPrepared(mutation, GuildGoldRejection.EXTERNAL_UNAVAILABLE)
            is ExternalTransferResult.Rejected -> rejectPrepared(mutation, GuildGoldRejection.EXTERNAL_REJECTED)
            is ExternalTransferResult.Failed -> GuildGoldResult.Failed(mutation.transactionId, false)
        }
    }
}
```

- [ ] **Step 4: Run compensation tests**

Run: `./gradlew test --tests "*GuildGoldPersonalTransferTest" --tests "*VaultPersonalEconomyAdapterTest" --tests "*BankConfigEnforcementTest" --no-daemon --console=plain`

Expected: PASS, including unavailable-provider behavior.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/PersonalEconomyPort.kt src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/VaultPersonalEconomyAdapter.kt src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldPersonalTransferTest.kt src/test/kotlin/net/lumalyte/lg/infrastructure/services/VaultPersonalEconomyAdapterTest.kt
git commit -m "feat(gold): compensate Vault transfers"
```

### Task 5: Physical raw-gold reservation adapter

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/PhysicalGoldPort.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/BukkitPhysicalGoldAdapter.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldPhysicalTransferTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/BukkitPhysicalGoldAdapterTest.kt`

**Interfaces:**
- Consumes: Task 3 service and existing configured physical currency material/block values.
- Produces: opaque item reservations with exact restoration and safe overflow delivery.

- [ ] **Step 1: Write failing physical reservation tests**

```kotlin
data class PhysicalGoldReservation(val id: UUID, val playerId: UUID, val value: Long)
sealed interface PhysicalReservationResult {
    data class Reserved(val value: PhysicalGoldReservation) : PhysicalReservationResult
    data object Insufficient : PhysicalReservationResult
}
interface PhysicalGoldPort {
    fun reserve(playerId: UUID, requestedValue: Long): PhysicalReservationResult
    fun commit(reservation: PhysicalGoldReservation): Boolean
    fun restore(reservation: PhysicalGoldReservation): Boolean
    fun deliver(playerId: UUID, value: Long, transactionId: UUID): ExternalTransferResult
}
```

Cover exact base/block conversion, mixed stacks, partial requested value, deposit reservation of `amount + depositFee`, no item removal before permission/policy validation, restore after repository failure, guild restoration after delivery failure, full-inventory safe drop, and duplicate delivery ID.

```kotlin
@Test fun `failed canonical credit restores exact reserved stacks`() {
    every { physical.reserve(playerId, 19) } returns PhysicalReservationResult.Reserved(reservation)
    every { repository.apply(any(), any(), any()) } returns GuildGoldResult.Failed(txId, false)
    every { physical.restore(reservation) } returns true
    service.depositPhysical(PhysicalGoldRequest(txId, guildId, playerId, 18, "menu deposit"))
    verifyOrder { physical.reserve(playerId, 19); repository.apply(any(), any(), any()); physical.restore(reservation) }
}

@Test fun `one configured block converts to nine base units`() {
    val inventory = inventoryWith(Material.RAW_GOLD_BLOCK, 1)
    assertEquals(9, adapter.availableValue(inventory))
}
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew test --tests "*GuildGoldPhysicalTransferTest" --tests "*BukkitPhysicalGoldAdapterTest" --no-daemon --console=plain`

Expected: compilation fails for the missing physical port/adapter.

- [ ] **Step 3: Implement reservation and compensation**

Store reserved `ItemStack` snapshots only inside the infrastructure adapter. The application layer receives the opaque reservation and numeric value. Validate policy/capacity before `reserve`; after reservation, apply the canonical credit then commit the reservation. On failure, restore to inventory and drop only overflow at the player location. Physical withdrawals debit canonical gold before delivery and issue an idempotent compensating credit if delivery fails.

```kotlin
fun depositPhysical(request: PhysicalGoldRequest): GuildGoldResult {
    val mutation = validatedPhysicalDeposit(request) ?: return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
    val prepared = repository.prepare(mutation)
    if (prepared !is GuildGoldPreparation.New) return prepared.toExistingResult()
    val reservation = when (val reserved = physicalGold.reserve(request.playerId, mutation.amount + mutation.fee)) {
        is PhysicalReservationResult.Reserved -> reserved.value
        PhysicalReservationResult.Insufficient -> return rejectPrepared(mutation, GuildGoldRejection.EXTERNAL_REJECTED)
    }
    val applied = repository.apply(mutation, capacity(request.guildId), null)
    if (applied is GuildGoldResult.Applied && physicalGold.commit(reservation)) return applied
    val restored = physicalGold.restore(reservation)
    repository.recordCompensation(request.transactionId, restored, "physical deposit restoration")
    return GuildGoldResult.Failed(request.transactionId, restored)
}
```

- [ ] **Step 4: Run physical and vault synchronization tests**

Run: `./gradlew test --tests "*GuildGoldPhysicalTransferTest" --tests "*BukkitPhysicalGoldAdapterTest" --tests "*VaultInventorySyncTest" --tests "*VaultInventoryManagerSharedInventoryTest" --no-daemon --console=plain`

Expected: PASS; ordinary vault items and slot capacity remain unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/PhysicalGoldPort.kt src/main/kotlin/net/lumalyte/lg/application/services/GuildGoldService.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/BukkitPhysicalGoldAdapter.kt src/test/kotlin/net/lumalyte/lg/application/services/GuildGoldPhysicalTransferTest.kt src/test/kotlin/net/lumalyte/lg/infrastructure/services/BukkitPhysicalGoldAdapterTest.kt
git commit -m "feat(gold): add physical item reservations"
```

### Task 6: Replace legacy bank facade mutations and UI bypasses

**Deposit-window adapter detail:** The shared Bukkit physical adapter supports a synchronous, server-thread-only inventory scope for dragged deposits. It excludes UI slots, reserves from that window through the existing application policy, and returns only unspent items on scope exit. Reservations with uncertain outcomes are never returned speculatively. Disconnect cancels the window and returns its contents without initiating a deposit. Gold is no longer part of the ordinary vault-slot write buffer; display/cache refreshes read the canonical balance.

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/BankService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/VaultInventoryManager.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldDepositMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldWithdrawMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/listeners/VaultInventoryListener.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/architecture/GuildGoldMutationOwnershipTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkitDelegationTest.kt`

**Interfaces:**
- Consumes: all Task 3–5 application methods.
- Produces: legacy callers retain `BankService` results while no interaction/infrastructure class directly mutates canonical gold.

- [ ] **Step 1: Write a failing source-ownership regression test**

Scan production Kotlin sources and assert `depositGold(`, `withdrawGold(`, `setGoldBalance(`, `addGoldBalance(`, and `subtractGoldBalance(` occur only inside the canonical SQL adapter or an explicitly read-only/cache synchronization method. Assert the four menu/listener files reference `GuildGoldService` or `BankService`, never `VaultInventoryManager` for gold mutations.

```kotlin
@Test fun `interaction code cannot call vault gold mutation methods`() {
    val forbidden = Regex("\\b(depositGold|withdrawGold|setGoldBalance|addGoldBalance|subtractGoldBalance)\\s*\\(")
    Files.walk(Path.of("src/main/kotlin/net/lumalyte/lg/interaction")).use { paths ->
        val violations = paths.filter { it.toString().endsWith(".kt") }
            .filter { forbidden.containsMatchIn(Files.readString(it)) }.toList()
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
```

- [ ] **Step 2: Run ownership/delegation tests and verify red**

Run: `./gradlew test --tests "*GuildGoldMutationOwnershipTest" --tests "*BankServiceBukkitDelegationTest" --no-daemon --console=plain`

Expected: FAIL listing current direct callers.

- [ ] **Step 3: Convert `BankServiceBukkit` into the compatibility facade**

Delegate `deposit`, `withdraw`, `deductFromGuildBank`, `creditToGuildBank`, `getBalance`, and `getTopBalances` to `GuildGoldService`. Map rich rejections to existing nullable/Boolean results and existing localized feedback without recalculating policy. Keep read-only history/statistics APIs backed by the canonical operation/audit records.

```kotlin
fun creditToGuildBank(transactionId: UUID, guildId: UUID, amount: Int, reason: String?): Boolean
fun deductFromGuildBank(transactionId: UUID, guildId: UUID, amount: Int, reason: String?): Boolean

override fun creditToGuildBank(transactionId: UUID, guildId: UUID, amount: Int, reason: String?): Boolean =
    guildGoldService.creditSystem(
        transactionId = transactionId,
        guildId = guildId, actorId = SYSTEM_ACTOR, amount = amount.toLong(),
        route = GuildGoldRoute.SYSTEM, reason = reason ?: "System credit"
    ) is GuildGoldResult.Applied
```

Keep the existing three-argument methods as deprecated wrappers that create a new UUID. Migrate every plugin-owned system caller in Task 7 to the explicit-ID overload; the wrappers exist only for compatibility and are forbidden by the final ownership test outside `BankServiceBukkit`.

- [ ] **Step 4: Route Java and Bedrock entry points through the facade**

Replace direct manager calls in `GoldDepositMenu`, `GoldWithdrawMenu`, `GuildBankMenu`, and `VaultInventoryListener`. Generate one transaction UUID per confirmed player action and retain it across retry callbacks. Update the gold display through a post-success read/broadcast, not by mutating the cached balance first.

```kotlin
private fun confirmDeposit(amount: Long, transactionId: UUID = UUID.randomUUID()) {
    when (val result = guildGoldService.depositPhysical(
        PhysicalGoldRequest(transactionId, guildId, player.uniqueId, amount, "Gold deposit menu")
    )) {
        is GuildGoldResult.Applied -> refreshFromCanonicalBalance(result.newBalance)
        is GuildGoldResult.Rejected -> showRejection(result.reason)
        is GuildGoldResult.Failed -> showTransferFailure(result.compensationSucceeded)
    }
}
```

- [ ] **Step 5: Restrict legacy mutation methods**

Remove or make private/internal the public gold mutation methods on `VaultInventoryManager` and mutation methods on `GuildVaultRepository` once compile confirms no callers. Preserve `getGoldBalance`, leaderboard reads, cache refresh, and visual button broadcasts until a later vault-cache refactor.

```kotlin
fun refreshGoldDisplay(guildId: UUID) {
    val canonical = guildGoldService.balance(guildId)
    updateGoldBalanceButton(guildId, canonical)
    broadcastGoldUpdate(guildId, canonical)
}
```

- [ ] **Step 6: Run UI, ownership, leaderboard, and vault tests**

Run: `./gradlew test --tests "*GuildGoldMutationOwnershipTest" --tests "*BankServiceBukkitDelegationTest" --tests "*BankLeaderboardFreshnessTest" --tests "*VaultLeaderboardConsistencyTest" --tests "*VaultInventory*Test" --no-daemon --console=plain`

Expected: PASS and no direct mutation bypass remains.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/BankService.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkit.kt src/main/kotlin/net/lumalyte/lg/infrastructure/vault/VaultInventoryManager.kt src/main/kotlin/net/lumalyte/lg/interaction/menus/guild src/main/kotlin/net/lumalyte/lg/interaction/listeners/VaultInventoryListener.kt src/test/kotlin/net/lumalyte/lg/architecture/GuildGoldMutationOwnershipTest.kt src/test/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkitDelegationTest.kt
git commit -m "refactor(gold): remove mutation bypasses"
```

### Task 7: Route interest, admin credits, and system costs

**2026-09-14 scope clarification:** The operator approved including LFG paid admission in this banking PR. Both item and personal-account fees must be collected once by `GuildGoldService`, with admission eligibility replacing member deposit permission. Keep the journal at `BALANCE_APPLIED` until membership is confirmed; ambiguous membership/provider outcomes remain guarded for staff reconciliation. Validate the live recruitment settings, default rank and actual progression membership limit before charging; display the fee-inclusive charge. Java and Bedrock confirmations must not remove items or money independently. Prove rejection, successful payment, duplicate submission, and a pending payment read through a new repository instance.

`BankCreditCommand` is a **personal-account recovery command**, not a guild credit command. Preserve its semantics and exclude it from guild-balance routing. Do not redirect player recovery payments into guild gold.

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/BankAutomationService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/admin/BankCreditCommand.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/PhysicalCurrencyServiceBukkit.kt`
- Modify: call sites returned by the Task 6 ownership scan for `creditToGuildBank`/`deductFromGuildBank`
- Test: `src/test/kotlin/net/lumalyte/lg/application/services/BankAutomationServiceTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldSystemRoutesTest.kt`

**Interfaces:**
- Consumes: `GuildGoldService.creditSystem` and `debitSystem`.
- Produces: stable transaction IDs for scheduled periods, admin commands, war wagers/refunds, home/perk purchases, and other system routes.

- [ ] **Step 1: Add failing interest idempotency/capacity tests**

Use transaction ID `UUID.nameUUIDFromBytes("interest:$guildId:$periodStart".toByteArray())`. Assert the same period credits once, capacity rejection records no partial interest, frozen banks accrue nothing, and the period marker advances only after an applied or explicitly capacity-skipped decision according to policy.

```kotlin
@Test fun `same interest period credits once`() {
    every { guildGold.creditSystem(any(), guildId, any(), 5, GuildGoldRoute.INTEREST, any()) } returns
        GuildGoldResult.Applied(periodId, 1_000, 1_005, 0) andThen
        GuildGoldResult.Applied(periodId, 1_000, 1_005, 0)
    service.accrueInterest()
    service.accrueInterest()
    verify(exactly = 1) { guildGold.creditSystem(periodId, guildId, any(), 5, GuildGoldRoute.INTEREST, "Interest accrual") }
}
```

- [ ] **Step 2: Add failing system-route tests**

Assert admin credit and war refund respect capacity; system cost is a fee-free audited debit; retries use the same transaction ID; `PhysicalCurrencyServiceBukkit` no longer edits inventory and canonical balance as two unrelated operations.

```kotlin
@Test fun `war cost is one fee-free canonical debit`() {
    every { guildGold.debitSystem(warTxId, guildId, actorId, 250, "War declaration") } returns applied
    assertTrue(currencyService.deductCurrency(guild, 250, "War declaration"))
    verify(exactly = 1) { guildGold.debitSystem(warTxId, guildId, actorId, 250, "War declaration") }
    verify(exactly = 0) { legacyVaultManager.withdrawGold(any(), any(), any()) }
}
```

- [ ] **Step 3: Run focused tests and verify red**

Run: `./gradlew test --tests "*BankAutomationServiceTest" --tests "*GuildGoldSystemRoutesTest" --no-daemon --console=plain`

Expected: FAIL because current routes lack stable IDs or bypass the service.

- [ ] **Step 4: Implement stable route IDs and canonical calls**

Pass transaction IDs from command invocation IDs, war IDs plus operation kind, home/perk purchase IDs, and interest period keys. Preserve the existing reason text in audit records. Delete fallback code that directly changes raw-gold inventory or `vault_gold` after the canonical result is available.

```kotlin
fun interestTransactionId(guildId: UUID, periodStart: Instant): UUID =
    UUID.nameUUIDFromBytes("interest:$guildId:${periodStart.toEpochMilli()}".toByteArray(StandardCharsets.UTF_8))

fun warTransactionId(warId: UUID, operation: String): UUID =
    UUID.nameUUIDFromBytes("war:$warId:$operation".toByteArray(StandardCharsets.UTF_8))
```

- [ ] **Step 5: Run bank automation and war/economy tests**

Run: `./gradlew test --tests "*BankAutomationServiceTest" --tests "*GuildGoldSystemRoutesTest" --tests "*War*Test" --tests "*PhysicalCurrency*Test" --no-daemon --console=plain`

Expected: PASS with one operation/audit per economic event.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/BankAutomationService.kt src/main/kotlin/net/lumalyte/lg/interaction/commands/admin/BankCreditCommand.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/PhysicalCurrencyServiceBukkit.kt src/test/kotlin/net/lumalyte/lg/application/services/BankAutomationServiceTest.kt src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldSystemRoutesTest.kt
git commit -m "fix(gold): route system balance changes"
```

### Task 8: Configuration, dependency injection, and degraded mode

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/config/MainConfig.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/PhysicalCurrencyServiceBukkit.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldAuthorizationAdapter.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/di/Modules.kt`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/kotlin/net/lumalyte/lg/config/GuildGoldConfigTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldDegradedModeTest.kt`

**Interfaces:**
- Consumes: all canonical service ports/adapters.
- Produces: validated runtime composition and player-facing capability flags.

- [ ] **Step 1: Write failing config/degraded-mode tests**

Assert `vault.bank_mode: BOTH` plus physical currency is accepted; `require_economy_plugin: false` starts without a provider; personal transfers report unavailable while physical exchange, system costs, balance reads, and ordinary vault storage remain enabled; invalid negative limits/fees and block values fail validation with exact paths.

```kotlin
data class GuildGoldCapabilities(
    val personalTransfers: Boolean, val physicalTransfers: Boolean,
    val systemDebits: Boolean, val vaultItems: Boolean
)

@Test fun `both mode remains available without Vault provider`() {
    val config = config(bankMode = "BOTH", requireEconomyPlugin = false, physicalCurrencyEnabled = true)
    val capabilities = composeWith(config, economyProvider = null).guildGoldCapabilities()
    assertFalse(capabilities.personalTransfers)
    assertTrue(capabilities.physicalTransfers)
    assertTrue(capabilities.systemDebits)
    assertTrue(capabilities.vaultItems)
}
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew test --tests "*GuildGoldConfigTest" --tests "*GuildGoldDegradedModeTest" --tests "*ConfigLoaderConsistencyTest" --no-daemon --console=plain`

Expected: FAIL because current physical validation requires `PHYSICAL` rather than accepting `BOTH`.

- [ ] **Step 3: Correct config semantics and bind the graph**

Bind `GuildGoldRepository`, `PersonalEconomyPort`, `PhysicalGoldPort`, and `GuildGoldService` once in `economyModule`/`vaultModule` without circular Koin dependencies. Expose capability reads from the application service so menus hide only personal-transfer controls when Vault Economy is absent.

```kotlin
single<GuildGoldRepository> { GuildGoldRepositorySQL(get()) }
single<PersonalEconomyPort> { VaultPersonalEconomyAdapter(get()) }
single<PhysicalGoldPort> { BukkitPhysicalGoldAdapter(get(), get()) }
single<GuildGoldAuthorizationPort> { GuildGoldAuthorizationAdapter(get()) }
single { GuildGoldService(get(), get(), get(), get(), get(), get()) }
single<BankService> { BankServiceBukkit(get(), get(), get(), get()) }
```

- [ ] **Step 4: Run DI/config and menu tests**

Run: `./gradlew test --tests "*GuildGoldConfigTest" --tests "*GuildGoldDegradedModeTest" --tests "*ConfigLoaderConsistencyTest" --tests "*GuildBankMenu*Test" --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/config/MainConfig.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/PhysicalCurrencyServiceBukkit.kt src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldAuthorizationAdapter.kt src/main/kotlin/net/lumalyte/lg/di/Modules.kt src/main/resources/config.yml src/test/kotlin/net/lumalyte/lg/config/GuildGoldConfigTest.kt src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildGoldDegradedModeTest.kt
git commit -m "fix(config): support combined guild gold modes"
```

### Task 9: Full regression proof and task closure

**Files:**
- Modify: `docs/tasks.md`
- Modify: `docs/implementation.md` only if actual class names differ from the approved blueprint.
- Test: `src/test/kotlin/net/lumalyte/lg/architecture/GuildGoldMutationOwnershipTest.kt`

**Interfaces:**
- Consumes: Tasks 1–8.
- Produces: compile/static proof that every balance mutation uses the pipeline and SPEAR evidence for LG-1209.

- [ ] **Step 1: Run the bypass scan**

Run:

```powershell
rg -n "depositGold\(|withdrawGold\(|setGoldBalance\(|addGoldBalance\(|subtractGoldBalance\(" src/main/kotlin
```

Expected: only canonical repository/cache synchronization definitions remain; no menu, listener, command, automation, war, home, or perk caller appears.

- [ ] **Step 2: Run focused architecture and canonical-gold suites**

Run: `./gradlew test --tests "*GuildGold*" --tests "*Bank*" --tests "*Vault*" --tests "*Architecture*" --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 3: Run the full project verification**

Run: `./gradlew clean test build --no-daemon --console=plain`

Expected: BUILD SUCCESSFUL with no new compiler warnings. If the isolated worktree lacks the ignored RoseChat/CombatLogX compile-only jars, copy the same local dependency artifacts from the main checkout into the worktree without committing them, then rerun.

- [ ] **Step 4: Mark LG-1209 complete with evidence**

Change `[ ]` to `[x]` in `docs/tasks.md` and record the focused test classes, full build command, and commit range in its Evidence field.

```markdown
- [x] **LG-1209** Canonical guild-gold pipeline
  - Evidence: `GuildGoldPolicyTest`, `GuildGoldRepositorySQLTest`, `GuildGoldServiceTest`,
    `GuildGoldMutationOwnershipTest`; `./gradlew clean test build`
```

- [ ] **Step 5: Commit**

```bash
git add docs/tasks.md docs/implementation.md src/test/kotlin/net/lumalyte/lg/architecture/GuildGoldMutationOwnershipTest.kt
git commit -m "docs(tasks): close canonical gold pipeline"
```

- [ ] **Step 6: Request review before reward-table work**

Open one pull request scoped to LG-1209. The PR description must call out the balance authority, compensation model, bypass scan, BOTH-mode correction, database-engine coverage, and the fact that reward/prestige behavior remains disabled and out of scope.
