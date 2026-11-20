package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/** REQ-194: [playerId] may not join [guildId] until unbanned. */
data class GuildBan(val guildId: UUID, val playerId: UUID, val bannedBy: UUID, val bannedAt: Instant)
