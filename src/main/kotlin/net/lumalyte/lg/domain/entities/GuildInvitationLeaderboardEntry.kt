package net.lumalyte.lg.domain.entities

import java.util.UUID

data class GuildInvitationLeaderboardEntry(
    val inviterPlayerId: UUID,
    val inviteCount: Int
)
