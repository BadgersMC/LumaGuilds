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
import net.lumalyte.lg.infrastructure.services.hall.HallPedestalBanner
import net.lumalyte.lg.infrastructure.services.hall.HallStatueBuilder
import net.lumalyte.lg.infrastructure.services.hall.HallStatuePose
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import net.lumalyte.lg.api.events.GuildBannerSetEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
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
    ) {
        /** What the statue itself is built from; everything but the banner. */
        fun statue(): Plan = copy(banner = null)
    }

    private class Standing(val plan: Plan, val entities: List<Entity>, val top: Location, val closer: (() -> Unit)?,
                           val sword: ItemDisplay?, val look: HallLook, val crest: ItemDisplay,
                           val plain: org.bukkit.entity.Mannequin? = null)

    private val standing = ConcurrentHashMap<Int, Standing>()
    /** Plans that failed to build; not retried until the plan changes, so a bad build can't loop. */
    private val failed = ConcurrentHashMap<Int, Plan>()
    @Volatile private var plans: List<Plan> = emptyList()
    @Volatile private var allPedestals: List<HallPedestal> = emptyList()
    private val tasks = mutableListOf<BukkitTask>()
    private val refreshQueued = AtomicBoolean(false)
    /** When each pedestal was last built, so a statue the world keeps removing isn't rebuilt every tick. */
    private val builtAt = ConcurrentHashMap<Int, Long>()
    /** REQ-292: losses in a row, each within seconds of a build; a one-off (a /kill) is simply rebuilt. */
    private val quickLosses = ConcurrentHashMap<Int, Int>()

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        net.lumalyte.lg.infrastructure.services.hall.HallSpawnGuard.register(plugin)
        net.lumalyte.lg.infrastructure.services.hall.HallStatueProtection.register(plugin)
        // REQ-277: statues built before Nexo loaded hold the vanilla stand-in; give them the real badge.
        net.lumalyte.lg.utils.NexoItemProvider.whenLoaded {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                standing.values.forEach { here ->
                    if (here.crest.isValid) here.crest.setItemStack(net.lumalyte.lg.interaction.menus.guild.PrestigeIcon.item(here.plan.prestige))
                }
            })
        }
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
        val placed: List<HallPedestal>
        val next = try {
            placed = pedestals.all()
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
        Bukkit.getScheduler().runTask(plugin, Runnable { apply(next, placed) })
    }

    private fun owner(guildId: UUID): UUID? = runCatching {
        val top = ranks.getHighestRank(guildId) ?: return@runCatching null
        members.getGuildMembers(guildId).firstOrNull { it.rankId == top.id }?.playerId
    }.getOrNull()

    private fun apply(next: List<Plan>, placed: List<HallPedestal>) {
        plans = next
        allPedestals = placed
        val wanted = next.associateBy { it.pedestal.slot }
        standing.keys.filter { it !in wanted }.forEach(::take)
        wanted.values.forEach(::place)
        placed.forEach(::paintBanner)
    }

    /**
     * REQ-278: repaints the real banner hanging under [pedestal] as the banner of the guild holding it,
     * or plain white while it is empty. Skipped while the pedestal's chunk is not loaded.
     */
    private fun paintBanner(pedestal: HallPedestal) {
        val world = Bukkit.getWorld(pedestal.world) ?: return
        if (!world.isChunkLoaded(Math.floor(pedestal.x).toInt() shr 4, Math.floor(pedestal.z).toInt() shr 4)) return
        val block = HallPedestalBanner.find(world, pedestal) ?: return
        val holder = plans.firstOrNull { it.pedestal.slot == pedestal.slot }
        val banner = holder?.banner?.let { runCatching { it.deserializeToItemStack() }.getOrNull() }
            ?.takeIf { it.type.name.endsWith("_BANNER") }
        runCatching { HallPedestalBanner.paint(block, banner) }
            .onFailure { logger.warn("Could not repaint the banner under Hall pedestal ${pedestal.slot}", it) }
    }

    /** REQ-278: a guild that changes its banner gets the new one under its pedestal without a manual refresh. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun guildBannerSet(event: GuildBannerSetEvent) {
        if (plans.any { it.guildId == event.guildId }) refreshSoon()
    }

    /** REQ-278: a banner staff hang under a pedestal takes the holding guild's banner straight away. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun bannerPlaced(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        if (!HallPedestalBanner.isWallBanner(block.type.name)) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            allPedestals.filter { HallPedestalBanner.belongsTo(block, it) }.forEach(::paintBanner)
        })
    }

    private fun place(plan: Plan) {
        val current = standing[plan.pedestal.slot]
        // The banner hangs under the pedestal, not on the statue: a banner change needs no rebuild.
        if (current != null && current.plan.statue() == plan.statue() && current.entities.all { it.isValid }) return
        if (failed[plan.pedestal.slot]?.statue() == plan.statue()) return
        take(plan.pedestal.slot)
        val world = Bukkit.getWorld(plan.pedestal.world) ?: return
        val base = Location(world, plan.pedestal.x, plan.pedestal.y, plan.pedestal.z, plan.pedestal.yaw, 0f)
        if (!world.isChunkLoaded(base.blockX shr 4, base.blockZ shr 4)) return
        val look = HallLook(plan.display, plan.prestige)
        try {
            val built = HallStatueBuilder.build(base, look, plan.guildName, plan.prestige, plan.skin,
                register = { it.isPersistent = false }, plainFigure = platform != null) { statue ->
                HallStatuePose.orNull()?.apply(statue, { id -> Bukkit.getPlayer(id)?.let { !bedrock(it) } ?: true }, plan.skin)
            }
            built.sword?.let { sword -> Bukkit.getOnlinePlayers().filter(::bedrock).forEach { it.hideEntity(plugin, sword) } }
            // REQ-294: Bedrock players can't see the model, so they get the plain standing figure instead.
            built.plain?.let { plain -> Bukkit.getOnlinePlayers().filter(::bedrock).forEach { it.showEntity(plugin, plain) } }
            builtAt[plan.pedestal.slot] = System.currentTimeMillis()
            standing[plan.pedestal.slot] = Standing(plan, built.entities, built.top, built.closer, built.sword, look, built.crest, built.plain)
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

    // Main thread, twice a second: rebuild anything the
    // world dropped (a chunk unloading takes the non-saved statue with it).
    private fun tick() {
        for (plan in plans) {
            val here = standing[plan.pedestal.slot]
            if (here == null) {
                place(plan)
                continue
            }
            val lost = here.entities.filterNot { it.isValid }
            if (lost.isNotEmpty()) {
                // REQ-276: a chunk unload takes the statue with it, so rebuild; but something removing it
                // again within seconds of a build would make it flicker, so stop and say what went.
                val age = System.currentTimeMillis() - (builtAt[plan.pedestal.slot] ?: 0L)
                val quick = if (age < REBUILD_GRACE_MS && here.top.isChunkLoaded) quickLosses.merge(plan.pedestal.slot, 1, Int::plus) ?: 1
                    else { quickLosses.remove(plan.pedestal.slot); 0 }
                if (quick >= QUICK_LOSS_LIMIT) {
                    quickLosses.remove(plan.pedestal.slot)
                    take(plan.pedestal.slot)
                    failed[plan.pedestal.slot] = plan
                    logger.warn("The Hall statue on pedestal ${plan.pedestal.slot} keeps being removed " +
                        "(${lost.joinToString { it.type.name }}); not rebuilding until it changes or /lumaguilds hall refresh")
                } else {
                    place(plan)
                }
                continue
            }
        }
    }

    @EventHandler
    fun chunkLoad(event: ChunkLoadEvent) {
        val hit = plans.filter { plan ->
            plan.pedestal.world == event.world.name &&
                (Math.floor(plan.pedestal.x).toInt() shr 4) == event.chunk.x &&
                (Math.floor(plan.pedestal.z).toInt() shr 4) == event.chunk.z
        }
        val banners = allPedestals.filter { pedestal ->
            pedestal.world == event.world.name &&
                (Math.floor(pedestal.x).toInt() shr 4) == event.chunk.x &&
                (Math.floor(pedestal.z).toInt() shr 4) == event.chunk.z
        }
        if (hit.isNotEmpty() || banners.isNotEmpty()) Bukkit.getScheduler().runTask(plugin, Runnable {
            hit.forEach(::place)
            banners.forEach(::paintBanner)
        })
    }

    @EventHandler
    fun join(event: PlayerJoinEvent) {
        if (!bedrock(event.player)) return
        standing.values.forEach { here ->
            here.sword?.let { event.player.hideEntity(plugin, it) }
            here.plain?.let { event.player.showEntity(plugin, it) }
        }
    }

    private companion object {
        const val START_DELAY = 100L
        const val REFRESH_TICKS = 20L * 60 * 5
        const val EFFECT_TICKS = 10L
        const val REBUILD_GRACE_MS = 10_000L
        const val QUICK_LOSS_LIMIT = 3
    }
}
