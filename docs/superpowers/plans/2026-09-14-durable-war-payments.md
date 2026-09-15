# Durable War Payments Implementation Plan

> Execute inline with the executing-plans and test-driven-development workflows, as requested by the operator.

**Goal:** Preserve war identity and payment decisions across restarts without duplicate guild-gold mutations.

**Architecture:** A versioned per-war aggregate stores declaration, war, statistics, wager and settlement intent together. The repository uses compare-and-set revisions to reject stale writers. GuildGoldService remains the only financial mutation owner; war orchestration persists intent before calling it and advances state only after a confirmed journal result.

**Tech Stack:** Existing Kotlin/JDBC/SQLite/MariaDB and Gson; no new dependencies.

**Spec:** REQ-039 and REQ-092 in `docs/requirements.md`, including the approved durable-war clarification.

## Global constraints

- Application ports and domain models import no Bukkit or storage implementation.
- Storage failure must never fall back to an empty in-memory war registry.
- Do not activate an unfunded war, overwrite an in-progress settlement outcome, or refund an uncertain debit.
- Runtime support is one authoritative war-service instance per server/database. Revision conflicts fail closed; no distributed lease protocol is introduced.
- No legacy war records exist to import from SQL. Never invent a pre-restart wager from a truncated audit description.
- First deployment must drain/settle existing in-memory wars before shutdown; a cold-start upgrade cannot recover data the previous build never persisted. Include this operator requirement in the eventual PR.

## Task 1: Durable aggregate and repository

Files: `domain/entities/DurableWarRecord.kt`, `application/persistence/WarRepository.kt`, `infrastructure/persistence/guilds/WarRepositorySQL.kt`, `infrastructure/persistence/guilds/WarRecordCodec.kt`, and `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/WarRepositorySQLTest.kt`.

Interface:
```kotlin
interface WarRepository {
    fun get(id: UUID): DurableWarRecord?
    fun getAll(): List<DurableWarRecord>
    // New rows require revision 0; successful saves increment the stored revision.
    fun save(record: DurableWarRecord): Boolean
}
```

- [x] Write a failing test that saves a declaration plus war/statistics/wager, recreates the repository, and asserts full equality with revision 1. Add stale-writer, independent-war, malformed-payload and unsupported-version rejection tests.
- [x] Run `gradlew test --tests '*WarRepositorySQLTest'` and capture red.
- [x] Implement immutable aggregate, strict versioned JSON codec with explicit Instant/Duration adapters, and an additive `guild_war_records` table with `war_id`, `revision`, `payload`. Insert revision 1 only when absent; update only where stored revision equals the supplied revision. Never overwrite a row on conflict.
- [x] Run the focused tests: 8/8 green, including database close/reopen and a red→green corrupt-null settlement-flag regression. Runtime migration remains Task 3.

## Task 2: Restart-safe financial lifecycle

Files: `application/services/WarPaymentService.kt`, `application/services/GuildGoldService.kt`, canonical repository, and `WarPaymentServiceTest.kt`.

API: `fund(recordId: UUID): Boolean`, `settle(recordId: UUID, winnerGuildId: UUID?): Boolean`. Persist phases `FUNDING`, `ESCROWED`, `REFUNDING`, `SETTLING`, `SETTLED`, `REVIEW` and immutable chosen outcome in the aggregate. Stable IDs derive from record ID, funding cycle, leg name and attempt number; a new attempt is allowed only after a journal-confirmed rejection with no balance mutation, never after an unknown result. Store that attempt number before submitting its transfer. Replay the same attempt after an interrupted response. A new funding cycle is allowed only after a failed acceptance has been fully refunded; it uses distinct IDs and cannot reuse the old, compensated debit as fresh escrow.

- [x] Write failing tests for restart after each leg, one successful draw refund followed by failed second refund, changed winner on retry, refund after a failed second escrow debit, capacity rejection followed by capacity becoming available, and ambiguous debit held for review.
- [x] Persist funding intent before money moves. Use typed canonical results (not Boolean wrappers). Failed acceptance becomes refunding, not an active war. Record compensated/settled state only after every necessary journal result is confirmed. Record a distinct new funding attempt only after the prior one is fully refunded.
- [x] Test that a database marker failure after a successful payment replays the recorded payment and never transfers twice. Audit rows retain complete war ID and operation kind.
- [x] Focused payment/storage tests and full `gradlew test build` pass. Nine payment tests include lost commit replies, failed funded/settled markers, and recovery through recreated services. Runtime integration remains Task 3.

## Task 3: War-service integration and recovery [~]

Files: `infrastructure/services/WarServiceBukkit.kt`, `di/Modules.kt`, `WarConfigEnforcementTest.kt`, new `WarRestartRecoveryTest.kt`, and `docs/tasks.md`.

- [x] Write a failing declaration→acceptance→restart test using real SQLite; assert identical war ID, active status, statistics and escrow, then settle and restart again without another payout.
- [ ] Replace authoritative maps for declarations/wars/statistics/wagers with repository reads and revision-checked updates. Use declaration ID as stable war identity. Preserve completed records for history. Persist the chosen result before scheduling settlement; cancellation records a refund outcome. Keep notifications after durable transitions.
- [ ] Wire exactly one SQL repository and payment service. Recovery reads unresolved records and resumes only safe journaled work. Corrupt or unavailable storage must fail initialization rather than expose an empty registry. Preserve anti-farming/peace state needed by restored active wars; test these at the integration boundary.
- [ ] Prove the runtime payment path honors both legacy `Guild.bankFrozen` and canonical freeze state before submitting new money movements, while journal-confirmed replays may finish their state marker. The current BankService facade performs the legacy check; do not bypass it when binding the new payment service.
- [ ] Run `gradlew test build`, the ownership scan, and update LG-1209 evidence. Continue the existing banking plan's backup/config tasks; do not close LG-1209 prematurely.
