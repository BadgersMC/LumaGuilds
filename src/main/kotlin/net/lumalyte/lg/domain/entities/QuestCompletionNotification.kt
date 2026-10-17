package net.lumalyte.lg.domain.entities

import java.util.UUID

data class QuestCompletionNotification(
    val id: UUID,
    val playerId: UUID,
    val weekId: String,
    val questId: String,
    val guildId: UUID,
    val createdAt: Long,
    val deliveredAt: Long? = null,
)
