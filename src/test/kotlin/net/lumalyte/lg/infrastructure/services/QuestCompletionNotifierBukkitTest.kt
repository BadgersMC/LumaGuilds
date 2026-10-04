package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.QuestCompletionNotificationRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.QuestCompletionNotification
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

class QuestCompletionNotifierBukkitTest {
    @Test
    fun `offline guild members receive persisted quest toast on next join`() {
        val memberService = mockk<MemberService>()
        val lang = mockk<LangService>()
        val toastSender = mockk<ToastSender>()
        val questRepository = mockk<QuestRepository>()
        val notificationRepository = InMemoryQuestCompletionNotifications()
        val icon = mockk<ItemStack>()
        val guildId = UUID.randomUUID()
        val onlineId = UUID.randomUUID()
        val offlineId = UUID.randomUUID()
        val rankId = UUID.randomUUID()
        val quest = quest()
        val week = WeeklyQuestSet(
            "2026-09-21",
            Instant.parse("2026-09-21T00:00:00Z"),
            Instant.parse("2026-09-28T00:00:00Z"),
            listOf(quest),
        )
        val progress = GuildQuestProgress(
            week.weekId,
            quest.id,
            guildId,
            currentCount = quest.targetCount,
            claimed = true,
            completedAt = Instant.parse("2026-09-24T12:00:00Z"),
            rewardDelivered = true,
        )

        every { memberService.getGuildMembers(guildId) } returns setOf(
            Member(onlineId, guildId, rankId, Instant.EPOCH),
            Member(offlineId, guildId, rankId, Instant.EPOCH),
        )
        every { questRepository.getQuestSet(week.weekId) } returns week
        every { lang.msg("notification.quest.complete.toast.title") } returns
            Component.text("QUEST COMPLETE!")
        every {
            lang.msg("notification.quest.complete.toast.description", *anyVararg())
        } returns Component.text("Quest complete")
        every {
            lang.msg("notification.quest.complete.toast.fallback", *anyVararg())
        } returns Component.text("Quest complete")
        every {
            toastSender.show(any(), any(), any(), any(), icon, ToastFrame.TASK)
        } returns true

        val online = mockk<Player> {
            every { uniqueId } returns onlineId
            every { isOnline } returns true
        }
        var offlineNow = false
        val offline = mockk<Player> {
            every { uniqueId } returns offlineId
            every { isOnline } answers { offlineNow }
        }

        val players = mapOf(onlineId to online, offlineId to offline)
        val service = QuestCompletionNotifierBukkit(
            memberService = memberService,
            lang = lang,
            toastSender = toastSender,
            notifications = notificationRepository,
            quests = questRepository,
            nowMillis = { 1234L },
            playerLookup = players::get,
            iconFactory = { icon },
        )

        service.onCompleted(guildId, quest, progress)

        assertEquals(0, notificationRepository.getPending(onlineId).size)
        assertEquals(1, notificationRepository.getPending(offlineId).size)
        verify(exactly = 1) {
            toastSender.show(online, any(), any(), any(), icon, ToastFrame.TASK)
        }

        offlineNow = true
        service.deliverUnread(offlineId)

        assertEquals(0, notificationRepository.getPending(offlineId).size)
        verify(exactly = 1) {
            toastSender.show(offline, any(), any(), any(), icon, ToastFrame.TASK)
        }
    }

    @Test
    fun `main thread timeout leaves completion notification pending`() {
        val memberService = mockk<MemberService>()
        val notificationRepository = InMemoryQuestCompletionNotifications()
        val questRepository = mockk<QuestRepository>()
        val guildId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val rankId = UUID.randomUUID()
        val quest = quest()
        val week = WeeklyQuestSet(
            "2026-09-21",
            Instant.parse("2026-09-21T00:00:00Z"),
            Instant.parse("2026-09-28T00:00:00Z"),
            listOf(quest),
        )
        val progress = GuildQuestProgress(
            week.weekId,
            quest.id,
            guildId,
            currentCount = quest.targetCount,
            claimed = true,
            completedAt = Instant.parse("2026-09-24T12:00:00Z"),
            rewardDelivered = true,
        )
        every { memberService.getGuildMembers(guildId) } returns setOf(
            Member(playerId, guildId, rankId, Instant.EPOCH)
        )
        every { questRepository.getQuestSet(week.weekId) } returns week

        val service = QuestCompletionNotifierBukkit(
            memberService = memberService,
            lang = mockk(relaxed = true),
            toastSender = mockk(relaxed = true),
            notifications = notificationRepository,
            quests = questRepository,
            onMainThread = { throw TimeoutException("main thread did not respond") },
        )

        service.onCompleted(guildId, quest, progress)

        assertEquals(1, notificationRepository.getPending(playerId).size)
    }

    private fun quest() = QuestDefinition(
        id = "fish-weekly",
        action = QuestAction.FISH,
        target = QuestTarget(
            id = "minecraft:item/any",
            allowedActions = setOf(QuestAction.FISH),
            minimumAmount = 1,
            maximumAmount = 500,
        ),
        targetCount = 100,
        tier = QuestRewardTier.COMMON,
        experienceReward = 5_000,
        leaderboard = true,
        leaderboardPayouts = mapOf(1 to 5_000),
    )
}

private class InMemoryQuestCompletionNotifications :
    QuestCompletionNotificationRepository {
    private val values = linkedMapOf<UUID, QuestCompletionNotification>()

    override fun add(notification: QuestCompletionNotification): Boolean =
        values.putIfAbsent(notification.id, notification) == null

    override fun getPending(playerId: UUID): List<QuestCompletionNotification> =
        values.values
            .filter { it.playerId == playerId && it.deliveredAt == null }
            .sortedBy { it.createdAt }

    override fun markDelivered(notificationId: UUID, deliveredAt: Long): Boolean {
        val current = values[notificationId] ?: return false
        if (current.deliveredAt != null) return false
        values[notificationId] = current.copy(deliveredAt = deliveredAt)
        return true
    }
}
