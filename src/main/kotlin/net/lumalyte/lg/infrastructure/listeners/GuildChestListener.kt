package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.AuditService
import net.lumalyte.lg.application.services.ItemBankingService
import net.lumalyte.lg.domain.entities.GuildChestAction
import net.lumalyte.lg.domain.entities.GuildChest
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class GuildChestListener : Listener, KoinComponent {

    private val itemBankingService: ItemBankingService by inject()
    private val auditService: AuditService by inject()

    // Track players who are in the process of breaking a guild chest
    private val chestBreakAttempts = mutableMapOf<UUID, UUID>() // playerId -> chestId

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val block = event.block

        // Check if this is a chest being placed
        if (block.type != Material.CHEST) return

        // Check if player has permission to create guild chests
        val playerGuildId = getPlayerGuildId(player) ?: return

        // Create guild chest
        val chest = itemBankingService.createGuildChest(
            guildId = playerGuildId,
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z,
            playerId = player.uniqueId
        )

        if (chest != null) {
            itemBankingService.logChestAccess(
                chestId = chest.id,
                playerId = player.uniqueId,
                action = GuildChestAction.OPEN, // Chest was just created, log as accessed
                itemType = null,
                itemAmount = 0
            )

            player.sendMessage("§aGuild chest created successfully!")
        } else {
            event.isCancelled = true
            player.sendMessage("§cFailed to create guild chest. You may not have permission or there's already a chest there.")
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.CHEST) return

        val player = event.player
        val playerGuildId = getPlayerGuildId(player) ?: return

        // Check if this is a guild chest
        val chest = itemBankingService.getGuildChestAt(
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z
        )

        if (chest == null) return

        // Check if player can access this chest
        if (!itemBankingService.canAccessGuildChest(
                playerId = player.uniqueId,
                guildId = playerGuildId,
                worldId = block.world.uid,
                x = block.x,
                y = block.y,
                z = block.z
            )) {
            event.isCancelled = true
            player.sendMessage("§cYou don't have permission to access this guild chest.")
            return
        }

        // Log access
        itemBankingService.logChestAccess(
            chestId = chest.id,
            playerId = player.uniqueId,
            action = GuildChestAction.OPEN
        )

        // Update last accessed time
        itemBankingService.updateLastAccessed(chest.id, java.time.Instant.now())

        // Open custom inventory GUI instead of default chest
        event.isCancelled = true
        openGuildChestGUI(player, chest)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.CHEST) return

        val player = event.player
        val playerGuildId = getPlayerGuildId(player) ?: return

        // Check if this is a guild chest
        val chest = itemBankingService.getGuildChestAt(
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z
        )

        if (chest == null) return

        // Check if player is in their own guild
        if (chest.guildId != playerGuildId) {
            // This is a chest from another guild
            val config = itemBankingService.getItemBankingConfig()

            if (config.requireClaimsForChestProtection) {
                // If claims are required, check if the player can break chests in this area
                // This would need claims integration
                event.isCancelled = true
                player.sendMessage("§cYou cannot break chests from other guilds in this area.")
                return
            } else {
                // If claims are not required, require explosions to break chests
                if (config.chestExplosionRequired) {
                    // Check if this break was caused by an explosion
                    if (!isExplosionBreak(event)) {
                        event.isCancelled = true
                        player.sendMessage("§cGuild chests can only be broken by explosions.")
                        return
                    }
                }

                // Auto-enemy the guilds
                itemBankingService.autoEnemyGuilds(
                    victimGuildId = chest.guildId,
                    attackerGuildId = playerGuildId,
                    reason = "Guild chest was destroyed"
                )

                // Log the break attempt
                itemBankingService.logChestAccess(
                    chestId = chest.id,
                    playerId = player.uniqueId,
                    action = GuildChestAction.BREAK_SUCCESS
                )

                // Audit the chest destruction
                auditService.recordChestDestruction(
                    actorId = player.uniqueId,
                    guildId = chest.guildId,
                    chestId = chest.id,
                    reason = "Cross-guild chest destruction"
                )

                player.sendMessage("§cYou have destroyed another guild's chest! This has created an enemy relationship.")

                // Remove the chest from database
                itemBankingService.removeGuildChest(chest.id, player.uniqueId)
                return
            }
        }

        // This is the player's own guild chest
        val config = itemBankingService.getItemBankingConfig()

        if (config.chestBreakConfirmation) {
            // Check if player has already attempted to break this chest
            if (chestBreakAttempts[player.uniqueId] == chest.id) {
                // This is the second break attempt - actually break the chest
                chestBreakAttempts.remove(player.uniqueId)

                // Log the break
                itemBankingService.logChestAccess(
                    chestId = chest.id,
                    playerId = player.uniqueId,
                    action = GuildChestAction.BREAK_SUCCESS
                )

                // Audit the chest destruction
                auditService.recordChestDestruction(
                    actorId = player.uniqueId,
                    guildId = chest.guildId,
                    chestId = chest.id,
                    reason = "Own guild chest destruction"
                )

                // Remove the chest
                itemBankingService.removeGuildChest(chest.id, player.uniqueId)

                player.sendMessage("§cGuild chest has been destroyed! All items have been dropped.")

                // Allow the break to proceed
                return
            } else {
                // First break attempt - show warning
                chestBreakAttempts[player.uniqueId] = chest.id
                event.isCancelled = true

                player.sendMessage("§c⚠️ Warning: Breaking this guild chest will drop all items!")
                player.sendMessage("§cBreak the chest again to confirm destruction.")

                // Schedule removal of the attempt after 10 seconds
                Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("LumaGuilds")!!,
                    Runnable { chestBreakAttempts.remove(player.uniqueId) },
                    200L // 10 seconds
                )
            }
        } else {
            // No confirmation required - just break
            itemBankingService.logChestAccess(
                chestId = chest.id,
                playerId = player.uniqueId,
                action = GuildChestAction.BREAK_SUCCESS
            )

            itemBankingService.removeGuildChest(chest.id, player.uniqueId)
            player.sendMessage("§cGuild chest destroyed.")
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val inventory = event.inventory

        // Check if this was a guild chest inventory
        // This would need to be tracked when opening the GUI
        // For now, just log that the chest was closed
    }

    private fun getPlayerGuildId(player: Player): UUID? {
        // This would need to be implemented to get the player's guild
        // For now, return null
        return null // TODO: Implement actual guild lookup
    }

    private fun isExplosionBreak(event: BlockBreakEvent): Boolean {
        // Check if the block break was caused by an explosion
        // This would need to check the cause or check for nearby explosions
        return false
    }

    private fun openGuildChestGUI(player: Player, chest: GuildChest) {
        // This would open a custom GUI showing the chest contents
        // For now, just send a message
        player.sendMessage("§aOpening guild chest GUI...")
        // TODO: Implement actual GUI opening
    }
}
