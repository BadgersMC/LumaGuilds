package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.PeaceAgreementRepository
import net.lumalyte.lg.domain.entities.PeaceAgreement
import net.lumalyte.lg.domain.entities.PeaceOffering
import net.lumalyte.lg.domain.entities.PeaceOfferingItem
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class PeaceAgreementRepositorySQLite(private val storage: SQLiteStorage) : PeaceAgreementRepository {

    private val agreements: MutableMap<UUID, PeaceAgreement> = mutableMapOf()

    init {
        createPeaceAgreementTables()
        preload()
    }

    private fun createPeaceAgreementTables() {
        // Peace agreements table
        val agreementTableSql = """
            CREATE TABLE IF NOT EXISTS peace_agreements (
                id TEXT PRIMARY KEY,
                war_id TEXT NOT NULL,
                proposing_guild_id TEXT NOT NULL,
                target_guild_id TEXT NOT NULL,
                proposed_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                peace_terms TEXT NOT NULL,
                accepted BOOLEAN DEFAULT FALSE,
                rejected BOOLEAN DEFAULT FALSE,
                accepted_at TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        // Peace offerings table
        val offeringTableSql = """
            CREATE TABLE IF NOT EXISTS peace_offerings (
                id TEXT PRIMARY KEY,
                agreement_id TEXT NOT NULL UNIQUE,
                money INTEGER DEFAULT 0,
                exp INTEGER DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        // Peace offering items table
        val offeringItemsTableSql = """
            CREATE TABLE IF NOT EXISTS peace_offering_items (
                id TEXT PRIMARY KEY,
                offering_id TEXT NOT NULL,
                material TEXT NOT NULL,
                amount INTEGER NOT NULL,
                display_name TEXT,
                lore TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        try {
            storage.connection.executeUpdate(agreementTableSql)
            storage.connection.executeUpdate(offeringTableSql)
            storage.connection.executeUpdate(offeringItemsTableSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create peace agreement tables", e)
        }
    }

    private fun preload() {
        preloadPeaceAgreements()
    }

    private fun preloadPeaceAgreements() {
        val sql = "SELECT * FROM peace_agreements"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val agreement = mapResultSetToPeaceAgreement(result)
                agreements[agreement.id] = agreement
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload peace agreements", e)
        }
    }

    private fun mapResultSetToPeaceAgreement(rs: co.aikar.idb.DbRow): PeaceAgreement {
        val id = UUID.fromString(rs.getString("id"))
        val warId = UUID.fromString(rs.getString("war_id"))
        val proposingGuildId = UUID.fromString(rs.getString("proposing_guild_id"))
        val targetGuildId = UUID.fromString(rs.getString("target_guild_id"))
        val proposedAt = Instant.parse(rs.getString("proposed_at"))
        val expiresAt = Instant.parse(rs.getString("expires_at"))
        val peaceTerms = rs.getString("peace_terms")
        val accepted = rs.getString("accepted").toBoolean()
        val rejected = rs.getString("rejected").toBoolean()
        val acceptedAt = rs.getString("accepted_at")?.let { Instant.parse(it) }

        // Load peace offering if exists
        val offering = getPeaceOfferingForAgreement(id)

        return PeaceAgreement(
            id = id,
            warId = warId,
            proposingGuildId = proposingGuildId,
            targetGuildId = targetGuildId,
            proposedAt = proposedAt,
            expiresAt = expiresAt,
            peaceTerms = peaceTerms,
            offering = offering,
            accepted = accepted,
            rejected = rejected,
            acceptedAt = acceptedAt
        )
    }

    private fun getPeaceOfferingForAgreement(agreementId: UUID): PeaceOffering? {
        val sql = "SELECT * FROM peace_offerings WHERE agreement_id = ?"
        return try {
            val results = storage.connection.getResults(sql, agreementId.toString())
            if (results.isEmpty()) return null

            val result = results.first()
            val offeringId = UUID.fromString(result.getString("id"))
            val money = result.getInt("money")
            val exp = result.getInt("exp")

            // Get offering items
            val items = getPeaceOfferingItems(offeringId)

            PeaceOffering(
                id = offeringId,
                items = items,
                money = money,
                exp = exp
            )
        } catch (e: SQLException) {
            null
        }
    }

    private fun getPeaceOfferingItems(offeringId: UUID): List<PeaceOfferingItem> {
        val sql = "SELECT * FROM peace_offering_items WHERE offering_id = ?"
        return try {
            val results = storage.connection.getResults(sql, offeringId.toString())
            results.map { result ->
                val material = result.getString("material")
                val amount = result.getInt("amount")
                val displayName = result.getString("display_name")
                val lore = result.getString("lore")?.split(";") ?: emptyList()

                PeaceOfferingItem(
                    material = material,
                    amount = amount,
                    displayName = displayName,
                    lore = lore
                )
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }

    override fun add(agreement: PeaceAgreement): Boolean {
        // First add the peace offering if it exists
        var offeringId: UUID? = null
        if (agreement.offering != null) {
            offeringId = addPeaceOffering(agreement.offering, agreement.id)
        }

        val sql = """
            INSERT INTO peace_agreements (id, war_id, proposing_guild_id, target_guild_id, proposed_at,
                                        expires_at, peace_terms, accepted, rejected, accepted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                agreement.id.toString(),
                agreement.warId.toString(),
                agreement.proposingGuildId.toString(),
                agreement.targetGuildId.toString(),
                agreement.proposedAt.toString(),
                agreement.expiresAt.toString(),
                agreement.peaceTerms,
                agreement.accepted,
                agreement.rejected,
                agreement.acceptedAt?.toString()
            )
            agreements[agreement.id] = agreement
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    private fun addPeaceOffering(offering: PeaceOffering, agreementId: UUID): UUID? {
        val offeringId = UUID.randomUUID()
        val sql = "INSERT INTO peace_offerings (id, agreement_id, money, exp) VALUES (?, ?, ?, ?)"

        return try {
            storage.connection.executeUpdate(sql,
                offeringId.toString(),
                agreementId.toString(),
                offering.money,
                offering.exp
            )

            // Add offering items
            for (item in offering.items) {
                addPeaceOfferingItem(item, offeringId)
            }

            offeringId
        } catch (e: SQLException) {
            null
        }
    }

    private fun addPeaceOfferingItem(item: PeaceOfferingItem, offeringId: UUID): Boolean {
        val sql = "INSERT INTO peace_offering_items (id, offering_id, material, amount, display_name, lore) VALUES (?, ?, ?, ?, ?, ?)"

        return try {
            storage.connection.executeUpdate(sql,
                UUID.randomUUID().toString(),
                offeringId.toString(),
                item.material,
                item.amount,
                item.displayName,
                item.lore.joinToString(";")
            ) > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun update(agreement: PeaceAgreement): Boolean {
        val sql = """
            UPDATE peace_agreements SET war_id = ?, proposing_guild_id = ?, target_guild_id = ?, proposed_at = ?,
                                      expires_at = ?, peace_terms = ?, accepted = ?, rejected = ?, accepted_at = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                agreement.warId.toString(),
                agreement.proposingGuildId.toString(),
                agreement.targetGuildId.toString(),
                agreement.proposedAt.toString(),
                agreement.expiresAt.toString(),
                agreement.peaceTerms,
                agreement.accepted,
                agreement.rejected,
                agreement.acceptedAt?.toString(),
                agreement.id.toString()
            )
            if (rowsAffected > 0) {
                agreements[agreement.id] = agreement
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun remove(agreementId: UUID): Boolean {
        val sql = "DELETE FROM peace_agreements WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, agreementId.toString())
            if (rowsAffected > 0) {
                agreements.remove(agreementId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getById(agreementId: UUID): PeaceAgreement? = agreements[agreementId]

    override fun getByWarId(warId: UUID): List<PeaceAgreement> =
        agreements.values.filter { it.warId == warId }

    override fun getPendingForGuild(guildId: UUID): List<PeaceAgreement> =
        agreements.values.filter { it.targetGuildId == guildId && it.isValid }

    override fun getProposedByGuild(guildId: UUID): List<PeaceAgreement> =
        agreements.values.filter { it.proposingGuildId == guildId }

    override fun getAll(): List<PeaceAgreement> = agreements.values.toList()

    override fun getExpiredAgreements(): List<PeaceAgreement> =
        agreements.values.filter { !it.isValid }
}
