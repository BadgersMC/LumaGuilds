package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import kotlin.test.*

class GuildRankChatRenderingTest {
    @Test
    fun hideRankRemovesInjectedPrefixAndKeepsMessage() {
        val format = GuildRankChatFormatter.decorate("{prefix}{player}{separator}{message}")!!
        assertEquals("{prefix}{player}{separator}{message}", GuildRankChatFormatter.render(format, "&aFounder", false))
        assertEquals("{prefix}{player}{separator}{message}", GuildRankChatFormatter.render(format, null, true))
        assertEquals(
            "{prefix}§aFounder§r{player}{message}",
            GuildRankChatFormatter.render("{prefix}%lumaguilds_guild_rank%{player}{message}", "&aFounder", true),
        )
    }
    @Test
    fun customRankWrapperCanBeHidden() {
        val template = "&6<&f<rank>&6>&r "
        val format = GuildRankChatFormatter.decorate("{player}{message}", template)!!
        assertEquals("{player}{message}", GuildRankChatFormatter.render(format, "&aFounder", false, template))
        assertEquals("{player}{message}", GuildRankChatFormatter.render("[%lumaguilds_guild_rank%] {player}{message}", "&aFounder", false))
    }
}
