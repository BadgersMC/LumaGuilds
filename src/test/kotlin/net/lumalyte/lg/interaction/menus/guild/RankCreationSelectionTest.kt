package net.lumalyte.lg.interaction.menus.guild

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

internal class RankCreationSelectionTest {
    @AfterEach fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    @Test fun categoryDoesNotGrant() {
        val server = MockBukkit.mock()
        val player = server.addPlayer()
        configureServices()
        val menu = createMenu(player)
        openCategory(menu)
        val field = menu.javaClass.getDeclaredField("selectedPermissions").apply { isAccessible = true }
        assertTrue((field.get(menu) as Set<*>).isEmpty(), "Opening a category must not grant permissions")
    }

    private fun openCategory(menu: RankCreationMenu) {
        val method =
            menu.javaClass.getDeclaredMethod(
                "openPermissionCategorySelection",
                String::class.java,
                List::class.java,
            )
        method.isAccessible = true
        method.invoke(menu, "Banking", listOf(RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK))
    }

    private fun createMenu(player: Player): RankCreationMenu {
        val menu =
            spyk(
                RankCreationMenu(
                    mockk<MenuNavigator>(relaxed = true),
                    player,
                    Guild(UUID.randomUUID(), "Selection", createdAt = Instant.EPOCH),
                ),
            )
        every { menu.open() } just Runs
        return menu
    }

    private fun configureServices() {
        configureBukkit()
        val lang = mockk<LangService>(relaxed = true)
        every { lang.raw(any()) } returns LABEL
        every { lang.msg(any()) } returns Component.text(LABEL)
        every { lang.msg(any(), any()) } returns Component.text(LABEL)
        every { lang.msg(any(), any(), any()) } returns Component.text(LABEL)
        val ranks = mockk<RankService>(relaxed = true)
        every { ranks.hasPermission(any(), any(), any()) } returns true
        startKoin { modules(servicesModule(lang, ranks)) }
    }

    private fun servicesModule(lang: LangService, ranks: RankService): org.koin.core.module.Module {
        return module {
            single { lang }
            single { ranks }
            single<ConfigService> { mockk { every { loadConfig() } returns MainConfig() } }
        }
    }

    private fun configureBukkit() {
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(org.bukkit.plugin.java.JavaPlugin::class)
        every {
            org.bukkit.plugin.java.JavaPlugin
                .getProvidingPlugin(any())
        } returns plugin
    }

    private companion object {
        const val LABEL = "Permission"
    }
}
