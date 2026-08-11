package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.BankSettings
import java.util.UUID

/**
 * Repository for per-guild bank settings (automation, budgets, dual-auth threshold).
 */
interface BankSettingsRepository {

    /**
     * Gets the persisted settings for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The settings, or null if none have been persisted yet.
     */
    fun getByGuildId(guildId: UUID): BankSettings?

    /**
     * Inserts or replaces the persisted settings for a guild.
     *
     * @param settings The settings to persist.
     * @return true if successful, false otherwise.
     */
    fun upsert(settings: BankSettings): Boolean
}
