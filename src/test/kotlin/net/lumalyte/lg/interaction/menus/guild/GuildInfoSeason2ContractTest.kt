package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildInfoSeason2ContractTest {
    private val source = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInfoMenu.kt"
    ).toFile().readText()

    @Test
    fun publicGuildInfoRefreshesCanonicalGuildWithoutRequiringMembership() {
        assertTrue(source.contains("guild = guildService.getGuild(guild.id) ?: run"))
        assertTrue(source.contains("menu.guild_info.feedback.guild_missing"))
        assertFalse(source.contains("memberService.getMember(player.uniqueId, guild.id) == null"))
        assertFalse(source.contains("RankPermission."))
    }
}