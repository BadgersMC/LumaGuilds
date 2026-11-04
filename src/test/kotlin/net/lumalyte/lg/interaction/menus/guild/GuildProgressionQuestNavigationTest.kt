package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID

private const val TEST_LANG = "localized"
private const val QUEST_PERMISSION = "lumaguilds.guild.quests"

class GuildProgressionQuestNavigationTest {
    @AfterEach fun cleanup() { stopKoin(); unmockkAll(); MockBukkit.unmock() }

    @Test fun `progression quest button opens member quest presentation`() = verifyClick(true, true)
    @Test fun `stale membership prevents progression quest navigation`() = verifyClick(false, true)
    @Test fun `explicit quest permission denial prevents navigation`() = verifyClick(true, false)

    /** Revoking a scoped view immediately denies quest authorization. */
    @Test fun scopedInspectorRevoked() {
        val playerId = UUID.randomUUID()
        val player = mockk<org.bukkit.entity.Player>()
        every { player.uniqueId } returns playerId
        every { player.hasPermission(QUEST_PERMISSION) } returns false
        val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)
        val members = mockk<MemberService>()
        every { members.getMember(playerId, guild.id) } returns null
        var scoped = true
        val admin = mockk<AdminOverrideService>()
        every { admin.getViewedGuild(playerId) } answers { if (scoped) guild.id else null }
        val menu =
            GuildProgressionMenu(
                mockk(), player, guild, mockk(), members, mockk(), mockk(), mockk(), mockk(), admin,
            )
        val check = GuildProgressionMenu::class.java.getDeclaredMethod("canOpenQuests").also { it.isAccessible = true }
        org.junit.jupiter.api.Assertions.assertTrue(check.invoke(menu) as Boolean)
        scoped = false
        org.junit.jupiter.api.Assertions.assertFalse(check.invoke(menu) as Boolean)
    }

    @Suppress("SpreadOperator") // MockK requires vararg matching in this test fixture.
    private fun verifyClick(member: Boolean, permission: Boolean) {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        val player = server.addPlayer()
        player.addAttachment(plugin, QUEST_PERMISSION, permission)
        val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)
        val members = mockk<MemberService>()
        every { members.getMember(player.uniqueId, guild.id) } returns if (member) mockk() else null
        val factory = mockk<MenuFactory>()
        val target = mockk<Menu>(relaxed = true)
        val navigator = mockk<MenuNavigator>(relaxed = true)
        every { factory.createGuildQuestsMenu(navigator, player, guild) } returns target
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } returns Component.text(TEST_LANG)
            every { raw(any()) } returns TEST_LANG
        }
        stopKoin(); startKoin { modules(module { single { lang }; single { members }; single { factory } }) }
        val menu = GuildProgressionMenu(navigator, player, guild, mockk(), members, mockk(), factory, mockk(), mockk())
        val pane = StaticPane(0, 0, 9, 6)
        val method = GuildProgressionMenu::class.java.getDeclaredMethod("addQuestsButton", StaticPane::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        method.invoke(menu, pane, 2, 5)
        pane.items.single().callAction(mockk(relaxed = true))
        verify(exactly = if (member && permission) 1 else 0) { navigator.openMenu(target) }
        clearMocks(navigator, answers = false)
        val bedrock = net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildProgressionInfoMenu(
            navigator, player, guild, plugin.logger)
        bedrock.javaClass.getDeclaredMethod("openQuests").apply { isAccessible = true }.invoke(bedrock)
        verify(exactly = if (member && permission) 1 else 0) { navigator.openMenu(target) }
    }
}
