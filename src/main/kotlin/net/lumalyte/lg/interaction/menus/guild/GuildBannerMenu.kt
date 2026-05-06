package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuildBannerMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val bankService: BankService by inject()
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    companion object {
        // Tracks which players currently have the Java guild banner menu open. Used by
        // BannerSelectionListener to identify banner-placement clicks without relying on
        // InventoryHolder identity (InventoryFramework owns the holder) or the inventory
        // title (deprecated and exploit-prone).
        val activeViewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    }

    override fun open() {
        activeViewers.add(player.uniqueId)

        // Create a 3x9 GUI for banner selection
        val gui = ChestGui(3, "§6Guild Banner - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent ->
            // Allow clicks on the banner placement slot (slot 11)
            if (guiEvent.slot != 11) {
                guiEvent.isCancelled = true
            }
            // Banner placement is handled by BannerSelectionListener
        }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Add banner selection slot at the position matching the visual border (2,1 = slot 11)
        addBannerSelectionSlot(pane, 2, 1)

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
        val currentItem = guild.banner?.let { bannerData ->
            // Try to deserialize the banner ItemStack
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem != null) {
                bannerItem.clone()
                    .name("§f🏴 CURRENT BANNER")
                    .lore("§7This banner represents your guild")
            } else {
                // Fallback to white banner if deserialization fails
                ItemStack.of(Material.WHITE_BANNER)
                    .name("§c⚠ BANNER ERROR")
                    .lore("§cFailed to load banner data")
                    .lore("§7Contact an administrator")
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)
            .name("§c❌ NO BANNER SET")
            .lore("§cNo custom banner configured")

        pane.addItem(GuiItem(currentItem), x, y)
    }

    private fun addVisualBorder(pane: StaticPane) {
        val borderItem = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE)
            .name("§8")
            .lore("§7Place any banner in the empty slot")
            .lore("§7to set it as your guild banner")
            .lore("§7")
            .lore("§eSupported: All banner types")

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
        val placeholderItem = ItemStack.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .name("§7📍 BANNER SLOT")
            .lore("§7Place any banner here")

        val guiItem = GuiItem(placeholderItem)

        pane.addItem(guiItem, x, y)
    }

    private fun addClearBannerButton(pane: StaticPane, x: Int, y: Int) {
        val hasBanner = guild.banner != null
        val clearItem = ItemStack.of(if (hasBanner) Material.BARRIER else Material.GRAY_DYE)
            .name(if (hasBanner) "§c🗑 CLEAR BANNER" else "§7🗑 CLEAR BANNER")
            .lore(if (hasBanner) {
                listOf(
                    "§7Remove the current banner",
                    "§7Will use default white banner",
                    "§7This action cannot be undone"
                )
            } else {
                listOf("§7No banner to clear")
            })

        val guiItem = GuiItem(clearItem) {
            if (hasBanner) {
                val success = guildService.setBanner(guild.id, null, player.uniqueId)
                if (success) {
                    player.sendMessage("§a✅ Guild banner cleared! Using default white banner.")
                    // Refresh guild data and reopen menu
                    guild = guildService.getGuild(guild.id) ?: guild
                    open()
                } else {
                    player.sendMessage("§c❌ Failed to clear banner. Check permissions.")
                }
            } else {
                player.sendMessage("§c❌ No banner to clear.")
            }
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addApplyChangesButton(pane: StaticPane, x: Int, y: Int) {
        val applyItem = ItemStack.of(Material.LIME_CONCRETE)
            .name("§a⏳ APPLY CHANGES")
            .lore("§7Place a banner in the slot,")
            .lore("§7then click here to save it")

        val guiItem = GuiItem(applyItem) { event ->
            // Check the actual inventory contents when clicked (slot 11 = pane position 2,1)
            val inventory = player.openInventory.topInventory
            val bannerSlot = 11
            val bannerItem = inventory.getItem(bannerSlot)

            // Check if there's a banner in the slot
            if (bannerItem != null && bannerItem.type.name.endsWith("_BANNER")) {
                // Clone the banner to preserve its data
                val bannerToSave = bannerItem.clone()

                // Apply the banner (pass the entire ItemStack to preserve patterns)
                val success = guildService.setBanner(guild.id, bannerToSave, player.uniqueId)

                if (success) {
                    player.sendMessage("§a✅ Guild banner set to ${bannerToSave.type.name.lowercase().replace("_", " ")}!")

                    // Return the banner to player's inventory
                    val remaining = player.inventory.addItem(bannerToSave)
                    if (remaining.isNotEmpty()) {
                        // Inventory full, drop at feet
                        player.world.dropItem(player.location, bannerToSave)
                        player.sendMessage("§e📦 Banner dropped at your feet (inventory full)")
                    }

                    // Clear the slot and close menu
                    inventory.setItem(bannerSlot, ItemStack.of(Material.AIR))
                    player.closeInventory()
                } else {
                    player.sendMessage("§c❌ Failed to set banner. Check permissions.")
                }
            } else {
                player.sendMessage("§c❌ Place a banner in the slot first!")
            }
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name("§c⬅ BACK")
            .lore("§7Return to main menu")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
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

        val copyItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name("§e📋 GET BANNER COPY")
            .lore("§7Get a copy of your guild banner")

        if (bannerCopyFree) {
            copyItem.lore("§7Cost: §aFREE")
        } else if (useItemCost) {
            // Item-based cost
            try {
                val material = Material.valueOf(itemMaterial.uppercase())
                copyItem.lore("§7Cost: §6$itemAmount x ${material.name.lowercase().replace("_", " ")}")
                copyItem.lore("§7Taken from your inventory")
            } catch (e: IllegalArgumentException) {
                copyItem.lore("§c❌ Invalid item material configured")
            }
        } else {
            // Coin-based cost or physical currency
            if (chargeGuildBank && physicalCurrencyService.isPhysicalCurrencyEnabled()) {
                // Physical currency cost
                val physicalCost = config.bannerCopyPhysicalCost
                val materialName = physicalCurrencyService.getCurrencyMaterialName()
                copyItem.lore("§7Cost: §6$physicalCost x ${materialName.lowercase().replace("_", " ")}")
                copyItem.lore("§7Charged from: §6Guild Vault (Physical Currency)")
            } else {
                // Virtual economy cost
                copyItem.lore("§7Cost: §6$bannerCopyCost coins")
                copyItem.lore("§7Charged from: §6${if (chargeGuildBank) "Guild Bank" else "Personal Balance"}")

                // Add fee information to lore if charging guild bank
                if (chargeGuildBank) {
                    val fee = bankService.calculateWithdrawalFee(guild.id, bannerCopyCost)
                        if (fee > 0) {
                            val totalCostForDisplay = bannerCopyCost + fee
                            copyItem.lore("§7Total: §6$totalCostForDisplay coins §7(§6$fee§7 fee)")
                        }
                }
            }
        }

        val guiItem = GuiItem(copyItem) {
            // Check if guild has a banner and deserialize it
            val bannerData = guild.banner
            if (bannerData == null) {
                player.sendMessage("§c❌ Your guild doesn't have a banner set!")
                return@GuiItem
            }

            // Try to deserialize the banner
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem == null) {
                player.sendMessage("§c❌ Failed to load guild banner data!")
                return@GuiItem
            }

            // Check if player has permission
            if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)) {
                player.sendMessage("§c❌ You don't have permission to get banner copies!")
                return@GuiItem
            }

            val success = if (bannerCopyFree) {
                // Free banner copy - no payment needed
                true
            } else if (useItemCost) {
                // Item-based payment
                try {
                    val material = Material.valueOf(itemMaterial.uppercase())
                    val requiredItem = ItemStack.of(material, itemAmount)

                    // Apply custom model data if specified (for matching custom items from resource packs)
                    @Suppress("DEPRECATION")
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
                        player.sendMessage("§c❌ You don't have enough items! (Need: §6$itemAmount x ${material.name.lowercase().replace("_", " ")}§c)")
                        return@GuiItem
                    }

                    // Remove items from player inventory
                    playerInventory.removeItem(requiredItem)
                    player.sendMessage("§a✅ Paid §6$itemAmount x ${material.name.lowercase().replace("_", " ")} §afor banner copy!")

                    true
                } catch (e: IllegalArgumentException) {
                    player.sendMessage("§c❌ Invalid item material configured for banner cost!")
                    false
                }
            } else if (chargeGuildBank) {
                // Coin-based payment from guild bank or physical currency
                if (physicalCurrencyService.isPhysicalCurrencyEnabled()) {
                    // Use physical currency
                    val physicalCost = configService.loadConfig().guild.bannerCopyPhysicalCost
                    val currentBalance = physicalCurrencyService.calculateVaultCurrencyValue(guild)

                    if (currentBalance < physicalCost) {
                        player.sendMessage("§c❌ Guild vault has insufficient physical currency! (Need: §6$physicalCost§c, Have: §6$currentBalance§c)")
                        return@GuiItem
                    }

                    val deductSuccess = physicalCurrencyService.deductCurrency(guild, physicalCost, "Banner copy purchase")
                    if (!deductSuccess) {
                        player.sendMessage("§c❌ Failed to deduct physical currency from guild vault!")
                        return@GuiItem
                    }

                    true
                } else {
                    // Use virtual economy
                    val cost = bannerCopyCost
                    val guildBalance = bankService.getBalance(guild.id)
                    val fee = bankService.calculateWithdrawalFee(guild.id, cost)
                    val totalCost = cost + fee

                    if (guildBalance < totalCost) {
                        player.sendMessage("§c❌ Guild bank has insufficient funds! (Need: §6$totalCost§c, Have: §6$guildBalance§c)")
                        return@GuiItem
                    }

                    bankService.deductFromGuildBank(guild.id, totalCost, "Banner copy purchase")
                }
            } else {
                // Coin-based payment from player balance
                val cost = bannerCopyCost
                val playerBalance = bankService.getPlayerBalance(player.uniqueId)
                if (playerBalance < cost.toInt()) {
                    player.sendMessage("§c❌ You don't have enough coins! (Need: §6$cost§c, Have: §6$playerBalance§c)")
                    return@GuiItem
                }
                bankService.withdrawPlayer(player.uniqueId, cost, "Banner copy purchase")
            }

            if (!success) {
                player.sendMessage("§c❌ Failed to process payment!")
                return@GuiItem
            }

            // Give the banner to player
            val bannerCopy = bannerItem.clone()

            // Mark the banner with persistent data to prevent furnace fuel usage
            val meta = bannerCopy.itemMeta
            if (meta != null) {
                meta.persistentDataContainer.set(
                    PluginKeys.GUILD_BANNER_MARKER,
                    PersistentDataType.BYTE,
                    1.toByte()
                )
                bannerCopy.itemMeta = meta
            }

            val remaining = player.inventory.addItem(bannerCopy)

            if (remaining.isNotEmpty()) {
                // Inventory full, drop at feet
                player.world.dropItem(player.location, bannerCopy)
                player.sendMessage("§e📦 Banner dropped at your feet (inventory full)")
            }

            if (bannerCopyFree) {
                player.sendMessage("§a✅ Free banner copy received!")
            } else if (useItemCost) {
                // Item cost message already sent above
                player.sendMessage("§a✅ Banner copy received!")
            } else {
                val cost = bannerCopyCost
                player.sendMessage("§a✅ Banner copy purchased for §6$cost §acoins!")
            }
            player.sendMessage("§7💡 The banner has been added to your inventory")
        }

        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

