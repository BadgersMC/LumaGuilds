package net.lumalyte.lg.infrastructure.bukkit.bannerman

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class BannermanListeners(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService,
    private val guildService: GuildService,
    private val memberService: MemberService
) : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) = trySpawn(e.player)

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) = renderer.despawnFor(e.player.uniqueId)

    @EventHandler
    fun onDeath(e: PlayerDeathEvent) = renderer.despawnFor(e.entity.uniqueId)

    @EventHandler
    fun onRespawn(e: PlayerRespawnEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { trySpawn(e.player) })
    }

    @EventHandler
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        renderer.despawnFor(e.player.uniqueId)
        trySpawn(e.player)
    }

    private fun trySpawn(player: Player) {
        val guildId = memberService.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return
        if (!guildService.getBannermanEnabled(guildId)) return
        val guild = guildService.getGuild(guildId) ?: return
        val banner = guild.banner?.deserializeToItemStack() ?: return
        renderer.spawnFor(player, banner)
    }

    /** Spawn displays for every online member when a guild flips bannerman ON. */
    fun onBannermanEnabled(guildId: UUID) {
        memberService.getGuildMembers(guildId)
            .mapNotNull { plugin.server.getPlayer(it.playerId) }
            .forEach { trySpawn(it) }
    }

    /** Despawn displays for every member when a guild flips bannerman OFF (or is disbanded). */
    fun onBannermanDisabled(guildId: UUID) {
        memberService.getGuildMembers(guildId).forEach { renderer.despawnFor(it.playerId) }
    }

    /** Re-render every online member when the guild banner ItemStack changes. */
    fun onGuildBannerChanged(guildId: UUID) {
        val guild = guildService.getGuild(guildId) ?: return
        val banner = guild.banner?.deserializeToItemStack() ?: return
        memberService.getGuildMembers(guildId).forEach { renderer.updateBanner(it.playerId, banner) }
    }
}
