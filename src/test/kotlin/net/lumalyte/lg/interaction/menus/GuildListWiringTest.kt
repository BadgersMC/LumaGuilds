package net.lumalyte.lg.interaction.menus

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GuildListWiringTest {
    @Test
    fun `guild list command opens bounded cross platform browser`() {
        val command = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt"
        ).readText()
        val factory = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt"
        ).readText()

        assertTrue(command.contains("menuFactory.createGuildListMenu(menuNavigator, player)"))
        assertTrue(factory.contains("fun createGuildListMenu("))
        assertTrue(factory.contains("BedrockGuildListMenu("))
        assertTrue(factory.contains("GuildListMenu(menuNavigator, player)"))
    }

    @Test
    fun `java and bedrock menus consume service pages without unbounded guild lookups`() {
        val java = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildListMenu.kt"
        ).readText()
        val bedrock = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildListMenu.kt"
        ).readText()

        assertTrue(java.contains("guildListService.getPage("))
        assertTrue(bedrock.contains("guildListService.getPage("))
        assertTrue(java.contains("guildListService.configuredPageSize()"))
        assertTrue(bedrock.contains("guildListService.configuredPageSize()"))
        assertFalse(java.contains("getAllGuilds"))
        assertFalse(bedrock.contains("getAllGuilds"))
        assertFalse(java.contains(".subList("))
        assertFalse(bedrock.contains(".subList("))
    }

    @Test
    fun `all four product sort modes and navigation are wired`() {
        val java = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildListMenu.kt"
        ).readText()
        assertTrue(java.contains("GuildListSortKey.ALL_TIME_ACTIVE"))
        assertTrue(java.contains("GuildListSortKey.WEEKLY_ACTIVE"))
        assertTrue(java.contains("GuildListSortKey.GUILD_LEVEL"))
        assertTrue(java.contains("GuildListSortKey.CREATED_AT"))
        assertTrue(java.contains("currentPage--"))
        assertTrue(java.contains("currentPage++"))
    }

    @Test
    fun `guild directory renders physical guild banners with shared white fallback resolver`() {
        val java = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildListMenu.kt"
        ).readText()
        val relation = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildRelationBrowserMenu.kt"
        ).readText()
        val resolver = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/GuildBannerItemResolver.kt"
        ).readText()

        assertTrue(java.contains("GuildBannerItemResolver.resolve(guild)"))
        assertFalse(java.contains("ItemStack.of(Material.BOOK)"))
        assertTrue(relation.contains("GuildBannerItemResolver.resolve(otherGuild)"))
        assertTrue(resolver.contains("Material.WHITE_BANNER"))
        assertTrue(resolver.contains("deserializeToItemStack()"))
    }

    @Test
    fun `page size is operator configured with required default`() {
        val config = File("src/main/resources/config.yml").readText()
        val loader = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt"
        ).readText()
        assertTrue(config.contains("guild_list:"))
        assertTrue(config.contains("page_size: 18"))
        assertTrue(loader.contains("config.getInt(\"guild_list.page_size\", 18)"))
    }
}
