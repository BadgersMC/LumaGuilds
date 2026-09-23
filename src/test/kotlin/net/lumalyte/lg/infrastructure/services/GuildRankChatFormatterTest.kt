package net.lumalyte.lg.infrastructure.services

import kotlin.test.Test
import kotlin.test.assertEquals

class GuildRankChatFormatterTest {

    @Test
    fun `default format inserts guild rank immediately before RoseChat player token`() {
        assertEquals(
            "{prefix}&8[&b%lumaguilds_guild_rank%&8]&f {player}{separator}{message}",
            GuildRankChatFormatter.decorate("{prefix}{player}{separator}{message}"),
        )
    }

    @Test
    fun `custom rank template replaces rank marker`() {
        assertEquals(
            "{prefix}<gold>[<white>%lumaguilds_guild_rank%</white>]</gold> {player}{message}",
            GuildRankChatFormatter.decorate(
                "{prefix}{player}{message}",
                "<gold>[<white><rank></white>]</gold> ",
            ),
        )
    }
    @Test
    fun `existing guild rank placeholder is never duplicated`() {
        val format = "{prefix}[%lumaguilds_guild_rank%] {player}{separator}{message}"

        assertEquals(format, GuildRankChatFormatter.decorate(format))
    }

    @Test
    fun `format without a player token remains untouched`() {
        val format = "{channel-prefix}{message}"

        assertEquals(format, GuildRankChatFormatter.decorate(format))
    }

    @Test
    fun `placeholderapi player token is supported for custom RoseChat formats`() {
        assertEquals(
            "&8[&b%lumaguilds_guild_rank%&8]&f %player_name%: {message}",
            GuildRankChatFormatter.decorate("%player_name%: {message}"),
        )
    }
}
