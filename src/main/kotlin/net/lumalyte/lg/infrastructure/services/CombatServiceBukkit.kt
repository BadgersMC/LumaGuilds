package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.CombatService
import net.lumalyte.lg.application.services.ModeService
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RelationType
import org.slf4j.LoggerFactory
import java.util.UUID

class CombatServiceBukkit(private val modeService: ModeService) : CombatService {

    private val logger = LoggerFactory.getLogger(CombatServiceBukkit::class.java)

    override fun isPvpAllowed(attackerId: UUID, victimId: UUID): Boolean {
        val allowed = modeService.isPvpAllowed(attackerId, victimId)

        if (!allowed) {
            val reason = getPvpBlockReason(attackerId, victimId)
            logger.debug("PvP blocked between $attackerId and $victimId: $reason")
        }

        return allowed
    }

    override fun isPvpAllowedInTerritory(playerId: UUID, territoryGuildId: UUID): Boolean {
        val allowed = modeService.isPvpAllowedInTerritory(playerId, territoryGuildId)

        if (!allowed) {
            val reason = getTerritoryPvpBlockReason(playerId, territoryGuildId)
            logger.debug("PvP blocked for $playerId in territory of $territoryGuildId: $reason")
        }

        return allowed
    }

    override fun canAttack(attackerId: UUID, victimId: UUID, territoryGuildId: UUID?): Boolean {
        // Check player-to-player PvP rules
        if (!isPvpAllowed(attackerId, victimId)) {
            return false
        }

        // Check territory-specific rules if territory is owned
        if (territoryGuildId != null && !isPvpAllowedInTerritory(attackerId, territoryGuildId)) {
            return false
        }

        return true
    }

    override fun getPvpBlockReason(attackerId: UUID, victimId: UUID): String? {
        // Check if players are in the same guild
        // This logic is duplicated from ModeService but provides more specific error messages
        val attackerGuilds = getPlayerGuilds(attackerId)
        val victimGuilds = getPlayerGuilds(victimId)

        for (attackerGuildId in attackerGuilds) {
            for (victimGuildId in victimGuilds) {
                if (attackerGuildId == victimGuildId) {
                    return "Cannot attack members of the same guild"
                }
            }
        }

        // Check peaceful mode restrictions
        for (attackerGuildId in attackerGuilds) {
            val attackerMode = modeService.getGuildMode(attackerGuildId)
            if (attackerMode == net.lumalyte.lg.domain.entities.GuildMode.PEACEFUL) {
                return "Your guild is in Peaceful mode"
            }
        }

        for (victimGuildId in victimGuilds) {
            val victimMode = modeService.getGuildMode(victimGuildId)
            if (victimMode == net.lumalyte.lg.domain.entities.GuildMode.PEACEFUL) {
                return "Target's guild is in Peaceful mode"
            }
        }

        // Check alliance restrictions
        for (attackerGuildId in attackerGuilds) {
            for (victimGuildId in victimGuilds) {
                val relationType = getRelationType(attackerGuildId, victimGuildId)
                if (relationType == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
                    return "Cannot attack allied guild members"
                }
            }
        }

        return null
    }

    override fun getTerritoryPvpBlockReason(playerId: UUID, territoryGuildId: UUID): String? {
        val territoryMode = modeService.getGuildMode(territoryGuildId)

        // Check territory peaceful mode
        if (territoryMode == net.lumalyte.lg.domain.entities.GuildMode.PEACEFUL) {
            return "Cannot PvP in Peaceful guild territory"
        }

        // Check if player is in the same guild
        val playerGuilds = getPlayerGuilds(playerId)
        if (playerGuilds.contains(territoryGuildId)) {
            return "Cannot PvP in your own guild territory"
        }

        // Check alliance restrictions
        for (playerGuildId in playerGuilds) {
            val relationType = getRelationType(playerGuildId, territoryGuildId)
            if (relationType == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
                return "Cannot PvP in allied guild territory"
            }
        }

        return null
    }

    // Helper methods to access services without circular dependencies
    // These would normally be injected or accessed through a service locator
    private fun getPlayerGuilds(playerId: UUID): Set<UUID> {
        // This would be injected in a real implementation
        // For now, return empty set as a placeholder
        return emptySet()
    }

    private fun getRelationType(guildA: UUID, guildB: UUID): net.lumalyte.lg.domain.entities.RelationType {
        // This would be injected in a real implementation
        // For now, return NEUTRAL as a placeholder
        return net.lumalyte.lg.domain.entities.RelationType.NEUTRAL
    }
}
