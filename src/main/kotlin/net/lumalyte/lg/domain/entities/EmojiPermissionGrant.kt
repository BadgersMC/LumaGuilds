package net.lumalyte.lg.domain.entities

import java.util.UUID

data class EmojiPermissionGrant(
    val playerId: UUID,
    val guildId: UUID,
    val permission: String,
)
