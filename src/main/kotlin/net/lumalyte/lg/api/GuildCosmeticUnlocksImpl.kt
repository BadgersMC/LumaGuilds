// Must be public: registered in Bukkit's ServicesManager and consumed by other plugins.
@file:Suppress("LibraryEntitiesShouldNotBePublic", "TooGenericExceptionCaught")

package net.lumalyte.lg.api

import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import org.slf4j.LoggerFactory
import java.util.UUID

/** [GuildCosmeticUnlocks] backed by [GuildCosmeticUnlockService]; never throws across the plugin boundary. */
class GuildCosmeticUnlocksImpl(private val service: GuildCosmeticUnlockService) : GuildCosmeticUnlocks {
    private val logger = LoggerFactory.getLogger(GuildCosmeticUnlocksImpl::class.java)

    override fun unlockCosmetic(guildId: UUID, type: String, key: String, displayName: String, source: String): Boolean =
        guarded("unlock $type:$key for $guildId", false) { service.unlock(guildId, type, key, displayName, source) }

    override fun revokeCosmetic(guildId: UUID, type: String, key: String): Boolean =
        guarded("revoke $type:$key for $guildId", false) { service.revoke(guildId, type, key) }

    override fun getUnlockedCosmetics(guildId: UUID, type: String): Set<String> =
        guarded("list $type for $guildId", emptySet()) { service.unlockedKeys(guildId, type) }

    private fun <T> guarded(action: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (exception: RuntimeException) {
        logger.error("GuildCosmeticUnlocks failed to $action", exception)
        fallback
    }
}
