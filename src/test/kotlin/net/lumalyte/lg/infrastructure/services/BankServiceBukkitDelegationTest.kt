package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import io.mockk.every
import io.mockk.spyk
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankServiceBukkitDelegationTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var bank: BankServiceBukkit
    private lateinit var gold: GuildGoldService
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private var personalBalance = 1_000L
    private var dailyLimit = 1_000L
    private var withdrawalFee = 0.0
    private var physicalBalance = 1_000L
    private var depositFee = 0.0
    private var memberBankPermission = true
    private var throwAfterPersonalDebit = false

    @BeforeEach fun setup() {
        MockBukkit.mock()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        gold = GuildGoldService(
            GuildGoldRepositorySQL(storage),
            GuildGoldPolicyProvider { GuildGoldPolicy(1, 1_000, 1.0, dailyLimit, depositFee, withdrawalFee, 128, 15, 1_000, 1_000, false) },
            GuildGoldCapacityProvider { GuildGoldCapacity(500, 0) },
            authorization = object : GuildGoldAuthorizationPort {
                override fun canDeposit(playerId: UUID, guildId: UUID) = memberBankPermission
                override fun canWithdraw(playerId: UUID, guildId: UUID) = memberBankPermission
            },
            personalEconomy = object : PersonalEconomyPort {
                override fun isAvailable() = true
                override fun balance(playerId: UUID) = personalBalance
                override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
                    if (amount > personalBalance) return ExternalTransferResult.Rejected("insufficient")
                    personalBalance -= amount
                    if (throwAfterPersonalDebit) error("Provider lost reply after debit")
                    return ExternalTransferResult.Applied
                }
                override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
                    personalBalance += amount
                    return ExternalTransferResult.Applied
                }
            },
            physicalGold = object : PhysicalGoldPort {
                override fun availableValue(playerId: UUID) = physicalBalance
                override fun reserve(playerId: UUID, requestedValue: Long): PhysicalReservationResult {
                    if (requestedValue > physicalBalance) return PhysicalReservationResult.Insufficient
                    physicalBalance -= requestedValue
                    return PhysicalReservationResult.Reserved(PhysicalGoldReservation(UUID.randomUUID(), playerId, requestedValue))
                }
                override fun commit(reservation: PhysicalGoldReservation) = true
                override fun restore(reservation: PhysicalGoldReservation): Boolean {
                    physicalBalance += reservation.value
                    return true
                }
                override fun deliver(playerId: UUID, value: Long, transactionId: UUID) = ExternalTransferResult.Unavailable
            },
        )
        bank = BankServiceBukkit(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), goldService = gold)
    }

    @AfterEach fun cleanup() {
        storage.connection.close()
        MockBukkit.unmock()
    }

    private fun paidJoin(physical: Boolean, membershipSucceeds: Boolean = true,
        rankAvailable: Boolean = true, memberLimit: Int = 50): Pair<LfgServiceBukkit, net.lumalyte.lg.domain.entities.Guild> {
        val guild = net.lumalyte.lg.domain.entities.Guild(guildId, "Paid join", createdAt = java.time.Instant.EPOCH,
            isOpen = true, joinFeeEnabled = true, joinFeeAmount = 100)
        val rank = net.lumalyte.lg.domain.entities.Rank(UUID.randomUUID(), guildId, "Member")
        val config = net.lumalyte.lg.config.MainConfig().apply { vault.usePhysicalCurrency = physical }
        var joined = false
        val members = mockk<MemberService>(relaxed = true) {
            every { getPlayerGuilds(actorId) } answers { if (joined) setOf(guildId) else emptySet() }
            every { getMemberCount(guildId) } returns 0
            every { getMemberLimit(guildId) } returns memberLimit
            every { addMember(actorId, guildId, rank.id) } answers {
                joined = membershipSucceeds
                if (joined) net.lumalyte.lg.domain.entities.Member(actorId, guildId, rank.id, java.time.Instant.EPOCH) else null
            }
        }
        val bankFacade = spyk(bank) {
            every { getPlayerBalance(actorId) } answers { personalBalance.toInt() }
        }
        val service = LfgServiceBukkit(
            mockk { every { getById(guildId) } returns guild }, mockk(relaxed = true), members,
            mockk {
                every { calculatePlayerInventoryValue(actorId) } answers { physicalBalance.toInt() }
                every { addCurrency(guild, any(), any()) } answers {
                    bank.creditToGuildBank(guildId, secondArg(), thirdArg())
                }
            },
            mockk { every { loadConfig() } returns config }, mockk(relaxed = true), bankFacade,
            mockk { every { getDefaultRank(guildId) } returns if (rankAvailable) rank else null })
        return service to guild
    }

    @Test fun `paid physical join removes currency and credits canonical bank exactly once`() {
        val (lfg, guild) = paidJoin(physical = true)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Success)
        assertEquals(900, physicalBalance)
        assertEquals(100, gold.balance(guildId))
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.AlreadyInGuild)
        assertEquals(900, physicalBalance)
    }

    @Test fun `paid personal join charges through canonical pipeline once`() {
        val (lfg, guild) = paidJoin(physical = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Success)
        assertEquals(900, personalBalance)
        assertEquals(100, gold.balance(guildId))
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.AlreadyInGuild)
        assertEquals(900, personalBalance)
    }

    @Test fun `failed paid membership blocks a second charge after recreation`() {
        val (lfg, guild) = paidJoin(physical = false, membershipSucceeds = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(900, personalBalance)
        assertEquals(100, gold.balance(guildId))
        val reopened = GuildGoldRepositorySQL(storage)
        assertTrue(reopened.prepare(GuildGoldMutation(UUID.randomUUID(), guildId, actorId,
            GuildGoldRoute.PERSONAL_ACCOUNT, GuildGoldDirection.CREDIT, 100, 0, "Retry admission")) is GuildGoldPreparation.Pending)
        val (retry, _) = paidJoin(physical = false, membershipSucceeds = false)
        assertTrue(retry.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(900, personalBalance)
        assertEquals(100, gold.balance(guildId))
    }

    @Test fun `paid join rejects bank overflow without charging player`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 450, GuildGoldRoute.SYSTEM, "seed")
        val (lfg, guild) = paidJoin(physical = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(1_000, personalBalance)
        assertEquals(450, gold.balance(guildId))
    }

    @Test fun `missing default rank prevents any join fee charge`() {
        val (lfg, guild) = paidJoin(physical = false, rankAvailable = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(1_000, personalBalance)
        assertEquals(0, gold.balance(guildId))
    }

    @Test fun `paid join uses unlocked membership limit before payment`() {
        val (lfg, guild) = paidJoin(physical = false, memberLimit = 0)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.GuildFull)
        assertEquals(1_000, personalBalance)
        assertEquals(0, gold.balance(guildId))
    }

    @Test fun `join requirement displays and checks the entire player charge including fee`() {
        depositFee = 0.01
        personalBalance = 100
        val (lfg, guild) = paidJoin(physical = false)
        assertEquals(101, lfg.getJoinRequirement(guild)?.amount)
        assertTrue(lfg.canJoinGuild(actorId, guild) is LfgJoinResult.InsufficientFunds)
        personalBalance = 101
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Success)
        assertEquals(0, personalBalance)
        assertEquals(100, gold.balance(guildId))
    }

    @Test fun `admission allows a nonmember payment without opening ordinary deposits`() {
        memberBankPermission = false
        assertEquals(null, bank.deposit(guildId, actorId, 100, "unauthorized normal deposit"))
        val (lfg, guild) = paidJoin(physical = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Success)
        assertEquals(900, personalBalance)
        assertEquals(100, gold.balance(guildId))
    }

    @Test fun `provider exception after paid join debit is not refunded or retried`() {
        throwAfterPersonalDebit = true
        val (lfg, guild) = paidJoin(physical = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(900, personalBalance)
        assertEquals(0, gold.balance(guildId))
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(900, personalBalance)
        assertEquals(0, gold.balance(guildId))
    }

    @Test fun `failed physical admission retains pending payment and blocks another item removal`() {
        val (lfg, guild) = paidJoin(physical = true, membershipSucceeds = false)
        assertTrue(lfg.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(900, physicalBalance)
        assertEquals(100, gold.balance(guildId))
        val (retry, _) = paidJoin(physical = true, membershipSucceeds = false)
        assertTrue(retry.joinGuild(actorId, guild) is LfgJoinResult.Error)
        assertEquals(900, physicalBalance)
        assertEquals(100, gold.balance(guildId))
    }

    @Test fun `bank reads canonical balance not a stale vault cache`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 300, GuildGoldRoute.SYSTEM, "seed")
        assertEquals(300, bank.getBalance(guildId))
        assertEquals(listOf(guildId to 300), bank.getTopBalances(1))
    }

    @Test fun `withdrawal preview uses canonical fee policy`() {
        withdrawalFee = 0.02
        assertEquals(2, bank.calculateWithdrawalFee(guildId, 100))
    }

    @Test fun `withdrawal maximum subtracts already used daily allowance`() {
        dailyLimit = 100
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        assertTrue(bank.withdrawOutcome(guildId, actorId, 80) is BankWithdrawalResult.Completed)
        assertEquals(20, bank.getMaxWithdrawalAmount(guildId, actorId))
    }

    @Test fun `personal transfers use canonical service without a legacy Vault provider`() {
        assertTrue(bank.deposit(guildId, actorId, 200, "deposit") != null)
        assertEquals(800, personalBalance)
        assertEquals(200, gold.balance(guildId))
        assertTrue(bank.withdrawOutcome(guildId, actorId, 100, "withdraw") is BankWithdrawalResult.Completed)
        assertEquals(900, personalBalance)
        assertEquals(100, gold.balance(guildId))
    }

    @Test fun `system credit enforces capacity and replays one transaction only`() {
        val transactionId = UUID.randomUUID()
        assertTrue(bank.creditToGuildBank(transactionId, guildId, 400, "credit"))
        assertTrue(bank.creditToGuildBank(transactionId, guildId, 400, "credit"))
        assertFalse(bank.creditToGuildBank(UUID.randomUUID(), guildId, 200, "overflow"))
        assertEquals(400, gold.balance(guildId))
    }

    @Test fun `system debit uses one fee free transaction`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        val transactionId = UUID.randomUUID()
        assertTrue(bank.deductFromGuildBank(transactionId, guildId, 100, "cost"))
        assertTrue(bank.deductFromGuildBank(transactionId, guildId, 100, "cost"))
        assertEquals(300, gold.balance(guildId))
    }

    @Test fun `physical system cost uses canonical gold rather than stored items`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        val config = net.lumalyte.lg.config.MainConfig().apply { vault.usePhysicalCurrency = true }
        val currency = PhysicalCurrencyServiceBukkit(
            mockk { every { loadConfig() } returns config }, bank)
        val guild = net.lumalyte.lg.domain.entities.Guild(guildId, "Cost test", createdAt = java.time.Instant.EPOCH)
        assertTrue(currency.deductCurrency(guild, 100, "Cost"))
        assertEquals(300, gold.balance(guildId))
        assertEquals(300, currency.calculateVaultCurrencyValue(guild))
        assertFalse(currency.addCurrency(guild, 300, "Over capacity"))
        assertEquals(300, gold.balance(guildId))
    }

    @Test fun `interest retry uses original amount after balance changes`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 200, GuildGoldRoute.SYSTEM, "seed")
        assertTrue(bank.creditInterest(guildId, 1234L, 0.5) is GuildGoldResult.Applied)
        assertEquals(300, gold.balance(guildId))
        assertTrue(bank.creditInterest(guildId, 1234L, 0.5) is GuildGoldResult.Applied)
        assertEquals(300, gold.balance(guildId))
    }

    @Test fun `interest respects capacity and does not partially credit`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        val result = bank.creditInterest(guildId, 1234L, 0.5)
        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED), result)
        assertEquals(400, gold.balance(guildId))
    }

    @Test fun `daily war charge survives scheduler recreation without charging twice`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        val config = net.lumalyte.lg.config.MainConfig().apply { guild.dailyWarMoneyCost = 100 }
        val configs = mockk<ConfigService> { every { loadConfig() } returns config }
        val wars = mockk<WarService> { every { getActiveWars() } returns listOf(
            net.lumalyte.lg.domain.entities.War(declaringGuildId = guildId, defendingGuildId = actorId)) }
        val guilds = mockk<GuildService> {
            every { getGuild(guildId) } returns net.lumalyte.lg.domain.entities.Guild(
                guildId, "Daily cost", createdAt = java.time.Instant.EPOCH)
            every { getGuild(actorId) } returns null
        }
        DailyWarCostsServiceBukkit(wars, guilds, configs, bank).applyDailyWarCosts()
        assertEquals(300, gold.balance(guildId))
        DailyWarCostsServiceBukkit(wars, guilds, configs, bank).applyDailyWarCosts()
        assertEquals(300, gold.balance(guildId))
    }

    @Test fun `scheduler retries failed marker without minting a second payment`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 200, GuildGoldRoute.SYSTEM, "seed")
        var settings = net.lumalyte.lg.domain.entities.BankSettings(guildId, interestRate = 0.5,
            lastInterestAccrual = java.time.Instant.now().minusSeconds(25 * 3600).toEpochMilli())
        val oldMarker = settings.lastInterestAccrual
        var allowMarker = false
        val settingsRepo = mockk<net.lumalyte.lg.application.persistence.BankSettingsRepository> {
            every { getByGuildId(guildId) } answers { settings }
            every { upsert(any()) } answers {
                if (allowMarker) settings = firstArg()
                allowMarker
            }
        }
        val guilds = mockk<net.lumalyte.lg.application.persistence.GuildRepository> {
            every { getAll() } returns setOf(net.lumalyte.lg.domain.entities.Guild(
                guildId, "Interest test", createdAt = java.time.Instant.EPOCH))
        }
        val config = mockk<ConfigService> { every { loadConfig() } returns net.lumalyte.lg.config.MainConfig() }
        val automation = BankAutomationService(mockk(relaxed = true), settingsRepo, guilds, bank, config)
        automation.accrueInterest()
        assertEquals(300, gold.balance(guildId))
        assertEquals(oldMarker, settings.lastInterestAccrual)
        allowMarker = true
        automation.accrueInterest()
        assertEquals(300, gold.balance(guildId))
        assertTrue(settings.lastInterestAccrual > oldMarker)
    }
}
