package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.HallPedestalRepository
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.HallThemeService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.rewards.HallDisplay
import net.lumalyte.lg.domain.rewards.HallPedestal
import net.lumalyte.lg.domain.rewards.HallRanking
import net.lumalyte.lg.infrastructure.services.hall.HallStatueBuilder
import net.lumalyte.lg.infrastructure.services.hall.HallStatuePose
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REQ-275: the Hall of Prestige. Each pedestal staff placed holds the statue of one of the highest
 * prestige guilds (slot 1 the top guild), wearing that guild's Hall Display choices. Java players see
 * the animated pose; Bedrock players, who can't see the model, see the plain standing statue.
 *
 * Statues are never saved with the world: they are rebuilt from the database on start, every few
 * minutes, when a guild changes its Hall look or prestiges, when a pedestal changes and when a
 * pedestal's chunk loads again.
 */
class HallStatueService(
    private val plugin: Plugin,
    private val pedestals: HallPedestalRepository,
    private val history: PrestigeHistory,
    private val guilds: GuildService,
    private val themes: HallThemeService,
    private val members: MemberService,
    private val ranks: RankService,
    private val platform: PlatformDetectionService?,
) : Listener {
    private val logger = LoggerFactory.getLogger(HallStatueService::class.java)

    /** What one pedestal should show; equal plans need no rebuild. */
    data class Plan(
        val pedestal: HallPedestal,
        val guildId: UUID,
        val guildName: String,
        val prestige: Int,
        val display: HallDisplay,
        val skin: UUID?,
        val banner: String?,
    )

    private class Standing(val plan: Plan, val entities: List<Entity>, val top: Location, val closer: (() -> Unit)?,
                           val sword: ItemDisplay?, val look: HallLook)

    private val standing = ConcurrentHashMap<Int, Standing>()
    /** Plans that failed to build; not retried until the plan changes, so a bad build can't loop. */
    private val failed = ConcurrentHashMap<Int, Plan>()
    @Volatile private var plans: List<Plan> = emptyList()
    private val tasks = mutableListOf<BukkitTask>()
    private val refreshQueued = AtomicBoolean(false)

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        val scheduler = Bukkit.getScheduler()
        tasks += scheduler.runTaskTimerAsynchronously(plugin, Runnable { refreshNow() }, START_DELAY, REFRESH_TICKS)
        tasks += scheduler.runTaskTimer(plugin, Runnable { tick() }, START_DELAY + 20, EFFECT_TICKS)
    }

    fun stop() {
        tasks.forEach { runCatching { it.cancel() } }
        tasks.clear()
        standing.keys.toList().forEach(::take)
        failed.clear()
    }

    /** Rebuilds soon; several calls close together run one refresh. */
    fun refreshSoon(retryFailed: Boolean = false) {
        if (retryFailed) Bukkit.getScheduler().runTask(plugin, Runnable { failed.clear() })
        if (!plugin.isEnabled || !refreshQueued.compareAndSet(false, true)) return
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
            refreshQueued.set(false)
            refreshNow()
        }, 40L)
    }

    fun pedestalCount(): Int = plans.size.coerceAtLeast(standing.size)

    /** The current plan, for `/lumaguilds hall list`. */
    fun currentPlans(): List<Plan> = plans

    // Off the main thread: every database read for the Hall.
    private fun refreshNow() {
        val next = try {
            val placed = pedestals.all()
            HallRanking.assign(placed, history.standings()).mapNotNull { (pedestal, entry) ->
                entry ?: return@mapNotNull null
                val guild = guilds.getGuild(entry.guildId) ?: return@mapNotNull null
                val display = themes.display(guild.id) ?: return@mapNotNull null
                Plan(pedestal, guild.id, guild.name, entry.prestige, display, display.skin ?: owner(guild.id), guild.banner)
            }
        } catch (e: Exception) {
            logger.warn("Could not read the Hall of Prestige; keeping the statues as they are", e)
            return
        }
        if (!plugin.isEnabled) return
        Bukkit.getScheduler().runTask(plugin, Runnable { apply(next) })
    }

    private fun owner(guildId: UUID): UUID? = runCatching {
        val top = ranks.getHighestRank(guildId) ?: return@runCatching null
        members.getGuildMembers(guildId).firstOrNull { it.rankId == top.id }?.playerId
    }.getOrNull()

    private fun apply(next: List<Plan>) {
        plans = next
        val wanted = next.associateBy { it.pedestal.slot }
        standing.keys.filter { it !in wanted }.forEach(::take)
        wanted.values.forEach(::place)
    }

    private fun place(plan: Plan) {
        val current = standing[plan.pedestal.slot]
        if (current != null && current.plan == plan && current.entities.all { it.isValid }) return
        if (failed[plan.pedestal.slot] == plan) return
        take(plan.pedestal.slot)
        val world = Bukkit.getWorld(plan.pedestal.world) ?: return
        val base = Location(world, plan.pedestal.x, plan.pedestal.y, plan.pedestal.z, plan.pedestal.yaw, 0f)
        if (!world.isChunkLoaded(base.blockX shr 4, base.blockZ shr 4)) return
        val look = HallLook(plan.display, plan.prestige)
        val banner = plan.banner?.let { runCatching { it.deserializeToItemStack() }.getOrNull() }?.takeIf { it.type.name.endsWith("_BANNER") }
        try {
            val built = HallStatueBuilder.build(base, look, plan.guildName, plan.prestige, plan.skin, banner,
                register = { it.isPersistent = false }) { statue ->
                HallStatuePose.orNull()?.apply(statue, { id -> Bukkit.getPlayer(id)?.let { !bedrock(it) } ?: true }, plan.skin, look.pose)
            }
            built.sword?.let { sword -> Bukkit.getOnlinePlayers().filter(::bedrock).forEach { it.hideEntity(plugin, sword) } }
            standing[plan.pedestal.slot] = Standing(plan, built.entities, built.top, built.closer, built.sword, look)
            failed.remove(plan.pedestal.slot)
        } catch (e: Exception) {
            failed[plan.pedestal.slot] = plan
            logger.warn("Could not build the Hall statue on pedestal ${plan.pedestal.slot}; not retrying until it changes", e)
        }
    }

    private fun take(slot: Int) {
        val gone = standing.remove(slot) ?: return
        runCatching { gone.closer?.invoke() }
        gone.entities.forEach { runCatching { it.remove() } }
    }

    private fun bedrock(player: Player) = runCatching { platform?.isBedrockPlayer(player) == true }.getOrDefault(false)

    // Main thread, twice a second: spotlight and aura for nearby players, and rebuild anything the
    // world dropped (a chunk unloading takes the non-saved statue with it).
    private fun tick() {
        for (plan in plans) {
            val here = standing[plan.pedestal.slot]
            if (here == null || here.entities.any { !it.isValid }) {
                place(plan)
                continue
            }
            val world = here.top.world
            if (world.getNearbyPlayers(here.top, EFFECT_RANGE).isEmpty()) continue
            for (y in 0..8) {
                world.spawnParticle(Particle.DUST, here.top.clone().add(0.0, y * 0.45, 0.0), 1, 0.3, 0.05, 0.3, 0.0,
                    Particle.DustOptions(here.look.spotlight, 1.1f))
            }
            world.spawnParticle(here.look.aura, here.top.clone().add(0.0, 1.0, 0.0), 3, 0.45, 0.7, 0.45, 0.01)
        }
    }

    @EventHandler
    fun chunkLoad(event: ChunkLoadEvent) {
        val hit = plans.filter { plan ->
            plan.pedestal.world == event.world.name &&
                (Math.floor(plan.pedestal.x).toInt() shr 4) == event.chunk.x &&
                (Math.floor(plan.pedestal.z).toInt() shr 4) == event.chunk.z
        }
        if (hit.isNotEmpty()) Bukkit.getScheduler().runTask(plugin, Runnable { hit.forEach(::place) })
    }

    @EventHandler
    fun join(event: PlayerJoinEvent) {
        if (!bedrock(event.player)) return
        standing.values.forEach { here -> here.sword?.let { event.player.hideEntity(plugin, it) } }
    }

    private companion object {
        const val START_DELAY = 100L
        const val REFRESH_TICKS = 20L * 60 * 5
        const val EFFECT_TICKS = 10L
        const val EFFECT_RANGE = 24.0
    }
}
