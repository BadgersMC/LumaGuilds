package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.guild.GuildPartyManagementMenu
import net.lumalyte.lg.interaction.menus.guild.GuildSettingsMenu
import net.lumalyte.lg.interaction.menus.guild.HomeAccessMenu
import net.lumalyte.lg.interaction.menus.guild.RecruitmentController
import net.lumalyte.lg.interaction.menus.management.ClaimFlagMenu
import net.lumalyte.lg.interaction.menus.management.ClaimRenamingMenu
import net.lumalyte.lg.interaction.menus.management.ClaimWidePermissionsMenu
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-DI-1..3 behavior for worker java-b: menus use the exact injected [MenuServices] collaborators,
 * resolve nothing at construction, keep claim services unresolved on guard paths and keep optional fallbacks.
 */
class JavaBMenuServicesBehaviorTest {
    private val lang = mockk<LangService>()
    private val player = mockk<Player>(relaxed = true)
    private val guild = Guild(UUID.randomUUID(), "Enthusiast", createdAt = Instant.EPOCH)

    private fun delegate(target: Any, property: String): Lazy<*> =
        target.javaClass.getDeclaredField("$property\$delegate").apply { isAccessible = true }.get(target) as Lazy<*>

    private fun factory(provider: (MenuFactory) -> MenuServices): MenuFactory {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply {
            bedrock.bedrockMenusEnabled = false
            bedrock.forceBedrockMenus = false
        }
        return MenuFactory(mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), lang,
            menuServicesProvider = provider)
    }

    @Test
    fun claimMenusWithoutClaimUseInjectedLangAndNeverResolveClaimActions() {
        assertNull(GlobalContext.getOrNull())
        val feedback = Component.text("no claim")
        every { lang.msg("menu.common.feedback.no_claim") } returns feedback
        val services = testMenuServices(overrides = mapOf(LangService::class to lang))
        val navigator = mockk<MenuNavigator>(relaxed = true)

        listOf(
            ClaimFlagMenu(navigator, player, null, services = services),
            ClaimRenamingMenu(navigator, player, null, services = services),
            ClaimWidePermissionsMenu(navigator, player, null, services = services),
        ).forEach { it.open() }

        verify(exactly = 3) { player.sendMessage(feedback) }
        assertFalse(services.getClaimFlags.isInitialized(), "claim flags must stay lazy (claims may be disabled)")
        assertFalse(services.updateClaimName.isInitialized())
        assertFalse(services.getClaimPermissions.isInitialized())
        assertFalse(services.grantClaimWidePermission.isInitialized())
    }

    @Test
    fun homeAccessDeniedUsesInjectedRankServiceAndFactoryWithoutResolvingGuilds() {
        val ranks = mockk<RankService>()
        every { ranks.hasPermission(any(), guild.id, RankPermission.MANAGE_HOME) } returns false
        val denied = Component.text("denied")
        every { lang.msg("menu.home_access.permission_denied") } returns denied
        val menuFactory = mockk<MenuFactory>(relaxed = true)
        val services = testMenuServices(menuFactory, mapOf(
            RankService::class to ranks, LangService::class to lang, MenuFactory::class to menuFactory,
        ))
        val menu = HomeAccessMenu(MenuNavigator(player), player, guild, "Home", services = services)
        assertFalse(services.rankService.isInitialized(), "construction must not resolve collaborators")

        menu.open()

        verify(exactly = 1) { player.sendMessage(denied) }
        verify(exactly = 1) { menuFactory.createGuildHomeMenu(any(), player, guild) }
        assertFalse(services.guildService.isInitialized(), "the home lookup is skipped when permission is denied")
    }

    @Test
    fun disabledPartiesShortCircuitWithoutResolvingPartyService() {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply { partiesEnabled = false }
        val disabled = Component.text("disabled")
        every { lang.msg("menu.party.management.feedback.disabled") } returns disabled
        val services = testMenuServices(overrides = mapOf(ConfigService::class to config, LangService::class to lang))

        GuildPartyManagementMenu(mockk(relaxed = true), player, guild, services = services).open()

        verify(exactly = 1) { player.sendMessage(disabled) }
        assertFalse(services.partyService.isInitialized())
        assertFalse(services.memberService.isInitialized())
    }

    @Test
    fun guildSettingsKeepsAbsentCosmeticLedgerOptionalAndLeavesNonMembers() {
        val guilds = mockk<GuildService>()
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val services = testMenuServices(overrides = mapOf(
            GuildCosmeticUnlockService::class to null,
            LangService::class to lang,
        ))
        val menu = GuildSettingsMenu(navigator, player, guild, guilds, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), services = services)

        menu.open()

        verify(exactly = 1) { navigator.goBack() }
        assertFalse(services.guildCosmeticUnlockServiceOrNull.isInitialized())
        assertFalse(services.guildRecruitmentService.isInitialized())
        val themeAccess = GuildSettingsMenu::class.java.getDeclaredMethod("getThemeAccess").apply { isAccessible = true }
        assertNull(themeAccess.invoke(menu), "absent ledger keeps holiday themes locked")
        assertTrue(services.guildCosmeticUnlockServiceOrNull.isInitialized())
    }

    @Test
    fun recruitmentControllerUsesInjectedServicesLazily() {
        val recruitment = mockk<GuildRecruitmentService>()
        val services = testMenuServices(overrides = mapOf(GuildRecruitmentService::class to recruitment))
        val controller = RecruitmentController(player, {}, services = services)

        assertFalse(services.guildRecruitmentService.isInitialized())
        assertFalse(services.recruitmentDirectoryRepository.isInitialized())
        assertSame(recruitment, controller.service)
        assertTrue(services.guildRecruitmentService.isInitialized())
        assertFalse(services.langService.isInitialized())
    }

    @Test
    fun menuFactoryPassesItsSharedServicesToJavaMenus() {
        var builds = 0
        lateinit var services: MenuServices
        val owner = factory { built -> builds++; testMenuServices(built).also { services = it } }
        val navigator = mockk<MenuNavigator>(relaxed = true)
        assertEquals(0, builds, "MenuFactory construction must not build menu services")

        val menus = listOf(
            owner.createClaimFlagMenu(navigator, player, null),
            owner.createClaimWidePermissionsMenu(navigator, player, null),
            owner.createEditToolMenu(navigator, player, null),
            owner.createClaimListMenu(navigator, player),
            owner.createHomeAccessMenu(navigator, player, guild, "Home"),
            owner.createGuildRelationsMenu(navigator, player, guild),
            owner.createGuildWarManagementMenu(navigator, player, guild),
            owner.createTruceRequestMenu(navigator, player, guild),
            owner.createGuildStallMenu(navigator, player, guild),
        )

        assertEquals(1, builds)
        menus.forEach { menu ->
            val field = menu.javaClass.getDeclaredField("services").apply { isAccessible = true }
            assertSame(services, field.get(menu), menu.javaClass.simpleName)
        }
        assertFalse(services.langService.isInitialized(), "no menu resolves collaborators on construction")
        assertIs<HomeAccessMenu>(menus[4])
    }
}
