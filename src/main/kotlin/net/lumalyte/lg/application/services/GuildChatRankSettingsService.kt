package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildChatRankSettingsRepository
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

class GuildChatRankSettingsService(
    private val repository: GuildChatRankSettingsRepository,
    private val guildService: GuildService,
) {
    fun ranksVisible(guildId: UUID): Boolean = repository.ranksVisible(guildId)
    fun setRanksVisible(guildId: UUID, visible: Boolean, actorId: UUID): Boolean {
        if (guildService.getGuild(guildId) == null ||
            !guildService.hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) return false
        return repository.setRanksVisible(guildId, visible)
    }
}
