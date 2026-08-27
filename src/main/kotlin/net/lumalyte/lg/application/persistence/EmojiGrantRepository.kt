package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.EmojiPermissionGrant
import java.util.UUID

interface EmojiGrantRepository {
    fun getAll(): List<EmojiPermissionGrant>
    fun getForGuild(guildId: UUID): List<EmojiPermissionGrant>
    fun getForPlayerAndGuild(playerId: UUID, guildId: UUID): EmojiPermissionGrant?
    fun upsert(grant: EmojiPermissionGrant): Boolean
    fun delete(playerId: UUID, guildId: UUID): Boolean
}
