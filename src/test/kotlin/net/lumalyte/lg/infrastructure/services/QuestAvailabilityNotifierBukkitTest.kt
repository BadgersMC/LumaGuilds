package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.config.ProgressionSystemConfig
import net.lumalyte.lg.config.QuestSystemConfig
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class QuestAvailabilityNotifierBukkitTest {
    private val quests = mockk<QuestService>()
    private val members = mockk<MemberService>()
    private val config = mockk<ProgressionConfigService>()
    private val lang = mockk<LangService>()
    private val player = mockk<Player>(relaxed = true)
    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val quest = QuestDefinition("stone", action = QuestAction.MINE_BLOCKS,
        target = QuestTarget("minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, 100),
        targetCount = 100, tier = QuestRewardTier.COMMON)
    private val active = WeeklyQuestSet("week", now.minusSeconds(60), now.plusSeconds(60), listOf(quest))
    private val notifier = QuestAvailabilityNotifierBukkit(quests, members, config, lang) { now }

    init {
        every { player.uniqueId } returns playerId
        every { player.isOnline } returns true
        every { player.hasPermission("lumaguilds.guild.quests") } returns true
        every { config.getProgressionConfig() } returns ProgressionSystemConfig(quests = QuestSystemConfig())
        every { members.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { quests.activeQuestSet() } returns active
        every { quests.guildProgress(guildId) } returns emptyList()
        every { lang.msg("quest_discovery.available", *anyVararg()) } returns Component.text("Available")
    }

    @Test fun `ordinary member receives clickable join reminder without management permission`() {
        val message = slot<Component>()
        every { player.sendMessage(capture(message)) } just Runs
        notifier.onJoin(player)
        assertEquals(ClickEvent.runCommand("/g quests"), message.captured.clickEvent())
        verify(exactly = 0) { player.hasPermission("lumaguilds.guild.menu") }
    }

    @Test fun `disabled join notifications leave reset notifications available`() {
        every { config.getProgressionConfig() } returns ProgressionSystemConfig(quests = QuestSystemConfig(notifyOnJoin = false))
        notifier.onJoin(player)
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
        notifier.onReset(listOf(player))
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }

    @Test fun `disabled reset notifications send nothing`() {
        every { config.getProgressionConfig() } returns ProgressionSystemConfig(quests = QuestSystemConfig(notifyOnReset = false))
        notifier.onReset(listOf(player))
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }

    @Test fun `completed milestones do not trigger reminder`() {
        every { quests.guildProgress(guildId) } returns listOf(GuildQuestProgress("week", quest.id, guildId, currentCount = 150))
        notifier.onJoin(player)
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }

    @Test fun `expired future disabled and missing sets do not trigger reminders`() {
        every { quests.activeQuestSet() } returns active.copy(endsAt = now)
        notifier.onJoin(player)
        every { quests.activeQuestSet() } returns active.copy(startsAt = now.plusSeconds(1))
        notifier.onJoin(player)
        every { quests.activeQuestSet() } returns null
        notifier.onJoin(player)
        every { quests.activeQuestSet() } returns active
        every { config.getProgressionConfig() } returns ProgressionSystemConfig(quests = QuestSystemConfig(enabled = false))
        notifier.onJoin(player)
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }

    @Test fun `nonmembers and denied players do not receive reminder`() {
        every { members.getPlayerGuilds(playerId) } returns emptySet()
        notifier.onJoin(player)
        every { members.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { player.hasPermission("lumaguilds.guild.quests") } returns false
        notifier.onJoin(player)
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }
}


