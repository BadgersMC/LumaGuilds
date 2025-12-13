package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.slf4j.Logger
import java.time.Instant
import java.util.UUID

/**
 * Tests for LfgServiceBukkit implementation.
 */
class LfgServiceBukkitTest {

    private lateinit var guildRepository: GuildRepository
    private lateinit var guildService: GuildService
    private lateinit var memberService: MemberService
    private lateinit var physicalCurrencyService: PhysicalCurrencyService
    private lateinit var configService: ConfigService
    private lateinit var vaultService: GuildVaultService
    private lateinit var bankService: BankService
    private lateinit var rankService: RankService
    private lateinit var logger: Logger

    private lateinit var lfgService: LfgServiceBukkit
    private lateinit var defaultRank: Rank

    private lateinit var openGuild: Guild
    private lateinit var closedGuild: Guild
    private lateinit var fullGuild: Guild
    private lateinit var openGuildWithFee: Guild

    @BeforeEach
    fun setUp() {
        // Initialize mocks
        guildRepository = mockk(relaxed = true)
        guildService = mockk(relaxed = true)
        memberService = mockk(relaxed = true)
        physicalCurrencyService = mockk(relaxed = true)
        configService = mockk(relaxed = true)
        vaultService = mockk(relaxed = true)
        bankService = mockk(relaxed = true)
        rankService = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        // Set up default config with maxMembersPerGuild = 50
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        // Create service
        lfgService = LfgServiceBukkit(
            guildRepository = guildRepository,
            guildService = guildService,
            memberService = memberService,
            physicalCurrencyService = physicalCurrencyService,
            configService = configService,
            vaultService = vaultService,
            bankService = bankService,
            rankService = rankService,
            logger = logger
        )

        // Set up default rank for guilds
        defaultRank = Rank(
            id = UUID.randomUUID(),
            guildId = UUID.randomUUID(),
            name = "Member",
            priority = 0,
            permissions = emptySet()
        )

        // Set up test guilds
        openGuild = Guild(
            id = UUID.randomUUID(),
            name = "Alpha Guild",
            createdAt = Instant.now(),
            isOpen = true,
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        closedGuild = Guild(
            id = UUID.randomUUID(),
            name = "Beta Guild",
            createdAt = Instant.now(),
            isOpen = false,
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        fullGuild = Guild(
            id = UUID.randomUUID(),
            name = "Charlie Guild",
            createdAt = Instant.now(),
            isOpen = true,
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        openGuildWithFee = Guild(
            id = UUID.randomUUID(),
            name = "Delta Guild",
            createdAt = Instant.now(),
            isOpen = true,
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )
    }

    // ===== getAvailableGuilds Tests =====

    @Test
    fun `getAvailableGuilds should return guilds where isOpen is true and has slots`() {
        // Given: Mix of open and closed guilds
        every { guildRepository.getAll() } returns setOf(openGuild, closedGuild)
        every { memberService.getMemberCount(openGuild.id) } returns 10
        every { memberService.getMemberCount(closedGuild.id) } returns 5

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should only return open guild
        assertEquals(1, result.size)
        assertEquals(openGuild.id, result[0].id)
    }

    @Test
    fun `getAvailableGuilds should exclude guilds where isOpen is false`() {
        // Given: Only closed guilds
        every { guildRepository.getAll() } returns setOf(closedGuild)

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should return empty list
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAvailableGuilds should exclude guilds at max capacity`() {
        // Given: Open guild that is full (50 members with max 50)
        every { guildRepository.getAll() } returns setOf(fullGuild)
        every { memberService.getMemberCount(fullGuild.id) } returns 50

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should return empty list
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAvailableGuilds should include guilds with available slots`() {
        // Given: Open guild with 49 members (1 slot available)
        every { guildRepository.getAll() } returns setOf(openGuild)
        every { memberService.getMemberCount(openGuild.id) } returns 49

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should return the guild
        assertEquals(1, result.size)
        assertEquals(openGuild.id, result[0].id)
    }

    @Test
    fun `getAvailableGuilds should return empty list when no guilds match criteria`() {
        // Given: No guilds
        every { guildRepository.getAll() } returns emptySet()

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should return empty list
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAvailableGuilds should return guilds sorted by name alphabetically`() {
        // Given: Multiple open guilds with different names
        val guildZ = openGuild.copy(id = UUID.randomUUID(), name = "Zeta Guild")
        val guildA = openGuild.copy(id = UUID.randomUUID(), name = "Alpha Guild")
        val guildM = openGuild.copy(id = UUID.randomUUID(), name = "Mike Guild")

        every { guildRepository.getAll() } returns setOf(guildZ, guildA, guildM)
        every { memberService.getMemberCount(any()) } returns 10

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should be sorted alphabetically
        assertEquals(3, result.size)
        assertEquals("Alpha Guild", result[0].name)
        assertEquals("Mike Guild", result[1].name)
        assertEquals("Zeta Guild", result[2].name)
    }

    @Test
    fun `getAvailableGuilds should include guilds with join requirements`() {
        // Given: Open guild with join fee
        every { guildRepository.getAll() } returns setOf(openGuildWithFee)
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should include guild with fee (filtering is for eligibility, not listing)
        assertEquals(1, result.size)
        assertEquals(openGuildWithFee.id, result[0].id)
        assertTrue(result[0].joinFeeEnabled)
        assertEquals(500, result[0].joinFeeAmount)
    }

    @Test
    fun `getAvailableGuilds should handle multiple guilds correctly`() {
        // Given: Mix of open, closed, and full guilds
        val openGuild2 = openGuild.copy(id = UUID.randomUUID(), name = "Echo Guild")
        every { guildRepository.getAll() } returns setOf(openGuild, closedGuild, fullGuild, openGuildWithFee, openGuild2)
        every { memberService.getMemberCount(openGuild.id) } returns 10
        every { memberService.getMemberCount(closedGuild.id) } returns 5
        every { memberService.getMemberCount(fullGuild.id) } returns 50 // Full
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 20
        every { memberService.getMemberCount(openGuild2.id) } returns 30

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should return only open guilds with slots, sorted by name
        assertEquals(3, result.size)
        assertEquals("Alpha Guild", result[0].name)
        assertEquals("Delta Guild", result[1].name)
        assertEquals("Echo Guild", result[2].name)
    }

    @Test
    fun `getAvailableGuilds should use config maxMembersPerGuild for capacity check`() {
        // Given: Config with lower max members
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 10),
            vault = VaultConfig()
        )
        every { configService.loadConfig() } returns config

        // Guild with 10 members (at capacity)
        every { guildRepository.getAll() } returns setOf(openGuild)
        every { memberService.getMemberCount(openGuild.id) } returns 10

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should be excluded as it's at capacity
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAvailableGuilds should include guild with 0 members`() {
        // Given: Empty open guild
        every { guildRepository.getAll() } returns setOf(openGuild)
        every { memberService.getMemberCount(openGuild.id) } returns 0

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should return the guild
        assertEquals(1, result.size)
        assertEquals(openGuild.id, result[0].id)
    }

    @Test
    fun `getAvailableGuilds should be case-insensitive for name sorting`() {
        // Given: Guilds with mixed case names
        val guildLower = openGuild.copy(id = UUID.randomUUID(), name = "alpha guild")
        val guildUpper = openGuild.copy(id = UUID.randomUUID(), name = "BETA GUILD")
        val guildMixed = openGuild.copy(id = UUID.randomUUID(), name = "Charlie Guild")

        every { guildRepository.getAll() } returns setOf(guildLower, guildUpper, guildMixed)
        every { memberService.getMemberCount(any()) } returns 10

        // When: Get available guilds
        val result = lfgService.getAvailableGuilds()

        // Then: Should be sorted case-insensitively
        assertEquals(3, result.size)
        assertEquals("alpha guild", result[0].name)
        assertEquals("BETA GUILD", result[1].name)
        assertEquals("Charlie Guild", result[2].name)
    }

    // ===== getJoinRequirement Tests =====

    @Test
    fun `getJoinRequirement should return null when joinFeeEnabled is false`() {
        // Given: Guild with join fee disabled
        val guild = openGuild.copy(joinFeeEnabled = false, joinFeeAmount = 0)

        // When: Get join requirement
        val result = lfgService.getJoinRequirement(guild)

        // Then: Should return null
        assertNull(result)
    }

    @Test
    fun `getJoinRequirement should return null when joinFeeAmount is 0`() {
        // Given: Guild with join fee enabled but zero amount
        val guild = openGuild.copy(joinFeeEnabled = true, joinFeeAmount = 0)

        // When: Get join requirement
        val result = lfgService.getJoinRequirement(guild)

        // Then: Should return null (no meaningful requirement)
        assertNull(result)
    }

    @Test
    fun `getJoinRequirement should return physical currency requirement when usePhysicalCurrency is true`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        // Guild with join fee
        val guild = openGuildWithFee.copy(joinFeeEnabled = true, joinFeeAmount = 500)

        // When: Get join requirement
        val result = lfgService.getJoinRequirement(guild)

        // Then: Should return physical currency requirement
        assertNotNull(result)
        assertEquals(500, result!!.amount)
        assertTrue(result.isPhysicalCurrency)
        assertEquals("RAW_GOLD", result.currencyName)
    }

    @Test
    fun `getJoinRequirement should return virtual currency requirement when usePhysicalCurrency is false`() {
        // Given: Virtual currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = false, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        // Guild with join fee
        val guild = openGuildWithFee.copy(joinFeeEnabled = true, joinFeeAmount = 1000)

        // When: Get join requirement
        val result = lfgService.getJoinRequirement(guild)

        // Then: Should return virtual currency requirement
        assertNotNull(result)
        assertEquals(1000, result!!.amount)
        assertFalse(result.isPhysicalCurrency)
        assertEquals("Coins", result.currencyName)
    }

    @Test
    fun `getJoinRequirement should use configured physical currency material`() {
        // Given: Config with different physical currency material
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "DIAMOND")
        )
        every { configService.loadConfig() } returns config

        // Guild with join fee
        val guild = openGuildWithFee.copy(joinFeeEnabled = true, joinFeeAmount = 100)

        // When: Get join requirement
        val result = lfgService.getJoinRequirement(guild)

        // Then: Should use configured material
        assertNotNull(result)
        assertEquals(100, result!!.amount)
        assertTrue(result.isPhysicalCurrency)
        assertEquals("DIAMOND", result.currencyName)
    }

    @Test
    fun `getJoinRequirement should return correct amount from guild settings`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "EMERALD")
        )
        every { configService.loadConfig() } returns config

        // Guild with specific join fee
        val guild = openGuildWithFee.copy(joinFeeEnabled = true, joinFeeAmount = 12345)

        // When: Get join requirement
        val result = lfgService.getJoinRequirement(guild)

        // Then: Should have correct amount
        assertNotNull(result)
        assertEquals(12345, result!!.amount)
    }

    // ===== canJoinGuild Tests =====

    @Test
    fun `canJoinGuild should return AlreadyInGuild when player is already in a guild`() {
        // Given: Player is already in a guild
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns setOf(openGuild.id)

        // When: Check if player can join
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return AlreadyInGuild
        assertTrue(result is LfgJoinResult.AlreadyInGuild)
    }

    @Test
    fun `canJoinGuild should return GuildFull when guild is at max capacity`() {
        // Given: Player not in a guild, but target guild is full
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuild.id) } returns 50 // At max

        // When: Check if player can join
        val result = lfgService.canJoinGuild(playerId, openGuild)

        // Then: Should return GuildFull
        assertTrue(result is LfgJoinResult.GuildFull)
    }

    @Test
    fun `canJoinGuild should return Success when guild has no join fee`() {
        // Given: Player not in a guild, guild is open with no fee
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuild.id) } returns 10 // Has slots

        // When: Check if player can join
        val result = lfgService.canJoinGuild(playerId, openGuild)

        // Then: Should return Success
        assertTrue(result is LfgJoinResult.Success)
    }

    @Test
    fun `canJoinGuild should return Success when player has enough physical currency`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { physicalCurrencyService.calculatePlayerInventoryValue(playerId) } returns 600 // Has enough

        // When: Check if player can join guild with 500 fee
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return Success
        assertTrue(result is LfgJoinResult.Success)
    }

    @Test
    fun `canJoinGuild should return InsufficientFunds when player lacks physical currency`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { physicalCurrencyService.calculatePlayerInventoryValue(playerId) } returns 200 // Not enough

        // When: Check if player can join guild with 500 fee
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return InsufficientFunds with details
        assertTrue(result is LfgJoinResult.InsufficientFunds)
        val insufficient = result as LfgJoinResult.InsufficientFunds
        assertEquals(500, insufficient.required)
        assertEquals(200, insufficient.current)
        assertEquals("RAW_GOLD", insufficient.currencyType)
    }

    @Test
    fun `canJoinGuild should return Success when player has exact fee amount`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { physicalCurrencyService.calculatePlayerInventoryValue(playerId) } returns 500 // Exact amount

        // When: Check if player can join
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return Success
        assertTrue(result is LfgJoinResult.Success)
    }

    @Test
    fun `canJoinGuild should return InsufficientFunds when player has zero currency`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { physicalCurrencyService.calculatePlayerInventoryValue(playerId) } returns 0

        // When: Check if player can join
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return InsufficientFunds
        assertTrue(result is LfgJoinResult.InsufficientFunds)
        val insufficient = result as LfgJoinResult.InsufficientFunds
        assertEquals(500, insufficient.required)
        assertEquals(0, insufficient.current)
    }

    @Test
    fun `canJoinGuild should check virtual currency when usePhysicalCurrency is false`() {
        // Given: Virtual currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = false)
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { bankService.getPlayerBalance(playerId) } returns 1000 // Has enough

        // When: Check if player can join guild with 500 fee
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return Success
        assertTrue(result is LfgJoinResult.Success)
    }

    @Test
    fun `canJoinGuild should return InsufficientFunds for virtual currency when player lacks funds`() {
        // Given: Virtual currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = false)
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { bankService.getPlayerBalance(playerId) } returns 100 // Not enough

        // When: Check if player can join guild with 500 fee
        val result = lfgService.canJoinGuild(playerId, openGuildWithFee)

        // Then: Should return InsufficientFunds
        assertTrue(result is LfgJoinResult.InsufficientFunds)
        val insufficient = result as LfgJoinResult.InsufficientFunds
        assertEquals(500, insufficient.required)
        assertEquals(100, insufficient.current)
        assertEquals("Coins", insufficient.currencyType)
    }

    @Test
    fun `canJoinGuild should allow joining closed guild when not checking isOpen`() {
        // Note: canJoinGuild validates eligibility, not listing availability
        // The guild passed to canJoinGuild could be from a direct invitation
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(closedGuild.id) } returns 10

        // When: Check if player can join closed guild
        val result = lfgService.canJoinGuild(playerId, closedGuild)

        // Then: Should return Success (isOpen is for LFG listing, not joining)
        assertTrue(result is LfgJoinResult.Success)
    }

    // ===== joinGuild Tests =====

    @Test
    fun `joinGuild should return AlreadyInGuild when player is already in a guild`() {
        // Given: Player is already in a guild
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns setOf(openGuild.id)

        // When: Try to join
        val result = lfgService.joinGuild(playerId, openGuildWithFee)

        // Then: Should return AlreadyInGuild
        assertTrue(result is LfgJoinResult.AlreadyInGuild)
    }

    @Test
    fun `joinGuild should return GuildFull when guild is at max capacity`() {
        // Given: Player not in a guild, but target guild is full
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuild.id) } returns 50 // At max

        // When: Try to join
        val result = lfgService.joinGuild(playerId, openGuild)

        // Then: Should return GuildFull
        assertTrue(result is LfgJoinResult.GuildFull)
    }

    @Test
    fun `joinGuild should succeed and add member when guild has no join fee`() {
        // Given: Player not in a guild, guild is open with no fee
        val playerId = UUID.randomUUID()
        val guildDefaultRank = defaultRank.copy(guildId = openGuild.id)
        val newMember = Member(playerId, openGuild.id, guildDefaultRank.id, Instant.now())

        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuild.id) } returns 10
        every { rankService.getDefaultRank(openGuild.id) } returns guildDefaultRank
        every { memberService.addMember(playerId, openGuild.id, guildDefaultRank.id) } returns newMember

        // When: Join the guild
        val result = lfgService.joinGuild(playerId, openGuild)

        // Then: Should return Success and add member
        assertTrue(result is LfgJoinResult.Success)
        verify { memberService.addMember(playerId, openGuild.id, guildDefaultRank.id) }
    }

    @Test
    fun `joinGuild should collect physical currency and add to guild vault`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        val guildDefaultRank = defaultRank.copy(guildId = openGuildWithFee.id)
        val newMember = Member(playerId, openGuildWithFee.id, guildDefaultRank.id, Instant.now())

        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { physicalCurrencyService.calculatePlayerInventoryValue(playerId) } returns 600
        every { rankService.getDefaultRank(openGuildWithFee.id) } returns guildDefaultRank
        every { memberService.addMember(playerId, openGuildWithFee.id, guildDefaultRank.id) } returns newMember
        every { physicalCurrencyService.addCurrency(openGuildWithFee, 500, any()) } returns true

        // When: Join the guild with 500 fee
        val result = lfgService.joinGuild(playerId, openGuildWithFee)

        // Then: Should succeed and add currency to vault
        assertTrue(result is LfgJoinResult.Success)
        verify { physicalCurrencyService.addCurrency(openGuildWithFee, 500, any()) }
        verify { memberService.addMember(playerId, openGuildWithFee.id, guildDefaultRank.id) }
    }

    @Test
    fun `joinGuild should return InsufficientFunds when player lacks physical currency`() {
        // Given: Physical currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = true, physicalCurrencyMaterial = "RAW_GOLD")
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { physicalCurrencyService.calculatePlayerInventoryValue(playerId) } returns 200 // Not enough

        // When: Try to join guild with 500 fee
        val result = lfgService.joinGuild(playerId, openGuildWithFee)

        // Then: Should return InsufficientFunds
        assertTrue(result is LfgJoinResult.InsufficientFunds)
        val insufficient = result as LfgJoinResult.InsufficientFunds
        assertEquals(500, insufficient.required)
        assertEquals(200, insufficient.current)
    }

    @Test
    fun `joinGuild should collect virtual currency and deposit to guild bank`() {
        // Given: Virtual currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = false)
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        val guildDefaultRank = defaultRank.copy(guildId = openGuildWithFee.id)
        val newMember = Member(playerId, openGuildWithFee.id, guildDefaultRank.id, Instant.now())

        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { bankService.getPlayerBalance(playerId) } returns 1000
        every { bankService.withdrawPlayer(playerId, 500, any()) } returns true
        every { bankService.deposit(openGuildWithFee.id, playerId, 500, any()) } returns mockk()
        every { rankService.getDefaultRank(openGuildWithFee.id) } returns guildDefaultRank
        every { memberService.addMember(playerId, openGuildWithFee.id, guildDefaultRank.id) } returns newMember

        // When: Join the guild with 500 fee
        val result = lfgService.joinGuild(playerId, openGuildWithFee)

        // Then: Should succeed and transfer currency
        assertTrue(result is LfgJoinResult.Success)
        verify { bankService.withdrawPlayer(playerId, 500, any()) }
        verify { bankService.deposit(openGuildWithFee.id, playerId, 500, any()) }
        verify { memberService.addMember(playerId, openGuildWithFee.id, guildDefaultRank.id) }
    }

    @Test
    fun `joinGuild should return InsufficientFunds for virtual currency when player lacks funds`() {
        // Given: Virtual currency config
        val config = MainConfig(
            guild = GuildConfig(maxMembersPerGuild = 50),
            vault = VaultConfig(usePhysicalCurrency = false)
        )
        every { configService.loadConfig() } returns config

        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuildWithFee.id) } returns 10
        every { bankService.getPlayerBalance(playerId) } returns 100 // Not enough

        // When: Try to join guild with 500 fee
        val result = lfgService.joinGuild(playerId, openGuildWithFee)

        // Then: Should return InsufficientFunds
        assertTrue(result is LfgJoinResult.InsufficientFunds)
        val insufficient = result as LfgJoinResult.InsufficientFunds
        assertEquals(500, insufficient.required)
        assertEquals(100, insufficient.current)
        assertEquals("Coins", insufficient.currencyType)
    }

    @Test
    fun `joinGuild should return Error when no default rank exists`() {
        // Given: Player can join but no default rank
        val playerId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuild.id) } returns 10
        every { rankService.getDefaultRank(openGuild.id) } returns null

        // When: Try to join
        val result = lfgService.joinGuild(playerId, openGuild)

        // Then: Should return Error
        assertTrue(result is LfgJoinResult.Error)
    }

    @Test
    fun `joinGuild should return Error when addMember fails`() {
        // Given: Player can join but addMember fails
        val playerId = UUID.randomUUID()
        val guildDefaultRank = defaultRank.copy(guildId = openGuild.id)

        every { memberService.getPlayerGuilds(playerId) } returns emptySet()
        every { memberService.getMemberCount(openGuild.id) } returns 10
        every { rankService.getDefaultRank(openGuild.id) } returns guildDefaultRank
        every { memberService.addMember(playerId, openGuild.id, guildDefaultRank.id) } returns null

        // When: Try to join
        val result = lfgService.joinGuild(playerId, openGuild)

        // Then: Should return Error
        assertTrue(result is LfgJoinResult.Error)
    }
}
