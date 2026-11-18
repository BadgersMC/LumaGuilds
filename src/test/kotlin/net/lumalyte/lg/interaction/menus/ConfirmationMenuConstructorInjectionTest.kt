package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.logging.Logger
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ConfirmationMenuConstructorInjectionTest {

    @Test
    fun `confirmation menu is not a Koin component`() {
        assertFalse(
            KoinComponent::class.java.isAssignableFrom(ConfirmationMenu::class.java),
            "ConfirmationMenu must receive LangService by constructor, not Koin service location",
        )
    }

    @Test
    fun `confirmation menu requires LangService in its primary constructor without default`() {
        val constructor = assertNotNull(ConfirmationMenu::class.primaryConstructor)
        val langParam = constructor.parameters.singleOrNull { it.type.classifier == LangService::class }
        assertNotNull(langParam, "ConfirmationMenu primary constructor must declare a LangService parameter")
        assertFalse(langParam.isOptional, "LangService parameter must not have a default value")
    }

    @Test
    fun `menu factory passes its injected LangService to Java confirmation menus for both overloads`() {
        val lang = mockk<LangService>(relaxed = true)
        val platform = mockk<PlatformDetectionService>(relaxed = true)
        val configService = mockk<ConfigService>()
        every { configService.loadConfig() } returns MainConfig().apply { bedrock.bedrockMenusEnabled = false }
        val player = mockk<Player>(relaxed = true)
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val factory = MenuFactory(platform, configService, Logger.getAnonymousLogger(), lang)

        val basic = factory.createConfirmationMenu(navigator, player, "title") {}
        val enhanced = factory.createConfirmationMenu(navigator, player, "title", "message", {})

        assertSame(lang, injectedLang(assertIs<ConfirmationMenu>(basic)))
        assertSame(lang, injectedLang(assertIs<ConfirmationMenu>(enhanced)))
    }

    private fun injectedLang(menu: ConfirmationMenu): Any? =
        ConfirmationMenu::class.java.getDeclaredField("lang").apply { isAccessible = true }.get(menu)
}
