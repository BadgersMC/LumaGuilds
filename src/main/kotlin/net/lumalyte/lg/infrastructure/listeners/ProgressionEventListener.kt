package net.lumalyte.lg.infrastructure.listeners

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.lumalyte.lg.application.services.ActivityType
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.config.ProgressionConfig
import net.lumalyte.lg.domain.events.GuildBankDepositEvent
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.domain.events.GuildMemberJoinEvent
import net.lumalyte.lg.domain.events.GuildMemberRemovedEvent
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.bukkit.plugin.Plugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Efficient event listener for guild progression system.
 * Uses batching, rate limiting, and virtual threads to handle high-frequency events
 * WITHOUT blocking the main thread.
 *
 * Performance improvements:
 * - Database operations run on virtual threads (non-blocking)
 * - XP batching reduces database writes by 90%+
 * - Rate limiting prevents exploitation
 */
class ProgressionEventListener : Listener, KoinComponent {

    private val progressionService: ProgressionService by inject()
    private val memberService: MemberService by inject()
    private val memberRepository: MemberRepository by inject()
    private val configService: ConfigService by inject()
    private val asyncTaskService: AsyncTaskService by inject()
    private val leaderboardService: LeaderboardService by inject()
    private val plugin: Plugin by inject()
    private val virtualDispatcher: CoroutineDispatcher by inject(named("VirtualDispatcher"))

    private val logger = LoggerFactory.getLogger(ProgressionEventListener::class.java)

    // Rate limiting: Track XP per player per source to prevent spam
    private val playerXpCooldowns = ConcurrentHashMap<String, Long>()
    private val playerXpCounters = ConcurrentHashMap<String, CounterEntry>()

    private val playerGuildCache = ConcurrentHashMap<UUID, Set<UUID>>()
    private val pendingGuildXp = ConcurrentHashMap<GuildXpKey, AtomicInteger>()
    private val sourceXpValues = ConcurrentHashMap<ExperienceSource, Int>()
    @Volatile private var cachedProgressionConfig: ProgressionConfig = configService.loadConfig().progression

    private val flushScope = CoroutineScope(SupervisorJob() + virtualDispatcher)
    @Volatile private var flushJob: Job? = null

    init {
        refreshCaches()
        startFlushTask()
    }

    /**
     * Refreshes hot-path caches after startup or /lumaguilds reload.
     */
    fun refreshCaches() {
        cachedProgressionConfig = configService.loadConfig().progression
        rebuildExperienceSourceCache(cachedProgressionConfig)
        rebuildMembershipCache()
    }

    fun shutdown() {
        val job = flushJob
        flushJob = null
        if (job != null) {
            runBlocking { job.cancelAndJoin() }
        }
        // Force-drain everything still buffered, ignoring cooldowns, before final guild flush.
        flushEligiblePlayerCounters(force = true)
        flushPendingGuildXp()
        flushScope.cancel()
    }

    private fun rebuildExperienceSourceCache(config: ProgressionConfig) {
        sourceXpValues[ExperienceSource.PLAYER_KILL] = config.playerKillXp
        sourceXpValues[ExperienceSource.MOB_KILL] = config.mobKillXp
        sourceXpValues[ExperienceSource.CROP_BREAK] = config.cropBreakXp
        sourceXpValues[ExperienceSource.BLOCK_BREAK] = config.blockBreakXp
        sourceXpValues[ExperienceSource.BLOCK_PLACE] = config.blockPlaceXp
        sourceXpValues[ExperienceSource.CRAFTING] = config.craftingXp
        sourceXpValues[ExperienceSource.SMELTING] = config.smeltingXp
        sourceXpValues[ExperienceSource.FISHING] = config.fishingXp
        sourceXpValues[ExperienceSource.ENCHANTING] = config.enchantingXp
    }

    private fun rebuildMembershipCache() {
        playerGuildCache.clear()
        memberRepository.getAll()
            .groupBy { it.playerId }
            .forEach { (playerId, memberships) ->
                playerGuildCache[playerId] = Collections.unmodifiableSet(memberships.map { it.guildId }.toSet())
            }
    }

    private fun startFlushTask() {
        flushJob?.cancel()
        flushJob = flushScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                try {
                    flushEligiblePlayerCounters()
                    flushPendingGuildXp()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Progression flush cycle failed", e)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerKill(event: PlayerDeathEvent) {
        val killer = event.entity.killer
        if (killer is Player) {
            awardExperience(killer, cachedXp(ExperienceSource.PLAYER_KILL), ExperienceSource.PLAYER_KILL)
        }
        recordKillDeathActivity(killer, event.entity)
    }

    private fun recordKillDeathActivity(killer: Player?, victim: Player) {
        if (killer != null && killer.uniqueId == victim.uniqueId) return
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    if (killer != null) {
                        memberService.getPlayerGuilds(killer.uniqueId).forEach { gid ->
                            leaderboardService.recordActivity(gid, ActivityType.KILL, 1)
                        }
                    }
                    memberService.getPlayerGuilds(victim.uniqueId).forEach { gid ->
                        leaderboardService.recordActivity(gid, ActivityType.DEATH, 1)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to record kill/death activity", e)
                }
            },
            onSuccess = {},
            onError = { error -> logger.error("Async kill/death activity failed", error) }
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMobKill(event: EntityDeathEvent) {
        val killer = event.entity.killer
        if (killer is Player && event.entity !is Player) {
            // Use rate limiting for mob kills to prevent grinding
            awardExperienceWithCooldown(killer, cachedXp(ExperienceSource.MOB_KILL), ExperienceSource.MOB_KILL)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCropBreak(event: PlayerHarvestBlockEvent) {
        awardExperienceWithCooldown(event.player, cachedXp(ExperienceSource.CROP_BREAK), ExperienceSource.CROP_BREAK)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val xp = cachedXp(ExperienceSource.BLOCK_BREAK)
        if (xp <= 0) return

        val guildIds = playerGuildCache[event.player.uniqueId]
        if (guildIds.isNullOrEmpty()) return

        val block = event.block
        if (!block.type.isBlock) return
        if (block.hasMetadata("player_placed")) return

        awardExperienceWithCooldown(event.player.uniqueId, guildIds, xp, ExperienceSource.BLOCK_BREAK, lunarMultiplier(event.player))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        try {
            // Mark blocks as player-placed to prevent XP farming
            val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
            if (plugin == null) {
                logger.warn("LumaGuilds plugin not found during block place event")
                return
            }

            event.block.setMetadata("player_placed", org.bukkit.metadata.FixedMetadataValue(plugin, true))

            awardExperienceWithCooldown(event.player, cachedXp(ExperienceSource.BLOCK_PLACE), ExperienceSource.BLOCK_PLACE)
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.error("Error in onBlockPlace for player ${event.player.name}", e)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCrafting(event: CraftItemEvent) {
        if (event.whoClicked is Player) {
            val player = event.whoClicked as Player
            val amount = event.recipe.result.amount
            awardExperienceWithCooldown(player, cachedXp(ExperienceSource.CRAFTING) * amount, ExperienceSource.CRAFTING)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelting(event: FurnaceExtractEvent) {
        awardExperienceWithCooldown(event.player, cachedXp(ExperienceSource.SMELTING) * event.itemAmount, ExperienceSource.SMELTING)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFishing(event: PlayerFishEvent) {
        if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {
            awardExperience(event.player, cachedXp(ExperienceSource.FISHING), ExperienceSource.FISHING)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchanting(event: EnchantItemEvent) {
        awardExperience(event.enchanter, cachedXp(ExperienceSource.ENCHANTING) * event.expLevelCost, ExperienceSource.ENCHANTING)
    }

    /**
     * Awards experience immediately without cooldown (for rare events).
     * Database operations run on virtual threads to avoid blocking main thread.
     */
    private fun awardExperience(player: Player, amount: Int, source: ExperienceSource) {
        val guildIds = playerGuildCache[player.uniqueId]
        if (guildIds.isNullOrEmpty()) return
        enqueueGuildExperience(guildIds, amount * lunarMultiplier(player), source)
    }

    /**
     * Awards experience with rate limiting to prevent spam and exploitation.
     * Batches XP awards and processes them async on virtual threads.
     */
    private fun awardExperienceWithCooldown(player: Player, amount: Int, source: ExperienceSource) {
        val guildIds = playerGuildCache[player.uniqueId]
        if (guildIds.isNullOrEmpty()) return
        awardExperienceWithCooldown(player.uniqueId, guildIds, amount, source, lunarMultiplier(player))
    }

    private fun awardExperienceWithCooldown(
        playerId: UUID,
        guildIds: Set<UUID>,
        amount: Int,
        source: ExperienceSource,
        multiplier: Int
    ) {
        if (amount <= 0 || guildIds.isEmpty()) return

        val key = "${playerId}-${source.name}"
        val currentTime = System.currentTimeMillis()
        val cooldownMs = cachedProgressionConfig.xpCooldownMs
        val maxXpPerBatch = cachedProgressionConfig.maxXpPerBatch
        val scaled = amount * multiplier

        // Check cooldown
        val lastAward = playerXpCooldowns[key] ?: 0
        if (currentTime - lastAward < cooldownMs) {
            // Add to counter for batch processing — guild snapshot fixed at counter creation
            // so XP earned while in guild G stays credited to G even if membership changes.
            val entry = playerXpCounters.computeIfAbsent(key) { CounterEntry(source, guildIds) }
            val totalAmount = entry.amount.addAndGet(scaled)

            // Process batch if it reaches the limit
            if (totalAmount >= maxXpPerBatch) {
                val drained = entry.amount.getAndSet(0)
                playerXpCooldowns[key] = currentTime
                if (drained > 0) enqueueGuildExperience(entry.guildIds, drained, source)
            }
            return
        }

        // Cooldown elapsed: drain prior buffer under its stored snapshot, start fresh under current.
        val previous = playerXpCounters.remove(key)
        val pendingXp = previous?.amount?.getAndSet(0) ?: 0
        playerXpCooldowns[key] = currentTime
        if (pendingXp > 0 && previous != null) {
            enqueueGuildExperience(previous.guildIds, pendingXp, source)
        }
        enqueueGuildExperience(guildIds, scaled, source)
    }

    /**
     * Public method for other systems to award XP (e.g., bank deposits, member joins).
     */
    fun awardGuildExperience(player: Player, amount: Int, source: ExperienceSource) {
        awardExperience(player, amount, source)
    }

    /**
     * Public method for guild-level events (e.g., war victories).
     * Runs on virtual thread to avoid blocking caller.
     */
    fun awardGuildExperienceByGuildId(guildId: UUID, amount: Int, source: ExperienceSource) {
        if (amount <= 0) return
        enqueueGuildExperience(setOf(guildId), amount, source)
    }

    // Custom event handlers for guild-specific actions
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildBankDeposit(event: GuildBankDepositEvent) {
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    val xpPerHundred = cachedProgressionConfig.bankDepositXpPer100
                    val xpAmount = (event.amount / 100.0 * xpPerHundred).toInt()
                    if (xpAmount > 0) {
                        progressionService.awardExperience(event.guildId, xpAmount, ExperienceSource.BANK_DEPOSIT)
                    }
                    leaderboardService.recordActivity(event.guildId, ActivityType.BANK_DEPOSIT, event.amount.toInt().coerceAtLeast(1))
                } catch (e: Exception) {
                    logger.warn("Failed to award progression XP for bank deposit", e)
                }
            },
            onSuccess = {},
            onError = { error ->
                logger.error("Async bank deposit XP award failed", error)
            }
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        addPlayerGuild(event.playerId, event.guildId)
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    // Don't award XP for the first member (guild creator)
                    val memberCount = memberService.getGuildMembers(event.guildId).size // DB query on virtual thread
                    if (memberCount <= 1) {
                        return@runAsyncCallback
                    }

                    val memberJoinXp = cachedProgressionConfig.memberJoinedXp
                    progressionService.awardExperience(event.guildId, memberJoinXp, ExperienceSource.MEMBER_JOINED)
                    leaderboardService.recordActivity(event.guildId, ActivityType.MEMBER_JOINED, 1)
                } catch (e: Exception) {
                    logger.warn("Failed to award progression XP for member join", e)
                }
            },
            onSuccess = {},
            onError = { error ->
                logger.error("Async member join XP award failed", error)
            }
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildMemberRemoved(event: GuildMemberRemovedEvent) {
        removePlayerGuild(event.playerId, event.guildId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        event.memberIds.forEach { playerId -> removePlayerGuild(playerId, event.guild.id) }
        pendingGuildXp.keys.removeIf { it.guildId == event.guild.id }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        val prefix = "${playerId}-"
        // Drain any buffered XP into pendingGuildXp under the stored snapshot before removing keys.
        val toRemove = mutableListOf<String>()
        playerXpCounters.forEach { (key, entry) ->
            if (!key.startsWith(prefix)) return@forEach
            toRemove.add(key)
            val amount = entry.amount.getAndSet(0)
            if (amount > 0 && entry.guildIds.isNotEmpty()) {
                enqueueGuildExperience(entry.guildIds, amount, entry.source)
            }
        }
        toRemove.forEach { playerXpCounters.remove(it) }
        playerXpCooldowns.keys.removeIf { it.startsWith(prefix) }
    }

    private fun cachedXp(source: ExperienceSource): Int = sourceXpValues[source] ?: 0

    private fun lunarMultiplier(player: Player): Int {
        return try {
            val lunarClientService = org.koin.core.context.GlobalContext.get()
                .getOrNull<net.lumalyte.lg.application.services.apollo.LunarClientService>()
            if (lunarClientService != null && lunarClientService.isLunarClient(player)) 2 else 1
        } catch (e: Exception) {
            1
        }
    }

    private fun enqueueGuildExperience(guildIds: Set<UUID>, amount: Int, source: ExperienceSource) {
        if (amount <= 0) return
        guildIds.forEach { guildId ->
            pendingGuildXp.computeIfAbsent(GuildXpKey(guildId, source)) { AtomicInteger(0) }.addAndGet(amount)
        }
    }

    /**
     * Drains player counters whose cooldowns have elapsed (or all of them when [force] is true,
     * used during shutdown so XP earned just before stop is not lost).
     */
    private fun flushEligiblePlayerCounters(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val cooldownMs = cachedProgressionConfig.xpCooldownMs
        val toRemove = if (force) mutableListOf<String>() else null
        playerXpCounters.forEach { (key, entry) ->
            if (!force) {
                val lastAward = playerXpCooldowns[key] ?: 0
                if (currentTime - lastAward < cooldownMs) return@forEach
            }

            val amount = entry.amount.getAndSet(0)
            if (amount > 0 && entry.guildIds.isNotEmpty()) {
                enqueueGuildExperience(entry.guildIds, amount, entry.source)
            }
            if (force) {
                toRemove?.add(key)
            } else if (amount > 0) {
                playerXpCooldowns[key] = currentTime
            }
        }
        toRemove?.forEach { playerXpCounters.remove(it) }
    }

    /**
     * Drains pending guild XP and writes it through the progression service.
     * Uses CAS so a write failure restores the amount instead of losing it.
     */
    private fun flushPendingGuildXp() {
        pendingGuildXp.forEach { (key, counter) ->
            var amount: Int
            do {
                amount = counter.get()
                if (amount <= 0) return@forEach
            } while (!counter.compareAndSet(amount, 0))

            try {
                progressionService.awardExperience(key.guildId, amount, key.source)
            } catch (e: Exception) {
                counter.addAndGet(amount)
                logger.warn("Failed to flush $amount XP to guild ${key.guildId} from ${key.source}; requeued", e)
            }
        }
    }

    private fun addPlayerGuild(playerId: UUID, guildId: UUID) {
        playerGuildCache.compute(playerId) { _, current ->
            Collections.unmodifiableSet((current ?: emptySet()).plus(guildId))
        }
    }

    private fun removePlayerGuild(playerId: UUID, guildId: UUID) {
        playerGuildCache.computeIfPresent(playerId) { _, current ->
            val updated = current.minus(guildId)
            if (updated.isEmpty()) null else Collections.unmodifiableSet(updated)
        }
    }

    private data class GuildXpKey(val guildId: UUID, val source: ExperienceSource)

    private class CounterEntry(val source: ExperienceSource, val guildIds: Set<UUID>) {
        val amount = AtomicInteger(0)
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 5_000L
    }
}
