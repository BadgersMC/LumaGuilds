package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.bedrock.BedrockClaimCreationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockClaimPlayerMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockClaimTransferNamingMenu
import net.lumalyte.lg.interaction.menus.guild.GuildGettingStartedMenu
import org.bukkit.Location
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.UUID
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SimpleMenuConstructorInjectionTest {
    private fun factory(lang: LangService, bedrock: Boolean): MenuFactory {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply {
            this.bedrock.bedrockMenusEnabled = bedrock
            this.bedrock.forceBedrockMenus = bedrock
        }
        return MenuFactory(mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), lang)
    }

    @Test
    fun `four menu constructors require LangService and do not use their own Koin property delegate`() {
        val types: List<KClass<*>> = listOf(
            GuildGettingStartedMenu::class,
            BedrockClaimCreationMenu::class,
            BedrockClaimPlayerMenu::class,
            BedrockClaimTransferNamingMenu::class,
        )
        types.forEach { type ->
            val argument = type.primaryConstructor?.parameters?.singleOrNull {
                it.type.classifier == LangService::class
            }
            assertTrue(argument != null && !argument.isOptional, "${type.simpleName} requires LangService")
            assertTrue(type.java.declaredFields.none { it.name == "lang\$delegate" })
        }
        assertFalse(KoinComponent::class.java.isAssignableFrom(GuildGettingStartedMenu::class.java))
        // Bedrock classes still inherit BaseBedrockMenu's legacy Koin internals.
    }

    @Test
    fun `MenuFactory passes its language service to Java and Bedrock forms`() {
        val lang = mockk<LangService>()
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val javaMenu = factory(lang, false).createGuildGettingStartedMenu(navigator, player, null)
        assertSame(lang, declaredLang(javaMenu))

        val claim = mockk<Claim>()
        every { claim.id } returns UUID.randomUUID()
        val bedrockFactory = factory(lang, true)
        val forms = listOf(
            bedrockFactory.createClaimCreationMenu(player, navigator, mockk<Location>()),
            bedrockFactory.createClaimPlayerMenu(navigator, player, claim),
            bedrockFactory.createClaimTransferNamingMenu(navigator, claim, player),
        )
        assertTrue(forms[0] is BedrockClaimCreationMenu)
        assertTrue(forms[1] is BedrockClaimPlayerMenu)
        assertTrue(forms[2] is BedrockClaimTransferNamingMenu)
        forms.forEach { assertSame(lang, declaredLang(it)) }
    }

    private fun declaredLang(menu: Menu): Any? =
        menu.javaClass.getDeclaredField("lang").apply { isAccessible = true }.get(menu)
}
