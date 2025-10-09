package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.BankSecuritySettings
import java.util.UUID

/**
 * Repository interface for bank security settings.
 */
interface BankSecuritySettingsRepository {
    /**
     * Finds security settings by guild ID.
     */
    fun findByGuildId(guildId: UUID): BankSecuritySettings?

    /**
     * Saves or updates security settings.
     */
    fun save(settings: BankSecuritySettings): Boolean

    /**
     * Deletes security settings by guild ID.
     */
    fun deleteByGuildId(guildId: UUID): Boolean

    /**
     * Finds all guilds with emergency freeze enabled.
     */
    fun findGuildsWithEmergencyFreeze(): List<UUID>

    /**
     * Updates the emergency freeze status for a guild.
     */
    fun updateEmergencyFreeze(guildId: UUID, emergencyFreeze: Boolean): Boolean
}
