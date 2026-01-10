package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.domain.events.GuildCreatedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Listener that creates default guild chat channels when a guild is created.
 * This decouples the guild service from the party service, breaking the circular dependency.
 */
class GuildChannelCreationListener(
    private val partyService: PartyService,
    private val rankService: RankService
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildChannelCreationListener::class.java)

    @EventHandler
    fun onGuildCreated(event: GuildCreatedEvent) {
        createDefaultChannels(event.guild.id, event.guild.name, event.ownerId)
    }

    /**
     * Creates default guild chat channels when a guild is formed.
     * Creates: Guild_Chat (all members), Officer_Chat (officer+ ranks), Leader_Chat (leader only).
     */
    private fun createDefaultChannels(guildId: UUID, guildName: String, ownerId: UUID) {
        try {
            val ranks = rankService.listRanks(guildId)

            // Find leader rank (name matches "Leader" or "Owner" case-insensitive)
            val leaderRank = ranks.firstOrNull { rank ->
                rank.name.equals("Leader", ignoreCase = true) ||
                rank.name.equals("Owner", ignoreCase = true)
            }

            // Find officer ranks (name matches common officer/admin/moderator patterns)
            val officerRanks = ranks.filter { rank ->
                rank.name.matches(Regex("(?i)(officer|admin|moderator|co-?leader|leader|owner)"))
            }

            // 1. Guild_Chat - All ranks (no restrictions)
            val guildChat = Party(
                id = UUID.randomUUID(),
                name = "Guild_Chat",
                guildIds = setOf(guildId),
                leaderId = ownerId,
                status = PartyStatus.ACTIVE,
                createdAt = Instant.now(),
                restrictedRoles = null // No restrictions = all members
            )

            if (partyService.createParty(guildChat, suppressBroadcast = true) != null) {
                logger.info("Created Guild_Chat channel for guild $guildName")
            } else {
                logger.warn("Failed to create Guild_Chat channel for guild $guildName")
            }

            // 2. Officer_Chat - Officer+ ranks only (if officer ranks exist)
            if (officerRanks.isNotEmpty()) {
                val officerChat = Party(
                    id = UUID.randomUUID(),
                    name = "Officer_Chat",
                    guildIds = setOf(guildId),
                    leaderId = ownerId,
                    status = PartyStatus.ACTIVE,
                    createdAt = Instant.now(),
                    restrictedRoles = officerRanks.map { it.id }.toSet()
                )

                if (partyService.createParty(officerChat, suppressBroadcast = true) != null) {
                    logger.info("Created Officer_Chat channel for guild $guildName")
                } else {
                    logger.warn("Failed to create Officer_Chat channel for guild $guildName")
                }
            }

            // 3. Leader_Chat - Leader rank only (if leader rank exists)
            if (leaderRank != null) {
                val leaderChat = Party(
                    id = UUID.randomUUID(),
                    name = "Leader_Chat",
                    guildIds = setOf(guildId),
                    leaderId = ownerId,
                    status = PartyStatus.ACTIVE,
                    createdAt = Instant.now(),
                    restrictedRoles = setOf(leaderRank.id)
                )

                if (partyService.createParty(leaderChat, suppressBroadcast = true) != null) {
                    logger.info("Created Leader_Chat channel for guild $guildName")
                } else {
                    logger.warn("Failed to create Leader_Chat channel for guild $guildName")
                }
            }
        } catch (e: Exception) {
            // Non-critical operation - catching all exceptions to prevent service failure
            // Log error but don't fail guild creation - channels are non-critical
            logger.error("Failed to create default channels for guild $guildName", e)
        }
    }
}
