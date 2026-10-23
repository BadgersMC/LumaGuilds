package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant

/** Consent and onboarding gates for quiet player discovery shortcuts. */
internal class GuildRecruitmentDiscovery(
    private val directory: RecruitmentDirectoryRepository,
    private val members: MemberService,
    private val config: ConfigService,
    private val lang: LangService,
) {
    fun onLeave(player: Player) {
        val guildless = members.getPlayerGuilds(player.uniqueId).isEmpty()
        val enabled = directory.remindersEnabled(player.uniqueId)
        if (guildless && enabled && player.hasPermission(RECRUITMENT_PERMISSION)) {
            player.sendMessage(
                lang.msg("recruitment.discovery.leave").clickEvent(ClickEvent.runCommand(RECRUITMENT_COMMAND)),
            )
        }
    }

    fun prompt(player: Player, joined: Instant?, repeat: Boolean) {
        if (eligible(player, joined)) {
            val minutes = config.loadConfig().recruitment.reminderMinutes
            if (!repeat || minutes != 0) {
                val interval = if (repeat) Duration.ofMinutes(minutes.toLong()) else null
                deliverPrompt(player, interval)
            }
        }
    }

    private fun deliverPrompt(player: Player, interval: Duration?) {
        if (directory.consumePrompt(player.uniqueId, Instant.now(), interval)) {
            player.sendMessage(
                lang.msg("recruitment.discovery.prompt").clickEvent(ClickEvent.runCommand(RECRUITMENT_COMMAND)),
            )
        }
    }

    private fun eligible(player: Player, joined: Instant?): Boolean {
        return player.isOnline && player.hasPermission(RECRUITMENT_PERMISSION) &&
            members.getPlayerGuilds(player.uniqueId).isEmpty() && joined != null &&
            !Instant.now().isBefore(joined.plusSeconds(config.loadConfig().recruitment.onboardingDelaySeconds.toLong()))
    }
}
