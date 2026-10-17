package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import net.lumalyte.lg.api.events.GuildExplorationMilestoneEvent
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.advancement.Advancement
import org.bukkit.entity.ElderGuardian
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

class ProgressionEventListenerTest {
    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val rankId = UUID.randomUUID()

    @Test
    fun `elder guardian death reaches weekly boss award path`() {
        val fixture = fixture()
        try {
            val guardian = mockk<ElderGuardian> {
                every { killer } returns fixture.player
                every { type } returns EntityType.ELDER_GUARDIAN
            }
            val event = mockk<EntityDeathEvent> {
                every { entity } returns guardian
            }

            fixture.listener.onMobKill(event)

            verify(exactly = 1) {
                fixture.progressionService.awardPlayerActivity(
                    guildId,
                    playerId,
                    1,
                    ExperienceSource.ELDER_GUARDIAN_KILL,
                    eligible = true,
                )
            }
        } finally {
            fixture.listener.shutdown()
        }
    }

    @Test
    fun `vanilla adventure advancement reaches exploration award path`() {
        val fixture = fixture()
        try {
            val advancement = mockk<Advancement> {
                every { key } returns NamespacedKey.minecraft("adventure/adventuring_time")
            }
            val event = mockk<PlayerAdvancementDoneEvent> {
                every { player } returns fixture.player
                every { this@mockk.advancement } returns advancement
            }

            fixture.listener.onExplorationMilestone(event)

            verify(exactly = 1) {
                fixture.progressionService.awardPlayerActivity(
                    guildId,
                    playerId,
                    1,
                    ExperienceSource.EXPLORATION_MILESTONE,
                    eligible = true,
                )
            }
        } finally {
            fixture.listener.shutdown()
        }
    }

    @Test
    fun `external exploration milestone reaches the same capped award path`() {
        val fixture = fixture()
        try {
            val event = GuildExplorationMilestoneEvent(
                fixture.player,
                "EnthusiaAdvancements",
                "exploration:ancient_city",
            )

            fixture.listener.onExternalExplorationMilestone(event)

            verify(exactly = 1) {
                fixture.progressionService.awardPlayerActivity(
                    guildId,
                    playerId,
                    1,
                    ExperienceSource.EXPLORATION_MILESTONE,
                    eligible = true,
                )
            }
        } finally {
            fixture.listener.shutdown()
        }
    }

    @Test
    fun `non adventure vanilla advancement does not count as exploration`() {
        val fixture = fixture()
        try {
            val advancement = mockk<Advancement> {
                every { key } returns NamespacedKey.minecraft("story/mine_stone")
            }
            val event = mockk<PlayerAdvancementDoneEvent> {
                every { player } returns fixture.player
                every { this@mockk.advancement } returns advancement
            }

            fixture.listener.onExplorationMilestone(event)

            verify(exactly = 0) {
                fixture.progressionService.awardPlayerActivity(any(), any(), any(), any(), any())
            }
        } finally {
            fixture.listener.shutdown()
        }
    }

    private fun fixture(): Fixture {
        val progressionService = mockk<ProgressionService>(relaxed = true)
        val memberRepository = mockk<MemberRepository> {
            every { getAll() } returns setOf(Member(playerId, guildId, rankId, Instant.EPOCH))
        }
        val configService = object : ConfigService {
            override fun loadConfig(): MainConfig = MainConfig()
        }
        val asyncTaskService = mockk<AsyncTaskService>()
        every { asyncTaskService.runAsyncCallback<Unit>(any(), any(), any()) } answers {
            firstArg<() -> Unit>().invoke()
        }
        val plugin = mockk<Plugin>(relaxed = true) {
            every { name } returns "LumaGuilds"
        }
        val player = mockk<Player> {
            every { uniqueId } returns playerId
            every { gameMode } returns GameMode.SURVIVAL
        }
        val listener = ProgressionEventListener(
            progressionService = progressionService,
            memberService = mockk<MemberService>(relaxed = true),
            memberRepository = memberRepository,
            configService = configService,
            asyncTaskService = asyncTaskService,
            leaderboardService = mockk<LeaderboardService>(relaxed = true),
            playtimeActivityService = object : PlaytimeActivityService {
                override fun isXpBlocked(playerId: UUID): Boolean = false
            },
            blockProvenanceRepository = mockk<BlockProvenanceRepository>(relaxed = true),
            plugin = plugin,
            virtualDispatcher = Dispatchers.Unconfined,
            provenanceOperations = BlockProvenanceOperationQueue(Executor { it.run() }),
        )
        return Fixture(listener, progressionService, player)
    }

    private data class Fixture(
        val listener: ProgressionEventListener,
        val progressionService: ProgressionService,
        val player: Player,
    )
}
