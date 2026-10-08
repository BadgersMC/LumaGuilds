package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildChatReconnectSettingsRepository
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/** Authorizes guild-wide reconnect settings without depending on a chat platform. */
internal class GuildChatReconnectSettingsService(
    private val repository: GuildChatReconnectSettingsRepository,
    private val guildService: GuildService,
) {
    /** Returns the guild preference, disabled by default. */
    fun resetOnJoin(guildId: UUID): Boolean = repository.resetOnJoin(guildId)

    /** A member's own guild policy applies; allied guild settings never do. */
    fun shouldReset(playerId: UUID): Boolean =
        guildService.getPlayerGuilds(playerId).any { repository.resetOnJoin(it.id) }

    /** Apply a rendered preference only with current authority and unchanged persistence. */
    fun apply(guildId: UUID, rendered: Boolean, submitted: Boolean, actorId: UUID): Boolean {
        if (guildService.getGuild(guildId) == null ||
            !guildService.hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)
        ) {
            return false
        }
        return rendered == submitted || repository.resetOnJoin(guildId) == submitted ||
            repository.compareAndSet(guildId, rendered, submitted)
    }
}
