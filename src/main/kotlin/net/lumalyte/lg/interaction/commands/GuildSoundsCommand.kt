package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.lumalyte.lg.domain.values.GuildSoundCatalog
import net.lumalyte.lg.domain.values.GuildSoundEntry
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * REQ-254: `/lumaguilds sounds [id|all|stop]`. Plays guild sounds to the sender only, so staff can
 * hear every one without causing the event. Nothing about any guild changes.
 */
internal class GuildSoundsCommand(
    private val lang: LangService,
    private val play: (Player, GuildSoundEntry) -> Unit,
    private val later: (Long, () -> Unit) -> Unit,
    private val stop: (Player, GuildSoundEntry) -> Unit = { _, _ -> },
) {
    /** One running "all" playlist per player; a new command replaces it. */
    private val playlists = ConcurrentHashMap<UUID, Any>()

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERMISSION)) return sender.sendMessage(lang.msg("admin.common.no_permission"))
        val player = sender as? Player ?: return sender.sendMessage(lang.msg("admin.sounds.players_only"))
        val wanted = args.getOrNull(1)?.lowercase()
        playlists.remove(player.uniqueId)
        when (wanted) {
            null, "list" -> list(player)
            "stop" -> {
                GuildSoundCatalog.entries.forEach { stop(player, it) }
                player.sendMessage(lang.msg("admin.sounds.stopped"))
            }
            "all" -> playAll(player)
            else -> {
                val entry = GuildSoundCatalog.find(wanted) ?: return player.sendMessage(lang.msg("admin.sounds.unknown", "id" to wanted))
                announce(player, entry)
                play(player, entry)
            }
        }
    }

    private fun list(player: Player) {
        player.sendMessage(lang.msg("admin.sounds.header", "count" to GuildSoundCatalog.entries.size))
        GuildSoundCatalog.entries.groupBy { it.group }.forEach { (group, entries) ->
            player.sendMessage(lang.msg("admin.sounds.group", "group" to group))
            entries.forEach { entry ->
                player.sendMessage(lang.msg("admin.sounds.entry", "label" to entry.label, "id" to entry.id)
                    .hoverEvent(HoverEvent.showText(Component.text(entry.heardWhen)))
                    .clickEvent(ClickEvent.runCommand("/lumaguilds sounds ${entry.id}")))
            }
        }
        player.sendMessage(lang.msg("admin.sounds.footer")
            .clickEvent(ClickEvent.runCommand("/lumaguilds sounds all")))
    }

    private fun playAll(player: Player) {
        val token = Any()
        playlists[player.uniqueId] = token
        var at = 0L
        GuildSoundCatalog.entries.forEach { entry ->
            later(at) {
                if (playlists[player.uniqueId] === token && player.isOnline) {
                    announce(player, entry)
                    play(player, entry)
                }
            }
            at += entry.lengthTicks + GAP_TICKS
        }
        later(at) { if (playlists.remove(player.uniqueId, token) && player.isOnline) player.sendMessage(lang.msg("admin.sounds.finished")) }
        player.sendMessage(lang.msg("admin.sounds.playing_all", "count" to GuildSoundCatalog.entries.size, "seconds" to at / 20))
    }

    private fun announce(player: Player, entry: GuildSoundEntry) =
        player.sendActionBar(lang.msg("admin.sounds.now", "label" to entry.label, "when" to entry.heardWhen))

    companion object {
        const val PERMISSION = "lumaguilds.admin.sounds"
        const val GAP_TICKS = 20L
    }
}
