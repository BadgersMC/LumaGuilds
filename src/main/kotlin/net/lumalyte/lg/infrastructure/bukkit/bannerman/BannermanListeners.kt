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
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

internal class BannermanListeners(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService,
    private val guildService: GuildService,
    private val memberService: MemberService,
) : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) = trySpawn(e.player)

    /** Despawn the bannerman display when a player quits. */
    @EventHandler
    fun onQuit(e: PlayerQuitEvent) = renderer.despawnFor(e.player.uniqueId)

    /** Despawn the bannerman display when a player dies. */
    @EventHandler
    fun onDeath(e: PlayerDeathEvent) = renderer.despawnFor(e.entity.uniqueId)

    /**
     * Respawn the bannerman display one tick after the player respawns. The respawn-event
     * location is still in flux on the same tick, so an immediate runTask can land the
     * display at the death point.
     */
    @EventHandler
    fun onRespawn(e: PlayerRespawnEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { trySpawn(e.player) }, 1L)
    }

    /**
     * Respawn the bannerman display after a player changes worlds, deferred one tick so
     * the world transfer is fully applied before we spawn the display in the new world.
     */
    @EventHandler
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        renderer.despawnFor(e.player.uniqueId)
        plugin.server.scheduler.runTaskLater(plugin, Runnable { trySpawn(e.player) }, 1L)
    }

    private fun trySpawn(player: Player) {
        val banner = getBannerForPlayer(player) ?: return
        renderer.spawnFor(player, banner)
    }

    private fun getBannerForPlayer(player: Player): ItemStack? {
        val guildId = memberService.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return null
        if (!guildService.getBannermanEnabled(guildId)) return null
        val guild = guildService.getGuild(guildId) ?: return null
        return guild.banner?.deserializeToItemStack()
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

    /**
     * Re-render every online member when the guild banner ItemStack changes.
     * If the banner has been cleared (or fails to deserialize) we despawn rather than bail —
     * leaving stale displays around would mean a removed banner stays visible forever.
     */
    fun onGuildBannerChanged(guildId: UUID) {
        val guild = guildService.getGuild(guildId) ?: return
        val members = memberService.getGuildMembers(guildId)
        val banner = guild.banner?.deserializeToItemStack()
        if (banner == null) {
            members.forEach { renderer.despawnFor(it.playerId) }
            return
        }
        // Online members without an existing display (e.g. just joined while the banner was
        // being edited) need a fresh spawn rather than a silent update on nothing.
        members.forEach { member ->
            if (renderer.isTracking(member.playerId)) {
                renderer.updateBanner(member.playerId, banner)
            } else {
                plugin.server.getPlayer(member.playerId)?.let { trySpawn(it) }
            }
        }
    }
}
