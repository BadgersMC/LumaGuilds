package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.ChronicleGoal
import net.lumalyte.lg.application.persistence.ChronicleGoalRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildChronicleService
import net.lumalyte.lg.domain.rewards.GuildRewardRead
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.domain.rewards.RewardEntitlementResolver
import net.lumalyte.lg.domain.rewards.RewardOwnership
import net.lumalyte.lg.infrastructure.services.BukkitPhysicalGoldAdapter
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuServices
import net.lumalyte.lg.interaction.menus.testMenuServices
import net.lumalyte.lg.utils.GuiTheme
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
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
 * REQ-NEXUS-DI-1..3 behavioral regressions for worker java-a: menus read the explicitly supplied
 * [MenuServices] instances, child menus receive the same bundle, and nothing resolves before first use.
 */
class JavaAMenuServicesInjectionTest {
    private lateinit var server: ServerMock
    private val guild = Guild(UUID.randomUUID(), "Injected", createdAt = Instant.EPOCH)
    private val lang = mockk<LangService>(relaxed = true) {
        every { msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        every { raw(any()) } answers { firstArg() }
    }

    @BeforeEach
    fun setup() {
        stopKoin()
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
        MockBukkit.unmock()
        assertNull(GlobalContext.getOrNull(), "menus must never start or need a global Koin")
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun invoke(target: Any, name: String, vararg args: Any?): Any? =
        target.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(target, *args)

    // --- dashboard / control panel family ---

    @Test
    fun dashboardSecurityCheckUsesInjectedAdminOverrideAtCallTime() {
        val player = mockk<Player>(relaxed = true)
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val members = mockk<MemberService> { every { getMember(any(), guild.id) } returns null }
        val admin = mockk<AdminOverrideService> { every { hasOverride(any()) } returns false }
        val services = testMenuServices(overrides = mapOf(AdminOverrideService::class to admin, LangService::class to lang))
        val menu = GuildDashboard(navigator, player, guild, mockk(), mockk(), members, mockk(), services)
        assertFalse(services.adminOverrideService.isInitialized(), "admin override resolves only during open()")
        assertFalse(services.bankService.isInitialized())

        menu.open()

        verify(exactly = 1) { admin.hasOverride(player.uniqueId) }
        verify { player.sendMessage(Component.text("menu.dashboard.feedback.not_member")) }
        verify(exactly = 1) { navigator.goBack() }
        assertFalse(services.bankService.isInitialized(), "denied dashboard must not resolve bank services")
    }

    // --- relations / homes family ---

    @Test
    fun allyHomeAccessDeniesThroughInjectedRankServiceWithoutResolvingRelations() {
        val player = mockk<Player>(relaxed = true)
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val ranks = mockk<RankService> { every { hasPermission(any(), guild.id, RankPermission.MANAGE_HOME) } returns false }
        val services = testMenuServices(overrides = mapOf(RankService::class to ranks, LangService::class to lang))
        val menu = AllyHomeAccessMenu(navigator, player, guild, services)

        menu.open()

        verify(exactly = 1) { ranks.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME) }
        verify { player.sendMessage(Component.text("menu.ally_home_access.permission_denied")) }
        verify(exactly = 1) { navigator.returnTo(GuildHomeMenu::class.java, any()) }
        assertFalse(services.relationService.isInitialized())
        assertFalse(services.guildService.isInitialized())
    }

    @Test
    fun relationMenusShareTheSuppliedLazyCollaborators() {
        val services = testMenuServices(overrides = mapOf(LangService::class to lang))
        val player = mockk<Player>(relaxed = true)
        val menus = listOf(
            AlliesListMenu(mockk(), player, guild, services),
            EnemiesListMenu(mockk(), player, guild, services),
            AllianceRequestMenu(mockk(), player, guild, services),
            EnemyDeclarationMenu(mockk(), player, guild, services),
        )
        menus.forEach { menu ->
            assertSame(services.relationService, field(menu, "relationService\$delegate"), menu.javaClass.simpleName)
            assertSame(services.langService, field(menu, "lang\$delegate"), menu.javaClass.simpleName)
        }
        assertFalse(services.relationService.isInitialized())
        assertFalse(services.langService.isInitialized())
    }

    // --- info / members family ---

    @Test
    fun guildInfoUsesInjectedGuildServiceAndDoesNotResolveMemberServices() {
        val player = mockk<Player>(relaxed = true)
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val guilds = mockk<GuildService> { every { getGuild(guild.id) } returns null }
        val services = testMenuServices(overrides = mapOf(GuildService::class to guilds, LangService::class to lang))

        GuildInfoMenu(navigator, player, guild, services).open()

        verify(exactly = 1) { guilds.getGuild(guild.id) }
        verify { player.sendMessage(Component.text("menu.guild_info.feedback.guild_missing")) }
        verify(exactly = 1) { navigator.goBack() }
        assertFalse(services.memberService.isInitialized())
        assertFalse(services.warService.isInitialized())
    }

    // --- progression / chronicle family ---

    @Test
    fun progressionReadsInjectedProgressionRepositoryOnlyWhenRendering() {
        val repository = mockk<ProgressionRepository> { every { getGuildProgression(guild.id) } returns null }
        val services = testMenuServices(overrides = mapOf(ProgressionRepository::class to repository))
        val menu = GuildProgressionMenu(mockk(), mockk(relaxed = true), guild, mockk(), mockk(), mockk(),
            mockk(), mockk(), mockk(), services)
        assertFalse(services.progressionRepository.isInitialized())

        assertNull(invoke(menu, "repoProgression"))

        verify(exactly = 1) { repository.getGuildProgression(guild.id) }
    }

    private fun chronicleMenu(services: MenuServices, player: Player, pages: MutableList<GuildDialogPage>): GuildChronicleMenu {
        val presenter = GuildDialogPresenter { _, page, _, _ -> pages += page; true }
        return GuildChronicleMenu(MenuNavigator(player), player, guild, services, presenter)
    }

    private fun chronicleServices(extra: Map<kotlin.reflect.KClass<*>, Any?>): MenuServices {
        val catalog = RewardCatalog.chapterTwo()
        val repository = object : ChronicleGoalRepository {
            override fun read(guildId: UUID) = ChronicleGoal(null, 0)
            override fun compareAndSet(guildId: UUID, expected: ChronicleGoal, rewardId: String?) = false
        }
        val chronicle = GuildChronicleService(catalog, repository, { _, _ -> true }, { _, _ -> true },
            { GuildRewardRead.Available(1, 0, RewardEntitlementResolver(catalog).resolve(1, RewardOwnership())) })
        val members = mockk<MemberService> {
            every { getMember(any(), guild.id) } returns mockk()
            every { hasPermission(any(), guild.id, any()) } returns false
        }
        val config = mockk<ConfigService> { every { loadConfig() } returns MainConfig() }
        return testMenuServices(overrides = mapOf(
            GuildChronicleService::class to chronicle, MemberService::class to members,
            ConfigService::class to config, LangService::class to lang,
        ) + extra)
    }

    @Test
    fun chronicleFallsBackToSnapshotThemeWhenOptionalGuildServiceIsAbsent() {
        val player = server.addPlayer()
        val pages = mutableListOf<GuildDialogPage>()
        val services = chronicleServices(emptyMap())

        chronicleMenu(services, player, pages).open()

        assertEquals(guild.guiTheme, pages.last().theme)
        assertNull(services.guildServiceOrNull.value, "absent optional service stays absent")
    }

    @Test
    fun chronicleUsesLiveThemeFromOptionalGuildServiceWhenPresent() {
        val player = server.addPlayer()
        val pages = mutableListOf<GuildDialogPage>()
        val guilds = mockk<GuildService> { every { getGuild(guild.id) } returns guild.copy(guiTheme = GuiTheme.FROSTBOUND) }
        val services = chronicleServices(mapOf(GuildService::class to guilds))

        chronicleMenu(services, player, pages).open()

        assertEquals(GuiTheme.FROSTBOUND, pages.last().theme)
        verify(atLeast = 1) { guilds.getGuild(guild.id) }
    }

    // --- bank family ---

    private fun bankServices(): MenuServices {
        val vault = mockk<VaultInventoryManager>(relaxed = true) { every { getGoldBalance(any()) } returns 0L }
        val members = mockk<MemberService>(relaxed = true) { every { hasPermission(any(), any(), any()) } returns true }
        return testMenuServices(overrides = mapOf(
            LangService::class to lang, VaultInventoryManager::class to vault, MemberService::class to members,
        ))
    }

    @Test
    fun bankMenuPassesItsServicesToTheTransactionHistoryChild() {
        val player = server.addPlayer()
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val services = bankServices()
        val bank = GuildBankMenu(navigator, player, guild, services)
        val opened = slot<Menu>()
        every { navigator.openMenu(capture(opened)) } returns Unit

        invoke(bank, "openTransactionHistory")

        val history = assertIs<GuildBankTransactionHistoryMenu>(opened.captured)
        assertSame(services.bankService, field(history, "bankService\$delegate"))
        assertSame(services.langService, field(history, "lang\$delegate"))
    }

    @Test
    fun bankSecurityBackNavigationRebuildsBankMenuWithSameServices() {
        val player = server.addPlayer()
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val services = bankServices()
        val security = GuildBankSecurityMenu(navigator, player, guild, services)
        val fallback = slot<() -> Menu>()
        every { navigator.returnTo(GuildBankMenu::class.java, capture(fallback)) } returns Unit
        invoke(security, "setupNavigation")
        val back = (field(security, "mainPane") as net.lumalyte.lg.utils.inventoryframework.StaticPane).getItem(0, 0)!!

        back.callAction(mockk(relaxed = true))

        val rebuilt = assertIs<GuildBankMenu>(fallback.captured())
        assertSame(services.vaultInventoryManager, field(rebuilt, "vaultInventoryManager\$delegate"))
        assertSame(services.langService, field(rebuilt, "lang\$delegate"))
    }

    @Test
    fun goldDepositUsesInjectedBukkitPhysicalGoldAdapterOnlyWhenFinishing() {
        val player = server.addPlayer()
        val physical = mockk<BukkitPhysicalGoldAdapter>()
        every { physical.withDepositInventory<Any?>(any(), any(), any(), any()) } returns Unit
        val bank = mockk<BankService>(relaxed = true)
        val services = testMenuServices(overrides = mapOf(
            BukkitPhysicalGoldAdapter::class to physical, BankService::class to bank, LangService::class to lang,
        ))
        val menu = GoldDepositMenu(MockBukkit.createMockPlugin(), player, guild.id, guild.name,
            mockk(relaxed = true), mockk(relaxed = true), services)
        assertFalse(services.bukkitPhysicalGoldAdapter.isInitialized(), "physical gold adapter is resolved on finish")
        menu.open()

        invoke(menu, "finishDeposit", true)

        verify(exactly = 1) { physical.withDepositInventory<Any?>(player.uniqueId, any(), setOf(13, 22), any()) }
    }

    @Test
    fun goldWithdrawPreservesConstructorTimeBalanceReadFromInjectedBank() {
        val player = server.addPlayer()
        val bank = mockk<BankService>(relaxed = true)
        val services = testMenuServices(overrides = mapOf(LangService::class to lang, BankService::class to bank))
        assertFalse(services.bankService.isInitialized())
        GoldWithdrawMenu(MockBukkit.createMockPlugin(), player, guild.id, guild.name,
            mockk(relaxed = true), mockk(relaxed = true), services)
        assertTrue(services.bankService.isInitialized(), "withdraw inventory already loaded its balance in the legacy constructor")
        verify(atLeast = 1) { bank.getBalance(guild.id) }
    }

    // --- emoji (two classes in one file) ---

    @Test
    fun bothEmojiMenusUseTheSuppliedBundle() {
        val player = mockk<Player>(relaxed = true)
        val nexo = mockk<NexoEmojiService>(relaxed = true)
        val services = testMenuServices(overrides = mapOf(NexoEmojiService::class to nexo, LangService::class to lang))
        val parent = GuildEmojiMenu(mockk(relaxed = true), player, guild, services)
        val selection = EmojiSelectionMenu(mockk(relaxed = true), player, guild, parent, services)
        assertSame(services.nexoEmojiService, field(parent, "nexoEmojiService\$delegate"))
        assertSame(services.nexoEmojiService, field(selection, "nexoEmojiService\$delegate"))
        assertSame(services.menuFactory, field(selection, "menuFactory\$delegate"))
    }

    // --- factory create methods ---

    @Test
    fun menuFactoryPassesItsBoundServicesToJavaMenus() {
        val config = mockk<ConfigService> {
            every { loadConfig() } returns MainConfig().apply {
                bedrock.bedrockMenusEnabled = false
                bedrock.forceBedrockMenus = false
            }
        }
        lateinit var services: MenuServices
        var builds = 0
        val factory = MenuFactory(mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), lang,
            menuServicesProvider = { owner -> builds++; services = testMenuServices(owner, mapOf(LangService::class to lang)); services })
        assertEquals(0, builds)
        val player = mockk<Player>(relaxed = true)
        val navigator = mockk<MenuNavigator>(relaxed = true)

        val leave = factory.createGuildLeaveConfirmationMenu(navigator, player, guild)
        val kick = factory.createGuildKickMenu(navigator, player, guild)
        val allies = factory.createAlliesListMenu(navigator, player, guild)

        assertEquals(1, builds)
        assertSame(services.memberService, field(leave, "memberService\$delegate"))
        assertSame(services.guildService, field(kick, "guildService\$delegate"))
        assertSame(services.relationService, field(allies, "relationService\$delegate"))
        assertTrue(listOf(services.memberService, services.guildService, services.relationService).none { it.isInitialized() })
    }
}
