package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.QuestCompletionNotificationRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestCompletionNotifier
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestCompletionNotification
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.utils.QuestDisplayFormatter
import net.lumalyte.lg.utils.QuestIconProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.UUID

class QuestCompletionNotifierBukkit(
    private val memberService: MemberService,
    private val lang: LangService,
    private val toastSender: ToastSender,
    private val notifications: QuestCompletionNotificationRepository,
    private val quests: QuestRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val playerLookup: (UUID) -> Player? = { Bukkit.getPlayer(it) },
    private val iconFactory: (QuestDefinition) -> ItemStack = QuestIconProvider::vanillaItemFor,
) : QuestCompletionNotifier {
    private val logger = LoggerFactory.getLogger(QuestCompletionNotifierBukkit::class.java)

    override fun onCompleted(
        guildId: UUID,
        quest: QuestDefinition,
        progress: GuildQuestProgress,
    ): Boolean {
        var durable = true
        memberService.getGuildMembers(guildId).forEach { member ->
            val notification = QuestCompletionNotification(
                id = notificationId(
                    progress.weekId,
                    quest.id,
                    guildId,
                    member.playerId,
                ),
                playerId = member.playerId,
                weekId = progress.weekId,
                questId = quest.id,
                guildId = guildId,
                createdAt = progress.completedAt?.toEpochMilli() ?: nowMillis(),
            )
            val queued = runCatching {
                notifications.add(notification)
                true
            }.onFailure {
                logger.error(
                    "Failed to persist weekly quest completion for ${member.playerId}",
                    it,
                )
            }.getOrDefault(false)
            durable = durable && queued
            if (queued) deliverUnread(member.playerId)
        }
        return durable
    }

    override fun deliverUnread(playerId: UUID) {
        val pending = runCatching { notifications.getPending(playerId) }
            .onFailure {
                logger.error(
                    "Failed to load weekly quest completion notifications for $playerId",
                    it,
                )
            }
            .getOrDefault(emptyList())

        pending.forEach { notification ->
            val questSet = quests.getQuestSet(notification.weekId)
            val quest = questSet?.quests?.firstOrNull { it.id == notification.questId }
            if (quest == null) {
                logger.warn(
                    "Cannot resolve quest ${notification.questId} from week ${notification.weekId} " +
                        "for completion notification ${notification.id}"
                )
                return@forEach
            }

            val delivered = runCatching {
                onMainThread {
                    val player = playerLookup(playerId)?.takeIf { it.isOnline }
                    if (player == null) false else {
                        present(player, notification, quest)
                        true
                    }
                }
            }.onFailure {
                logger.error(
                    "Failed to deliver weekly quest completion ${notification.id}",
                    it,
                )
            }.getOrDefault(false)

            if (delivered) {
                runCatching {
                    notifications.markDelivered(notification.id, nowMillis())
                }.onFailure {
                    logger.error(
                        "Failed to mark weekly quest completion ${notification.id} delivered",
                        it,
                    )
                }
            }
        }
    }

    private fun present(
        player: Player,
        notification: QuestCompletionNotification,
        quest: QuestDefinition,
    ) {
        val objective = QuestDisplayFormatter.name(quest)
        val title = lang.msg("notification.quest.complete.toast.title")
        val description = lang.msg(
            "notification.quest.complete.toast.description",
            "objective" to objective,
            "xp" to quest.experienceReward,
        )
        val fallback = lang.msg(
            "notification.quest.complete.toast.fallback",
            "objective" to objective,
            "xp" to quest.experienceReward,
        )
        val frame = when (quest.tier) {
            QuestRewardTier.COMMON -> ToastFrame.TASK
            QuestRewardTier.CHALLENGING -> ToastFrame.GOAL
            QuestRewardTier.HEADLINE,
            QuestRewardTier.CONDITIONED -> ToastFrame.CHALLENGE
        }
        val shown = toastSender.show(
            player = player,
            notificationId = notification.id,
            title = title,
            description = description,
            icon = iconFactory(quest),
            frame = frame,
        )
        if (!shown) player.sendActionBar(fallback)
    }

    private fun notificationId(
        weekId: String,
        questId: String,
        guildId: UUID,
        playerId: UUID,
    ): UUID = UUID.nameUUIDFromBytes(
        "weekly-quest-complete:$weekId:$questId:$guildId:$playerId"
            .toByteArray(StandardCharsets.UTF_8)
    )
}
