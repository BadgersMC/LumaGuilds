package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildDiscordRoleLink
import java.util.UUID

interface GuildDiscordRoleRepository {
    fun get(guildId: UUID): GuildDiscordRoleLink?
    fun getAll(): List<GuildDiscordRoleLink>
    fun upsert(link: GuildDiscordRoleLink): Boolean
    fun delete(guildId: UUID): Boolean
}
