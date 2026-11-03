package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import org.bukkit.entity.Player
import java.time.Instant

/** Availability reminders do not claim rewards or change progression. Called on the server thread. */
class QuestAvailabilityNotifierBukkit(
    private val quests: QuestService,
    private val members: MemberService,
    private val config: ProgressionConfigService,
    private val lang: LangService,
    private val now: () -> Instant = Instant::now
) {
    fun onJoin(player: Player) {
        if (config.getProgressionConfig().quests.notifyOnJoin) notify(player)
    }

    fun onReset(players: Collection<Player>) {
        if (config.getProgressionConfig().quests.notifyOnReset) players.forEach(::notify)
    }

    private fun notify(player: Player) {
        if (!player.isOnline || !player.hasPermission("lumaguilds.guild.quests")) return
        if (!config.getProgressionConfig().quests.enabled) return
        val guildId = members.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return
        val active = quests.activeQuestSet() ?: return
        val instant = now()
        if (active.startsAt.isAfter(instant) || !active.endsAt.isAfter(instant)) return
        val progress = quests.guildProgress(guildId).associateBy { it.questId }
        val remaining = active.quests.count {
            it.targetCount > 0 && (progress[it.id]?.currentCount ?: 0) < it.targetCount
        }
        if (remaining == 0) return
        player.sendMessage(
            lang.msg("quest_discovery.available", "count" to remaining)
                .clickEvent(ClickEvent.runCommand("/g quests"))
        )
    }
}
