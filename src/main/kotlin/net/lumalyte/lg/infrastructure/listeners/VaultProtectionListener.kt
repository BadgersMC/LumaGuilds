package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.VaultStatus
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener for guild vault chest protection and placement.
 * Handles:
 * - Vault chest placement validation
 * - Vault chest break protection with warning system
 * - Explosion protection for vault chests
 * - Hopper/dropper/dispenser blocking (vaults are virtual, not physical chests)
 * - Piston blocking with backfire (attempting to push a vault destroys the piston)
 */
class VaultProtectionListener : Listener, KoinComponent {

    private val vaultService: GuildVaultService by inject()
    private val guildService: GuildService by inject()
    private val guildRepository: net.lumalyte.lg.application.persistence.GuildRepository by inject()
    private val rankService: net.lumalyte.lg.application.services.RankService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val hologramService: net.lumalyte.lg.infrastructure.services.VaultHologramService by inject()

    private val logger = LoggerFactory.getLogger(VaultProtectionListener::class.java)

    // Track break warnings: player UUID -> (vault location key -> timestamp)
    private val breakWarnings = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    // Cached vault config to avoid disk I/O on every event
    @Volatile
    private var cachedVaultConfig = configService.loadConfig().vault

    /**
     * Gets the vault configuration.
     * Uses cached config to avoid disk I/O on every event handler call.
     * To reload config, call refreshConfig().
     */
    private fun getConfig() = cachedVaultConfig

    /**
     * Refreshes the cached vault configuration from disk.
     * Call this when config is reloaded.
     */
    fun refreshConfig() {
        cachedVaultConfig = configService.loadConfig().vault
        logger.debug("Refreshed vault config cache")
    }

    /**
     * Handle vault chest placement
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        val player = event.player
        val itemInHand = event.itemInHand

        // Only handle chest placement
        if (block.type != Material.CHEST) return

        // Check if this is a special guild vault chest
        val meta = itemInHand.itemMeta ?: return

        if (!meta.persistentDataContainer.has(net.lumalyte.lg.common.PluginKeys.GUILD_VAULT_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
            // Not a vault chest - check if trying to place next to an existing vault chest (double chest exploit)
            if (isAdjacentToVaultChest(block.location)) {
                event.isCancelled = true
                player.sendMessage("§c§lBLOCKED§r §7» §fYou cannot place a chest next to a guild vault!")
                player.sendMessage("§7This would create a double chest and bypass vault security.")
                logger.info("Blocked ${player.name} from placing chest next to vault chest (double chest exploit prevention)")
            }
            return
        }

        // This is a guild vault chest!
        val guildIdString = meta.persistentDataContainer.get(net.lumalyte.lg.common.PluginKeys.GUILD_VAULT_ID, org.bukkit.persistence.PersistentDataType.STRING)
        if (guildIdString == null) {
            event.isCancelled = true
            player.sendMessage("§cInvalid guild vault chest! Guild ID not found.")
            return
        }

        val guildId = try {
            java.util.UUID.fromString(guildIdString)
        } catch (e: IllegalArgumentException) {
            // Invalid UUID format in PDC - corrupted data
            event.isCancelled = true
            player.sendMessage("§cInvalid guild vault chest! Corrupted guild ID.")
            logger.error("Failed to parse guild ID from vault chest: $guildIdString", e)
            return
        }

        // Get the guild
        val guild = guildService.getGuild(guildId)
        if (guild == null) {
            event.isCancelled = true
            player.sendMessage("§cInvalid guild vault chest! Guild no longer exists.")
            return
        }

        // Check if player is in the guild
        val member = memberService.getMember(player.uniqueId, guild.id)
        if (member == null) {
            event.isCancelled = true
            player.sendMessage("§cYou cannot place this vault chest as you're not in ${guild.name}!")
            return
        }

        // Check if vault already exists (double-check in case of race condition)
        if (guild.vaultStatus == net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
            val existingLocation = vaultService.getVaultLocation(guild)
            if (existingLocation != null) {
                event.isCancelled = true
                player.sendMessage("§cYour guild already has a vault chest placed!")
                player.sendMessage("§7Location: §f${existingLocation.world?.name} (${existingLocation.blockX}, ${existingLocation.blockY}, ${existingLocation.blockZ})")
                player.sendMessage("§7Break the existing vault first.")
                return
            }
        }

        // Place the vault!
        val placeResult = vaultService.placeVaultChest(guild, block.location, player)
        when (placeResult) {
            is net.lumalyte.lg.application.services.VaultResult.Success -> {
                // Update guild in database
                guildRepository.update(placeResult.data)

                player.sendMessage("§a§l✓ VAULT PLACED§r")
                player.sendMessage("§aYour guild's physical vault has been set up!")
                player.sendMessage("§7Location: §f${block.location.world?.name} (${block.location.blockX}, ${block.location.blockY}, ${block.location.blockZ})")
                player.sendMessage("§7")
                player.sendMessage("§6Access: §fOpen the guild bank menu §7(/guild menu → Bank)")
                player.sendMessage("§6Capacity: §f${vaultService.getCapacityForLevel(guild.level)} slots")

                // Play success sound
                player.playSound(player.location, org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.2f)
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)

                logger.info("${player.name} placed guild vault for ${guild.name} at ${block.location}")

                // Create hologram for the vault
                hologramService.createHologram(block.location, placeResult.data)
            }
            is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                event.isCancelled = true
                player.sendMessage("§c§lFAILED§r")
                player.sendMessage("§cCouldn't place vault: ${placeResult.message}")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
                logger.error("Failed to place vault for ${guild.name}: ${placeResult.message}")
            }
        }
    }

    /**
     * Handle vault chest interaction (right-click to open)
     * CRITICAL: Opens the SAME custom inventory as /guild vault command
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Only handle right-click on blocks
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        val player = event.player

        // Only handle chest interactions
        if (block.type != Material.CHEST) return

        // Check if this is a guild vault chest
        val guild = vaultService.getGuildForVaultChest(block.location)

        if (guild != null) {
            // THIS IS A VAULT CHEST - Cancel default chest opening
            event.isCancelled = true

            logger.debug("${player.name} is interacting with vault chest for guild ${guild.name}")

            // Open the SAME custom inventory that /guild vault opens
            val result = vaultService.openVaultInventory(player, guild)
            when (result) {
                is net.lumalyte.lg.application.services.VaultResult.Success -> {
                    player.sendMessage("§a§lVAULT OPENED§r")
                    player.sendMessage("§aAccessing §6${guild.name}§a's vault...")
                    player.playSound(player.location, org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f)
                    logger.debug("Opened custom vault inventory for ${player.name} (guild: ${guild.name}, capacity: ${vaultService.getCapacityForLevel(guild.level)} slots)")
                }
                is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                    player.sendMessage("§c§lFAILED§r")
                    player.sendMessage("§c${result.message}")
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
                    logger.warn("Failed to open vault for ${player.name}: ${result.message}")
                }
            }
        } else {
            // Not a vault chest - check if it's adjacent to a vault chest (double chest exploit)
            if (isAdjacentToVaultChest(block.location)) {
                event.isCancelled = true
                player.sendMessage("§c§lBLOCKED§r §7» §fThis chest is part of a guild vault!")
                player.sendMessage("§7You cannot access it directly - use the vault interface instead.")
                logger.info("Blocked ${player.name} from opening chest adjacent to vault chest (double chest exploit prevention)")
            }
        }
    }

    /**
     * Handle vault chest break attempts
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player

        // Only handle chest breaks
        if (block.type != Material.CHEST) return

        // Check if this is a vault chest
        val guild = vaultService.getGuildForVaultChest(block.location) ?: return

        logger.info("Player ${player.name} is attempting to break vault chest for guild ${guild.name}")

        // Check if player has BREAK_VAULT permission (also verifies guild membership)
        val rank = rankService.getPlayerRank(player.uniqueId, guild.id)
        if (rank == null) {
            // Non-member trying to break vault
            event.isCancelled = true
            player.sendMessage("§c§lVAULT PROTECTED§r §7» §fOnly guild members can interact with the vault.")
            logger.info("Blocked non-member ${player.name} from breaking vault chest")
            return
        }

        val hasBreakPermission = rank.permissions.contains(RankPermission.BREAK_VAULT)
        if (!hasBreakPermission) {
            event.isCancelled = true
            player.sendMessage("§c§lPERMISSION DENIED§r §7» §fYou don't have permission to break the vault chest.")
            logger.info("Blocked ${player.name} from breaking vault chest (no BREAK_VAULT permission)")
            return
        }

        // Warning system for guild members
        val locationKey = locationToKey(block.location)
        val playerWarnings = breakWarnings.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        val lastWarning = playerWarnings[locationKey]
        val currentTime = System.currentTimeMillis()
        val warningTimeout = getConfig().breakWarningTimeoutSeconds * 1000L

        if (lastWarning == null || (currentTime - lastWarning) > warningTimeout) {
            // First warning or warning expired
            event.isCancelled = true
            playerWarnings[locationKey] = currentTime
            player.sendMessage("§e§lWARNING§r §7» §fBreaking the vault will drop ALL items and currency on the ground!")
            player.sendMessage("§e§lWARNING§r §7» §fThey will be permanently lost if not picked up!")
            player.sendMessage("§e§lWARNING§r §7» §fBreak again within §e${getConfig().breakWarningTimeoutSeconds} seconds §fto confirm.")
            logger.info("Warned ${player.name} about breaking vault chest (first warning)")
        } else {
            // Second break attempt within timeout - allow break
            val dropItems = getConfig().dropItemsOnBreak

            // Use removeVaultChest which handles items correctly
            val removeResult = vaultService.removeVaultChest(guild, dropItems = dropItems)

            when (removeResult) {
                is net.lumalyte.lg.application.services.VaultResult.Success -> {
                    if (dropItems) {
                        player.sendMessage("§c§lVAULT BROKEN§r §7» §fAll items and currency have been dropped on the ground!")
                        player.sendMessage("§c§lVAULT BROKEN§r §7» §fPick them up quickly before they despawn!")
                        logger.info("${player.name} broke vault chest, items dropped and cleared from database")
                    } else {
                        player.sendMessage("§c§lVAULT REMOVED§r §7» §fThe vault chest has been removed.")
                        player.sendMessage("§e§lITEMS PRESERVED§r §7» §fPlace a new vault chest to restore your items!")
                        logger.info("${player.name} broke vault chest, items preserved in database")
                    }

                    // Update guild in repo
                    guildRepository.update(removeResult.data)

                    // Clear warning
                    playerWarnings.remove(locationKey)

                    // Remove hologram
                    hologramService.removeHologram(block.location)
                }
                is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                    event.isCancelled = true
                    player.sendMessage("§c§lERROR§r §7» §f${removeResult.message}")
                    logger.error("Failed to remove vault: ${removeResult.message}")
                }
            }
        }
    }

    /**
     * Handle explosions near vault chests
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val config = getConfig()

        // Check each block in the explosion
        val blocksToRemove = mutableListOf<Block>()
        for (block in event.blockList()) {
            if (block.type != Material.CHEST) continue

            // Check if this is a vault chest
            val guild = vaultService.getGuildForVaultChest(block.location)
            if (guild != null) {
                logger.info("Vault chest for guild ${guild.name} caught in explosion")

                // Use removeVaultChest with dropItems based on config
                val dropItems = config.dropItemsOnExplosion
                val removeResult = vaultService.removeVaultChest(guild, dropItems = dropItems)

                when (removeResult) {
                    is net.lumalyte.lg.application.services.VaultResult.Success -> {
                        if (dropItems) {
                            logger.info("Dropped all items from vault chest due to explosion and cleared from database")
                        } else {
                            logger.info("Vault chest destroyed by explosion, items preserved in database")
                        }
                        guildRepository.update(removeResult.data)
                    }
                    is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                        logger.error("Failed to remove vault from explosion: ${removeResult.message}")
                    }
                }

                // Remove hologram
                hologramService.removeHologram(block.location)

                // Remove the chest from explosion (we'll handle destruction)
                blocksToRemove.add(block)

                // Notify guild members online with appropriate message
                for (member in memberService.getGuildMembers(guild.id)) {
                    val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                    if (onlinePlayer != null && onlinePlayer.isOnline) {
                        onlinePlayer.sendMessage("§c§lVAULT DESTROYED§r §7» §fYour guild's vault chest was destroyed by an explosion!")
                        if (dropItems) {
                            onlinePlayer.sendMessage("§c§lALL ITEMS DROPPED§r §7» §fAll items and currency scattered at the vault location!")
                            onlinePlayer.sendMessage("§c§lACT FAST§r §7» §fItems will despawn if not picked up!")
                        } else {
                            onlinePlayer.sendMessage("§e§lITEMS PRESERVED§r §7» §fPlace a new vault chest to restore your items!")
                        }
                    }
                }
            }
        }

        // Remove vault chests from explosion block list
        event.blockList().removeAll(blocksToRemove.toSet())
    }

    /**
     * Handle player quit - cleanup hologram visibility tracking
     */
    @EventHandler
    fun onPlayerQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        hologramService.onPlayerQuit(event.player)
    }

    /**
     * Block hoppers, droppers, and dispensers from interacting with vault chests.
     * Guild vaults are virtual storage systems, not physical chests.
     * Allowing hopper interactions would bypass the vault management system and cause item loss.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        // Check if source inventory is trying to pull from a vault chest
        val sourceLocation = event.source.location
        if (sourceLocation != null) {
            val sourceBlock = sourceLocation.block
            if (sourceBlock.type == Material.CHEST) {
                val guild = vaultService.getGuildForVaultChest(sourceLocation)
                if (guild != null) {
                    event.isCancelled = true
                    logger.debug("Blocked hopper/dropper from pulling items from vault chest (guild: ${guild.name})")
                    return
                }
            }
        }

        // Check if destination inventory is trying to insert into a vault chest
        val destLocation = event.destination.location
        if (destLocation != null) {
            val destBlock = destLocation.block
            if (destBlock.type == Material.CHEST) {
                val guild = vaultService.getGuildForVaultChest(destLocation)
                if (guild != null) {
                    event.isCancelled = true
                    logger.debug("Blocked hopper/dropper from inserting items into vault chest (guild: ${guild.name})")
                    return
                }
            }
        }
    }

    /**
     * Block pistons from pushing vault chests.
     * BACKFIRE: If a piston attempts to push a vault chest, the piston breaks and drops.
     * This provides clear feedback that vault chests are immovable special blocks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPistonExtend(event: BlockPistonExtendEvent) {
        handlePistonVaultInteraction(event.blocks, event.block, "push") {
            event.isCancelled = true
        }
    }

    /**
     * Block sticky pistons from pulling vault chests.
     * BACKFIRE: If a sticky piston attempts to pull a vault chest, the piston breaks and drops.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPistonRetract(event: BlockPistonRetractEvent) {
        handlePistonVaultInteraction(event.blocks, event.block, "pull") {
            event.isCancelled = true
        }
    }

    /**
     * Common handler for piston interactions with vault chests.
     * Implements the "backfire" mechanic where pistons are destroyed when attempting to move vaults.
     *
     * @param blocks The blocks being pushed/pulled by the piston
     * @param pistonBlock The piston block itself
     * @param action The action being performed ("push" or "pull") for logging purposes
     * @param cancelEvent Lambda to cancel the piston event
     */
    private fun handlePistonVaultInteraction(
        blocks: List<org.bukkit.block.Block>,
        pistonBlock: org.bukkit.block.Block,
        action: String,
        cancelEvent: () -> Unit
    ) {
        // Check if any blocks being moved are vault chests
        for (block in blocks) {
            if (block.type == Material.CHEST) {
                val guild = vaultService.getGuildForVaultChest(block.location)
                if (guild != null) {
                    // Cancel the piston action
                    cancelEvent()

                    // BACKFIRE: Break the piston and drop it
                    val world = pistonBlock.world
                    val pistonLocation = pistonBlock.location

                    // Drop the piston as an item
                    world.dropItemNaturally(pistonLocation, org.bukkit.inventory.ItemStack(pistonBlock.type, 1))

                    // Break the piston block
                    pistonBlock.type = Material.AIR

                    // Visual/audio feedback
                    world.playSound(pistonLocation, org.bukkit.Sound.BLOCK_PISTON_CONTRACT, 1.0f, 0.5f)
                    world.playSound(pistonLocation, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
                    world.spawnParticle(org.bukkit.Particle.BLOCK, pistonLocation.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.0, pistonBlock.blockData)

                    logger.info("Piston backfire: Destroyed piston attempting to $action vault chest for guild ${guild.name} at $pistonLocation")
                    return
                }
            }
        }
    }

    /**
     * Convert location to a unique string key
     */
    private fun locationToKey(location: org.bukkit.Location): String {
        return "${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    /**
     * Clean up expired warnings periodically (called externally if needed)
     */
    fun cleanupExpiredWarnings() {
        val currentTime = System.currentTimeMillis()
        val warningTimeout = getConfig().breakWarningTimeoutSeconds * 1000L

        breakWarnings.forEach { (playerId, warnings) ->
            warnings.entries.removeIf { (_, timestamp) ->
                (currentTime - timestamp) > warningTimeout
            }
            if (warnings.isEmpty()) {
                breakWarnings.remove(playerId)
            }
        }
    }

    /**
     * Check if a location is adjacent to a guild vault chest.
     * Used to prevent double chest exploit.
     *
     * @param location The location to check.
     * @return true if adjacent to a vault chest.
     */
    private fun isAdjacentToVaultChest(location: org.bukkit.Location): Boolean {
        val world = location.world ?: return false

        // Check all 4 cardinal directions for vault chests
        val adjacentOffsets = listOf(
            Pair(1, 0),   // East
            Pair(-1, 0),  // West
            Pair(0, 1),   // South
            Pair(0, -1)   // North
        )

        for ((dx, dz) in adjacentOffsets) {
            val adjacentLocation = location.clone().add(dx.toDouble(), 0.0, dz.toDouble())
            val adjacentBlock = world.getBlockAt(adjacentLocation)

            // Check if this adjacent block is a vault chest
            if (adjacentBlock.type == Material.CHEST) {
                val guild = vaultService.getGuildForVaultChest(adjacentLocation)
                if (guild != null) {
                    return true
                }
            }
        }

        return false
    }
}
