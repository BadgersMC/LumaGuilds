package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.InvitationService
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.domain.entities.GuildInvitation
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.values.Flag
import java.time.Instant
import java.util.UUID

/**
 * Hytale implementation of InvitationService.
 *
 * Handles guild invitation operations including sending, accepting, and declining invitations.
 */
class HytaleInvitationService(
    private val invitationRepository: GuildInvitationRepository,
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val rankRepository: RankRepository,
    private val playerService: PlayerService
) : InvitationService {

    override fun sendInvitation(guildId: UUID, invitedPlayerId: UUID, inviterPlayerId: UUID): Boolean {
        // Check if inviter has permission to invite
        val inviterMember = memberRepository.getByPlayerAndGuild(inviterPlayerId, guildId)
            ?: return false

        val guild = guildRepository.getById(guildId) ?: return false

        // Check if inviter has invite permission (usually officers and above)
        // For now, we'll allow any member to invite (you can add permission checks later)

        // Check if player is already in the guild
        if (memberRepository.getByPlayerAndGuild(invitedPlayerId, guildId) != null) {
            return false
        }

        // Check if invitation already exists
        if (invitationRepository.hasInvitation(invitedPlayerId, guildId)) {
            return false
        }

        // Get player names
        val inviterName = playerService.getPlayerName(inviterPlayerId) ?: "Unknown"

        // Create and add invitation
        val invitation = GuildInvitation(
            guildId = guildId,
            guildName = guild.name,
            invitedPlayerId = invitedPlayerId,
            inviterPlayerId = inviterPlayerId,
            inviterName = inviterName,
            timestamp = Instant.now()
        )

        return invitationRepository.add(invitation)
    }

    override fun acceptInvitation(playerId: UUID, guildId: UUID): Boolean {
        // Get the invitation
        val invitation = invitationRepository.getByPlayerAndGuild(playerId, guildId)
            ?: return false

        // Check if player is already in a guild
        val existingGuilds = memberRepository.getGuildsByPlayer(playerId)
        if (existingGuilds.isNotEmpty()) {
            return false // Player already in a guild
        }

        // Get the guild
        val guild = guildRepository.getById(guildId) ?: return false

        // Get the default rank for new members
        val defaultRank = rankRepository.getDefaultRank(guildId)?.id
            ?: return false // No default rank found

        // Add player to guild
        val member = Member(
            guildId = guildId,
            playerId = playerId,
            rankId = defaultRank,
            joinedAt = Instant.now()
        )

        val success = memberRepository.add(member)

        // Remove the invitation
        if (success) {
            invitationRepository.remove(playerId, guildId)
        }

        return success
    }

    override fun declineInvitation(playerId: UUID, guildId: UUID): Boolean {
        return invitationRepository.remove(playerId, guildId)
    }

    override fun getPlayerInvitations(playerId: UUID): List<GuildInvitation> {
        return invitationRepository.getByPlayer(playerId)
    }

    override fun cancelInvitation(playerId: UUID, guildId: UUID, cancellerPlayerId: UUID): Boolean {
        // Check if canceller has permission (must be in the guild)
        val cancellerMember = memberRepository.getByPlayerAndGuild(cancellerPlayerId, guildId)
            ?: return false

        return invitationRepository.remove(playerId, guildId)
    }

    override fun hasInvitation(playerId: UUID, guildId: UUID): Boolean {
        return invitationRepository.hasInvitation(playerId, guildId)
    }
}
