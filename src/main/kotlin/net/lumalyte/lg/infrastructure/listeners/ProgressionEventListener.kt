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
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.config.ProgressionConfig
import net.lumalyte.lg.api.events.GuildBankDepositEvent
import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import net.lumalyte.lg.domain.values.BlockPosition
import org.bukkit.GameMode
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.bukkit.plugin.Plugin
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CompletableFuture
import io.papermc.paper.event.inventory.ItemCraftedEvent

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
class ProgressionEventListener(
    private val progressionService: ProgressionService,
    private val memberService: MemberService,
    private val memberRepository: MemberRepository,
    private val configService: ConfigService,
    private val asyncTaskService: AsyncTaskService,
    private val leaderboardService: LeaderboardService,
    private val playtimeActivityService: PlaytimeActivityService,
    private val blockProvenanceRepository: BlockProvenanceRepository,
    private val plugin: Plugin,
    private val virtualDispatcher: CoroutineDispatcher,
) : Listener {

    private val logger = LoggerFactory.getLogger(ProgressionEventListener::class.java)
    private val brewingXpMarker = NamespacedKey(plugin, "progression_brewing_xp_awarded")

    // Rate limiting: Track XP per player per source to prevent spam
    private val playerXpCooldowns = ConcurrentHashMap<String, Long>()
    private val playerXpCounters = ConcurrentHashMap<String, CounterEntry>()

    private val playerGuildCache = ConcurrentHashMap<UUID, Set<UUID>>()
    private val pendingGuildXp = ConcurrentHashMap<GuildXpKey, AtomicInteger>()
    private val sourceXpValues = ConcurrentHashMap<ExperienceSource, Int>()
    private val pendingProvenanceWrites = ConcurrentHashMap<BlockPosition, CompletableFuture<Boolean>>()
    @Volatile private var cachedProgressionConfig: ProgressionConfig = configService.loadConfig().progression
    @Volatile private var classifier = ProgressionActivityClassifier(
        cachedProgressionConfig.materialPools,
        cachedProgressionConfig.entityPools
    )

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
        classifier = ProgressionActivityClassifier(
            cachedProgressionConfig.materialPools,
            cachedProgressionConfig.entityPools
        )
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
        sourceXpValues.clear()
        config.sourcePolicies.forEach { (source, policy) ->
            if (policy.enabled) sourceXpValues[source] = policy.awardXp
        }
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
        val victim = event.entity
        val killer = victim.killer
        if (killer is Player) {
            awardPlayerKillExperienceUnlessSameGuild(killer, victim)
        }
        recordKillDeathActivity(killer, victim)
    }

    /**
     * Guild PvP may be allowed, but killing a guildmate must not grant progression XP
     * (prevents friendly-fire farming). Each player is in at most one guild.
     */
    private fun awardPlayerKillExperienceUnlessSameGuild(killer: Player, victim: Player) {
        if (!eligible(killer)) return

        val killerGuilds = playerGuildCache[killer.uniqueId]
            ?: memberRepository.getGuildsByPlayer(killer.uniqueId)
        val killerGuildId = killerGuilds.singleOrNull() ?: return

        val victimGuilds = playerGuildCache[victim.uniqueId]
            ?: memberRepository.getGuildsByPlayer(victim.uniqueId)
        val victimGuildId = victimGuilds.singleOrNull()
        if (victimGuildId != null && victimGuildId == killerGuildId) return

        requestPlayerActivity(killer, setOf(killerGuildId), 1, ExperienceSource.PLAYER_KILL)
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
        if (killer is Player && event.entity !is Player && eligible(killer)) {
            classifier.sourceForKill(event.entity.type)?.let { source ->
                requestPlayerActivity(killer, units = 1, source = source)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCropBreak(event: PlayerHarvestBlockEvent) {
        if (!eligible(event.player)) return
        val block = event.harvestedBlock
        val position = BlockPosition(block.world.uid, block.x, block.y, block.z)
        val material = block.type
        val data = block.blockData
        val mature = data is Ageable && data.age >= data.maximumAge
        resolveProvenance(position, removeAfterRead = false) { playerPlaced ->
            if (classifier.sourceForBreak(material, playerPlaced, mature) == ExperienceSource.CROP_BREAK) {
                requestPlayerActivity(event.player, units = 1, source = ExperienceSource.CROP_BREAK)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!eligible(event.player)) return
        val guildIds = playerGuildCache[event.player.uniqueId]
        if (guildIds.isNullOrEmpty()) return
        val block = event.block
        val position = BlockPosition(block.world.uid, block.x, block.y, block.z)
        val data = block.blockData
        val matureCrop = data is Ageable && data.age >= data.maximumAge
        val material = block.type
        resolveProvenance(position, removeAfterRead = true) { playerPlaced ->
            val source = classifier.sourceForBreak(material, playerPlaced, matureCrop) ?: return@resolveProvenance
            requestPlayerActivity(event.player, guildIds, 1, source)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        try {
            if (!eligible(event.player)) return
            val block = event.block
            val position = BlockPosition(block.world.uid, block.x, block.y, block.z)
            val write = asyncTaskService.runAsync {
                blockProvenanceRepository.recordPlayerPlaced(position)
            }
            pendingProvenanceWrites[position] = write
            write.whenComplete { _, _ -> pendingProvenanceWrites.remove(position, write) }
            classifier.sourceForPlace(block.type)?.let { source ->
                requestPlayerActivity(event.player, units = 1, source = source)
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.error("Error in onBlockPlace for player ${event.player.name}", e)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCrafting(event: ItemCraftedEvent) {
        val player = event.player
        if (!eligible(player)) return
        classifier.sourceForCraft(event.craftedItem.type)?.let { source ->
            requestPlayerActivity(player, units = event.craftedItem.amount, source = source)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelting(event: FurnaceExtractEvent) {
        if (eligible(event.player)) {
            requestPlayerActivity(event.player, units = event.itemAmount, source = ExperienceSource.SMELTING)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFishing(event: PlayerFishEvent) {
        if (event.state == PlayerFishEvent.State.CAUGHT_FISH && eligible(event.player)) {
            requestPlayerActivity(event.player, units = 1, source = ExperienceSource.FISHING)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchanting(event: EnchantItemEvent) {
        if (eligible(event.enchanter)) {
            requestPlayerActivity(event.enchanter, units = 1, source = ExperienceSource.ENCHANTING)
        }
    }

    /**
     * Awards experience immediately without cooldown (for rare events).
     * Database operations run on virtual threads to avoid blocking main thread.
     */
    private fun awardExperience(player: Player, amount: Int, source: ExperienceSource) {
        if (playtimeActivityService.isXpBlocked(player.uniqueId)) return
        val guildIds = playerGuildCache[player.uniqueId]
        if (guildIds.isNullOrEmpty()) return
        enqueueGuildExperience(guildIds, amount * lunarMultiplier(player), source)
    }

    /**
     * Awards experience with rate limiting to prevent spam and exploitation.
     * Batches XP awards and processes them async on virtual threads.
     */
    private fun awardExperienceWithCooldown(player: Player, amount: Int, source: ExperienceSource) {
        if (playtimeActivityService.isXpBlocked(player.uniqueId)) return
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

    private fun eligible(player: Player): Boolean = classifier.isEligible(player.gameMode)

    private fun resolveProvenance(
        position: BlockPosition,
        removeAfterRead: Boolean,
        callback: (Boolean) -> Unit,
    ) {
        val pendingWrite = if (removeAfterRead) pendingProvenanceWrites.remove(position) else pendingProvenanceWrites[position]
        asyncTaskService.runAsyncCallback(
            task = {
                pendingWrite?.join()
                val placed = blockProvenanceRepository.wasPlayerPlaced(position)
                if (removeAfterRead) blockProvenanceRepository.remove(position)
                placed
            },
            onSuccess = callback,
            onError = { error -> logger.warn("Failed to resolve block provenance at $position", error) },
        )
    }

    private fun requestPlayerActivity(
        player: Player,
        guildIds: Set<UUID> = playerGuildCache[player.uniqueId].orEmpty(),
        units: Int,
        source: ExperienceSource,
    ) {
        if (units <= 0 || guildIds.isEmpty()) return
        val scaledUnits = try {
            Math.multiplyExact(units, lunarMultiplier(player))
        } catch (_: ArithmeticException) {
            return
        }
        asyncTaskService.runAsyncCallback(
            task = {
                guildIds.forEach { guildId ->
                    progressionService.awardPlayerActivity(guildId, player.uniqueId, scaledUnits, source, eligible = true)
                }
            },
            onSuccess = {},
            onError = { error -> logger.warn("Failed to award $source player activity", error) },
        )
    }

    /** Bukkit does not expose the brewer on BrewEvent, so attribute XP when a player takes a finished potion. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBrewedPotionTaken(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!eligible(player) || event.clickedInventory?.type != InventoryType.BREWING || event.slot !in 0..2) return
        val item = event.currentItem ?: return
        if (!classifier.isBrewedPotion(item.type)) return
        if (item.persistentDataContainer.has(brewingXpMarker, PersistentDataType.BYTE)) return
        item.editMeta { meta -> meta.persistentDataContainer.set(brewingXpMarker, PersistentDataType.BYTE, 1) }
        requestPlayerActivity(player, units = item.amount, source = ExperienceSource.BREWING)
    }

    /** Vanilla adventure advancements are one-time, naturally bounded exploration milestones. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onExplorationMilestone(event: PlayerAdvancementDoneEvent) {
        if (!eligible(event.player)) return
        val key = event.advancement.key
        if (classifier.isExplorationMilestone(key.namespace, key.key)) {
            requestPlayerActivity(event.player, units = 1, source = ExperienceSource.EXPLORATION_MILESTONE)
        }
    }

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
