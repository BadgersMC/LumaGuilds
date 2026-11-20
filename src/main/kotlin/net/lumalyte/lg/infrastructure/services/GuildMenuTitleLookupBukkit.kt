package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.GuildMenuTitleLookup
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.utils.MenuTitleBuilder
import org.bukkit.Bukkit
import java.util.UUID

internal class GuildMenuTitleLookupBukkit(
    private val guilds: GuildService,
    private val platforms: PlatformDetectionService,
) : GuildMenuTitleLookup {
    override fun title(guildId: UUID, viewerId: UUID, rows: Int, plainTitle: String): String? {
        val viewer = Bukkit.getPlayer(viewerId) ?: return null
        if (rows !in 3..6 || platforms.isBedrockPlayer(viewer)) return null
        val guild = guilds.getGuild(guildId) ?: return null
        val theme = guild.guiTheme.resolved()
        if (!theme.hasBackground || nexoGlyph("guild_bg_${theme.name.lowercase()}_${rows}_row") == null) return null
        return MenuTitleBuilder.build(theme, rows, plainTitle)
    }
}
