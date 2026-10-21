package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.events.*
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.Ageable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/** Completed, non-cancelled game activity only. Shared events credit online guild members. */
internal class GuildAchievementListener(private val runtime: GuildAchievementRuntime) : Listener {
    private fun actor(id: UUID, type: String) { Bukkit.getPlayer(id)?.let { runtime.signal(it, type) } }
    private fun activity(player: Player, type: String, amount: Int = 1) {
        if (player.gameMode !in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)) return
        runtime.guild(player.uniqueId)?.let { runtime.shared(it, type, amount = amount) }
    }
    @EventHandler(priority = EventPriority.MONITOR) fun created(e: GuildCreatedEvent) = actor(e.ownerId, "GUILD_CREATE")
    @EventHandler(priority = EventPriority.MONITOR) fun banner(e: GuildBannerSetEvent) = actor(e.playerId, "GUILD_BANNER_SET")
    @EventHandler(priority = EventPriority.MONITOR) fun home(e: GuildHomeSetEvent) = actor(e.playerId, "GUILD_HOME_SET")
    @EventHandler(priority = EventPriority.MONITOR) fun vault(e: GuildVaultPlacedEvent) = actor(e.playerId, "GUILD_VAULT_PLACED")
    @EventHandler(priority = EventPriority.MONITOR) fun kicked(e: GuildMemberRemovedEvent) {
        if (e.wasKicked) actor(e.actorId, "GUILD_MEMBER_REMOVED")
    }
    @EventHandler(priority = EventPriority.MONITOR) fun transferred(e: GuildOwnershipTransferEvent) = actor(e.newOwnerId, "GUILD_OWNERSHIP_TRANSFER")
    @EventHandler(priority = EventPriority.MONITOR) fun level(e: GuildLevelUpEvent) = runtime.shared(e.guildId, "GUILD_LEVEL_UP", e.newLevel.toString())
    @EventHandler(priority = EventPriority.MONITOR) fun declared(e: GuildWarDeclaredEvent) = runtime.shared(e.declaringGuildId, "GUILD_WAR_DECLARED")
    @EventHandler(priority = EventPriority.MONITOR) fun won(e: GuildWarEndEvent) {
        e.winnerGuildId?.let { runtime.shared(it, "GUILD_WAR_WON", receipt = "war:${e.warId}") }
    }
    @EventHandler(priority = EventPriority.MONITOR) fun kill(e: GuildWarKillEvent) = actor(e.killerId, "GUILD_WAR_KILL")
    @EventHandler(priority = EventPriority.MONITOR) fun relation(e: GuildRelationChangeEvent) {
        if (e.newRelationType == RelationType.ALLY) listOf(e.guild1, e.guild2).forEach { runtime.shared(it, "GUILD_ALLIANCE_FORMED") }
    }
    @EventHandler(priority = EventPriority.MONITOR) fun rank(e: GuildLeaderboardRankChangeEvent) =
        runtime.shared(e.guildId, "GUILD_LEADERBOARD_RANK", e.newRank.toString())
    @EventHandler(priority = EventPriority.MONITOR) fun joined(e: PlayerJoinEvent) { runtime.initialize(e.player) }
    @EventHandler(priority = EventPriority.MONITOR) fun left(e: PlayerQuitEvent) { runtime.leave(e.player) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) fun smelt(e: FurnaceExtractEvent) = activity(e.player, "GUILD_COLLECTIVE_SMELT", e.itemAmount)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) fun block(e: BlockBreakEvent) {
        activity(e.player, "GUILD_COLLECTIVE_BLOCK_BREAK")
        val data = e.block.blockData
        val mature = data is Ageable && data.age == data.maximumAge
        if (e.block.type in CROPS && mature || e.block.type in setOf(Material.MELON, Material.PUMPKIN)) {
            activity(e.player, "GUILD_COLLECTIVE_CROP_HARVEST")
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) fun death(e: EntityDeathEvent) {
        val killer = e.entity.killer ?: return
        if (killer.gameMode !in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)) return
        val guild = runtime.guild(killer.uniqueId) ?: return
        val members = runtime.online(guild).filter { it.world == e.entity.world }
        when (e.entity.type) {
            EntityType.ENDER_DRAGON -> if (e.entity.world.environment == World.Environment.THE_END && members.size >= 3)
                members.forEach { runtime.signal(it, "GUILD_COLLECTIVE_BOSS_KILL", "ENDER_DRAGON", receipt = "boss:${e.entity.uniqueId}") }
            EntityType.WITHER -> members.forEach { runtime.signal(it, "GUILD_COLLECTIVE_BOSS_KILL", "WITHER", receipt = "boss:${e.entity.uniqueId}") }
            else -> if (e.entity is Monster) runtime.shared(guild, "GUILD_COLLECTIVE_MOB_KILL")
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) fun friendly(e: EntityDamageByEntityEvent) {
        val actor = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val guild = runtime.guild(actor.uniqueId) ?: return
        if (actor.uniqueId != victim.uniqueId && runtime.guild(victim.uniqueId) == guild && e.finalDamage > 0)
            runtime.signal(actor, "GUILD_FRIENDLY_FIRE")
    }
    companion object {
        private val CROPS = setOf(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH)
    }
}
