package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.ServerMock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real bank menu and service with a controllable Vault provider. */
internal class GuildBankAccountWithdrawalTest {
    private lateinit var menu: GuildBankMenu
    private lateinit var manager: VaultInventoryManager
    private lateinit var economy: Economy
    private lateinit var player: PlayerMock
    private lateinit var server: ServerMock
    private var guildGold = START_GOLD
    private var personalGold = 0.0
    private val config = MainConfig().apply { bank.withdrawalFeePercent = 0.0 }

    /** Build the real menu and bank service around fake external balances. */
    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin("Vault")
        player = server.addPlayer()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        mockkObject(PluginKeys)
        every { PluginKeys.getPlugin() } returns plugin
        mockkObject(GoldBalanceButton)
        every { GoldBalanceButton.convertToItems(any()) } throws IllegalStateException("Physical delivery forbidden")
        manager = mockk(relaxed = true)
        every { manager.getGoldBalance(any()) } answers { guildGold }
        every { manager.withdrawGold(any(), any(), any()) } answers {
            val amount = thirdArg<Long>()
            if (amount > guildGold) -1L else { guildGold -= amount; guildGold }
        }
        every { manager.depositGold(any(), any(), any()) } answers {
            guildGold += thirdArg<Long>()
            guildGold
        }
        economy = mockk(relaxed = true)
        every { economy.getBalance(player) } answers { personalGold }
        every { economy.depositPlayer(player, any<Double>()) } answers {
            assertTrue(org.bukkit.Bukkit.isPrimaryThread(), "Vault payout must run on the server thread")
            personalGold += secondArg<Double>()
            EconomyResponse(secondArg(), personalGold, EconomyResponse.ResponseType.SUCCESS, null)
        }
        server.servicesManager.register(Economy::class.java, economy, plugin, ServicePriority.Normal)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(any(), any(), any()) } returns true
        val memberRepo = mockk<MemberRepository>(relaxed = true)
        val rankRepo = mockk<RankRepository>(relaxed = true)
        every { rankRepo.getById(any())!!.permissions } returns setOf(RankPermission.WITHDRAW_FROM_BANK)
        val progression = mockk<ProgressionRepository>(relaxed = true)
        every { progression.getGuildProgression(any()) } returns null
        val configService = mockk<ConfigService> { every { loadConfig() } returns config }
        val bank = BankServiceBukkit(
            mockk(relaxed = true), memberRepo, rankRepo, progression,
            mockk<ProgressionConfigService>(relaxed = true), configService,
            mockk(relaxed = true), mockk(relaxed = true), manager,
        )
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } returns Component.text("localized")
            every { raw(any()) } returns "localized"
        }
        stopKoin()
        startKoin {
            modules(module {
                single { manager }
                single { lang }
                single { members }
                single<GuildService> { mockk(relaxed = true) }
                single<BankService> { bank }
                single<MenuFactory> { mockk(relaxed = true) }
            })
        }
        menu = GuildBankMenu(
            mockk<MenuNavigator>(relaxed = true), player,
            Guild(id = UUID.randomUUID(), name = "Payout test", createdAt = Instant.EPOCH),
        )
    }

    /** Release the server and globally registered mocks. */
    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    /** A full inventory must not prevent a personal-account withdrawal. */
    @Test
    fun creditsFullInventoryAccount() {
        val contents = Array(INVENTORY_SIZE) { ItemStack(Material.STONE, Material.STONE.maxStackSize) }
        player.inventory.storageContents = contents
        assertTrue(withdraw())
        assertEquals(0L, guildGold)
        assertEquals(START_GOLD.toDouble(), personalGold)
        assertEquals(contents.toList(), player.inventory.storageContents.toList())
        verify(exactly = 0) { GoldBalanceButton.convertToItems(any()) }
    }

    /** An explicit rejection by Vault must refund the guild and all charged fees. */
    @Test
    fun rejectedPayoutRefundsGuild() {
        every { economy.depositPlayer(player, any<Double>()) } returns
            EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Provider rejected payout")
        assertFalse(withdraw())
        assertEquals(START_GOLD, guildGold)
        assertEquals(0.0, personalGold)
    }

    /** Rejection restores the fee as well as the requested payout. */
    @Test
    fun rejectionRefundsFee() {
        config.bank.withdrawalFeePercent = FEE_RATE
        config.bank.maxWithdrawalFee = MAX_FEE
        every { economy.depositPlayer(player, any<Double>()) } returns
            EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Provider rejected payout")
        quickWithdrawAll()
        assertEquals(START_GOLD, guildGold)
        assertEquals(0.0, personalGold)
        verify(exactly = 1) { manager.depositGold(any(), any(), START_GOLD) }
        verify(exactly = 1) { manager.forceFlush(any()) }
    }

    /** A provider exception before any credit must not erase guild gold. */
    @Test
    fun thrownPayoutRefundsGuild() {
        every { economy.depositPlayer(player, any<Double>()) } throws IllegalStateException("Provider unavailable")
        assertFalse(withdraw())
        assertEquals(START_GOLD, guildGold)
        assertEquals(0.0, personalGold)
    }

    /** The actual quick action uses a fresh balance and pays Vault on the server thread. */
    @Test
    fun quickActionCreditsLiveBalance() {
        guildGold = START_GOLD * 2
        quickWithdrawAll()
        assertEquals(0L, guildGold)
        assertEquals((START_GOLD * 2).toDouble(), personalGold)
        verify(exactly = 1) { economy.depositPlayer(player, (START_GOLD * 2).toDouble()) }
    }

    /** Withdraw All leaves enough for the fee rather than requesting an unaffordable payout. */
    @Test
    fun withdrawAllIncludesFees() {
        config.bank.withdrawalFeePercent = FEE_RATE
        config.bank.maxWithdrawalFee = MAX_FEE
        quickWithdrawAll()
        assertEquals(0L, guildGold)
        assertEquals((START_GOLD - MAX_FEE).toDouble(), personalGold)
    }

    /** Refunding a provider that already credited before throwing would duplicate currency. */
    @Test
    fun creditedExceptionAvoidsRefund() {
        every { economy.depositPlayer(player, any<Double>()) } answers {
            personalGold += secondArg<Double>()
            error("Provider threw after credit")
        }
        assertFalse(withdraw())
        assertEquals(0L, guildGold)
        assertEquals(START_GOLD.toDouble(), personalGold)
        verify(exactly = 0) { manager.depositGold(any(), any(), any()) }
    }

    private fun quickWithdrawAll() {
        val method = GuildBankMenu::class.java.getDeclaredMethod(
            "handleQuickAction", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        method.invoke(menu, -1, false)
        server.scheduler.performTicks(2)
    }

    private fun withdraw(): Boolean {
        val method = GuildBankMenu::class.java.getDeclaredMethod("handleWithdrawal", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        return method.invoke(menu, START_GOLD.toInt()) as Boolean
    }

    private companion object {
        const val START_GOLD = 1_000L
        const val INVENTORY_SIZE = 36
        const val FEE_RATE = 0.02
        const val MAX_FEE = 15
    }
}
