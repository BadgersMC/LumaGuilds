package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RankPermission
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ModeServiceBukkit(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val relationService: RelationService,
    private val configService: ConfigService
) : ModeService {

    private val logger = LoggerFactory.getLogger(ModeServiceBukkit::class.java)

    override fun setGuildMode(guildId: UUID, newMode: GuildMode, actorId: UUID): net.lumalyte.lg.domain.entities.Guild? {
        try {
            // Validate permissions
            if (!canPlayerChangeMode(actorId, guildId)) {
                logger.warn("Player $actorId cannot change mode for guild $guildId")
                return null
            }

            // Validate the mode switch is allowed
            if (!canSwitchToMode(guildId, newMode)) {
                val reason = getModeSwitchBlockReason(guildId, newMode)
                logger.warn("Cannot switch guild $guildId to mode $newMode: $reason")
                return null
            }

            // Get current guild
            val currentGuild = guildService.getGuild(guildId)
            if (currentGuild == null) {
                logger.warn("Guild $guildId not found")
                return null
            }

            // Use GuildService to set the mode
            val success = guildService.setMode(guildId, newMode, actorId)
            if (success) {
                logger.info("Guild $guildId mode changed from ${currentGuild.mode} to $newMode by player $actorId")
                // Return updated guild
                return guildService.getGuild(guildId)
            } else {
                logger.error("Failed to update guild mode")
                return null
            }
        } catch (e: Exception) {
            logger.error("Error setting guild mode", e)
            return null
        }
    }

    override fun getGuildMode(guildId: UUID): GuildMode {
        return guildService.getMode(guildId)
    }

    override fun canSwitchToMode(guildId: UUID, targetMode: GuildMode): Boolean {
        val reason = getModeSwitchBlockReason(guildId, targetMode)
        return reason == null
    }

    override fun isModeSwitchOnCooldown(guildId: UUID): Boolean {
        val cooldown = getModeSwitchCooldown(guildId)
        return !cooldown.isZero
    }

    override fun getModeSwitchCooldown(guildId: UUID): Duration {
        val guild = guildService.getGuild(guildId)
        if (guild == null || guild.modeChangedAt == null) {
            return Duration.ZERO
        }

        val lastChange = guild.modeChangedAt
        val cooldownDuration = getModeSwitchCooldownDuration()
        val nextChangeTime = lastChange.plus(cooldownDuration)
        val now = Instant.now()

        return if (now.isBefore(nextChangeTime)) {
            Duration.between(now, nextChangeTime)
        } else {
            Duration.ZERO
        }
    }

    override fun canPlayerChangeMode(playerId: UUID, guildId: UUID): Boolean {
        return memberService.hasPermission(playerId, guildId, RankPermission.MANAGE_MODE)
    }

    override fun isPvpAllowed(attackerId: UUID, victimId: UUID): Boolean {
        // Get guilds for both players
        val attackerGuilds = memberService.getPlayerGuilds(attackerId)
        val victimGuilds = memberService.getPlayerGuilds(victimId)

        // If neither player is in a guild, PvP is always allowed
        if (attackerGuilds.isEmpty() && victimGuilds.isEmpty()) {
            return true
        }

        // Check each guild combination
        for (attackerGuildId in attackerGuilds) {
            for (victimGuildId in victimGuilds) {
                // Same guild - PvP not allowed regardless of mode
                if (attackerGuildId == victimGuildId) {
                    return false
                }

                // Check guild modes and relations
                val attackerMode = getGuildMode(attackerGuildId)
                val victimMode = getGuildMode(victimGuildId)

                // If either guild is peaceful, PvP is not allowed between their members
                if (attackerMode == GuildMode.PEACEFUL || victimMode == GuildMode.PEACEFUL) {
                    return false
                }

                // If both guilds are hostile, check relations
                val relationType = relationService.getRelationType(attackerGuildId, victimGuildId)
                if (relationType == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
                    return false // Allies cannot PvP
                }
            }
        }

        // If one player has no guild and the other has a peaceful guild, PvP is not allowed
        for (attackerGuildId in attackerGuilds) {
            if (victimGuilds.isEmpty()) {
                val attackerMode = getGuildMode(attackerGuildId)
                if (attackerMode == GuildMode.PEACEFUL) {
                    return false
                }
            }
        }

        for (victimGuildId in victimGuilds) {
            if (attackerGuilds.isEmpty()) {
                val victimMode = getGuildMode(victimGuildId)
                if (victimMode == GuildMode.PEACEFUL) {
                    return false
                }
            }
        }

        return true
    }

    override fun isPvpAllowedInTerritory(playerId: UUID, territoryGuildId: UUID): Boolean {
        val territoryMode = getGuildMode(territoryGuildId)

        // If territory guild is peaceful, PvP is not allowed in their territory
        if (territoryMode == GuildMode.PEACEFUL) {
            return false
        }

        // Get player's guilds
        val playerGuilds = memberService.getPlayerGuilds(playerId)

        // If player is not in any guild and territory is hostile, PvP is allowed
        if (playerGuilds.isEmpty()) {
            return true
        }

        // Check if player is in the same guild as territory owner
        if (playerGuilds.contains(territoryGuildId)) {
            return false // Cannot PvP in own guild territory
        }

        // Check relations between player's guilds and territory guild
        for (playerGuildId in playerGuilds) {
            val relationType = relationService.getRelationType(playerGuildId, territoryGuildId)
            if (relationType == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
                return false // Allies cannot PvP in each other's territory
            }
        }

        return true
    }

    override fun canGuildParticipateInWars(guildId: UUID): Boolean {
        val mode = getGuildMode(guildId)
        return mode == GuildMode.HOSTILE
    }

    override fun getModeSwitchCooldownDuration(): Duration {
        val config = configService.loadConfig()
        return Duration.ofDays(config.guild.modeSwitchCooldownDays.toLong())
    }

    override fun isGuildInActiveWar(guildId: UUID): Boolean {
        return relationService.isGuildInActiveWar(guildId)
    }

    override fun canSendWarRequest(requestingGuildId: UUID, targetGuildId: UUID): Boolean {
        // Both guilds must be hostile to participate in wars
        val requestingMode = getGuildMode(requestingGuildId)
        val targetMode = getGuildMode(targetGuildId)

        return requestingMode == GuildMode.HOSTILE && targetMode == GuildMode.HOSTILE
    }

    override fun getModeSwitchBlockReason(guildId: UUID, targetMode: GuildMode): String? {
        val currentMode = getGuildMode(guildId)

        // Cannot switch to the same mode
        if (currentMode == targetMode) {
            return "Guild is already in $targetMode mode"
        }

        // Cannot switch to Peaceful if in active war
        if (targetMode == GuildMode.PEACEFUL && isGuildInActiveWar(guildId)) {
            return "Cannot switch to Peaceful mode while in active war"
        }

        // Check cooldown
        if (isModeSwitchOnCooldown(guildId)) {
            val remainingCooldown = getModeSwitchCooldown(guildId)
            val minutes = remainingCooldown.toMinutes()
            return "Mode switch on cooldown. $minutes minutes remaining"
        }

        return null // No blocking reason
    }
}
