package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.management.ClaimPlayerMenu
import net.lumalyte.lg.interaction.menus.management.ClaimPlayerMenuDependencies
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

/** The Java claim player menu receives explicit dependencies from MenuFactory. */
class ClaimPlayerMenuConstructorInjectionTest {
    private val lang = mockk<LangService>()
    private val players = mockk<GetPlayersWithPermissionInClaim>()

    private fun factory(): MenuFactory {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply {
            bedrock.bedrockMenusEnabled = false
            bedrock.forceBedrockMenus = false
        }
        return MenuFactory(mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), lang)
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    @Test
    fun javaClaimPlayerMenuIsNotKoinComponentAndHasNoInjectedDelegates() {
        assertFalse(KoinComponent::class.java.isAssignableFrom(ClaimPlayerMenu::class.java))
        assertFalse(ClaimPlayerMenu::class.java.declaredFields.any { it.name.endsWith("\$delegate") })
    }

    @Test
    fun factoryResolvesDependenciesLazilyAndPassesSameInstances() {
        val owner = factory()
        val navigator = mockk<MenuNavigator>()
        val player = mockk<Player>(relaxed = true)
        var requests = 0
        owner.bindClaimPlayerMenuDependencies {
            requests++
            ClaimPlayerMenuDependencies(lang, players)
        }
        assertEquals(0, requests, "do not resolve claim services when MenuFactory is constructed")

        val menu = assertIs<ClaimPlayerMenu>(owner.createClaimPlayerMenu(navigator, player, null))
        assertEquals(1, requests)
        assertSame(lang, field(menu, "lang"))
        assertSame(players, field(menu, "getPlayersWithPermissionInClaim"))
        assertSame(navigator, field(menu, "menuNavigator"))

        val feedback = Component.text("no claim")
        every { lang.msg("menu.common.feedback.no_claim") } returns feedback
        menu.open()
        verify(exactly = 1) { player.sendMessage(feedback) }
        verify(exactly = 0) { players.execute(any()) }
    }

    @Test
    fun unboundDependenciesFailClosedInsteadOfFallingBackToGlobalKoin() {
        val exception = assertFailsWith<IllegalStateException> {
            factory().createClaimPlayerMenu(mockk(), mockk<Player>(relaxed = true), null)
        }
        assertEquals("Claim player menu dependencies were not bound", exception.message)
    }

    @Test
    fun duplicateBindingsAreRejected() {
        val owner = factory()
        owner.bindClaimPlayerMenuDependencies { ClaimPlayerMenuDependencies(lang, players) }
        val exception = assertFailsWith<IllegalStateException> {
            owner.bindClaimPlayerMenuDependencies { error("should not replace existing provider") }
        }
        assertEquals("Claim player menu dependencies already bound", exception.message)
    }
}
