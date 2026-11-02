package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lg.application.services.GuildPrestigeAnnouncement
import net.lumalyte.lg.application.services.GuildService
import org.bukkit.Bukkit
import java.util.UUID

/** Called by the shared prestige completion path on the server thread. */
class GuildPrestigeAnnouncementBukkit(
    private val guilds: GuildService,
    private val lang: LangService,
    private val broadcaster: (Component) -> Unit = { Bukkit.broadcast(it) },
) : GuildPrestigeAnnouncement {
    override fun announce(guildId: UUID, prestigeCount: Int) {
        val guild = guilds.getGuild(guildId) ?: error("Committed prestige guild is unavailable: $guildId")
        val tier = when (prestigeCount) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            else -> prestigeCount.toString()
        }
        broadcaster(lang.msg(
            "notification.guild.prestige.broadcast",
            // Nexus accepts serialized components as strings; serialization escapes literal tags.
            "guild" to MiniMessage.miniMessage().serialize(Component.text(guild.name)),
            "tier" to tier,
        ))
    }
}
