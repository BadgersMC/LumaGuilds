package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit
import net.lumalyte.lg.infrastructure.services.VaultPersonalEconomyAdapter
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Real menu + bank facade + canonical service/SQLite, with only the external economy controlled. */
internal class GuildBankAccountWithdrawalTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var sql: GuildGoldRepositorySQL
    private lateinit var menu: GuildBankMenu
    private lateinit var bank: BankServiceBukkit
    private lateinit var manager: VaultInventoryManager
    private lateinit var economy: Economy
    private lateinit var player: PlayerMock
    private lateinit var server: ServerMock
    private lateinit var lang: LangService
    private lateinit var history: BankRepository
    private val guildId = UUID.randomUUID()
    private val legacyAudits = mutableListOf<BankAudit>()
    private var personalGold = 0.0
    private var feeRate = 0.0
    private var depositFeeRate = 0.0
    private var rejectDebit = false
    private var rejectJournal = false
    private var rejectCompletion = false
    private var rejectRefund = false
    private var lastTransaction: UUID? = null
    private val balance get() = sql.getBalance(guildId)

    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin("Vault")
        player = server.addPlayer()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        mockkObject(PluginKeys)
        every { PluginKeys.getPlugin() } returns plugin
        mockkObject(GoldBalanceButton)
        every { GoldBalanceButton.convertToItems(any()) } throws IllegalStateException("Physical delivery forbidden")
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        sql = GuildGoldRepositorySQL(storage)
        seed(1_000)
        val repository = object : GuildGoldRepository by sql {
            override fun prepare(mutation: GuildGoldMutation): GuildGoldPreparation {
                if (rejectJournal) error("Journal unavailable")
                lastTransaction = mutation.transactionId
                return sql.prepare(mutation)
            }
            override fun applyExternalDebit(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long): GuildGoldResult {
                if (rejectDebit) error("Debit write unavailable")
                return sql.applyExternalDebit(mutation, capacity, periodStartEpochMs)
            }
            override fun completeExternal(transactionId: UUID) =
                !rejectCompletion && sql.completeExternal(transactionId)
            override fun compensateDebit(originalTransactionId: UUID, compensation: GuildGoldMutation,
                capacity: Long, periodStartEpochMs: Long, details: String): GuildGoldResult =
                if (rejectRefund) GuildGoldResult.Failed(originalTransactionId, false)
                else sql.compensateDebit(originalTransactionId, compensation, capacity, periodStartEpochMs, details)
        }
        economy = mockk(relaxed = true)
        server.servicesManager.register(Economy::class.java, economy, plugin, org.bukkit.plugin.ServicePriority.Normal)
        every { economy.getBalance(player) } answers { personalGold }
        every { economy.depositPlayer(player, any<Double>()) } answers {
            assertTrue(org.bukkit.Bukkit.isPrimaryThread())
            personalGold += secondArg<Double>()
            EconomyResponse(secondArg(), personalGold, EconomyResponse.ResponseType.SUCCESS, null)
        }
        val physical = net.lumalyte.lg.infrastructure.services.BukkitPhysicalGoldAdapter(
            { player }, Material.RAW_GOLD, Material.RAW_GOLD_BLOCK, 9)
        val gold = GuildGoldService(repository,
            GuildGoldPolicyProvider { GuildGoldPolicy(1, 100_000, 1.0, 50_000, depositFeeRate, feeRate, 128, 15, 1_000_000, 50_000, false) },
            GuildGoldCapacityProvider { GuildGoldCapacity(1_000_000, 0) },
            personalEconomy = VaultPersonalEconomyAdapter({ economy }, { player }),
            physicalGold = physical)
        manager = mockk(relaxed = true)
        every { manager.getGoldBalance(any()) } answers { balance }
        history = mockk(relaxed = true)
        every { history.getAuditForGuild(any(), any()) } answers { legacyAudits.toList() }
        val config = MainConfig().apply { bank.withdrawalFeePercent = 0.0 }
        val configService = mockk<ConfigService> { every { loadConfig() } answers {
            config.bank.withdrawalFeePercent = feeRate
            config.bank.maxWithdrawalFee = 15
            config
        } }
        GoldBalanceButton.initialize(plugin, configService)
        bank = BankServiceBukkit(history, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), configService,
            mockk(relaxed = true), mockk(relaxed = true), manager, goldService = gold)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(any(), any(), any()) } returns true
        lang = mockk {
            every { msg(any(), *anyVararg()) } returns Component.text("localized")
            every { raw(any()) } returns "localized"
        }
        stopKoin()
        startKoin { modules(module {
            single { manager }; single { lang }; single { members }; single { physical }
            single<BankService> { bank }; single<GuildService> { mockk(relaxed = true) }
            single<MenuFactory> { mockk(relaxed = true) }
        }) }
        menu = GuildBankMenu(mockk<MenuNavigator>(relaxed = true), player,
            Guild(guildId, "Payout test", createdAt = Instant.EPOCH))
    }

    @AfterEach fun cleanup() {
        storage.connection.close()
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    @Test fun creditsFullInventoryAccount() {
        val contents = Array(36) { ItemStack(Material.STONE, 64) }
        player.inventory.storageContents = contents
        assertTrue(withdraw())
        assertEquals(0, balance)
        assertEquals(1_000.0, personalGold)
        assertEquals(contents.toList(), player.inventory.storageContents.toList())
        verify(exactly = 0) { GoldBalanceButton.convertToItems(any()) }
    }

    @Test fun physicalOnlyBankMenuOpensWithoutEconomyProvider() {
        server.servicesManager.unregisterAll(PluginKeys.getPlugin())
        val unavailableBank = BankServiceBukkit(history, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk { every { loadConfig() } returns MainConfig() },
            mockk(relaxed = true), mockk(relaxed = true), manager,
            goldService = GuildGoldService(sql,
                GuildGoldPolicyProvider { GuildGoldPolicy(1, 100_000, 1.0, 50_000, 0.0, 0.0, 128, 15, 1_000_000, 50_000, false) },
                GuildGoldCapacityProvider { GuildGoldCapacity(1_000_000, 0) }))
        assertFalse(unavailableBank.isEconomyAvailable())
        org.koin.core.context.loadKoinModules(module { single<BankService> { unavailableBank } })
        menu = GuildBankMenu(mockk(relaxed = true), player, Guild(guildId, "Physical only", createdAt = Instant.EPOCH))
        menu.open()
        assertEquals(54, player.openInventory?.topInventory?.size, "Physical-only bank must open")
        assertFalse(withdraw())
        assertEquals(1_000, balance)
        assertEquals(0.0, personalGold)
    }

    @Test fun physicalDepositUsesCanonicalBalanceAndDoesNotMutateManager() {
        player.inventory.addItem(ItemStack(Material.RAW_GOLD, 40))
        val result = GuildBankMenu::class.java.getDeclaredMethod("handleDeposit", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(menu, 40) as Boolean
        assertTrue(result)
        assertEquals(1_040, balance)
        assertEquals(0, player.inventory.storageContents.filterNotNull().filter { it.type == Material.RAW_GOLD }.sumOf { it.amount })
    }

    @Test fun physicalWithdrawalMenuUsesCanonicalServiceAndDeliversItems() {
        val physicalMenu = GoldWithdrawMenu(PluginKeys.getPlugin() as JavaPlugin, player, guildId, "Test",
            manager, mockk(relaxed = true))
        GoldWithdrawMenu::class.java.getDeclaredMethod("confirmWithdrawal", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(physicalMenu, 18L)
        assertEquals(982, balance)
        assertEquals(2, player.inventory.storageContents.filterNotNull()
            .filter { it.type == Material.RAW_GOLD_BLOCK }.sumOf { it.amount })
        assertEquals(0.0, personalGold)
    }

    @Test fun vaultDepositAllMenuUsesCanonicalService() {
        player.inventory.addItem(ItemStack(Material.RAW_GOLD, 40))
        val depositMenu = GoldDepositMenu(PluginKeys.getPlugin() as JavaPlugin, player, guildId, "Test",
            manager, mockk(relaxed = true))
        GoldDepositMenu::class.java.getDeclaredMethod("depositAllGold")
            .apply { isAccessible = true }.invoke(depositMenu)
        assertEquals(1_040, balance)
        assertEquals(0, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
    }

    @Test fun draggedDepositCreditsCanonicalBalanceOnClose() {
        val depositMenu = GoldDepositMenu(PluginKeys.getPlugin() as JavaPlugin, player, guildId, "Test",
            manager, mockk(relaxed = true))
        depositMenu.open()
        player.openInventory.topInventory.setItem(0, ItemStack(Material.RAW_GOLD, 40))
        player.closeInventory()
        assertEquals(1_040, balance)
        assertEquals(0, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
    }

    @Test fun rejectedDraggedDepositReturnsItems() {
        legacyAudits += BankAudit(id = UUID.randomUUID(), guildId = guildId,
            actorId = player.uniqueId, action = AuditAction.PAYOUT_PENDING,
            transactionId = UUID.randomUUID(), details = "Pending")
        val depositMenu = GoldDepositMenu(PluginKeys.getPlugin() as JavaPlugin, player, guildId, "Test",
            manager, mockk(relaxed = true))
        depositMenu.open()
        player.openInventory.topInventory.setItem(0, ItemStack(Material.RAW_GOLD, 40))
        player.closeInventory()
        assertEquals(1_000, balance)
        assertEquals(40, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
    }

    @Test fun disconnectReturnsDraggedItemsWithoutCrediting() {
        val depositMenu = GoldDepositMenu(PluginKeys.getPlugin() as JavaPlugin, player, guildId, "Test",
            manager, mockk(relaxed = true))
        depositMenu.open()
        player.openInventory.topInventory.setItem(0, ItemStack(Material.RAW_GOLD, 40))
        val quitting = player
        depositMenu.onPlayerQuit(mockk { every { getPlayer() } returns quitting })
        assertEquals(1_000, balance)
        assertEquals(40, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
    }

    @Test fun vaultShiftDepositUsesCanonicalService() {
        player.inventory.addItem(ItemStack(Material.RAW_GOLD, 40))
        val listener = net.lumalyte.lg.interaction.listeners.VaultInventoryListener(
            PluginKeys.getPlugin() as JavaPlugin, manager, mockk(relaxed = true),
            net.lumalyte.lg.config.VaultConfig(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        listener.javaClass.getDeclaredMethod("depositAllGold", org.bukkit.entity.Player::class.java,
            UUID::class.java, String::class.java).apply { isAccessible = true }.invoke(listener, player, guildId, "Test")
        assertEquals(1_040, balance)
        assertEquals(0, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
        verify { lang.msg("menu.bank.feedback.deposit_success", "amount" to 40L) }
    }

    @Test fun canonicalSystemDebitRemainsAppliedWhenHistoryWriteThrows() {
        every { history.recordTransaction(any()) } throws IllegalStateException("History unavailable")
        val transaction = UUID.randomUUID()
        assertTrue(bank.deductFromGuildBank(transaction, guildId, 100, "System cost"))
        assertEquals(900, balance)
        assertTrue(bank.deductFromGuildBank(transaction, guildId, 100, "System cost"))
        assertEquals(900, balance)
        verify(atLeast = 1) { history.recordTransaction(any()) }
    }

    @Test fun rejectedPayoutRefundsGuild() {
        rejectPayout()
        assertFalse(withdraw())
        assertEquals(1_000, balance)
        assertEquals(0.0, personalGold)
    }

    @Test fun physicalDepositRejectionPreservesItems() {
        player.inventory.addItem(ItemStack(Material.RAW_GOLD, 40))
        val before = player.inventory.storageContents.map { it?.clone() }
        val result = bank.depositPhysical(PhysicalGoldRequest(
            UUID.randomUUID(), guildId, player.uniqueId, 100_001, "Over transaction limit"))
        assertTrue(result is GuildGoldResult.Rejected)
        assertEquals(1_000, balance)
        assertEquals(before, player.inventory.storageContents.toList())
    }

    @Test fun legacyPendingPayoutBlocksPhysicalDepositWithoutRemovingItems() {
        legacyAudits += BankAudit(id = UUID.randomUUID(), guildId = guildId,
            actorId = player.uniqueId, action = AuditAction.PAYOUT_PENDING,
            transactionId = UUID.randomUUID(), details = "Unresolved previous payout")
        player.inventory.addItem(ItemStack(Material.RAW_GOLD, 40))
        val result = bank.depositPhysical(PhysicalGoldRequest(
            UUID.randomUUID(), guildId, player.uniqueId, 40, "Blocked deposit"))
        assertTrue(result is GuildGoldResult.Failed && !result.compensationSucceeded)
        assertEquals(1_000, balance)
        assertEquals(40, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
    }

    @Test fun rejectionRefundsFee() {
        feeRate = 0.02
        rejectPayout()
        quickWithdrawAll()
        assertEquals(1_000, balance)
        assertEquals(0.0, personalGold)
        assertEquals(GuildGoldOperationStatus.COMPENSATED, operationStatus())
    }

    @Test fun thrownPayoutRefundsGuild() {
        every { economy.depositPlayer(player, any<Double>()) } throws IllegalStateException("Before credit")
        assertFalse(withdraw())
        assertEquals(1_000, balance)
        assertEquals(0.0, personalGold)
        assertEquals(GuildGoldOperationStatus.COMPENSATED, operationStatus())
    }

    @Test fun balanceLookupFailureRejects() {
        every { economy.getBalance(player) } throws UnsupportedOperationException("Balance unavailable")
        assertEquals(BankWithdrawalResult.Rejected, bank.withdrawOutcome(guildId, player.uniqueId, 1_000))
        assertEquals(1_000, balance)
        verify(exactly = 0) { economy.depositPlayer(player, any<Double>()) }
    }

    @Test fun failedDebitWriteBlocksPayout() {
        rejectDebit = true
        assertFalse(withdraw())
        assertEquals(1_000, balance)
        verify(exactly = 0) { economy.depositPlayer(player, any<Double>()) }
        assertEquals(GuildGoldOperationStatus.PREPARED, operationStatus())
    }

    @Test fun journalPrecedesPayout() {
        every { economy.depositPlayer(player, any<Double>()) } answers {
            assertEquals(0, balance)
            assertEquals(GuildGoldOperationStatus.BALANCE_APPLIED, operationStatus())
            personalGold += secondArg<Double>()
            EconomyResponse(secondArg(), personalGold, EconomyResponse.ResponseType.SUCCESS, null)
        }
        assertTrue(withdraw())
        assertEquals(GuildGoldOperationStatus.APPLIED, operationStatus())
    }

    @Test fun legacyPendingOperationBlocksRetry() {
        val transactionId = UUID.randomUUID()
        legacyAudits += BankAudit(transactionId = transactionId, guildId = guildId,
            actorId = player.uniqueId, action = AuditAction.PAYOUT_PENDING, details = "Interrupted legacy payout")
        assertEquals(BankWithdrawalResult.Ambiguous(transactionId),
            bank.withdrawOutcome(guildId, player.uniqueId, 100))
        assertEquals(1_000, balance)
        verify(exactly = 0) { economy.depositPlayer(player, any<Double>()) }
    }

    @Test fun failedJournalPreventsDebit() {
        rejectJournal = true
        assertFalse(withdraw())
        assertEquals(1_000, balance)
        verify(exactly = 0) { economy.depositPlayer(player, any<Double>()) }
    }

    @Test fun failedCompletionRecordBlocksRetryWithoutSecondPayout() {
        rejectCompletion = true
        val first = bank.withdrawOutcome(guildId, player.uniqueId, 100)
        assertTrue(first is BankWithdrawalResult.Ambiguous)
        assertEquals(first, bank.withdrawOutcome(guildId, player.uniqueId, 100))
        assertEquals(900, balance)
        assertEquals(100.0, personalGold)
    }

    @Test fun confirmedRefundAllowsLaterWithdrawal() {
        rejectPayout()
        assertEquals(BankWithdrawalResult.Rejected, bank.withdrawOutcome(guildId, player.uniqueId, 100))
        every { economy.depositPlayer(player, any<Double>()) } answers {
            personalGold += secondArg<Double>()
            EconomyResponse(secondArg(), personalGold, EconomyResponse.ResponseType.SUCCESS, null)
        }
        assertTrue(bank.withdrawOutcome(guildId, player.uniqueId, 100) is BankWithdrawalResult.Completed)
        assertEquals(900, balance)
        assertEquals(100.0, personalGold)
    }

    @Test fun failedRefundWriteRequiresReview() {
        rejectRefund = true
        rejectPayout()
        assertFalse(withdraw())
        assertEquals(0, balance)
        verify { lang.msg("menu.bank.feedback.withdraw_pending", *anyVararg()) }
    }

    @Test fun activityAuditFailureDoesNotHideCompletedPayment() {
        every { history.recordTransaction(any()) } throws UnsupportedOperationException("History unavailable")
        assertTrue(withdraw())
        assertEquals(0, balance)
        assertEquals(1_000.0, personalGold)
    }

    @Test fun successOverlayUsesOneMinusSign() {
        assertTrue(withdraw())
        verify { lang.msg("menu.bank.overlay.success.amount", "amount" to "-1000") }
    }

    @Test fun quickActionCreditsLiveBalance() {
        seed(1_000)
        quickWithdrawAll()
        assertEquals(0, balance)
        assertEquals(2_000.0, personalGold)
    }

    @Test fun depositAllUsesPhysicalGoldIncludingFeeWithoutVaultProvider() {
        depositFeeRate = 0.1
        server.servicesManager.unregisterAll(PluginKeys.getPlugin())
        player.inventory.addItem(ItemStack(Material.RAW_GOLD, 44))
        GuildBankMenu::class.java.getDeclaredMethod("handleQuickAction", Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(menu, -1, true)
        server.scheduler.performTicks(2)
        assertEquals(1_040, balance)
        assertEquals(0, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
        assertEquals(0.0, personalGold)
    }

    @Test fun withdrawAllIncludesFees() {
        feeRate = 0.02
        quickWithdrawAll()
        assertEquals(0, balance)
        assertEquals(985.0, personalGold)
    }

    @Test fun creditedExceptionAvoidsRefund() {
        every { economy.depositPlayer(player, any<Double>()) } answers {
            personalGold += secondArg<Double>()
            error("After credit")
        }
        assertFalse(withdraw())
        assertEquals(0, balance)
        assertEquals(1_000.0, personalGold)
        assertEquals(GuildGoldOperationStatus.BALANCE_APPLIED, operationStatus())
        verify { lang.msg("menu.bank.feedback.withdraw_pending", *anyVararg()) }
        verify(exactly = 0) { lang.msg("menu.bank.overlay.error.retry", *anyVararg()) }
    }

    private fun seed(amount: Long) {
        sql.apply(GuildGoldMutation(UUID.randomUUID(), guildId, player.uniqueId,
            GuildGoldRoute.SYSTEM, GuildGoldDirection.CREDIT, amount, 0, "seed"), 1_000_000, null)
    }
    private fun operationStatus() = sql.findOperation(requireNotNull(lastTransaction))?.status
    private fun rejectPayout() {
        every { economy.depositPlayer(player, any<Double>()) } returns
            EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Rejected")
    }
    private fun withdraw(): Boolean = GuildBankMenu::class.java.getDeclaredMethod("handleWithdrawal",
        Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(menu, 1_000) as Boolean
    private fun quickWithdrawAll() {
        GuildBankMenu::class.java.getDeclaredMethod("handleQuickAction", Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(menu, -1, false)
        server.scheduler.performTicks(2)
    }
}
