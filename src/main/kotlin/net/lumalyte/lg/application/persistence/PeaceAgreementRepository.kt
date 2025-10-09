package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.PeaceAgreement
import java.util.UUID

/**
 * Repository interface for managing peace agreement persistence.
 */
interface PeaceAgreementRepository {

    /**
     * Adds a new peace agreement to the repository.
     *
     * @param agreement The peace agreement to add.
     * @return true if the agreement was added successfully, false otherwise.
     */
    fun add(agreement: PeaceAgreement): Boolean

    /**
     * Updates an existing peace agreement in the repository.
     *
     * @param agreement The peace agreement to update.
     * @return true if the agreement was updated successfully, false otherwise.
     */
    fun update(agreement: PeaceAgreement): Boolean

    /**
     * Removes a peace agreement from the repository.
     *
     * @param agreementId The ID of the agreement to remove.
     * @return true if the agreement was removed successfully, false otherwise.
     */
    fun remove(agreementId: UUID): Boolean

    /**
     * Gets a peace agreement by its ID.
     *
     * @param agreementId The ID of the agreement to retrieve.
     * @return The peace agreement if found, null otherwise.
     */
    fun getById(agreementId: UUID): PeaceAgreement?

    /**
     * Gets peace agreements for a specific war.
     *
     * @param warId The ID of the war.
     * @return List of peace agreements for the war.
     */
    fun getByWarId(warId: UUID): List<PeaceAgreement>

    /**
     * Gets pending peace agreements for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of pending peace agreements that the guild can respond to.
     */
    fun getPendingForGuild(guildId: UUID): List<PeaceAgreement>

    /**
     * Gets peace agreements proposed by a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of peace agreements proposed by the guild.
     */
    fun getProposedByGuild(guildId: UUID): List<PeaceAgreement>

    /**
     * Gets all peace agreements in the repository.
     *
     * @return List of all peace agreements.
     */
    fun getAll(): List<PeaceAgreement>

    /**
     * Gets expired peace agreements that need cleanup.
     *
     * @return List of expired agreements.
     */
    fun getExpiredAgreements(): List<PeaceAgreement>
}
