package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.PrestigeRepository
import net.lumalyte.lg.application.persistence.PrestigeConfirmation
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.WarStatus
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.migrations.VaultCapacityUnlockSchema
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Atomic prestige transaction: canonical guild gold, reward ownership, progression reset,
 * guild-level mirror and idempotent receipt commit on one database connection.
 */
class PrestigeRepositorySQL(
    private val storage: Storage<Database>,
    private val catalog: RewardCatalog,
    private val owners: RewardOwnershipRepositorySQL,
    private val gold: GuildGoldRepositorySQL,
    private val guildActions: GuildActionCoordinator,
    private val config: () -> MainConfig,
) : PrestigeRepository, PrestigeHistory, net.lumalyte.lg.application.persistence.PrestigeAdministration {
    private val resolver = RewardEntitlementResolver(catalog)
    private val lockSuffix = if (storage.dialect == SqlDialect.MARIADB) " FOR UPDATE" else ""

    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { connection ->
            connection.createStatement().use {
                it.execute("""CREATE TABLE IF NOT EXISTS guild_prestige_transactions (
                    transaction_id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    actor_id VARCHAR(36) NOT NULL,
                    retained_reward_id VARCHAR(64) NOT NULL,
                    quoted_fee BIGINT NOT NULL,
                    expected_version BIGINT NOT NULL,
                    expected_prestige_count INTEGER NOT NULL,
                    outcome VARCHAR(32) NOT NULL,
                    rejection VARCHAR(64) NULL,
                    old_balance BIGINT NULL,
                    new_balance BIGINT NULL,
                    ownership_version BIGINT NULL,
                    prestige_count INTEGER NULL,
                    post_bank_capacity BIGINT NULL,
                    home_capacity INTEGER NULL
                )$engine""")
                // REQ-185: staff corrections, apart from the prestige receipts above.
                it.execute("""CREATE TABLE IF NOT EXISTS guild_prestige_adjustments (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    actor_id VARCHAR(36) NOT NULL,
                    old_count INTEGER NOT NULL,
                    new_count INTEGER NOT NULL,
                    adjusted_at BIGINT NOT NULL
                )$engine""")
            }
        }
    }

    override fun confirm(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeResult =
        confirmWithCommitStatus(request, guard).result

    // REQ-152: receipts joined to their own canonical gold debit for the commit time.
    override fun history(guildId: UUID): List<PrestigeRecord> =
        records("p.guild_id = ?", guildId.toString())

    override fun firstToReach(tier: Int): PrestigeRecord? =
        records("p.prestige_count = ?", tier, limit = 1).firstOrNull()

    // REQ-275: current prestige counts from the reward accounts; the latest applied receipt dates
    // when each guild reached its count (a guild with no dated receipt sorts last on ties).
    override fun standings(): List<net.lumalyte.lg.domain.rewards.HallStanding> =
        storage.connection.connection.use { connection ->
            val counts = connection.prepareStatement(
                "SELECT guild_id, prestige_count FROM guild_reward_accounts WHERE prestige_count > 0"
            ).use { ps -> ps.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getInt(2)) } } }
            if (counts.isEmpty()) return@use emptyList()
            val reached = connection.prepareStatement(
                """SELECT p.guild_id, MAX(g.created_at)
                   FROM guild_prestige_transactions p
                   JOIN guild_gold_operations g ON $journalJoin
                   WHERE p.outcome = 'APPLIED' AND g.status = 'APPLIED'
                   GROUP BY p.guild_id"""
            ).use { ps -> ps.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) } } }
            counts.mapNotNull { (id, count) ->
                val guildId = runCatching { UUID.fromString(id) }.getOrNull() ?: return@mapNotNull null
                net.lumalyte.lg.domain.rewards.HallStanding(guildId, count,
                    reached[id]?.let(Instant::ofEpochMilli) ?: Instant.ofEpochMilli(Long.MAX_VALUE / 2))
            }
        }

    // MariaDB: the journal pins utf8mb4_unicode_ci while receipts use the server default,
    // so convert the receipt side; comparing them directly fails with mixed collations.
    private val journalJoin = if (storage.dialect == SqlDialect.MARIADB)
        "g.transaction_id = CONVERT(p.transaction_id USING utf8mb4) COLLATE utf8mb4_unicode_ci"
    else "g.transaction_id = p.transaction_id"

    private fun records(filter: String, value: Any, limit: Int? = null): List<PrestigeRecord> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """SELECT p.transaction_id, p.guild_id, p.actor_id, p.prestige_count,
                          p.retained_reward_id, g.created_at
                   FROM guild_prestige_transactions p
                   JOIN guild_gold_operations g ON $journalJoin
                   WHERE p.outcome = 'APPLIED' AND g.status = 'APPLIED' AND $filter
                   ORDER BY g.created_at, p.transaction_id${limit?.let { " LIMIT $it" } ?: ""}"""
            ).use { statement ->
                statement.setObject(1, value)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            PrestigeRecord(
                                transactionId = UUID.fromString(rows.getString("transaction_id")),
                                guildId = UUID.fromString(rows.getString("guild_id")),
                                actorId = UUID.fromString(rows.getString("actor_id")),
                                tier = rows.getInt("prestige_count"),
                                retainedRewardId = rows.getString("retained_reward_id"),
                                appliedAt = Instant.ofEpochMilli(rows.getLong("created_at")),
                            )
                        )
                    }
                }
            }
        }

    override fun confirmWithCommitStatus(
        request: PrestigeQuote,
        guard: () -> PrestigeRejection?,
    ): PrestigeConfirmation = commit(request, guard, staff = false)

    // REQ-186: same transaction, receipt and replay as a paid prestige, minus fee and level.
    override fun grantWithCommitStatus(request: PrestigeQuote): PrestigeConfirmation =
        if (request.quotedFee != 0L) PrestigeConfirmation(PrestigeResult.Rejected(PrestigeRejection.FEE_CHANGED))
        else commit(request, { null }, staff = true)

    private fun commit(
        request: PrestigeQuote,
        guard: () -> PrestigeRejection?,
        staff: Boolean,
    ): PrestigeConfirmation = try {
        guildActions.withGuilds(request.guildId) {
            gold.withGuildLock(request.guildId) {
                storage.connection.connection.use { connection ->
                    transaction(connection) {
                        // Fence before replay opens a MariaDB snapshot. An absent guild can still
                        // replay its retained receipt; fresh execution keeps its existing guards.
                        connection.prepareStatement("UPDATE guilds SET level = level WHERE id = ?").use {
                            it.setString(1, request.guildId.toString())
                            it.executeUpdate()
                        }
                        replay(connection, request)?.let { PrestigeConfirmation(it) }
                            ?: execute(connection, request, guard, staff).let { result ->
                                saveReceipt(connection, request, result)
                                // This envelope escapes only after transaction() commits.
                                PrestigeConfirmation(result, result is PrestigeResult.Applied)
                            }
                    }
                }
            }
        }
    } catch (e: Exception) {
        net.lumalyte.lg.application.logging.SwallowedErrors.log("PrestigeRepositorySQL:149", e)
        PrestigeConfirmation(PrestigeResult.Failed(request.transactionId))
    }

    override fun setPrestigeCount(
        guildId: UUID,
        count: Int,
        actorId: UUID,
        at: Instant,
    ): PrestigeAdjustmentResult = try {
        if (count < 0 || count > config().progression.prestige.maxCount) {
            PrestigeAdjustmentResult.OutOfRange
        } else {
            guildActions.withGuilds(guildId) {
                storage.connection.connection.use { connection ->
                    transaction(connection) { adjust(connection, guildId, count, actorId, at) }
                }
            }
        }
    } catch (e: Exception) {
        net.lumalyte.lg.application.logging.SwallowedErrors.log("PrestigeRepositorySQL:168", e)
        PrestigeAdjustmentResult.Failed
    }

    private fun adjust(connection: Connection, guildId: UUID, count: Int, actorId: UUID, at: Instant): PrestigeAdjustmentResult {
        val snapshot = owners.readSnapshot(connection, guildId) ?: return PrestigeAdjustmentResult.NotFound
        val old = snapshot.ownership.prestigeCount
        if (old == count) return PrestigeAdjustmentResult.Unchanged
        if (count > old) return PrestigeAdjustmentResult.RaiseNeedsGrant
        val dropped = dropped(snapshot.ownership, history(guildId).map { it.retainedRewardId }, old - count)
        check(owners.lowerPrestigeInTransaction(connection, guildId, snapshot.version, count, dropped.toSet())) {
            "Ownership changed during prestige adjustment"
        }
        connection.prepareStatement(
            """INSERT INTO guild_prestige_adjustments (id, guild_id, actor_id, old_count, new_count, adjusted_at)
               VALUES (?, ?, ?, ?, ?, ?)"""
        ).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, guildId.toString())
            it.setString(3, actorId.toString())
            it.setInt(4, old)
            it.setInt(5, count)
            it.setLong(6, at.toEpochMilli())
            check(it.executeUpdate() == 1)
        }
        return PrestigeAdjustmentResult.Applied(old, count, dropped)
    }

    override fun rewardsDroppedBy(guildId: UUID, count: Int): List<String> {
        val snapshot = (owners.read(guildId) as? RewardOwnershipRead.Found)?.snapshot ?: return emptyList()
        val old = snapshot.ownership.prestigeCount
        if (count !in 0 until old) return emptyList()
        return dropped(snapshot.ownership, history(guildId).map { it.retainedRewardId }, old - count)
    }

    /** The newest kept selections first (by prestige history), then any others by id. */
    private fun dropped(ownership: RewardOwnership, keptInOrder: List<String>, n: Int): List<String> {
        val selections = ownership.permanent.filter { catalog.find(it)?.permanentOnPurchase == false }.toSet()
        val newestFirst = keptInOrder.asReversed().filter { it in selections }.distinct()
        return (newestFirst + (selections - newestFirst.toSet()).sorted()).take(n)
    }

    override fun adjustments(guildId: UUID): List<PrestigeAdjustment> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """SELECT guild_id, actor_id, old_count, new_count, adjusted_at FROM guild_prestige_adjustments
                   WHERE guild_id = ? ORDER BY adjusted_at, id"""
            ).use { statement ->
                statement.setString(1, guildId.toString())
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            PrestigeAdjustment(
                                UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                                rows.getInt(3), rows.getInt(4), Instant.ofEpochMilli(rows.getLong(5)),
                            )
                        )
                    }
                }
            }
        }

    private fun execute(
        connection: Connection,
        request: PrestigeQuote,
        guard: () -> PrestigeRejection?,
        staff: Boolean = false,
    ): PrestigeResult {
        fun reject(reason: PrestigeRejection) = PrestigeResult.Rejected(reason)

        if (gold.findOperation(connection, request.transactionId, false) != null) {
            return reject(PrestigeRejection.ID_CONFLICT)
        }

        val accountLocked = connection.prepareStatement(
            "UPDATE guild_reward_accounts SET version = version WHERE guild_id = ?"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeUpdate() == 1
        }
        if (!accountLocked) return reject(PrestigeRejection.UNINITIALIZED)

        guard()?.let { return reject(it) }

        val settings = config()
        val prestige = settings.progression.prestige
        if (!prestige.enabled) return reject(PrestigeRejection.UNAVAILABLE)

        val snapshot = owners.readSnapshot(connection, request.guildId)
            ?: return reject(PrestigeRejection.UNINITIALIZED)
        if (snapshot.version != request.expectedOwnershipVersion ||
            snapshot.ownership.prestigeCount != request.expectedPrestigeCount
        ) {
            return reject(PrestigeRejection.STALE_STATE)
        }
        if (snapshot.ownership.prestigeCount >= prestige.maxCount) {
            return reject(PrestigeRejection.MAX_PRESTIGE)
        }

        val expectedFee = if (staff) 0L else prestige.feeFor(snapshot.ownership.prestigeCount)
        if (request.quotedFee != expectedFee) return reject(PrestigeRejection.FEE_CHANGED)

        val (level, totalExperience) = connection.prepareStatement(
            "SELECT current_level, total_experience FROM guild_progression WHERE guild_id = ?$lockSuffix"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) to rows.getInt(2) else null }
        } ?: return reject(PrestigeRejection.UNINITIALIZED)
        if (level != 100 && !staff) return reject(PrestigeRejection.NOT_LEVEL_100)

        // A staff grant offers the choices the guild would have at level 100.
        val currentEntitlements = resolver.resolve(
            if (staff) 100 else level,
            snapshot.ownership,
            settings.bank.maxBankBalance.toLong(),
            settings.guild.maxMembersPerGuild,
        )
        if (currentEntitlements.prestigeChoices.none { it.id == request.retainedRewardId }) {
            return reject(PrestigeRejection.INVALID_SELECTION)
        }

        if (hasBlockingWar(connection, request.guildId)) {
            return reject(PrestigeRejection.ACTIVE_WAR)
        }

        val frozen = connection.prepareStatement(
            "SELECT frozen FROM guild_gold_security WHERE guild_id = ?$lockSuffix"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
        }
        if (frozen) return reject(PrestigeRejection.FROZEN)

        val pendingGold = connection.prepareStatement(
            """SELECT transaction_id FROM guild_gold_operations
               WHERE guild_id = ? AND transaction_id <> ?
                 AND status IN ('PREPARED', 'BALANCE_APPLIED', 'FAILED_COMPENSATION')
               LIMIT 1$lockSuffix"""
        ).use {
            it.setString(1, request.guildId.toString())
            it.setString(2, request.transactionId.toString())
            it.executeQuery().use { rows -> rows.next() }
        }
        if (pendingGold) return reject(PrestigeRejection.PENDING_GOLD)

        val balance = connection.prepareStatement(
            "SELECT balance FROM vault_gold WHERE guild_id = ?$lockSuffix"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
        }
        if (balance < expectedFee) return reject(PrestigeRejection.INSUFFICIENT_FUNDS)

        val nextOwnership = snapshot.ownership.copy(
            currentRun = emptySet(),
            permanent = snapshot.ownership.permanent + request.retainedRewardId,
            prestigeCount = snapshot.ownership.prestigeCount + 1,
        )
        // REQ-175: optional XP carried into the next climb sets the level it starts at.
        val curve = ProgressionCurve.from(settings.progression)
        val carried = PrestigeCarryOver.experience(totalExperience, curve, settings.progression.prestige.carryOver)
        val postLevel = curve.levelFromExperience(carried)
        val postEntitlements = resolver.resolve(
            postLevel,
            nextOwnership,
            settings.bank.maxBankBalance.toLong(),
            settings.guild.maxMembersPerGuild,
        )
        // REQ-157: a balance above the post-prestige capacity is kept; the gold
        // repository then refuses credits until capacity catches up.

        val mutation = mutation(request)
        val paid = gold.applyInTransaction(connection, mutation, Long.MAX_VALUE, null)
        if (paid is GuildGoldResult.Rejected) {
            check(paid.reason == GuildGoldRejection.INSUFFICIENT_FUNDS) {
                "Unexpected canonical prestige debit rejection: ${paid.reason}"
            }
            return reject(PrestigeRejection.INSUFFICIENT_FUNDS)
        }
        check(paid is GuildGoldResult.Applied) { "Canonical prestige debit did not complete" }

        // This guild proved level 100. Preserve the full 54 slots in this same
        // atomic prestige transaction, even when every upper slot is empty.
        VaultCapacityUnlockSchema.recordAtLeast(
            connection, request.guildId, 54, storage.dialect == SqlDialect.MARIADB,
        )
        val now = Instant.now().toEpochMilli()
        val progressionUpdated = connection.prepareStatement(
            """UPDATE guild_progression
               SET total_experience = ?,
                   current_level = ?,
                   experience_this_level = ?,
                   experience_for_next_level = ?,
                   last_level_up = NULL,
                   unlocked_perks = '[]',
                   last_updated = ?
               WHERE guild_id = ?${if (staff) "" else " AND current_level = 100"}"""
        ).use {
            it.setInt(1, carried)
            it.setInt(2, postLevel)
            it.setInt(3, curve.experienceInCurrentLevel(carried))
            it.setInt(4, curve.experienceForNextLevel(postLevel))
            if (storage.dialect == SqlDialect.MARIADB) it.setTimestamp(5, Timestamp(now))
            else it.setLong(5, now)
            it.setString(6, request.guildId.toString())
            it.executeUpdate() == 1
        }
        check(progressionUpdated) { "Progression changed during prestige" }

        connection.prepareStatement("UPDATE guilds SET level = ? WHERE id = ?").use {
            it.setInt(1, postLevel)
            it.setString(2, request.guildId.toString())
            check(it.executeUpdate() == 1) { "Guild disappeared during prestige" }
        }

        val ownershipSaved = owners.saveInTransaction(
            connection,
            request.guildId,
            snapshot.version,
            nextOwnership,
        )
        check(ownershipSaved is RewardOwnershipWrite.Saved) { "Ownership changed during prestige" }

        return PrestigeResult.Applied(
            transactionId = request.transactionId,
            retainedRewardId = request.retainedRewardId,
            fee = expectedFee,
            oldBalance = paid.oldBalance,
            newBalance = paid.newBalance,
            ownershipVersion = ownershipSaved.snapshot.version,
            prestigeCount = nextOwnership.prestigeCount,
            postBankCapacity = postEntitlements.bankCapacity,
            homeCapacity = postEntitlements.homeCapacity,
            carriedExperience = carried,
            postLevel = postLevel,
        )
    }

    private fun hasBlockingWar(connection: Connection, guildId: UUID): Boolean =
        connection.prepareStatement("SELECT payload FROM guild_war_records").use { statement ->
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val record = WarRecordCodec.decode(rows.getString("payload"))
                    val war = record.war
                    if (war != null &&
                        guildId in setOf(war.declaringGuildId, war.defendingGuildId) &&
                        war.status in setOf(WarStatus.DECLARED, WarStatus.ACTIVE)
                    ) {
                        return true
                    }
                    val declaration = record.declaration
                    if (declaration != null && !declaration.accepted && !declaration.rejected &&
                        guildId in setOf(declaration.declaringGuildId, declaration.defendingGuildId)
                    ) {
                        return true
                    }
                }
                false
            }
        }

    private fun replay(connection: Connection, request: PrestigeQuote): PrestigeResult? =
        connection.prepareStatement(
            "SELECT * FROM guild_prestige_transactions WHERE transaction_id = ?"
        ).use {
            it.setString(1, request.transactionId.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) return null
                val original = PrestigeQuote(
                    transactionId = request.transactionId,
                    guildId = UUID.fromString(rows.getString("guild_id")),
                    actorId = UUID.fromString(rows.getString("actor_id")),
                    retainedRewardId = rows.getString("retained_reward_id"),
                    quotedFee = rows.getLong("quoted_fee"),
                    expectedOwnershipVersion = rows.getLong("expected_version"),
                    expectedPrestigeCount = rows.getInt("expected_prestige_count"),
                )
                if (original != request) {
                    return PrestigeResult.Rejected(PrestigeRejection.ID_CONFLICT)
                }
                when (rows.getString("outcome")) {
                    "REJECTED" -> PrestigeResult.Rejected(
                        PrestigeRejection.valueOf(rows.getString("rejection"))
                    )
                    "APPLIED" -> {
                        val operation = gold.findOperation(connection, request.transactionId, false)
                        check(operation?.mutation == mutation(request) &&
                            operation.status == GuildGoldOperationStatus.APPLIED) {
                            "Prestige receipt disagrees with canonical gold journal"
                        }
                        PrestigeResult.Applied(
                            transactionId = request.transactionId,
                            retainedRewardId = request.retainedRewardId,
                            fee = request.quotedFee,
                            oldBalance = rows.getLong("old_balance"),
                            newBalance = rows.getLong("new_balance"),
                            ownershipVersion = rows.getLong("ownership_version"),
                            prestigeCount = rows.getInt("prestige_count"),
                            postBankCapacity = rows.getLong("post_bank_capacity"),
                            homeCapacity = rows.getInt("home_capacity"),
                        )
                    }
                    else -> error("Invalid prestige receipt outcome")
                }
            }
        }

    private fun saveReceipt(connection: Connection, request: PrestigeQuote, result: PrestigeResult) {
        check(result !is PrestigeResult.Failed)
        val applied = result as? PrestigeResult.Applied
        connection.prepareStatement(
            """INSERT INTO guild_prestige_transactions
               (transaction_id, guild_id, actor_id, retained_reward_id, quoted_fee,
                expected_version, expected_prestige_count, outcome, rejection,
                old_balance, new_balance, ownership_version, prestige_count,
                post_bank_capacity, home_capacity)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use {
            it.setString(1, request.transactionId.toString())
            it.setString(2, request.guildId.toString())
            it.setString(3, request.actorId.toString())
            it.setString(4, request.retainedRewardId)
            it.setLong(5, request.quotedFee)
            it.setLong(6, request.expectedOwnershipVersion)
            it.setInt(7, request.expectedPrestigeCount)
            it.setString(8, if (applied != null) "APPLIED" else "REJECTED")
            it.setString(9, (result as? PrestigeResult.Rejected)?.reason?.name)
            it.setObject(10, applied?.oldBalance)
            it.setObject(11, applied?.newBalance)
            it.setObject(12, applied?.ownershipVersion)
            it.setObject(13, applied?.prestigeCount)
            it.setObject(14, applied?.postBankCapacity)
            it.setObject(15, applied?.homeCapacity)
            check(it.executeUpdate() == 1)
        }
    }

    private fun mutation(request: PrestigeQuote) = GuildGoldMutation(
        transactionId = request.transactionId,
        guildId = request.guildId,
        actorId = request.actorId,
        route = GuildGoldRoute.SYSTEM,
        direction = GuildGoldDirection.DEBIT,
        amount = request.quotedFee,
        fee = 0,
        description = "Guild prestige retaining ${request.retainedRewardId}",
    )

    private fun <T> transaction(connection: Connection, block: () -> T): T {
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = autoCommit
        }
    }
}
