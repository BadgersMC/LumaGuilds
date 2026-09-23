package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

data class GuildDiscordRoleLink(
    val guildId: UUID,
    val discordRoleId: String,
    val unlockedAt: Instant,
) {
    init {
        require(discordRoleId.matches(Regex("\\d{5,30}"))) { "Invalid Discord role ID" }
    }
}
