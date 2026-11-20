package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.api.events.GuildRelationChangeEvent
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.values.GuildMoment
import net.lumalyte.lg.infrastructure.services.GuildMomentPresenter
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** REQ-234: online members of both guilds see "ALLIANCE FORMED" when an alliance starts. */
class GuildAllianceMomentListener(
    private val guilds: GuildRepository,
    private val members: MemberRepository,
    private val moments: GuildMomentPresenter,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRelationChange(event: GuildRelationChangeEvent) {
        if (event.newRelationType != RelationType.ALLY) return
        val first = guilds.getById(event.guild1) ?: return
        val second = guilds.getById(event.guild2) ?: return
        val show = Runnable {
            listOf(first.id, second.id).flatMap { members.getByGuild(it) }.map { it.playerId }.toSet()
                .mapNotNull(Bukkit::getPlayer).filter { it.isOnline }
                .forEach { moments.show(it, GuildMoment.ALLIANCE, first.name, second.name) }
        }
        if (Bukkit.isPrimaryThread()) show.run()
        else Bukkit.getPluginManager().getPlugin("LumaGuilds")?.let { Bukkit.getScheduler().runTask(it, show) }
    }
}
