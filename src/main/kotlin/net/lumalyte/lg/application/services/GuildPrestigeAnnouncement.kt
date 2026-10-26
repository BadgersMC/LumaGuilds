package net.lumalyte.lg.application.services

import java.util.UUID

/** Public presentation of a newly committed prestige, separate from settlement. */
fun interface GuildPrestigeAnnouncement {
    fun announce(guildId: UUID, prestigeCount: Int)
}
