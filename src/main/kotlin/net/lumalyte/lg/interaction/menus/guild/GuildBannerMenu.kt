package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildBannerMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val bankService: BankService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // Custom inventory holder for secure identification
    class BannerMenuHolder(val guildName: String) : org.bukkit.inventory.InventoryHolder {
        private var inventory: org.bukkit.inventory.Inventory? = null

        override fun getInventory(): org.bukkit.inventory.Inventory {
            return inventory ?: throw IllegalStateException("Inventory not set")
        }

        fun setInventory(inv: org.bukkit.inventory.Inventory) {
            inventory = inv
        }
    }

    override fun open() {
        // Create secure custom holder to prevent title-based exploits
        val holder = BannerMenuHolder(guild.name)

        // Create a 3x9 GUI for banner selection
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Guild Banner - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Set the holder on the inventory
        holder.setInventory(gui.getInventory())

        // Add banner selection slot FIRST (so it's the first available for shift-clicking)
        addBannerSelectionSlot(pane, 0, 0)

        // Add current banner display
        addCurrentBannerDisplay(pane, 2, 0)

        // Add visual border around banner selection area
        addVisualBorder(pane)

        // Add clear banner option
        addClearBannerButton(pane, 4, 1)

        // Add apply changes button
        addApplyChangesButton(pane, 5, 1)

        // Add get banner copy button
        addGetBannerCopyButton(pane, 7, 1)

        // Add back button
        addBackButton(pane, 8, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addCurrentBannerDisplay(pane: StaticPane, x: Int, y: Int) {
        val currentItem = if (guild.banner != null) {
            // Try to deserialize the banner ItemStack
            val bannerData = guild.banner!!
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem != null) {
                bannerItem.clone()
                    .setAdventureName(player, messageService, "<white>🏴 CURRENT BANNER")
                    .addAdventureLore(player, messageService, "<gray>This banner represents your guild")
            } else {
                // Fallback to white banner if deserialization fails
                ItemStack(Material.WHITE_BANNER)
                    .setAdventureName(player, messageService, "<red>⚠️ BANNER ERROR")
                    .addAdventureLore(player, messageService, "<red>Failed to load banner data")
                    .addAdventureLore(player, messageService, "<gray>Contact an administrator")
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
                .setAdventureName(player, messageService, "<red>❌ NO BANNER SET")
                .addAdventureLore(player, messageService, "<red>No custom banner configured")
        }

        pane.addItem(GuiItem(currentItem), x, y)
    }

    private fun addVisualBorder(pane: StaticPane) {
        val borderItem = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            .setAdventureName(player, messageService, "<dark_gray>")
            .addAdventureLore(player, messageService, "<gray>Place any banner in the empty slot")
            .addAdventureLore(player, messageService, "<gray>to set it as your guild banner")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Supported: All banner types")

        // Create a tight border around just the banner placement slot (2,1)
        val borderPositions = listOf(
            Pair(1, 0), Pair(2, 0), Pair(3, 0), // Top row above banner slot
            Pair(1, 1), Pair(3, 1),           // Left and right of banner slot
            Pair(1, 2), Pair(2, 2), Pair(3, 2) // Bottom row below banner slot
        )

        borderPositions.forEach { (x, y) ->
            pane.addItem(GuiItem(borderItem.clone()), x, y)
        }
    }

    private fun addBannerSelectionSlot(pane: StaticPane, x: Int, y: Int) {
        // Use a placeholder item that allows banner placement
        // The BannerSelectionListener will handle the actual placement logic
        val placeholderItem = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .setAdventureName(player, messageService, "<gray>📍 BANNER SLOT")
            .addAdventureLore(player, messageService, "<gray>Place any banner here")

        val guiItem = GuiItem(placeholderItem)

        pane.addItem(guiItem, x, y)
    }

    private fun addClearBannerButton(pane: StaticPane, x: Int, y: Int) {
        val hasBanner = guild.banner != null
        val clearItem = ItemStack(if (hasBanner) Material.BARRIER else Material.GRAY_DYE)
            .name(if (hasBanner) "<red>🗑️ CLEAR BANNER" else "<gray>🗑️ CLEAR BANNER")
            .lore(if (hasBanner) {
                listOf(
                    "<gray>Remove the current banner",
                    "<gray>Will use default white banner",
                    "<gray>This action cannot be undone"
                )
            } else {
                listOf("<gray>No banner to clear")
            })

        val guiItem = GuiItem(clearItem) {
            if (hasBanner) {
                val success = guildService.setBanner(guild.id, null, player.uniqueId)
                if (success) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Guild banner cleared! Using default white banner.")
                    // Refresh guild data and reopen menu
                    guild = guildService.getGuild(guild.id) ?: guild
                    open()
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to clear banner. Check permissions.")
                }
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No banner to clear.")
            }
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addApplyChangesButton(pane: StaticPane, x: Int, y: Int) {
        val applyItem = ItemStack(Material.GRAY_CONCRETE)
            .setAdventureName(player, messageService, "<gray>⏳ APPLY CHANGES")
            .addAdventureLore(player, messageService, "<gray>Place a banner in the slot first")

        val guiItem = GuiItem(applyItem) { event ->
            // Check the actual inventory contents when clicked (position 0,0)
            val inventory = player.openInventory.topInventory
            val cursorItem = player.itemOnCursor

            // Check all slots for banners
            val bannerSlots = mutableListOf<Int>()
            for (slot in 0..26) {
                val item = inventory.getItem(slot)
                if (item != null && item.type.name.endsWith("_BANNER")) {
                    bannerSlots.add(slot)
                }
            }

            // Calculate the expected slot based on StaticPane position (0,0)
            // In a 9-slot wide inventory: slot = y * 9 + x
            val expectedSlot = 0 * 9 + 0 // Should be slot 0

            var bannerSlotItem: org.bukkit.inventory.ItemStack? = null

            // First try the expected slot
            val expectedItem = inventory.getItem(expectedSlot)
            if (expectedItem != null && (expectedItem.type.name.endsWith("_BANNER") ||
                (expectedItem.type == Material.LIGHT_GRAY_STAINED_GLASS_PANE && cursorItem?.type?.name?.endsWith("_BANNER") == true))) {
                // If we have a banner directly in the slot, or cursor has banner and slot has placeholder
                bannerSlotItem = if (expectedItem.type.name.endsWith("_BANNER")) expectedItem else cursorItem
            } else {
                // If not found in expected slot, scan all banner slots we found
                for (slot in bannerSlots) {
                    val item = inventory.getItem(slot)
                    if (item != null && item.type.name.endsWith("_BANNER")) {
                        bannerSlotItem = item
                        break
                    }
                }

                // Also check if player has banner on cursor and is hovering over placeholder
                if (bannerSlotItem == null && cursorItem?.type?.name?.endsWith("_BANNER") == true) {
                    for (slot in 0..26) {
                        val item = inventory.getItem(slot)
                        if (item != null && item.type == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                            bannerSlotItem = cursorItem
                            break
                        }
                    }
                }
            }

            if (bannerSlotItem != null) {
                // Apply the banner (pass the entire ItemStack to preserve patterns)
                val success = guildService.setBanner(guild.id, bannerSlotItem, player.uniqueId)

                if (success) {
                    player.sendMessage("<green>✅ Guild banner set to ${bannerSlotItem.type.name.lowercase().replace("_", " ")}!")

                    // Return the banner to player's inventory
                    val remaining = player.inventory.addItem(bannerSlotItem)
                    if (remaining.isNotEmpty()) {
                        // Inventory full, drop at feet
                        player.world.dropItem(player.location, bannerSlotItem)
                        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📦 Banner dropped at your feet (inventory full)")
                    }

                    // Clear the slot and close menu
                    inventory.setItem(0, ItemStack(Material.AIR))
                    player.closeInventory()
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to set banner. Check permissions.")
                }
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Place a banner in the slot first!")
            }
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>⬅️ BACK")
            .addAdventureLore(player, messageService, "<gray>Return to settings menu")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }

        pane.addItem(backGuiItem, x, y)
    }

    private fun addGetBannerCopyButton(pane: StaticPane, x: Int, y: Int) {
        val config = configService.loadConfig().guild

        // Don't show button if feature is disabled
        if (!config.bannerCopyEnabled) return

        val bannerCopyCost = config.bannerCopyCost
        val chargeGuildBank = config.bannerCopyChargeGuildBank
        val bannerCopyFree = config.bannerCopyFree
        val useItemCost = config.bannerCopyUseItemCost
        val itemMaterial = config.bannerCopyItemMaterial
        val itemAmount = config.bannerCopyItemAmount
        val itemCustomModelData = config.bannerCopyItemCustomModelData

        val copyItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<yellow>📋 GET BANNER COPY")
            .addAdventureLore(player, messageService, "<gray>Get a copy of your guild banner")

        if (bannerCopyFree) {
            copyItem.addAdventureLore(player, messageService, "<gray>Cost: <green>FREE")
        } else if (useItemCost) {
            // Item-based cost
            try {
                val material = Material.valueOf(itemMaterial.uppercase())
                copyItem.lore("<gray>Cost: <gold>$itemAmount x ${material.name.lowercase().replace("_", " ")}")
                copyItem.addAdventureLore(player, messageService, "<gray>Taken from your inventory")
            } catch (e: IllegalArgumentException) {
                copyItem.addAdventureLore(player, messageService, "<red>❌ Invalid item material configured")
            }
        } else {
            // Coin-based cost
            copyItem.addAdventureLore(player, messageService, "<gray>Cost: <gold>$bannerCopyCost coins")
            copyItem.lore("<gray>Charged from: <gold>${if (chargeGuildBank) "Guild Bank" else "Personal Balance"}")

            // Add fee information to lore if charging guild bank
            if (chargeGuildBank) {
                val fee = bankService.calculateWithdrawalFee(guild.id, bannerCopyCost)
                    if (fee > 0) {
                        val totalCostForDisplay = bannerCopyCost + fee
                        copyItem.addAdventureLore(player, messageService, "<gray>Total: <gold>$totalCostForDisplay coins <gray>(<gold>$fee<gray> fee)")
                    }
            }
        }

        val guiItem = GuiItem(copyItem) {
            // Check if guild has a banner
            if (guild.banner == null) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Your guild doesn't have a banner set!")
                return@GuiItem
            }

            // Try to deserialize the banner
            val bannerData = guild.banner!!
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem == null) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to load guild banner data!")
                return@GuiItem
            }

            // Check if player has permission
            if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to get banner copies!")
                return@GuiItem
            }

            val success = if (bannerCopyFree) {
                // Free banner copy - no payment needed
                true
            } else if (useItemCost) {
                // Item-based payment
                try {
                    val material = Material.valueOf(itemMaterial.uppercase())
                    val requiredItem = ItemStack(material, itemAmount)

                    // Apply custom model data if specified
                    if (itemCustomModelData != null) {
                        val meta = requiredItem.itemMeta
                        if (meta != null) {
                            meta.setCustomModelData(itemCustomModelData)
                            requiredItem.itemMeta = meta
                        }
                    }

                    // Check if player has enough items
                    val playerInventory = player.inventory
                    val hasEnough = playerInventory.containsAtLeast(requiredItem, itemAmount)

                    if (!hasEnough) {
                        player.sendMessage("<red>❌ You don't have enough items! (Need: <gold>$itemAmount x ${material.name.lowercase().replace("_", " ")}<red>)")
                        return@GuiItem
                    }

                    // Remove items from player inventory
                    playerInventory.removeItem(requiredItem)
                    player.sendMessage("<green>✅ Paid <gold>$itemAmount x ${material.name.lowercase().replace("_", " ")} <green>for banner copy!")

                    true
                } catch (e: IllegalArgumentException) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Invalid item material configured for banner cost!")
                    false
                }
            } else if (chargeGuildBank) {
                // Coin-based payment from guild bank
                val cost = bannerCopyCost
                val guildBalance = bankService.getBalance(guild.id)
                val fee = bankService.calculateWithdrawalFee(guild.id, cost)
                val totalCost = cost + fee

                if (guildBalance < totalCost) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Guild bank has insufficient funds! (Need: <gold>$totalCost<red>, Have: <gold>$guildBalance<red>)")
                    return@GuiItem
                }

                bankService.deductFromGuildBank(guild.id, totalCost, "Banner copy purchase")
            } else {
                // Coin-based payment from player balance
                val cost = bannerCopyCost
                val playerBalance = bankService.getPlayerBalance(player.uniqueId)
                if (playerBalance < cost.toInt()) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have enough coins! (Need: <gold>$cost<red>, Have: <gold>$playerBalance<red>)")
                    return@GuiItem
                }
                bankService.withdrawPlayer(player.uniqueId, cost, "Banner copy purchase")
            }

            if (!success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to process payment!")
                return@GuiItem
            }

            // Give the banner to player
            val bannerCopy = bannerItem.clone()
            val remaining = player.inventory.addItem(bannerCopy)

            if (remaining.isNotEmpty()) {
                // Inventory full, drop at feet
                player.world.dropItem(player.location, bannerCopy)
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📦 Banner dropped at your feet (inventory full)")
            }

            if (bannerCopyFree) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Free banner copy received!")
            } else if (useItemCost) {
                // Item cost message already sent above
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Banner copy received!")
            } else {
                val cost = bannerCopyCost
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Banner copy purchased for <gold>$cost <green>coins!")
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>💡 The banner has been added to your inventory")
        }

        pane.addItem(guiItem, x, y)
    }}

