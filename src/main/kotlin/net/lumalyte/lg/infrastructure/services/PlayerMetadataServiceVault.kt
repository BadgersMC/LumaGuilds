package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.config.MainConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.milkbowl.vault.chat.Chat
import org.bukkit.Bukkit
import org.bukkit.Server
import java.util.UUID

internal fun playerMetadataService(server: Server, config: MainConfig, dispatcher: CoroutineDispatcher): PlayerMetadataService {
    if (server.pluginManager.getPlugin("Vault") != null) {
        val chat = server.servicesManager.getRegistration(Chat::class.java)?.provider
        if (chat != null) return PlayerMetadataServiceVault(chat, config, dispatcher)
    }
    return ConfiguredClaimLimits(config)
}

private class ConfiguredClaimLimits(private val config: MainConfig) : PlayerMetadataService {
    override fun getPlayerClaimLimit(playerId: UUID) = config.claimLimit
    override fun getPlayerClaimBlockLimit(playerId: UUID) = config.claimBlockLimit
    override suspend fun getPlayerClaimLimitAsync(playerId: UUID) = config.claimLimit
    override suspend fun getPlayerClaimBlockLimitAsync(playerId: UUID) = config.claimBlockLimit
}

class PlayerMetadataServiceVault(
    private val metadata: Chat,
    private val config: MainConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlayerMetadataService {
    override fun getPlayerClaimLimit(playerId: UUID): Int {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
        return metadata.getPlayerInfoInteger(null, offlinePlayer, "bellclaims.claim_limit", config.claimLimit)
    }

    override fun getPlayerClaimBlockLimit(playerId: UUID): Int {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
        return metadata.getPlayerInfoInteger(null, offlinePlayer, "bellclaims.claim_block_limit", config.claimBlockLimit)
    }

    override suspend fun getPlayerClaimLimitAsync(playerId: UUID): Int {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
        return withContext(ioDispatcher) {
            metadata.getPlayerInfoInteger(null, offlinePlayer, "bellclaims.claim_limit", config.claimLimit)
        }
    }

    override suspend fun getPlayerClaimBlockLimitAsync(playerId: UUID): Int {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
        return withContext(ioDispatcher) {
            metadata.getPlayerInfoInteger(null, offlinePlayer, "bellclaims.claim_block_limit", config.claimBlockLimit)
        }
    }
}
