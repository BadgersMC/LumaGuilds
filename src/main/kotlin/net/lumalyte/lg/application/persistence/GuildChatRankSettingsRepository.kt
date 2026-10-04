package net.lumalyte.lg.application.persistence

import java.util.UUID

interface GuildChatRankSettingsRepository {
    fun ranksVisible(guildId: UUID): Boolean
    fun setRanksVisible(guildId: UUID, visible: Boolean): Boolean
}
