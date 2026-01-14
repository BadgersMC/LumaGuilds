package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.application.services.ConfigService
import org.slf4j.LoggerFactory
import java.util.UUID

class HytalePlayerMetadataService(
    private val playerService: PlayerService,
    private val configService: ConfigService
) : PlayerMetadataService {

    private val log = LoggerFactory.getLogger(HytalePlayerMetadataService::class.java)

    override fun getPlayerClaimLimit(playerId: UUID): Int {
        // Check for permission-based limits
        // lumaguilds.claims.limit.5 → 5 claims
        // lumaguilds.claims.limit.10 → 10 claims
        // lumaguilds.claims.limit.unlimited → unlimited

        for (limit in listOf(100, 50, 25, 20, 15, 10, 5, 3, 1)) {
            if (playerService.hasPermission(playerId, "lumaguilds.claims.limit.$limit")) {
                return limit
            }
        }

        if (playerService.hasPermission(playerId, "lumaguilds.claims.limit.unlimited")) {
            return Int.MAX_VALUE
        }

        // Default from config
        val claimLimit = configService.loadConfig().claimLimit
        return if (claimLimit > 0) claimLimit else 3
    }

    override fun getPlayerClaimBlockLimit(playerId: UUID): Int {
        // Check for permission-based limits
        for (limit in listOf(50000, 25000, 10000, 5000, 2500, 1000, 500)) {
            if (playerService.hasPermission(playerId, "lumaguilds.claims.blocks.$limit")) {
                return limit
            }
        }

        if (playerService.hasPermission(playerId, "lumaguilds.claims.blocks.unlimited")) {
            return Int.MAX_VALUE
        }

        // Default from config
        val blockLimit = configService.loadConfig().claimBlockLimit
        return if (blockLimit > 0) blockLimit else 1000
    }

    override suspend fun getPlayerClaimLimitAsync(playerId: UUID): Int {
        return getPlayerClaimLimit(playerId)
    }

    override suspend fun getPlayerClaimBlockLimitAsync(playerId: UUID): Int {
        return getPlayerClaimBlockLimit(playerId)
    }
}
