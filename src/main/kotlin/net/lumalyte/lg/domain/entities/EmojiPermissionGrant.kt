package net.lumalyte.lg.domain.entities

import java.util.UUID

const val MAX_EMOJI_PERMISSION_LENGTH = 255

data class EmojiPermissionGrant(
    val playerId: UUID,
    val guildId: UUID,
    val permission: String,
)
