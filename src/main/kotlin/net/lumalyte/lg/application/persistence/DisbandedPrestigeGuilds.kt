package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.DisbandedPrestigeGuild
import java.util.UUID

/** Names of prestiged guilds that no longer exist, for history and server-firsts (REQ-163). */
fun interface DisbandedPrestigeGuilds {
    fun disbanded(guildId: UUID): DisbandedPrestigeGuild?
}
