package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import java.util.UUID

interface GuildDisbandAnnouncementService {
    fun announce(
        guild: Guild,
        formerMemberIds: Set<UUID>,
        relatedGuilds: Map<UUID, RelationType>,
    )
}
