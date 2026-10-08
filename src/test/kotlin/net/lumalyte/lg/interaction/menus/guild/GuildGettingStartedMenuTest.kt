package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuildGettingStartedMenuTest {
    @AfterEach fun cleanup() {
        stopKoin(); unmockkStatic(JavaPlugin::class); MockBukkit.unmock()
    }
    @Test fun `Java guide renders all topics without long unwrapped lore`() {
        stopKoin()
        val server = MockBukkit.mock()
        val player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        val guild = Guild(UUID.randomUUID(), "Guide", createdAt = Instant.now())
        val service = mockk<GuildGettingStartedService>()
        every { service.snapshot(any(), any()) } returns GuildStartSnapshot(guild,
            GuildStartTopic.entries.map { GuildStartStep(it, GuildStartState.AVAILABLE, it, "onboarding.permission") }, 1, 5)
        val lang = mockk<LangService>()
        every { lang.msg(any(), *anyVararg()) } answers {
            Component.text("First steps explain permissions and unlock requirements without changing configured purchase prices.")
        }
        startKoin { modules(module { single { service }; single { lang } }) }
        GuildGettingStartedMenu(MenuNavigator(player), player, guild.id).open()
        val inventory = player.openInventory.topInventory
        assertEquals(27, inventory.size)
        for (slot in 9..17) {
            assertEquals(Material.BOOK, inventory.getItem(slot)!!.type)
            val lore = inventory.getItem(slot)!!.itemMeta.lore()!!
            assertTrue(lore.size > 2)
            val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            assertTrue(lore.drop(1).all { plain.serialize(it).length <= 48 })
        }
    }
}
