package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.config.CombatConfig
import net.lumalyte.lg.config.LevelRewardConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarStatus
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
import org.bukkit.Bukkit
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * REQ-008: the combat configuration must actually be enforced —
 * `war_duration_hours` caps declared war length, `max_simultaneous_wars`
 * (refined by progression) limits concurrent wars, `war_end_grace_period_minutes`
 * delays force-end, and `kill_cooldown_minutes` + `same_player_kill_limit`
 * suppress farmed-kill XP.
 */
class WarConfigEnforcementTest {

    // ---------- pure decision helpers (no Bukkit) ----------

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

    // ---------- declaration flow (REQ-024: no auto-accept) ----------

    private fun newService(configService: ConfigService): WarServiceBukkit {
        val config = mockk<MainConfig>()
        every { config.combat } returns CombatConfig()
        every { configService.loadConfig() } returns config
        return WarServiceBukkit(
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = mockk<ProgressionRepository>(relaxed = true),
            progressionConfigService = mockk(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    private fun mockBukkitPluginManager() {
        mockkStatic(Bukkit::class)
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)
    }

    @Test
    fun `declareWar creates a pending declaration, not an active war`() {
        val service = newService(mockk())

        val declaring = UUID.randomUUID()
        val defending = UUID.randomUUID()

        val declaration = service.declareWar(declaring, defending, Duration.ofDays(7), emptySet(), UUID.randomUUID())

        assertNotNull(declaration, "declareWar must create a declaration (REQ-024)")
        assertEquals(declaring, declaration!!.declaringGuildId)
        assertEquals(defending, declaration.defendingGuildId)
        assertNull(service.getCurrentWarBetweenGuilds(declaring, defending), "no active war may exist before acceptance")
        assertNotNull(service.getPendingDeclarationsForGuild(defending).firstOrNull { it.id == declaration.id })
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
            configService = configService,
            bankService = mockk(relaxed = true),
            progressionRepository = mockk<ProgressionRepository>(relaxed = true),
            progressionConfigService = mockk(relaxed = true)
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
    fun `farming detection is per victim - different victims are not flagged`() {
        val service = newServiceWithCombat(CombatConfig(killCooldownMinutes = 5, samePlayerKillLimit = 1))

        assertFalse(service.recordWarKillAndCheckFarming(UUID.randomUUID(), UUID.randomUUID()))
        assertFalse(service.recordWarKillAndCheckFarming(UUID.randomUUID(), UUID.randomUUID()))
    }

    private fun levelReward(warSlots: Int = 0, bankLimit: Int = 0) =
        LevelRewardConfig(warSlots = warSlots, bankLimit = bankLimit)
}
