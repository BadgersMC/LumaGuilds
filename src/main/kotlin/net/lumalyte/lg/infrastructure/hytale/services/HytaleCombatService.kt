package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.CombatService
import net.lumalyte.lg.domain.entities.GuildMode
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Hytale implementation of CombatService for managing PvP and combat rules.
 *
 * This service integrates with Hytale's Damage event system to enforce guild-based PvP restrictions.
 * PvP rules are determined by:
 * - Guild modes (PEACEFUL vs HOSTILE)
 * - Guild membership (same guild members cannot attack each other)
 * - Territory ownership (guilds can control PvP in their territory)
 */
class HytaleCombatService(
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository
) : CombatService {

    private val log = LoggerFactory.getLogger(HytaleCombatService::class.java)

    override fun isPvpAllowed(attackerId: UUID, victimId: UUID): Boolean {
        // Cannot attack yourself
        if (attackerId == victimId) {
            return false
        }

        // Get guilds for both players
        val attackerGuilds = memberRepository.getGuildsByPlayer(attackerId)
        val victimGuilds = memberRepository.getGuildsByPlayer(victimId)

        // If players share any guild, PvP is not allowed (friendly fire prevention)
        if (attackerGuilds.intersect(victimGuilds).isNotEmpty()) {
            return false
        }

        // Check if either player is in a PEACEFUL guild
        for (guildId in attackerGuilds) {
            val guild = guildRepository.getById(guildId)
            if (guild?.mode == GuildMode.PEACEFUL) {
                log.debug("PvP denied: Attacker $attackerId is in peaceful guild $guildId")
                return false
            }
        }

        for (guildId in victimGuilds) {
            val guild = guildRepository.getById(guildId)
            if (guild?.mode == GuildMode.PEACEFUL) {
                log.debug("PvP denied: Victim $victimId is in peaceful guild $guildId")
                return false
            }
        }

        // If neither player is in a peaceful guild and they're not in the same guild, PvP is allowed
        return true
    }

    override fun isPvpAllowedInTerritory(playerId: UUID, territoryGuildId: UUID): Boolean {
        // Get the guild that owns the territory
        val territoryGuild = guildRepository.getById(territoryGuildId) ?: run {
            log.warn("Territory guild $territoryGuildId not found")
            return false
        }

        // If the territory guild is PEACEFUL, no PvP is allowed in their territory
        if (territoryGuild.mode == GuildMode.PEACEFUL) {
            return false
        }

        // Check if the player is a member of the territory guild
        val playerGuilds = memberRepository.getGuildsByPlayer(playerId)
        val isPlayerMember = territoryGuildId in playerGuilds

        // If player is a member of the territory guild, check if they're in PEACEFUL mode themselves
        if (isPlayerMember) {
            // Member of this guild - PvP rules depend on guild mode (already checked above)
            return true
        }

        // Player is not a member - check if they're in any PEACEFUL guild
        for (guildId in playerGuilds) {
            val guild = guildRepository.getById(guildId)
            if (guild?.mode == GuildMode.PEACEFUL) {
                return false
            }
        }

        // If the territory is HOSTILE and player is not in a PEACEFUL guild, PvP is allowed
        return true
    }

    override fun canAttack(attackerId: UUID, victimId: UUID, territoryGuildId: UUID?): Boolean {
        // First check basic PvP allowance between players
        if (!isPvpAllowed(attackerId, victimId)) {
            return false
        }

        // If there's no territory (wilderness), basic PvP rules apply
        if (territoryGuildId == null) {
            return true
        }

        // Check territory-specific rules for both attacker and victim
        val attackerAllowedInTerritory = isPvpAllowedInTerritory(attackerId, territoryGuildId)
        val victimAllowedInTerritory = isPvpAllowedInTerritory(victimId, territoryGuildId)

        // Both players must be allowed to engage in PvP in this territory
        return attackerAllowedInTerritory && victimAllowedInTerritory
    }

    override fun getPvpBlockReason(attackerId: UUID, victimId: UUID): String? {
        // Check if they can PvP
        if (isPvpAllowed(attackerId, victimId)) {
            return null
        }

        // They're the same player
        if (attackerId == victimId) {
            return "You cannot attack yourself."
        }

        // Get guilds for both players
        val attackerGuilds = memberRepository.getGuildsByPlayer(attackerId)
        val victimGuilds = memberRepository.getGuildsByPlayer(victimId)

        // Check for shared guild membership
        val sharedGuilds = attackerGuilds.intersect(victimGuilds)
        if (sharedGuilds.isNotEmpty()) {
            val guildName = guildRepository.getById(sharedGuilds.first())?.name ?: "Unknown"
            return "You cannot attack members of your guild ($guildName)."
        }

        // Check if attacker is in a PEACEFUL guild
        for (guildId in attackerGuilds) {
            val guild = guildRepository.getById(guildId)
            if (guild?.mode == GuildMode.PEACEFUL) {
                return "Your guild (${guild.name}) is in PEACEFUL mode. PvP is disabled."
            }
        }

        // Check if victim is in a PEACEFUL guild
        for (guildId in victimGuilds) {
            val guild = guildRepository.getById(guildId)
            if (guild?.mode == GuildMode.PEACEFUL) {
                return "Target player is in a PEACEFUL guild (${guild.name})."
            }
        }

        // Generic fallback
        return "PvP is not allowed between you and this player."
    }

    override fun getTerritoryPvpBlockReason(playerId: UUID, territoryGuildId: UUID): String? {
        // Check if PvP is allowed in this territory
        if (isPvpAllowedInTerritory(playerId, territoryGuildId)) {
            return null
        }

        val territoryGuild = guildRepository.getById(territoryGuildId)
        if (territoryGuild == null) {
            return "Invalid territory."
        }

        // Check if territory guild is PEACEFUL
        if (territoryGuild.mode == GuildMode.PEACEFUL) {
            val playerGuilds = memberRepository.getGuildsByPlayer(playerId)
            val isPlayerMember = territoryGuildId in playerGuilds

            return if (isPlayerMember) {
                "Your guild (${territoryGuild.name}) is in PEACEFUL mode. PvP is disabled in your territory."
            } else {
                "PvP is not allowed in ${territoryGuild.name}'s territory (PEACEFUL guild)."
            }
        }

        // Check if player is in a PEACEFUL guild
        val playerGuilds = memberRepository.getGuildsByPlayer(playerId)
        for (guildId in playerGuilds) {
            val guild = guildRepository.getById(guildId)
            if (guild?.mode == GuildMode.PEACEFUL) {
                return "Your guild (${guild.name}) is in PEACEFUL mode. You cannot engage in PvP."
            }
        }

        // Generic fallback
        return "PvP is not allowed in this territory."
    }
}
