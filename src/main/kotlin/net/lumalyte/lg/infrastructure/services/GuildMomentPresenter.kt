package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.lumalyte.lg.domain.values.GuildMoment
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Duration

/** REQ-233 to REQ-235: shows a moment's title, chat line and sound to one player. */
class GuildMomentPresenter(private val plugin: Plugin, private val lang: LangService) {

    /** [a] and [b] fill the moment's guild placeholders; their meaning depends on the moment. */
    fun show(player: Player, moment: GuildMoment, a: String, b: String = "") {
        val (title, subtitle, chat) = texts(moment, a, b)
        if (title != null) {
            player.showTitle(Title.title(title, subtitle ?: Component.empty(),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3500), Duration.ofMillis(750))))
        }
        chat?.let(player::sendMessage)
        play(player, moment)
    }

    fun play(player: Player, moment: GuildMoment) = play(player, moment.cues)

    /** REQ-254: also used by the staff sound preview. */
    fun play(player: Player, cues: List<net.lumalyte.lg.domain.values.SoundCue>) {
        cues.forEach { cue ->
            val sound = net.kyori.adventure.sound.Sound.sound(Key.key(cue.sound), net.kyori.adventure.sound.Sound.Source.MASTER, cue.volume, cue.pitch)
            if (cue.delayTicks <= 0) player.playSound(sound)
            else plugin.server.scheduler.runTaskLater(plugin, Runnable { if (player.isOnline) player.playSound(sound) }, cue.delayTicks)
            cue.stopAfterTicks?.let { after ->
                val stop = net.kyori.adventure.sound.SoundStop.named(Key.key(cue.sound))
                plugin.server.scheduler.runTaskLater(plugin, Runnable { if (player.isOnline) player.stopSound(stop) }, cue.delayTicks + after)
            }
        }
    }

    private fun texts(moment: GuildMoment, a: String, b: String): Triple<Component?, Component?, Component?> = when (moment) {
        GuildMoment.DECLARED -> Triple(lang.msg("notification.moments.declared.title"),
            lang.msg("notification.moments.declared.subtitle", "declarer" to a, "defender" to b),
            lang.msg("notification.moments.declared.chat", "declarer" to a, "defender" to b))
        GuildMoment.BEGINS -> Triple(lang.msg("notification.moments.begins.title"),
            lang.msg("notification.moments.begins.subtitle", "first" to a, "second" to b),
            lang.msg("notification.moments.begins.chat", "first" to a, "second" to b))
        GuildMoment.VICTORY -> Triple(lang.msg("notification.moments.victory.title"),
            lang.msg("notification.moments.victory.subtitle", "guild" to a),
            lang.msg("notification.moments.victory.chat", "guild" to a))
        GuildMoment.DEFEAT -> Triple(lang.msg("notification.moments.defeat.title"),
            lang.msg("notification.moments.defeat.subtitle", "guild" to a),
            lang.msg("notification.moments.defeat.chat", "guild" to a))
        GuildMoment.PEACE -> Triple(lang.msg("notification.moments.peace.title"),
            lang.msg("notification.moments.peace.subtitle", "first" to a, "second" to b),
            lang.msg("notification.moments.peace.chat"))
        GuildMoment.ALLIANCE -> Triple(lang.msg("notification.moments.alliance.title"),
            lang.msg("notification.moments.alliance.subtitle", "first" to a, "second" to b),
            lang.msg("notification.moments.alliance.chat", "first" to a, "second" to b))
        GuildMoment.KILL -> Triple(null, null, null)
    }
}
