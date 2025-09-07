package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.config.MainConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.milkbowl.vault.chat.Chat
import org.bukkit.Bukkit
import java.util.UUID

class PlayerMetadataServiceVault(private val metadata: Chat,
                                 private val config: MainConfig): PlayerMetadataService {
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
        return withContext(Dispatchers.IO) {
            metadata.getPlayerInfoInteger(null, offlinePlayer, "bellclaims.claim_limit", config.claimLimit)
        }
    }

    override suspend fun getPlayerClaimBlockLimitAsync(playerId: UUID): Int {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
        return withContext(Dispatchers.IO) {
            metadata.getPlayerInfoInteger(null, offlinePlayer, "bellclaims.claim_block_limit", config.claimBlockLimit)
        }
    }
}
