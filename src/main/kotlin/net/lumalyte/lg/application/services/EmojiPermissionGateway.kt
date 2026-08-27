package net.lumalyte.lg.application.services

import java.util.UUID

interface EmojiPermissionGateway {
    fun grant(playerId: UUID, permission: String): Boolean
    fun revoke(playerId: UUID, permission: String): Boolean
}
