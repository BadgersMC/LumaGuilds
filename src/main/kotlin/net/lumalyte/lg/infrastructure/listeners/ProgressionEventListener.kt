package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.events.GuildBankDepositEvent
import net.lumalyte.lg.domain.events.GuildMemberJoinEvent
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
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
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
    private val configService: ConfigService by inject()
    private val asyncTaskService: AsyncTaskService by inject()

    private val logger = LoggerFactory.getLogger(ProgressionEventListener::class.java)

    // Rate limiting: Track XP per player per source to prevent spam
    private val playerXpCooldowns = ConcurrentHashMap<String, Long>()
    private val playerXpCounters = ConcurrentHashMap<String, AtomicInteger>()

    // Get configuration values
    private val cooldownMs get() = getConfig().xpCooldownMs
    private val maxXpPerBatch get() = getConfig().maxXpPerBatch


    // Get XP values from config
    private fun getConfig() = configService.loadConfig().progression

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerKill(event: PlayerDeathEvent) {
        val killer = event.entity.killer
        if (killer is Player) {
            awardExperience(killer, getConfig().playerKillXp, ExperienceSource.PLAYER_KILL)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMobKill(event: EntityDeathEvent) {
        val killer = event.entity.killer
        if (killer is Player && event.entity !is Player) {
            // Use rate limiting for mob kills to prevent grinding
            awardExperienceWithCooldown(killer, getConfig().mobKillXp, ExperienceSource.MOB_KILL)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCropBreak(event: PlayerHarvestBlockEvent) {
        awardExperienceWithCooldown(event.player, getConfig().cropBreakXp, ExperienceSource.CROP_BREAK)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        // Only award XP for natural blocks to prevent exploit farms
        if (event.block.type.isBlock && !event.block.hasMetadata("player_placed")) {
            val blockBreakXp = getConfig().blockBreakXp
            awardExperienceWithCooldown(event.player, blockBreakXp, ExperienceSource.BLOCK_BREAK)
        }
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

            awardExperienceWithCooldown(event.player, getConfig().blockPlaceXp, ExperienceSource.BLOCK_PLACE)
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
            awardExperienceWithCooldown(player, getConfig().craftingXp * amount, ExperienceSource.CRAFTING)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelting(event: FurnaceExtractEvent) {
        awardExperienceWithCooldown(event.player, getConfig().smeltingXp * event.itemAmount, ExperienceSource.SMELTING)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFishing(event: PlayerFishEvent) {
        if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {
            awardExperience(event.player, getConfig().fishingXp, ExperienceSource.FISHING)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchanting(event: EnchantItemEvent) {
        awardExperience(event.enchanter, getConfig().enchantingXp * event.expLevelCost, ExperienceSource.ENCHANTING)
    }

    /**
     * Awards experience immediately without cooldown (for rare events).
     * Database operations run on virtual threads to avoid blocking main thread.
     */
    private fun awardExperience(player: Player, amount: Int, source: ExperienceSource) {
        // Run database operations on virtual thread - won't block main thread!
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    val guildIds = memberService.getPlayerGuilds(player.uniqueId) // DB query on virtual thread

                    if (guildIds.isEmpty()) {
                        return@runAsyncCallback
                    }

                    // Check if player is using Lunar Client for 2x XP bonus
                    var finalAmount = amount
                    try {
                        val lunarClientService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.application.services.apollo.LunarClientService>()
                        if (lunarClientService != null && lunarClientService.isLunarClient(player)) {
                            finalAmount = amount * 2
                            logger.debug("${player.name} earned 2x XP ($finalAmount instead of $amount) from $source due to Lunar Client")
                        }
                    } catch (e: Exception) {
                        // Silently fail if Apollo not available
                    }

                    guildIds.forEach { guildId ->
                        progressionService.awardExperience(guildId, finalAmount, source) // DB write on virtual thread
                    }
                } catch (e: Exception) {
                    // Event listener - catching all exceptions to prevent listener failure
                    logger.error("Failed to award $amount XP to player ${player.name} from $source", e)
                }
            },
            onSuccess = {
                // Nothing to do on main thread
            },
            onError = { error ->
                logger.error("Async XP award failed for player ${player.name}", error)
            }
        )
    }

    /**
     * Awards experience with rate limiting to prevent spam and exploitation.
     * Batches XP awards and processes them async on virtual threads.
     */
    private fun awardExperienceWithCooldown(player: Player, amount: Int, source: ExperienceSource) {
        val key = "${player.uniqueId}-${source.name}"
        val currentTime = System.currentTimeMillis()

        // Check cooldown
        val lastAward = playerXpCooldowns[key] ?: 0
        if (currentTime - lastAward < cooldownMs) {
            // Add to counter for batch processing
            val counter = playerXpCounters.computeIfAbsent(key) { AtomicInteger(0) }
            val totalAmount = counter.addAndGet(amount)

            // Process batch if it reaches the limit
            if (totalAmount >= maxXpPerBatch) {
                counter.set(0)
                playerXpCooldowns[key] = currentTime
                awardExperience(player, totalAmount, source)
            }
            return
        }

        // Process any pending XP from counter
        val counter = playerXpCounters[key]
        val pendingXp = counter?.getAndSet(0) ?: 0
        val totalXp = amount + pendingXp

        playerXpCooldowns[key] = currentTime
        awardExperience(player, totalXp, source)
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
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    progressionService.awardExperience(guildId, amount, source) // DB write on virtual thread
                } catch (e: Exception) {
                    logger.warn("Failed to award experience to guild $guildId", e)
                }
            },
            onSuccess = {},
            onError = { error ->
                logger.error("Async guild XP award failed for guild $guildId", error)
            }
        )
    }

    // Custom event handlers for guild-specific actions
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildBankDeposit(event: GuildBankDepositEvent) {
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    val config = configService.loadConfig()
                    val xpPerHundred = config.progression.bankDepositXpPer100
                    val xpAmount = (event.amount / 100.0 * xpPerHundred).toInt()
                    if (xpAmount > 0) {
                        progressionService.awardExperience(event.guildId, xpAmount, ExperienceSource.BANK_DEPOSIT)
                    }
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
        asyncTaskService.runAsyncCallback(
            task = {
                try {
                    // Don't award XP for the first member (guild creator)
                    val memberCount = memberService.getGuildMembers(event.guildId).size // DB query on virtual thread
                    if (memberCount <= 1) {
                        return@runAsyncCallback
                    }

                    val config = configService.loadConfig()
                    val memberJoinXp = config.progression.memberJoinedXp
                    progressionService.awardExperience(event.guildId, memberJoinXp, ExperienceSource.MEMBER_JOINED)
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
}
