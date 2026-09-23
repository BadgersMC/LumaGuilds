package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.WarNotificationService
import net.lumalyte.lg.api.events.GuildWarEndEvent
import net.lumalyte.lg.config.CombatConfig
import net.lumalyte.lg.config.LevelRewardConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.DurableWarRecord
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.GuildProgression
import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarObjective
import net.lumalyte.lg.domain.entities.WarStats
import net.lumalyte.lg.domain.entities.WarStatus
import net.lumalyte.lg.domain.values.ExperienceSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.bukkit.Bukkit
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * REQ-008: the combat configuration must actually be enforced —
 * `war_duration_hours` caps declared war length, `max_simultaneous_wars`
 * (refined by progression) limits concurrent wars, `war_end_grace_period_minutes`
 * delays force-end, `kill_cooldown_minutes` + `same_player_kill_limit` suppress
 * farmed-kill XP, and win/lose/kill XP is awarded. REQ-024: declarations require
 * accept/decline. REQ-039: wagers are escrowed on acceptance.
 */
class WarConfigEnforcementTest {

    private val records = mutableMapOf<UUID, net.lumalyte.lg.domain.entities.DurableWarRecord>()
    private val repository = mockk<net.lumalyte.lg.application.persistence.WarRepository> {
        every { getAll() } answers { records.values.toList() }
        every { this@mockk.get(any<UUID>()) } answers { records[firstArg<UUID>()] }
        every { save(any()) } answers {
            val record = firstArg<net.lumalyte.lg.domain.entities.DurableWarRecord>()
            if ((records[record.id]?.revision ?: 0L) != record.revision) false
            else { records[record.id] = record.copy(revision = record.revision + 1); true }
        }
    }
    private val payments = mockk<net.lumalyte.lg.application.services.WarPaymentService>(relaxed = true)

    // ---------- pure decision helpers (no Bukkit) ----------

    @Test
    fun `war kill win target defaults to twenty five`() {
        assertEquals(25, CombatConfig().warKillWinTarget)
    }

    @Test
    fun `nonpositive war kill target fails closed`() {
        val service = newService(mockk(), combatConfig = CombatConfig(warKillWinTarget = 0))
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            service.getWarKillWinTarget()
        }
    }

    @Test
    fun `requested duration below config cap is kept`() {
        assertEquals(
            Duration.ofDays(3),
            WarServiceBukkit.effectiveWarDuration(Duration.ofDays(3), configWarDurationHours = 168)
        )
    }

    @Test
    fun `requested duration above config cap is clamped`() {
        assertEquals(
            Duration.ofDays(7),
            WarServiceBukkit.effectiveWarDuration(Duration.ofDays(14), configWarDurationHours = 168)
        )
    }

    @Test
    fun `max wars defaults to config when no progression row exists`() {
        assertEquals(
            3,
            WarServiceBukkit.maxWarsForGuild(currentLevel = null, configMax = 3, levelRewards = emptyMap())
        )
    }

    @Test
    fun `max wars is refined upward by progression war slots`() {
        val rewards = mapOf(
            1 to levelReward(warSlots = 0),
            2 to levelReward(warSlots = 5),
            3 to levelReward(warSlots = 4)
        )
        assertEquals(
            5,
            WarServiceBukkit.maxWarsForGuild(currentLevel = 3, configMax = 3, levelRewards = rewards)
        )
    }

    @Test
    fun `max wars never drops below the config base`() {
        val rewards = mapOf(1 to levelReward(warSlots = 1), 2 to levelReward(warSlots = 0))
        assertEquals(
            3,
            WarServiceBukkit.maxWarsForGuild(currentLevel = 2, configMax = 3, levelRewards = rewards)
        )
    }

    @Test
    fun `elapsed active war reports expired when remaining duration is clamped to zero`() {
        val war = War(
            declaringGuildId = UUID.randomUUID(),
            defendingGuildId = UUID.randomUUID(),
            startedAt = Instant.now().minus(Duration.ofMinutes(5)),
            duration = Duration.ofMinutes(1),
            status = WarStatus.ACTIVE,
        )

        assertEquals(Duration.ZERO, war.remainingDuration)
        assertTrue(war.isExpired)
    }

    // ---------- declaration flow (REQ-024: no auto-accept) ----------

    private fun permissiveMemberService(): MemberService = mockk {
        every { hasPermission(any(), any(), RankPermission.DECLARE_WAR) } returns true
    }

    private fun hostileGuildRepository(): GuildRepository {
        val hostileGuild = mockk<Guild> {
            every { mode } returns GuildMode.HOSTILE
        }
        return mockk {
            every { getById(any()) } returns hostileGuild
        }
    }

    private fun newService(
        configService: ConfigService,
        progressionRepository: ProgressionRepository = mockk(relaxed = true),
        seasonalElo: SeasonalEloCoordinator? = null,
        combatConfig: CombatConfig = CombatConfig(),
        memberRepository: net.lumalyte.lg.application.persistence.MemberRepository? = null,
        memberService: MemberService = permissiveMemberService(),
        guildRepository: GuildRepository = hostileGuildRepository(),
        warNotifications: WarNotificationService? = null,
    ): WarServiceBukkit {
        val config = mockk<MainConfig>()
        every { config.combat } returns combatConfig
        every { configService.loadConfig() } returns config
        return WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = progressionRepository,
            progressionConfigService = mockk(relaxed = true),
            progressionService = mockk(relaxed = true),
            memberService = memberService,
            guildRepository = guildRepository,
            seasonalElo = seasonalElo,
            warNotifications = warNotifications,
            memberRepository = memberRepository ?: if (warNotifications != null) mockk(relaxed = true) else null,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    private fun mockBukkitPluginManager(): org.bukkit.plugin.PluginManager {
        mockkStatic(Bukkit::class)
        val pluginManager = mockk<org.bukkit.plugin.PluginManager>(relaxed = true)
        every { Bukkit.getPluginManager() } returns pluginManager
        return pluginManager
    }

    @Test
    fun `createWarDeclaration creates a pending declaration, not an active war`() {
        val service = newService(mockk())

        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()

        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 0,
            terms = null,
            actorId = UUID.randomUUID()
        )

        assertNotNull(declaration, "declaration must be created (REQ-024)")
        assertEquals(declaring, declaration!!.declaringGuildId)
        assertEquals(defending, declaration.defendingGuildId)
        assertNull(service.getCurrentWarBetweenGuilds(declaring, defending), "no active war may exist before acceptance")
        assertNotNull(service.getPendingDeclarationsForGuild(defending).firstOrNull { it.id == declaration.id })
        assertTrue(records[declaration.id]!!.declarationNotificationExpected)
    }

    @Test
    fun `war transitions snapshot exact notification recipients`() {
        mockBukkitPluginManager()
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaringPlayer = UUID.randomUUID()
        val defendingPlayer = UUID.randomUUID()
        val rank = UUID.randomUUID()
        val members = mockk<net.lumalyte.lg.application.persistence.MemberRepository>()
        every { members.getByGuild(declaring) } returns setOf(
            net.lumalyte.lg.domain.entities.Member(declaringPlayer, declaring, rank, java.time.Instant.EPOCH)
        )
        every { members.getByGuild(defending) } returns setOf(
            net.lumalyte.lg.domain.entities.Member(defendingPlayer, defending, rank, java.time.Instant.EPOCH)
        )
        val service = newService(mockk(), memberRepository = members)

        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        )!!
        assertEquals(setOf(declaringPlayer), records[declaration.id]!!.notificationRecipients.declarationSent)
        assertEquals(setOf(defendingPlayer), records[declaration.id]!!.notificationRecipients.declarationReceived)

        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!
        assertEquals(setOf(declaringPlayer), records[war.id]!!.notificationRecipients.acceptanceDeclaring)
        assertEquals(setOf(defendingPlayer), records[war.id]!!.notificationRecipients.acceptanceDefending)

        assertTrue(service.endWar(war.id, declaring, actorId = UUID.randomUUID()))
        assertEquals(setOf(declaringPlayer), records[war.id]!!.notificationRecipients.victory)
        assertEquals(setOf(defendingPlayer), records[war.id]!!.notificationRecipients.defeat)
    }

    @Test
    fun `acceptWarDeclaration activates the war with declaration data`() {
        mockBukkitPluginManager()
        val service = newService(mockk())

        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()

        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 0,
            terms = null,
            actorId = UUID.randomUUID()
        )!!

        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())

        assertNotNull(war)
        assertEquals(WarStatus.ACTIVE, war!!.status)
        assertNotNull(war.startedAt)
        assertTrue(service.getCurrentWarBetweenGuilds(declaring, defending)?.id == war.id)
        assertTrue(records[war.id]!!.declarationNotificationExpected)
        assertTrue(records[war.id]!!.acceptanceNotificationExpected)
    }

    @Test
    fun `opposing guild kill increments persisted counter for the correct side`() {
        mockBukkitPluginManager()
        val service = newService(mockk(), combatConfig = CombatConfig(warKillWinTarget = 25))
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        val first = service.recordOpposingGuildKill(war.id, declaring, defending)
        val second = service.recordOpposingGuildKill(war.id, defending, declaring)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, second!!.stats.declaringGuildKills)
        assertEquals(1, second.stats.defendingGuildKills)
        assertEquals(1, second.stats.declaringGuildDeaths)
        assertEquals(1, second.stats.defendingGuildDeaths)
        assertNull(second.winnerGuildId)
        assertEquals(25, second.killTarget)
        assertEquals(second.stats, service.getWarStats(war.id))
    }

    @Test
    fun `kill counter ignores non opposing guilds and inactive wars`() {
        mockBukkitPluginManager()
        val service = newService(mockk(), combatConfig = CombatConfig(warKillWinTarget = 25))
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val outsider = UUID.randomUUID()
        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        assertNull(service.recordOpposingGuildKill(war.id, declaring, outsider))
        assertEquals(0, service.getWarStats(war.id).declaringGuildKills)

        assertTrue(service.endWar(war.id, declaring, actorId = UUID.randomUUID()))
        assertTrue(records[war.id]!!.resolutionNotificationExpected)
        assertNull(service.recordOpposingGuildKill(war.id, defending, declaring))
        assertEquals(0, service.getActiveWars().size)
    }

    @Test
    fun `global kill target reports winner before trusted resolution and next war starts at zero`() {
        mockBukkitPluginManager()
        val combat = CombatConfig(
            warKillWinTarget = 3,
            warDeclarationCooldownHours = 0,
            warFarmingCooldownHours = 0,
        )
        val service = newService(mockk(), combatConfig = combat)
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()

        val firstDeclaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
        )!!
        val firstWar = service.acceptWarDeclaration(firstDeclaration.id, UUID.randomUUID())!!

        assertNull(service.recordOpposingGuildKill(firstWar.id, declaring, defending)!!.winnerGuildId)
        assertNull(service.recordOpposingGuildKill(firstWar.id, declaring, defending)!!.winnerGuildId)
        val winning = service.recordOpposingGuildKill(firstWar.id, declaring, defending)!!

        assertEquals(declaring, winning.winnerGuildId)
        assertEquals(3, winning.stats.declaringGuildKills)
        assertEquals(WarStatus.ACTIVE, service.getWar(firstWar.id)!!.status)

        assertTrue(service.resolveReachedKillTarget(firstWar.id, declaring))
        assertEquals(WarStatus.ENDED, service.getWar(firstWar.id)!!.status)
        assertEquals(declaring, service.getWar(firstWar.id)!!.winner)
        assertNull(service.recordOpposingGuildKill(firstWar.id, declaring, defending))

        val secondDeclaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
        )!!
        val secondWar = service.acceptWarDeclaration(secondDeclaration.id, UUID.randomUUID())!!
        assertEquals(0, service.getWarStats(secondWar.id).declaringGuildKills)
        assertEquals(0, service.getWarStats(secondWar.id).defendingGuildKills)
    }

    @Test
    fun `decisive persisted kill is recovered after restart gap`() {
        mockBukkitPluginManager()
        val combat = CombatConfig(warKillWinTarget = 1)
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val service = newService(mockk(), combatConfig = combat)
        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        val winning = service.recordOpposingGuildKill(war.id, declaring, defending)!!
        assertEquals(declaring, winning.winnerGuildId)
        assertEquals(WarStatus.ACTIVE, service.getWar(war.id)!!.status)

        val restored = newService(mockk(), combatConfig = combat)
        assertEquals(1, restored.reconcilePendingKillVictories())
        assertEquals(WarStatus.ENDED, restored.getWar(war.id)!!.status)
        assertEquals(declaring, restored.getWar(war.id)!!.winner)
    }

    @Test
    fun `pending persisted winner blocks later opposing kill before reconciliation tick`() {
        mockBukkitPluginManager()
        val combat = CombatConfig(warKillWinTarget = 1)
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val service = newService(mockk(), combatConfig = combat)
        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        val winning = service.recordOpposingGuildKill(war.id, declaring, defending)!!
        assertEquals(declaring, winning.winnerGuildId)
        assertEquals(WarStatus.ACTIVE, service.getWar(war.id)!!.status)

        assertNull(service.recordOpposingGuildKill(war.id, defending, declaring))
        assertEquals(WarStatus.ENDED, service.getWar(war.id)!!.status)
        assertEquals(declaring, service.getWar(war.id)!!.winner)
        assertEquals(0, service.getWarStats(war.id).defendingGuildKills)
    }

    @Test
    fun `rated declaration binds the scheduled chapter and requires both guilds at level 100`() {
        mockBukkitPluginManager()
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(declaring) } returns
            GuildProgression(guildId = declaring, currentLevel = 100)
        every { progression.getGuildProgression(defending) } returns
            GuildProgression(guildId = defending, currentLevel = 100)
        val elo = mockk<SeasonalEloCoordinator>()
        every { elo.currentRatedChapterId() } returns "chapter-2"
        val service = newService(mockk(), progression, elo)

        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
            rated = true,
        )

        assertNotNull(declaration)
        assertTrue(declaration!!.isRated)
        assertEquals("chapter-2", declaration.ratedChapterId)

        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())
        assertNotNull(war)
        assertTrue(war!!.isRated)
        assertEquals("chapter-2", war.ratedChapterId)
    }

    @Test
    fun `rated declaration is rejected when either guild is below level 100`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(declaring) } returns
            GuildProgression(guildId = declaring, currentLevel = 100)
        every { progression.getGuildProgression(defending) } returns
            GuildProgression(guildId = defending, currentLevel = 99)
        val elo = mockk<SeasonalEloCoordinator>(relaxed = true)
        val service = newService(mockk(), progression, elo)

        assertNull(service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
            rated = true,
        ))
        verify(exactly = 0) { elo.currentRatedChapterId() }
    }

    @Test
    fun `rated acceptance fails closed when the scheduled chapter changed after declaration`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(declaring) } returns
            GuildProgression(guildId = declaring, currentLevel = 100)
        every { progression.getGuildProgression(defending) } returns
            GuildProgression(guildId = defending, currentLevel = 100)
        val elo = mockk<SeasonalEloCoordinator>()
        every { elo.currentRatedChapterId() } returnsMany listOf("chapter-2", "chapter-3")
        val service = newService(mockk(), progression, elo)
        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
            rated = true,
        )!!

        assertNull(service.acceptWarDeclaration(declaration.id, UUID.randomUUID()))
        assertTrue(service.getPendingDeclarationsForGuild(defending).any { it.id == declaration.id })
    }

    @Test
    fun `escrowed rated acceptance resumes even after eligibility changes`() {
        mockBukkitPluginManager()
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(declaring) } returns
            GuildProgression(guildId = declaring, currentLevel = 100)
        every { progression.getGuildProgression(defending) } returns
            GuildProgression(guildId = defending, currentLevel = 100)
        val elo = mockk<SeasonalEloCoordinator>()
        every { elo.currentRatedChapterId() } returns "chapter-2"
        val service = newService(mockk(), progression, elo)
        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 500,
            actorId = UUID.randomUUID(),
            rated = true,
        )!!

        val pendingWar = net.lumalyte.lg.domain.entities.War(
            id = declaration.id,
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = declaration.proposedDuration,
            objectives = declaration.objectives,
            ratedChapterId = declaration.ratedChapterId,
        )
        val wager = net.lumalyte.lg.domain.entities.WarWager(
            warId = declaration.id,
            declaringGuildId = declaring,
            defendingGuildId = defending,
            declaringGuildWager = 500,
            defendingGuildWager = 500,
        )
        val current = records.getValue(declaration.id)
        repository.save(
            current.copy(
                war = pendingWar,
                wager = wager,
                paymentPhase = net.lumalyte.lg.domain.entities.WarPaymentPhase.ESCROWED,
            ),
        )
        every { progression.getGuildProgression(declaring) } returns
            GuildProgression(guildId = declaring, currentLevel = 99)
        every { progression.getGuildProgression(defending) } returns
            GuildProgression(guildId = defending, currentLevel = 99)
        every { payments.fund(declaration.id) } returns true

        val resumed = service.acceptWarDeclaration(declaration.id, UUID(0, 0))

        assertNotNull(resumed)
        assertEquals(WarStatus.ACTIVE, resumed!!.status)
        verify(exactly = 1) { elo.currentRatedChapterId() }
    }

    @Test
    fun `unrated declaration remains legacy-compatible and carries no chapter identity`() {
        val service = newService(mockk())
        val declaration = service.createWarDeclaration(
            declaringGuildId = UUID.randomUUID(),
            defendingGuildId = UUID.randomUUID(),
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
        )

        assertNotNull(declaration)
        assertFalse(declaration!!.isRated)
        assertNull(declaration.ratedChapterId)
    }

    @Test
    fun `rejectWarDeclaration removes the declaration without creating a war`() {
        val service = newService(mockk())

        val declaration = service.createWarDeclaration(
            declaringGuildId = UUID.randomUUID(),
            defendingGuildId = UUID.randomUUID(),
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 0,
            terms = null,
            actorId = UUID.randomUUID()
        )!!

        assertTrue(service.rejectWarDeclaration(declaration.id, UUID.randomUUID()))
        assertNull(service.getCurrentWarBetweenGuilds(declaration.declaringGuildId, declaration.defendingGuildId))
    }

    // ---------- anti-farming (REQ-008) ----------

    private fun newServiceWithCombat(combat: CombatConfig): WarServiceBukkit {
        val configService = mockk<ConfigService>()
        val config = mockk<MainConfig>()
        every { config.combat } returns combat
        every { configService.loadConfig() } returns config
        return WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = mockk<ProgressionRepository>(relaxed = true),
            progressionConfigService = mockk(relaxed = true),
            progressionService = mockk(relaxed = true),
            memberService = permissiveMemberService(),
            guildRepository = hostileGuildRepository(),
        )
    }

    @Test
    fun `farming detection flags kills beyond the per-victim limit within cooldown`() {
        val service = newServiceWithCombat(CombatConfig(killCooldownMinutes = 5, samePlayerKillLimit = 3))

        val killer = UUID.randomUUID()
        val victim = UUID.randomUUID()

        // First `samePlayerKillLimit` kills are legitimate
        assertFalse(service.recordWarKillAndCheckFarming(killer, victim))
        assertFalse(service.recordWarKillAndCheckFarming(killer, victim))
        assertFalse(service.recordWarKillAndCheckFarming(killer, victim))
        // Fourth kill within the cooldown window is farming
        assertTrue(service.recordWarKillAndCheckFarming(killer, victim))
    }

    @Test
    fun `farming detection is per victim - same killer different victims are not flagged`() {
        val service = newServiceWithCombat(CombatConfig(killCooldownMinutes = 5, samePlayerKillLimit = 1))

        val killer = UUID.randomUUID()
        val victimA = UUID.randomUUID()
        val victimB = UUID.randomUUID()

        // Each victim is a distinct key: killing A then B must not flag, even
        // though the same killer exceeds the limit for A.
        assertFalse(service.recordWarKillAndCheckFarming(killer, victimA))
        assertFalse(service.recordWarKillAndCheckFarming(killer, victimB))
        // Killing A again (limit 1) IS farming for that victim
        assertTrue(service.recordWarKillAndCheckFarming(killer, victimA))
    }

    // ---------- grace period + XP + escrow (REQ-008 / REQ-039) ----------

    @Test
    fun `war is force-ended only after duration plus grace period`() {
        mockBukkitPluginManager()
        val service = newService(mockk())

        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        // Accept a war with a tiny duration: with the 30-min default grace it
        // cannot be expired immediately regardless of wall-clock drift.
        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofSeconds(1),
            objectives = emptySet(),
            wagerAmount = 0,
            terms = null,
            actorId = UUID.randomUUID()
        )!!
        val accepted = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!
        // War has started; with grace 30min and duration 1s it cannot be expired
        // immediately regardless of wall-clock drift in the test.
        val processed = service.processExpiredWars()
        assertEquals(0, processed, "war inside grace period must not be force-ended")
        assertNotNull(service.getWar(accepted.id))
    }

    @Test
    fun `expired draw uses the standard war end lifecycle`() {
        val pluginManager = mockBukkitPluginManager()
        val notifications = mockk<WarNotificationService>(relaxed = true)
        val service = newService(
            mockk(),
            combatConfig = CombatConfig(warEndGracePeriodMinutes = 0),
            warNotifications = notifications,
        )
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofSeconds(1), emptySet(), actorId = UUID.randomUUID()
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!
        val persisted = records.getValue(war.id)
        records[war.id] = persisted.copy(
            war = persisted.war!!.copy(
                startedAt = Instant.now().minus(Duration.ofMinutes(5)),
                duration = Duration.ofSeconds(1),
            )
        )

        assertEquals(1, service.processExpiredWars())
        val ended = service.getWar(war.id)!!
        assertEquals(WarStatus.ENDED, ended.status)
        assertNull(ended.winner)
        verify(exactly = 1) {
            pluginManager.callEvent(match {
                it is GuildWarEndEvent && it.warId == war.id && it.winnerGuildId == null
            })
        }
        verify(exactly = 1) {
            notifications.warEnded(match { it.id == war.id && it.isEnded && it.winner == null })
        }
    }

    @Test
    fun `accepted peace agreement uses the standard war end lifecycle`() {
        val pluginManager = mockBukkitPluginManager()
        val notifications = mockk<WarNotificationService>(relaxed = true)
        val service = newService(mockk(), warNotifications = notifications)
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!
        val agreement = service.proposePeaceAgreement(war.id, declaring, "Mutual peace")!!

        val ended = service.acceptPeaceAgreement(agreement.id, defending)!!
        assertEquals(WarStatus.ENDED, ended.status)
        assertNull(ended.winner)
        assertEquals("Mutual peace", ended.peaceTerms)
        verify(exactly = 1) {
            pluginManager.callEvent(match {
                it is GuildWarEndEvent && it.warId == war.id && it.winnerGuildId == null
            })
        }
        verify(exactly = 1) {
            notifications.warEnded(match { it.id == war.id && it.peaceTerms == "Mutual peace" })
        }
    }

    @Test
    fun `war end delegates pre-cap XP to winner only`() {
        val progressionRepo = mockk<ProgressionRepository>(relaxed = true)
        val awardService = mockk<net.lumalyte.lg.application.services.ChapterTwoGuildAwardService>(relaxed = true)
        val configService = mockk<ConfigService>()
        val config = mockk<MainConfig>()
        every { config.combat } returns CombatConfig(warWinExperience = 500, warLoseExperience = 100)
        every { configService.loadConfig() } returns config

        val winnerProgression = GuildProgression(guildId = UUID.randomUUID(), currentLevel = 1, totalExperience = 0)
        val loserProgression = GuildProgression(guildId = UUID.randomUUID(), currentLevel = 1, totalExperience = 0)
        every { progressionRepo.getGuildProgression(winnerProgression.guildId) } returns winnerProgression
        every { progressionRepo.getGuildProgression(loserProgression.guildId) } returns loserProgression

        val service = WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = progressionRepo,
            progressionConfigService = mockk(relaxed = true),
            chapterTwoGuildAwardService = awardService,
            progressionService = mockk(relaxed = true),
            memberService = permissiveMemberService(),
            guildRepository = hostileGuildRepository(),
        )
        mockBukkitPluginManager()

        val declaration = service.createWarDeclaration(
            declaringGuildId = winnerProgression.guildId,
            defendingGuildId = loserProgression.guildId,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 0,
            terms = null,
            actorId = UUID.randomUUID()
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        service.endWar(war.id, winnerProgression.guildId, actorId = UUID.randomUUID())
        assertEquals(false, service.endWar(war.id, winnerProgression.guildId, actorId = UUID.randomUUID()))

        verify(exactly = 1) { awardService.awardPreCapWarWin(winnerProgression.guildId, 1, any()) }
        verify(exactly = 0) { awardService.awardPreCapWarWin(loserProgression.guildId, any(), any()) }
        verify(exactly = 0) { progressionRepo.saveGuildProgression(any()) }
    }

    @Test
    fun `rated war result changes Elo without awarding current-run war XP`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val progressionRepo = mockk<ProgressionRepository>()
        every { progressionRepo.getGuildProgression(declaring) } returns
            GuildProgression(guildId = declaring, currentLevel = 100, totalExperience = 5_446_893)
        every { progressionRepo.getGuildProgression(defending) } returns
            GuildProgression(guildId = defending, currentLevel = 100, totalExperience = 5_446_893)

        val awardService = mockk<net.lumalyte.lg.application.services.ChapterTwoGuildAwardService>(relaxed = true)
        val elo = mockk<SeasonalEloCoordinator>()
        every { elo.currentRatedChapterId() } returns "chapter-2"
        every {
            elo.rateWar(any(), "chapter-2", declaring, defending, 1.0, 0.0, any())
        } returns net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult.Rated(
            1000, 1000, 1020, 1000
        )
        val configService = mockk<ConfigService>()
        val config = mockk<MainConfig>()
        every { config.combat } returns CombatConfig()
        every { configService.loadConfig() } returns config
        val service = WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = progressionRepo,
            progressionConfigService = mockk(relaxed = true),
            chapterTwoGuildAwardService = awardService,
            progressionService = mockk(relaxed = true),
            memberService = permissiveMemberService(),
            guildRepository = hostileGuildRepository(),
            seasonalElo = elo,
        )
        mockBukkitPluginManager()

        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            actorId = UUID.randomUUID(),
            rated = true,
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        assertTrue(service.endWar(war.id, declaring, actorId = UUID.randomUUID()))

        verify(exactly = 1) {
            elo.rateWar(war.id, "chapter-2", declaring, defending, 1.0, 0.0, any())
        }
        verify(exactly = 0) { awardService.awardPreCapWarWin(any(), any(), any()) }
        verify(exactly = 0) { progressionRepo.saveGuildProgression(any()) }
    }

    @Test
    fun `war kill bonus uses capped player pipeline with killer identity`() {
        val progressionRepo = mockk<ProgressionRepository>(relaxed = true)
        val progressionService = mockk<ProgressionService>(relaxed = true)
        val configService = mockk<ConfigService>()
        val config = mockk<MainConfig>()
        every { config.combat } returns CombatConfig(killExperience = 10)
        every { configService.loadConfig() } returns config

        val service = WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = progressionRepo,
            progressionConfigService = mockk(relaxed = true),
            progressionService = progressionService,
            memberService = permissiveMemberService(),
            guildRepository = hostileGuildRepository(),
        )
        val guildId = UUID.randomUUID()
        val killerId = UUID.randomUUID()

        service.awardWarKillExperience(guildId, killerId)

        verify(exactly = 1) {
            progressionService.awardPlayerExperience(guildId, killerId, 10, ExperienceSource.PLAYER_KILL)
        }
        verify(exactly = 0) { progressionRepo.saveGuildProgression(any()) }
    }

    @Test
    fun `wager is escrowed on acceptance - both guilds deducted`() {
        val bankService = mockk<net.lumalyte.lg.application.services.BankService>(relaxed = true)
        val configService = mockk<ConfigService>()
        val config = mockk<MainConfig>()
        every { config.combat } returns CombatConfig()
        every { configService.loadConfig() } returns config

        val service = WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = bankService,
            progressionRepository = mockk<ProgressionRepository>(relaxed = true),
            progressionConfigService = mockk(relaxed = true),
            progressionService = mockk(relaxed = true),
            memberService = permissiveMemberService(),
            guildRepository = hostileGuildRepository(),
        )
        mockBukkitPluginManager()

        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        every { bankService.getBalance(any()) } returns 10_000
        // Relaxed mock would return false for Boolean — stubbing success so the
        // escrow deduction path proceeds.
        every { bankService.deductFromGuildBank(any(), any(), any()) } returns true
        every { payments.fund(any()) } answers {
            val record = records[firstArg<UUID>()]!!
            repository.save(record.copy(paymentPhase = net.lumalyte.lg.domain.entities.WarPaymentPhase.ESCROWED))
        }

        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 500,
            terms = null,
            actorId = UUID.randomUUID()
        )!!
        val war = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())!!

        val wager = service.getWager(war.id)
        assertNotNull(wager, "wager must be created on acceptance (REQ-039)")
        assertEquals(1_000, wager!!.totalPot)
        verify(exactly = 1) { payments.fund(war.id) }
        verify(exactly = 0) { bankService.deductFromGuildBank(any(), any(), any()) }
    }

    @Test
    fun `acceptance fails atomically when wager escrow cannot be funded`() {
        val bankService = mockk<net.lumalyte.lg.application.services.BankService>(relaxed = true)
        val configService = mockk<ConfigService>()
        val config = mockk<MainConfig>()
        every { config.combat } returns CombatConfig()
        every { configService.loadConfig() } returns config

        val service = WarServiceBukkit(
            warRepository = repository,
            warPayments = payments,
            configService = configService,
            bankService = bankService,
            progressionRepository = mockk<ProgressionRepository>(relaxed = true),
            progressionConfigService = mockk(relaxed = true),
            progressionService = mockk(relaxed = true),
            memberService = permissiveMemberService(),
            guildRepository = hostileGuildRepository(),
        )
        mockBukkitPluginManager()

        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        // Declaring guild has funds, defending guild cannot cover the match
        every { bankService.getBalance(declaring) } returns 10_000
        every { bankService.getBalance(defending) } returns 100
        every { bankService.deductFromGuildBank(any(), any(), any()) } returns true

        val declaration = service.createWarDeclaration(
            declaringGuildId = declaring,
            defendingGuildId = defending,
            duration = Duration.ofDays(7),
            objectives = emptySet(),
            wagerAmount = 500,
            terms = null,
            actorId = UUID.randomUUID()
        )!!

        val accepted = service.acceptWarDeclaration(declaration.id, UUID.randomUUID())

        assertNull(accepted, "acceptance must fail when the defending guild cannot fund the wager")
        assertNull(service.getCurrentWarBetweenGuilds(declaring, defending), "no active war may exist after failed escrow")
        assertNotNull(
            service.getPendingDeclarationsForGuild(defending).firstOrNull { it.id == declaration.id },
            "declaration must remain pending after failed escrow so the defender can retry"
        )
        // Declaring guild's deduction must not happen (createWager bails on the
        // balance check before any deduction) — and no wager/pot may exist.
        verify(exactly = 0) { bankService.deductFromGuildBank(declaring, any(), any()) }
    }

    @Test
    fun `war management permission is authoritative for declaration creation`() {
        val actor = UUID.randomUUID()
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { members.hasPermission(actor, declaring, RankPermission.DECLARE_WAR) } returns false
        val service = newService(mockk(), memberService = members)

        assertFalse(service.canPlayerManageWars(actor, declaring))
        assertNull(
            service.createWarDeclaration(
                declaring, defending, Duration.ofDays(1), emptySet(), actorId = actor
            )
        )
        assertTrue(records.isEmpty())
    }

    @Test
    fun `only the defending guild may accept or reject a declaration`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaringActor = UUID.randomUUID()
        val defendingActor = UUID.randomUUID()
        val intruder = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { members.hasPermission(any(), any(), RankPermission.DECLARE_WAR) } returns false
        every { members.hasPermission(declaringActor, declaring, RankPermission.DECLARE_WAR) } returns true
        every { members.hasPermission(defendingActor, defending, RankPermission.DECLARE_WAR) } returns true
        val service = newService(mockk(), memberService = members)
        mockBukkitPluginManager()

        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = declaringActor
        )!!
        assertNull(service.acceptWarDeclaration(declaration.id, intruder))
        assertNotNull(service.getPendingDeclarationsForGuild(defending).singleOrNull { it.id == declaration.id })
        assertNotNull(service.acceptWarDeclaration(declaration.id, defendingActor))
    }

    @Test
    fun `reject and cancel enforce declaration direction`() {
        val firstDeclaring = UUID.randomUUID()
        val firstDefending = UUID.randomUUID()
        val secondDeclaring = UUID.randomUUID()
        val secondDefending = UUID.randomUUID()
        val firstDeclaringActor = UUID.randomUUID()
        val firstDefendingActor = UUID.randomUUID()
        val secondDeclaringActor = UUID.randomUUID()
        val secondDefendingActor = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { members.hasPermission(any(), any(), RankPermission.DECLARE_WAR) } returns false
        every { members.hasPermission(firstDeclaringActor, firstDeclaring, RankPermission.DECLARE_WAR) } returns true
        every { members.hasPermission(firstDefendingActor, firstDefending, RankPermission.DECLARE_WAR) } returns true
        every { members.hasPermission(secondDeclaringActor, secondDeclaring, RankPermission.DECLARE_WAR) } returns true
        every { members.hasPermission(secondDefendingActor, secondDefending, RankPermission.DECLARE_WAR) } returns true
        val service = newService(mockk(), memberService = members)

        val rejectable = service.createWarDeclaration(
            firstDeclaring, firstDefending, Duration.ofDays(1), emptySet(), actorId = firstDeclaringActor
        )!!
        assertFalse(service.rejectWarDeclaration(rejectable.id, firstDeclaringActor))
        assertTrue(service.rejectWarDeclaration(rejectable.id, firstDefendingActor))

        val cancelable = service.createWarDeclaration(
            secondDeclaring, secondDefending, Duration.ofDays(1), emptySet(), actorId = secondDeclaringActor
        )!!
        assertFalse(service.cancelWarDeclaration(cancelable.id, secondDefendingActor))
        assertTrue(service.cancelWarDeclaration(cancelable.id, secondDeclaringActor))
    }

    @Test
    fun `war resolution rejects unrelated actors but allows either participant manager`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaringActor = UUID.randomUUID()
        val defendingActor = UUID.randomUUID()
        val intruder = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { members.hasPermission(any(), any(), RankPermission.DECLARE_WAR) } returns false
        every { members.hasPermission(declaringActor, declaring, RankPermission.DECLARE_WAR) } returns true
        every { members.hasPermission(defendingActor, defending, RankPermission.DECLARE_WAR) } returns true
        val service = newService(mockk(), memberService = members)
        mockBukkitPluginManager()

        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = declaringActor
        )!!
        val war = service.acceptWarDeclaration(declaration.id, defendingActor)!!

        assertFalse(service.endWar(war.id, declaring, actorId = declaringActor))
        assertFalse(service.endWar(war.id, defending, actorId = intruder))
        assertFalse(service.endWarAsDraw(war.id, "unauthorized", actorId = intruder))
        assertFalse(service.cancelWar(war.id, actorId = intruder))
        assertTrue(service.endWar(war.id, defending, actorId = declaringActor))
    }

    @Test
    fun `kill objectives resolve through the trusted internal transition`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val declaringActor = UUID.randomUUID()
        val defendingActor = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { members.hasPermission(any(), any(), RankPermission.DECLARE_WAR) } returns false
        every { members.hasPermission(declaringActor, declaring, RankPermission.DECLARE_WAR) } returns true
        every { members.hasPermission(defendingActor, defending, RankPermission.DECLARE_WAR) } returns true
        val service = newService(
            mockk(),
            combatConfig = CombatConfig(warKillWinTarget = 25),
            memberService = members,
        )
        mockBukkitPluginManager()

        val declaration = service.createWarDeclaration(
            declaring,
            defending,
            Duration.ofDays(1),
            setOf(WarObjective(type = ObjectiveType.KILLS, targetValue = 2, description = "Two kills")),
            actorId = declaringActor,
        )!!
        val war = service.acceptWarDeclaration(declaration.id, defendingActor)!!

        assertNull(service.recordOpposingGuildKill(war.id, declaring, defending)!!.winnerGuildId)
        val winning = service.recordOpposingGuildKill(war.id, declaring, defending)!!
        assertEquals(declaring, winning.winnerGuildId)
        assertEquals(WarStatus.ACTIVE, service.getWar(war.id)!!.status)
        assertNull(service.getWar(war.id)!!.winner)
        assertTrue(service.resolveReachedKillTarget(war.id, declaring))
        assertEquals(WarStatus.ENDED, service.getWar(war.id)!!.status)
        assertEquals(declaring, service.getWar(war.id)!!.winner)
        verify(exactly = 2) {
            members.hasPermission(any(), any(), RankPermission.DECLARE_WAR)
        }
    }

    @Test
    fun `configured kill target rejects oversized objectives`() {
        val service = newService(
            mockk(),
            combatConfig = CombatConfig(
                warKillWinTarget = 25,
                warDeclarationCooldownHours = 0,
                warFarmingCooldownHours = 0,
            ),
        )
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()

        assertNull(service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1),
            setOf(WarObjective(type = ObjectiveType.KILLS, targetValue = 50, description = "Too many kills")),
            actorId = UUID.randomUUID(),
        ))
        assertNotNull(service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1),
            setOf(WarObjective(type = ObjectiveType.KILLS, targetValue = 25, description = "Configured cap")),
            actorId = UUID.randomUUID(),
        ))
    }

    @Test
    fun `peaceful mode is enforced at declaration and acceptance`() {
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val modes = mutableMapOf(
            declaring to GuildMode.HOSTILE,
            defending to GuildMode.PEACEFUL,
        )
        val guilds = mockk<GuildRepository> {
            every { getById(any()) } answers {
                val id = firstArg<UUID>()
                Guild(
                    id = id,
                    name = "test-${id.toString().take(8)}",
                    mode = modes[id] ?: GuildMode.HOSTILE,
                    createdAt = Instant.EPOCH,
                )
            }
        }
        val service = newService(
            mockk(),
            combatConfig = CombatConfig(warDeclarationCooldownHours = 0, warFarmingCooldownHours = 0),
            guildRepository = guilds,
        )

        assertNull(service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        ))

        modes[defending] = GuildMode.HOSTILE
        val declaration = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        )
        assertNotNull(declaration)
        modes[defending] = GuildMode.PEACEFUL

        assertNull(service.acceptWarDeclaration(declaration!!.id, UUID.randomUUID()))
    }

    @Test
    fun `defending guild war limit is enforced at declaration and acceptance`() {
        val combat = CombatConfig(
            maxSimultaneousWars = 1,
            warDeclarationCooldownHours = 0,
            warFarmingCooldownHours = 0,
        )
        val service = newService(mockk(), combatConfig = combat)
        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()
        val other = UUID.randomUUID()

        val existing = War(
            declaringGuildId = defending,
            defendingGuildId = other,
            startedAt = Instant.now(),
            status = WarStatus.ACTIVE,
        )
        records[existing.id] = DurableWarRecord(
            id = existing.id,
            war = existing,
            stats = WarStats(existing.id),
        )
        assertNull(service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        ))

        records.remove(existing.id)
        val pending = service.createWarDeclaration(
            declaring, defending, Duration.ofDays(1), emptySet(), actorId = UUID.randomUUID()
        )
        assertNotNull(pending)
        records[existing.id] = DurableWarRecord(
            id = existing.id,
            war = existing,
            stats = WarStats(existing.id),
        )

        assertNull(service.acceptWarDeclaration(pending!!.id, UUID.randomUUID()))
    }

    private fun levelReward(warSlots: Int = 0, bankLimit: Int = 0) =
        LevelRewardConfig(warSlots = warSlots, bankLimit = bankLimit)
}
