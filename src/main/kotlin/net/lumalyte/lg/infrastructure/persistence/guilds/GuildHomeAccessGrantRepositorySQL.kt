package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildHomeAccessRepository
import net.lumalyte.lg.application.persistence.GuildHomeAccessState
import net.lumalyte.lg.application.persistence.GuildHomeAuditPreview
import net.lumalyte.lg.application.persistence.VerifiedGuildHomeGrant
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildHomeAccessGrantSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID

/**
 * The only automatic migration is SCHEMA creation. Historic saved homes are
 * UNRECONCILED until an audited exact-name mapping declares this guild READY.
 */
class GuildHomeAccessGrantRepositorySQL(private val storage: Storage<Database>) : GuildHomeAccessRepository {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mariaDb = storage.dialect == SqlDialect.MARIADB
    private val policy = GuildHomeAccessGrantSchema.POLICY
    private val grants = GuildHomeAccessGrantSchema.GRANTS

    init {
        storage.connection.connection.use { GuildHomeAccessGrantSchema.create(it, mariaDb) }
    }

    override fun state(guildId: UUID, homeName: String): GuildHomeAccessState = runCatching {
        storage.connection.connection.use { connection ->
            if (policyState(connection, guildId) != "READY") return@use GuildHomeAccessState.UNRECONCILED
            connection.prepareStatement("SELECT state FROM $grants WHERE guild_id = ? AND home_name = ?").use {
                it.setString(1, guildId.toString())
                it.setString(2, homeName)
                it.executeQuery().use { rows ->
                    if (rows.next()) GuildHomeAccessState.valueOf(rows.getString(1))
                    else GuildHomeAccessState.REVIEW_REQUIRED
                }
            }
        }
    }.getOrElse {
        logger.error("Cannot check saved-home access $guildId/$homeName; fail closed", it)
        GuildHomeAccessState.REVIEW_REQUIRED
    }

    override fun isManaged(guildId: UUID): Boolean = runCatching {
        storage.connection.connection.use { policyState(it, guildId) == "READY" }
    }.getOrElse {
        logger.error("Cannot check guild home reconciliation for $guildId", it)
        true
    }

    override fun previewReconciliation(guildId: UUID): GuildHomeAuditPreview = runCatching {
        storage.connection.connection.use { connection ->
            val ready = policyState(connection, guildId) == "READY"
            val states = savedHomeNames(connection, guildId).associateWith { name ->
                if (ready) grantState(connection, guildId, name) ?: GuildHomeAccessState.REVIEW_REQUIRED
                else GuildHomeAccessState.UNRECONCILED
            }
            // A VERIFIED global Chapter 1 backup is only a candidate provenance
            // source; a real restore/compare or owner name mapping is still needed.
            val candidateBackups = connection.prepareStatement(
                "SELECT storage_ref, sha256 FROM chapter_backup_evidence " +
                    "WHERE verification_status = 'VERIFIED' ORDER BY created_at DESC LIMIT 10",
            ).use {
                it.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getString(1) + " [sha256:" + rows.getString(2) + "]")
                    }
                }
            }
            GuildHomeAuditPreview(
                guildId, ready, states, candidateBackups,
                states.filterValues { !it.accessible }.keys + if (!ready) states.keys else emptySet(),
            )
        }
    }.getOrElse {
        logger.error("Cannot preview legacy home mapping for $guildId", it)
        GuildHomeAuditPreview(guildId, false, emptyMap(), emptyList(), emptySet(), unavailable = true)
    }

    override fun prepareEmptyGuild(guildId: UUID): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            if (policyState(connection, guildId) == "READY") return@transaction true
            if (savedHomeNames(connection, guildId).isNotEmpty()) return@transaction false
            val existing = policyState(connection, guildId)
            if (existing != null) return@transaction false
            connection.prepareStatement(
                "INSERT INTO $policy (guild_id, state, provenance_ref, reviewed_at) VALUES (?, 'READY', 'NEW_EMPTY_GUILD', ?)",
            ).use {
                it.setString(1, guildId.toString())
                it.setLong(2, System.currentTimeMillis())
                it.executeUpdate() == 1
            }
        } }
    }.getOrElse {
        logger.error("Cannot initialize empty guild's home-access ledger: $guildId", it)
        false
    }

    override fun canGrantNewHome(guildId: UUID, totalCapacity: Int): Boolean = runCatching {
        storage.connection.connection.use { connection ->
            policyState(connection, guildId) == "READY" &&
                (freeSourceKeys(connection, guildId, totalCapacity).any { source ->
                    !sourceAssigned(connection, guildId, source)
                } || unassignedPaidOutpost(connection, guildId) != null)
        }
    }.getOrElse {
        logger.error("Cannot verify new-home capacity for $guildId", it)
        false
    }

    override fun willUsePaidOutpostForNewHome(guildId: UUID, totalCapacity: Int): Boolean = runCatching {
        storage.connection.connection.use { connection ->
            policyState(connection, guildId) == "READY" &&
                freeSourceKeys(connection, guildId, totalCapacity).all { sourceAssigned(connection, guildId, it) } &&
                unassignedPaidOutpost(connection, guildId) != null
        }
    }.getOrElse {
        logger.error("Cannot preflight paid home slot for $guildId", it)
        false
    }

    override fun grantNewHome(guildId: UUID, homeName: String, totalCapacity: Int): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            if (policyState(connection, guildId) != "READY") return@transaction false
            if (homeName !in savedHomeNames(connection, guildId)) return@transaction false
            val old = grantState(connection, guildId, homeName)
            if (old != null) return@transaction old == GuildHomeAccessState.ACTIVE_BASE ||
                old == GuildHomeAccessState.ACTIVE_PRESTIGE
            val freeSource = freeSourceKeys(connection, guildId, totalCapacity)
                .firstOrNull { !sourceAssigned(connection, guildId, it) }
            val paidTx = if (freeSource == null) unassignedPaidOutpost(connection, guildId) else null
            if (freeSource == null && paidTx == null) return@transaction false
            val source = freeSource ?: "OUTPOST:$paidTx"
            val kind = when {
                paidTx != null -> "ACTIVE_OUTPOST"
                source.startsWith("BASE:") -> "ACTIVE_BASE"
                else -> "ACTIVE_PRESTIGE"
            }
            if (paidTx != null) {
                connection.prepareStatement(
                    "UPDATE guild_outpost_acquisitions SET assigned_home_name = ? " +
                        "WHERE guild_id = ? AND transaction_id = ? AND assigned_home_name IS NULL",
                ).use {
                    it.setString(1, homeName)
                    it.setString(2, guildId.toString())
                    it.setString(3, paidTx.toString())
                    if (it.executeUpdate() != 1) return@transaction false
                }
            }
            connection.prepareStatement(
                "INSERT INTO $grants (guild_id, home_name, state, source_key, provenance_ref, assigned_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
            ).use {
                it.setString(1, guildId.toString())
                it.setString(2, homeName)
                it.setString(3, kind)
                it.setString(4, source)
                it.setString(5, if (paidTx == null) "EARNED_FREE_SLOT" else "PAID_OUTPOST:$paidTx")
                it.setLong(6, System.currentTimeMillis())
                check(it.executeUpdate() == 1)
                true
            }
        } }
    }.getOrElse {
        logger.error("Cannot assign a newly saved free home $guildId/$homeName", it)
        false
    }

    override fun removeGrant(guildId: UUID, homeName: String): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            releasePaidAssignment(connection, guildId, homeName)
            connection.prepareStatement("DELETE FROM $grants WHERE guild_id = ? AND home_name = ?").use {
                it.setString(1, guildId.toString())
                it.setString(2, homeName)
                it.executeUpdate()
            }
            true
        } }
    }.getOrElse {
        logger.error("Cannot release removed home grant $guildId/$homeName", it)
        false
    }

    override fun removeAllGrants(guildId: UUID): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            connection.prepareStatement(
                "UPDATE guild_outpost_acquisitions SET assigned_home_name = NULL WHERE guild_id = ?",
            ).use {
                it.setString(1, guildId.toString())
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM $grants WHERE guild_id = ?").use {
                it.setString(1, guildId.toString())
                it.executeUpdate()
            }
            true
        } }
    }.getOrElse {
        logger.error("Cannot release removed home grants for $guildId", it)
        false
    }

    override fun reconcileVerified(
        guildId: UUID,
        grants: Map<String, VerifiedGuildHomeGrant>,
        provenance: String,
        reviewer: UUID,
    ): Boolean = runCatching {
        if (provenance.isBlank() || provenance.length > 512) return@runCatching false
        // Historical exploits can exceed 16 physically saved rows. Preserve all
        // names and coordinates; only accessible entitlements are capped at 16.
        if (grants.values.any {
            it.state == GuildHomeAccessState.UNRECONCILED ||
                it.state == GuildHomeAccessState.REVIEW_REQUIRED ||
                (it.state.accessible != !it.sourceKey.isNullOrBlank())
        }) return@runCatching false
        val sourceKeys = grants.values.mapNotNull { it.sourceKey }
        if (sourceKeys.size != sourceKeys.distinct().size) return@runCatching false
        storage.connection.connection.use { connection -> transaction(connection) {
            if (policyState(connection, guildId) == "READY") return@transaction false
            if (savedHomeNames(connection, guildId) != grants.keys) return@transaction false
            if (existingGrantCount(connection, guildId) > 0) return@transaction false
            if (!verifiedSourcesAllowed(connection, guildId, grants)) return@transaction false
            val now = System.currentTimeMillis()
            connection.prepareStatement(
                "INSERT INTO ${GuildHomeAccessGrantSchema.GRANTS} (guild_id, home_name, state, source_key, provenance_ref, reviewer_id, assigned_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                grants.forEach { (name, grant) ->
                    statement.setString(1, guildId.toString())
                    statement.setString(2, name)
                    statement.setString(3, grant.state.name)
                    statement.setString(4, grant.sourceKey)
                    statement.setString(5, provenance)
                    statement.setString(6, reviewer.toString())
                    statement.setLong(7, now)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            // READY is written LAST and atomically. A crash before commit leaves
            // the guild untouched and unclassified, never partially locked.
            val old = policyState(connection, guildId)
            if (old == null) {
                connection.prepareStatement(
                    "INSERT INTO $policy (guild_id, state, provenance_ref, reviewer_id, reviewed_at) " +
                        "VALUES (?, 'READY', ?, ?, ?)",
                ).use {
                    it.setString(1, guildId.toString())
                    it.setString(2, provenance)
                    it.setString(3, reviewer.toString())
                    it.setLong(4, now)
                    check(it.executeUpdate() == 1)
                }
            } else {
                connection.prepareStatement(
                    "UPDATE $policy SET state = 'READY', provenance_ref = ?, reviewer_id = ?, " +
                        "reviewed_at = ?, version = version + 1 WHERE guild_id = ? AND state = 'REVIEW_REQUIRED'",
                ).use {
                    it.setString(1, provenance)
                    it.setString(2, reviewer.toString())
                    it.setLong(3, now)
                    it.setString(4, guildId.toString())
                    check(it.executeUpdate() == 1)
                }
            }
            true
        } }
    }.getOrElse {
        logger.error("Verified guild-home mapping rejected for $guildId; no access transition committed", it)
        false
    }

    override fun assignAnyPaidOutpost(guildId: UUID, homeName: String): Boolean = runCatching {
        val unassigned = storage.connection.connection.use { unassignedPaidOutpost(it, guildId) }
            ?: return@runCatching false
        assignPaidOutpost(guildId, unassigned, homeName)
    }.getOrElse {
        logger.error("Cannot find paid Outpost assignment for $guildId/$homeName", it)
        false
    }

    override fun assignPaidOutpost(guildId: UUID, transactionId: UUID, homeName: String): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            if (policyState(connection, guildId) != "READY") return@transaction false
            if (homeName !in savedHomeNames(connection, guildId)) return@transaction false
            val old = grantState(connection, guildId, homeName)
            val source = "OUTPOST:$transactionId"
            val lock = if (mariaDb) " FOR UPDATE" else ""
            val assigned = connection.prepareStatement(
                "SELECT assigned_home_name FROM guild_outpost_acquisitions " +
                    "WHERE guild_id = ? AND transaction_id = ?$lock",
            ).use {
                it.setString(1, guildId.toString())
                it.setString(2, transactionId.toString())
                it.executeQuery().use { rows ->
                    if (!rows.next()) return@transaction false
                    rows.getString(1)
                }
            }
            if (assigned != null) {
                return@transaction assigned == homeName &&
                    old == GuildHomeAccessState.ACTIVE_OUTPOST &&
                    sourceAssignedToHome(connection, guildId, source, homeName)
            }
            if (old != GuildHomeAccessState.LOCKED_PREMATURE) return@transaction false
            connection.prepareStatement(
                "UPDATE guild_outpost_acquisitions SET assigned_home_name = ? " +
                    "WHERE guild_id = ? AND transaction_id = ? AND assigned_home_name IS NULL",
            ).use {
                it.setString(1, homeName)
                it.setString(2, guildId.toString())
                it.setString(3, transactionId.toString())
                if (it.executeUpdate() != 1) return@transaction false
            }
            connection.prepareStatement(
                "UPDATE $grants SET state = 'ACTIVE_OUTPOST', source_key = ?, " +
                    "provenance_ref = ?, assigned_at = ?, version = version + 1 " +
                    "WHERE guild_id = ? AND home_name = ? AND state = 'LOCKED_PREMATURE'",
            ).use {
                it.setString(1, source)
                it.setString(2, "PAID_OUTPOST:$transactionId")
                it.setLong(3, System.currentTimeMillis())
                it.setString(4, guildId.toString())
                it.setString(5, homeName)
                // If an intervening delete/state transition removed the locked
                // grant, roll back the paid acquisition's home assignment too.
                check(it.executeUpdate() == 1) { "Locked home grant changed during Outpost assignment" }
                true
            }
        } }
    }.getOrElse {
        logger.error("Cannot assign paid Outpost $transactionId to $guildId/$homeName", it)
        false
    }

    private fun policyState(connection: Connection, guildId: UUID): String? =
        connection.prepareStatement("SELECT state FROM $policy WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun grantState(connection: Connection, guildId: UUID, name: String): GuildHomeAccessState? =
        connection.prepareStatement("SELECT state FROM $grants WHERE guild_id = ? AND home_name = ?").use {
            it.setString(1, guildId.toString())
            it.setString(2, name)
            it.executeQuery().use { rows ->
                if (rows.next()) GuildHomeAccessState.valueOf(rows.getString(1)) else null
            }
        }

    private fun savedHomeNames(connection: Connection, guildId: UUID): Set<String> =
        connection.prepareStatement("SELECT name FROM guild_homes WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1)) }
            }
        }

    private fun existingGrantCount(connection: Connection, guildId: UUID): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM $grants WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }

    /**
     * Every active mapping must be backed by an actually earned BASE/PRESTIGE
     * source or a specifically paid acquisition bound to that saved home.
     * An operator's provenance string is not, by itself, an entitlement.
     */
    private fun verifiedSourcesAllowed(
        connection: Connection,
        guildId: UUID,
        candidates: Map<String, VerifiedGuildHomeGrant>,
    ): Boolean {
        val base = EarnedBaseHomeSlots.resolve(connection, guildId)
        val prestige = connection.prepareStatement(
            "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?",
        ).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else return false }
        }
        if (base !in 1..6 || prestige !in 0..6) return false
        val validFreeSources = ((1..base).map { "BASE:$it" } +
            (1..prestige).map { "PRESTIGE:$it" }).toSet()
        val paidCount = connection.prepareStatement(
            "SELECT COUNT(*) FROM guild_outpost_acquisitions WHERE guild_id = ?",
        ).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
        if (candidates.values.count { it.state.accessible } > minOf(16, base + prestige + paidCount)) {
            return false
        }
        return candidates.all { (name, grant) ->
            when (grant.state) {
                GuildHomeAccessState.ACTIVE_BASE ->
                    grant.sourceKey?.startsWith("BASE:") == true && grant.sourceKey in validFreeSources
                GuildHomeAccessState.ACTIVE_PRESTIGE ->
                    grant.sourceKey?.startsWith("PRESTIGE:") == true && grant.sourceKey in validFreeSources
                GuildHomeAccessState.ACTIVE_OUTPOST -> {
                    val tx = grant.sourceKey?.removePrefix("OUTPOST:") ?: return@all false
                    if (grant.sourceKey == tx || runCatching { UUID.fromString(tx) }.isFailure) return@all false
                    connection.prepareStatement(
                        "SELECT 1 FROM guild_outpost_acquisitions " +
                            "WHERE guild_id = ? AND transaction_id = ? AND assigned_home_name = ?",
                    ).use {
                        it.setString(1, guildId.toString())
                        it.setString(2, tx)
                        it.setString(3, name)
                        it.executeQuery().use { rows -> rows.next() }
                    }
                }
                GuildHomeAccessState.LOCKED_PREMATURE -> grant.sourceKey == null
                else -> false
            }
        }
    }

    private fun sourceAssigned(connection: Connection, guildId: UUID, source: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM $grants WHERE guild_id = ? AND source_key = ?").use {
            it.setString(1, guildId.toString())
            it.setString(2, source)
            it.executeQuery().use { rows -> rows.next() }
        }

    private fun sourceAssignedToHome(connection: Connection, guildId: UUID, source: String, name: String): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM $grants WHERE guild_id = ? AND source_key = ? AND home_name = ?",
        ).use {
            it.setString(1, guildId.toString())
            it.setString(2, source)
            it.setString(3, name)
            it.executeQuery().use { rows -> rows.next() }
        }

    private fun freeSourceKeys(connection: Connection, guildId: UUID, totalCapacity: Int): List<String> {
        if (totalCapacity !in 1..16) return emptyList()
        val base = EarnedBaseHomeSlots.resolve(connection, guildId)
        val prestige = connection.prepareStatement(
            "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?",
        ).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else return emptyList() }
        }
        if (base !in 1..6 || prestige !in 0..6) return emptyList()
        val paid = connection.prepareStatement(
            "SELECT COUNT(*) FROM guild_outpost_acquisitions WHERE guild_id = ?",
        ).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
        val usableFreeCount = (totalCapacity - paid).coerceAtLeast(0)
        return ((1..base).map { "BASE:$it" } + (1..prestige).map { "PRESTIGE:$it" })
            .take(usableFreeCount)
    }

    private fun unassignedPaidOutpost(connection: Connection, guildId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT transaction_id FROM guild_outpost_acquisitions " +
                "WHERE guild_id = ? AND assigned_home_name IS NULL ORDER BY prestige_rank LIMIT 1",
        ).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                if (rows.next()) UUID.fromString(rows.getString(1)) else null
            }
        }

    private fun releasePaidAssignment(connection: Connection, guildId: UUID, homeName: String) {
        connection.prepareStatement(
            "UPDATE guild_outpost_acquisitions SET assigned_home_name = NULL " +
                "WHERE guild_id = ? AND assigned_home_name = ?",
        ).use {
            it.setString(1, guildId.toString())
            it.setString(2, homeName)
            it.executeUpdate()
        }
    }

    private fun <T> transaction(connection: Connection, block: () -> T): T {
        val oldAuto = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = oldAuto
        }
    }
}
