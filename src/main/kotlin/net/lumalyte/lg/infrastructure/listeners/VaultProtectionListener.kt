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
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
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

    private fun getConfig() = configService.loadConfig().vault

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
        val key = org.bukkit.NamespacedKey(org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, "guild_vault_id")
        val meta = itemInHand.itemMeta ?: return

        if (!meta.persistentDataContainer.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            // Not a vault chest, allow normal placement
            return
        }

        // This is a guild vault chest!
        val guildIdString = meta.persistentDataContainer.get(key, org.bukkit.persistence.PersistentDataType.STRING)
        if (guildIdString == null) {
            event.isCancelled = true
            player.sendMessage("§cInvalid guild vault chest! Guild ID not found.")
            return
        }

        val guildId = try {
            java.util.UUID.fromString(guildIdString)
        } catch (e: Exception) {
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
        val member = memberService.getGuildMembers(guild.id).find { it.playerId == player.uniqueId }
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
        val guild = vaultService.getGuildForVaultChest(block.location) ?: return

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

        // Check if player is in the guild
        val member = memberService.getMember(player.uniqueId, guild.id)
        if (member == null) {
            // Non-member trying to break vault
            event.isCancelled = true
            player.sendMessage("§c§lVAULT PROTECTED§r §7» §fOnly guild members can interact with the vault.")
            logger.info("Blocked non-member ${player.name} from breaking vault chest")
            return
        }

        // Check if player has BREAK_VAULT permission
        if (!vaultService.hasVaultPermission(player, guild, requireWithdraw = false)) {
            event.isCancelled = true
            player.sendMessage("§c§lPERMISSION DENIED§r §7» §fYou don't have permission to break the vault chest.")
            logger.info("Blocked ${player.name} from breaking vault chest (no permission)")
            return
        }

        // Get permission to check specifically for BREAK_VAULT
        val rank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val hasBreakPermission = rank?.permissions?.contains(RankPermission.BREAK_VAULT) == true

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
            player.sendMessage("§e§lWARNING§r §7» §fBreaking the vault chest will drop all items!")
            player.sendMessage("§e§lWARNING§r §7» §fBreak again within §e${getConfig().breakWarningTimeoutSeconds} seconds §fto confirm.")
            logger.info("Warned ${player.name} about breaking vault chest (first warning)")
        } else {
            // Second break attempt within timeout - allow break and drop items
            if (getConfig().dropItemsOnBreak) {
                // Drop all items
                val dropResult = vaultService.dropAllVaultItems(guild)
                when (dropResult) {
                    is net.lumalyte.lg.application.services.VaultResult.Success -> {
                        player.sendMessage("§c§lVAULT BROKEN§r §7» §fAll items have been dropped!")
                        logger.info("${player.name} broke vault chest, items dropped")
                    }
                    is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                        player.sendMessage("§c§lERROR§r §7» §f${dropResult.message}")
                        logger.error("Failed to drop vault items: ${dropResult.message}")
                    }
                }
            }

            // Update vault status to unavailable
            val updatedGuild = vaultService.updateVaultStatus(guild, VaultStatus.UNAVAILABLE)
            guildRepository.update(updatedGuild)

            // Clear warning
            playerWarnings.remove(locationKey)

            // Allow the break
            player.sendMessage("§c§lVAULT REMOVED§r §7» §fThe vault chest has been removed.")
            logger.info("${player.name} broke vault chest (confirmed)")

            // Remove hologram
            hologramService.removeHologram(block.location)
        }
    }

    /**
     * Handle explosions near vault chests
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val config = getConfig()
        if (!config.dropItemsOnExplosion) return

        // Check each block in the explosion
        val blocksToRemove = mutableListOf<Block>()
        for (block in event.blockList()) {
            if (block.type != Material.CHEST) continue

            // Check if this is a vault chest
            val guild = vaultService.getGuildForVaultChest(block.location)
            if (guild != null) {
                logger.info("Vault chest for guild ${guild.name} caught in explosion")

                // Drop all items
                val dropResult = vaultService.dropAllVaultItems(guild)
                when (dropResult) {
                    is net.lumalyte.lg.application.services.VaultResult.Success -> {
                        logger.info("Dropped all items from vault chest due to explosion")
                    }
                    is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                        logger.error("Failed to drop vault items from explosion: ${dropResult.message}")
                    }
                }

                // Update vault status
                val updatedGuild = vaultService.updateVaultStatus(guild, VaultStatus.UNAVAILABLE)
                guildRepository.update(updatedGuild)

                // Remove hologram
                hologramService.removeHologram(block.location)

                // Remove the chest from explosion (we'll handle destruction)
                blocksToRemove.add(block)

                // Notify guild members online
                for (member in memberService.getGuildMembers(guild.id)) {
                    val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                    if (onlinePlayer != null && onlinePlayer.isOnline) {
                        onlinePlayer.sendMessage("§c§lVAULT DESTROYED§r §7» §fYour guild's vault chest was destroyed by an explosion!")
                        onlinePlayer.sendMessage("§c§lVAULT DESTROYED§r §7» §fAll items have been dropped at the vault location.")
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
}
