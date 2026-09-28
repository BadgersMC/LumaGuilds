package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestDefinition
import java.util.UUID

/**
 * Application-layer port for announcing the one-time completion of a weekly quest.
 */
interface QuestCompletionNotifier {
    /**
     * Durably queues the completion notification for the guild's members.
     * Returns false when durable queuing failed and reward settlement should retry.
     */
    fun onCompleted(
        guildId: UUID,
        quest: QuestDefinition,
        progress: GuildQuestProgress,
    ): Boolean

    fun deliverUnread(playerId: UUID) = Unit

    companion object {
        val NOOP = object : QuestCompletionNotifier {
            override fun onCompleted(
                guildId: UUID,
                quest: QuestDefinition,
                progress: GuildQuestProgress,
            ): Boolean = true
        }
    }
}
