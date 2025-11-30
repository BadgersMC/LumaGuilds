package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.events.GuildBankDepositEvent
import net.lumalyte.lg.domain.events.GuildMemberJoinEvent
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Efficient event listener for guild progression system.
 * Uses batching and rate limiting to handle high-frequency events.
 */
class ProgressionEventListener : Listener, KoinComponent {

    private val progressionService: ProgressionService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    
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
            logger.info("Awarding $blockBreakXp XP to ${event.player.name} for breaking ${event.block.type}")
            awardExperienceWithCooldown(event.player, blockBreakXp, ExperienceSource.BLOCK_BREAK)
        } else {
            logger.info("Not awarding XP to ${event.player.name} for breaking ${event.block.type} (player-placed or not a block)")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        try {
            // Mark blocks as player-placed to prevent XP farming
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds")
            if (plugin == null) {
                logger.warn("LumaGuilds plugin not found during block place event")
                return
            }

            if (event.block != null) {
                event.block.setMetadata("player_placed", org.bukkit.metadata.FixedMetadataValue(plugin, true))
            } else {
                logger.warn("Block is null in BlockPlaceEvent for player ${event.player.name}")
            }

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
     */
    private fun awardExperience(player: Player, amount: Int, source: ExperienceSource) {
        try {
            val guildIds = memberService.getPlayerGuilds(player.uniqueId)
            logger.debug("Player ${player.name} (${player.uniqueId}) guild membership check for $source: found ${guildIds.size} guilds: $guildIds")

            if (guildIds.isEmpty()) {
                logger.info("❌ BLOCKED: Player ${player.name} attempted to gain $amount XP from $source but is not in any guild")
                return
            }

            guildIds.forEach { guildId ->
                // Debug logging removed - issue is solved
                progressionService.awardExperience(guildId, amount, source)
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.error("❌ ERROR: Failed to award $amount XP to player ${player.name} from $source", e)
        }
    }

    /**
     * Awards experience with rate limiting to prevent spam and exploitation.
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
     */
    fun awardGuildExperienceByGuildId(guildId: java.util.UUID, amount: Int, source: ExperienceSource) {
        try {
            progressionService.awardExperience(guildId, amount, source)
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.warn("Failed to award experience to guild $guildId", e)
        }
    }

    // Custom event handlers for guild-specific actions
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildBankDeposit(event: GuildBankDepositEvent) {
        try {
            val config = configService.loadConfig()
            val xpPerHundred = config.progression.bankDepositXpPer100
            val xpAmount = (event.amount / 100.0 * xpPerHundred).toInt()
            if (xpAmount > 0) {
                progressionService.awardExperience(event.guildId, xpAmount, ExperienceSource.BANK_DEPOSIT)
                logger.info("Awarded $xpAmount XP to guild ${event.guildId} for bank deposit of ${event.amount}")
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.warn("Failed to award progression XP for bank deposit", e)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        try {
            val config = configService.loadConfig()
            val memberJoinXp = config.progression.memberJoinedXp
            progressionService.awardExperience(event.guildId, memberJoinXp, ExperienceSource.MEMBER_JOINED)
            logger.info("Awarded $memberJoinXp XP to guild ${event.guildId} for new member join")
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.warn("Failed to award progression XP for member join", e)
        }
    }
}
