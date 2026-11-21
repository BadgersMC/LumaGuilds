package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.management.ClaimTrustMenu
import net.lumalyte.lg.interaction.menus.management.ClaimTrustMenuDependencies
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/** The same constructor-injected Java menu is used by legacy Koin and inactive Nexus factories. */
class ClaimTrustMenuConstructorInjectionTest {
    private val lang = mockk<LangService>()
    private val players = mockk<GetPlayersWithPermissionInClaim>()
    private val permissions = mockk<GetClaimPlayerPermissions>()

    private fun factory(): MenuFactory {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply {
            bedrock.bedrockMenusEnabled = false
            bedrock.forceBedrockMenus = false
        }
        return MenuFactory(mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), lang)
    }

    private fun dependencies() = ClaimTrustMenuDependencies(lang, players, permissions)

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    @Test
    fun javaClaimTrustMenuHasNoKoinComponentOrInjectedDelegates() {
        assertFalse(KoinComponent::class.java.isAssignableFrom(ClaimTrustMenu::class.java))
        assertFalse(ClaimTrustMenu::class.java.declaredFields.any { it.name.endsWith("\$delegate") })
        listOf("lang", "getPlayersWithPermissionInClaim", "getClaimPlayerPermissions").forEach { name ->
            assertNotNull(ClaimTrustMenu::class.java.declaredFields.singleOrNull { it.name == name })
        }
    }

    @Test
    fun factoryResolvesJavaClaimTrustDependenciesLazilyAndPassesSameInstances() {
        val owner = factory()
        val navigator = mockk<MenuNavigator>()
        val player = mockk<Player>(relaxed = true)
        var requests = 0
        owner.bindClaimTrustMenuDependencies {
            requests++
            dependencies()
        }
        assertEquals(0, requests, "do not resolve claim services when MenuFactory is constructed")

        val menu = assertIs<ClaimTrustMenu>(owner.createClaimTrustMenu(navigator, player, null))
        assertEquals(1, requests)
        assertSame(owner, field(menu, "menuFactory"))
        assertSame(lang, field(menu, "lang"))
        assertSame(players, field(menu, "getPlayersWithPermissionInClaim"))
        assertSame(permissions, field(menu, "getClaimPlayerPermissions"))
        assertSame(navigator, field(menu, "menuNavigator"))

        val feedback = Component.text("no claim")
        every { lang.msg("menu.common.feedback.no_claim") } returns feedback
        menu.open()
        verify(exactly = 1) { player.sendMessage(feedback) }
        verify(exactly = 0) { players.execute(any()) }
        verify(exactly = 0) { permissions.execute(any(), any()) }
    }

    @Test
    fun unboundClaimDependenciesFailClosedInsteadOfFallingBackToGlobalKoin() {
        val owner = factory()
        val exception = assertFailsWith<IllegalStateException> {
            owner.createClaimTrustMenu(mockk(), mockk<Player>(relaxed = true), null)
        }
        assertEquals("Claim trust menu dependencies were not bound", exception.message)
    }

    @Test
    fun duplicateBindingsAreRejectedWithoutReplacingTheFirstProvider() {
        val owner = factory()
        owner.bindClaimTrustMenuDependencies { dependencies() }
        val exception = assertFailsWith<IllegalStateException> {
            owner.bindClaimTrustMenuDependencies { error("should not replace existing provider") }
        }
        assertEquals("Claim trust menu dependencies already bound", exception.message)
    }
}
