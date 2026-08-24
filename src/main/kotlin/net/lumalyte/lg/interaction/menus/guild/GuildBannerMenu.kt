package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
import org.bukkit.inventory.Inventory

class GuildBannerMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val bankService: BankService by inject()
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    companion object {
        // Maps player UUID -> the specific Inventory instance of their currently-open
        // GuildBannerMenu. Used by BannerSelectionListener to verify clicks are hitting
        // THIS menu and not some other chest a different plugin opened.
        val activeMenus: MutableMap<UUID, Inventory> = ConcurrentHashMap()
    }

    override fun open() {
        // Clean up any stale tracking for this player before opening a new instance.
        // A player can only have one inventory open at a time; replaces stale entries.
        activeMenus.remove(player.uniqueId)

        // Create a 3x9 GUI for banner selection
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.legacy("menu.guild_banner.title", "guild" to guild.name)))
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

        // Record the specific inventory instance so BannerSelectionListener can
        // distinguish THIS menu from any other chest the player might open.
        activeMenus[player.uniqueId] = gui.getInventory()
    }

    private fun addCurrentBannerDisplay(pane: StaticPane, x: Int, y: Int) {
        val currentItem = guild.banner?.let { bannerData ->
            // Try to deserialize the banner ItemStack
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem != null) {
                bannerItem.clone()
                    .name(lang.legacy("menu.guild_banner.current.name"))
                    .lore(lang.legacy("menu.guild_banner.current.description"))
            } else {
                // Fallback to white banner if deserialization fails
                ItemStack.of(Material.WHITE_BANNER)
                    .name(lang.legacy("menu.guild_banner.current.error"))
                    .lore(lang.legacy("menu.guild_banner.current.load_failed"))
                    .lore(lang.legacy("menu.guild_banner.current.contact"))
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)
            .name(lang.legacy("menu.guild_banner.current.none"))
            .lore(lang.legacy("menu.guild_banner.current.not_configured"))

        pane.addItem(GuiItem(currentItem), x, y)
    }

    private fun addVisualBorder(pane: StaticPane) {
        val borderItem = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE)
            .name(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_banner.slot.place"))
            .lore(lang.legacy("menu.guild_banner.slot.set"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_banner.slot.supported"))

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
            .name(lang.legacy("menu.guild_banner.slot.name"))
            .lore(lang.legacy("menu.guild_banner.slot.description"))

        val guiItem = GuiItem(placeholderItem)

        pane.addItem(guiItem, x, y)
    }

    private fun addClearBannerButton(pane: StaticPane, x: Int, y: Int) {
        val hasBanner = guild.banner != null
        val clearItem = ItemStack.of(if (hasBanner) Material.BARRIER else Material.GRAY_DYE)
            .name(if (hasBanner) lang.legacy("menu.guild_banner.clear.name") else lang.legacy("menu.guild_banner.clear.disabled"))
            .lore(if (hasBanner) {
                listOf(
                    lang.legacy("menu.guild_banner.clear.description"),
                    lang.legacy("menu.guild_banner.clear.fallback"),
                    lang.legacy("menu.guild_banner.clear.warning")
                )
            } else {
                listOf(lang.legacy("menu.guild_banner.clear.none"))
            })

        val guiItem = GuiItem(clearItem) {
            if (hasBanner) {
                val success = guildService.setBanner(guild.id, null, player.uniqueId)
                if (success) {
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.cleared"))
                    // Refresh guild data and reopen menu
                    guild = guildService.getGuild(guild.id) ?: guild
                    open()
                } else {
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.clear_failed"))
                }
            } else {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.no_banner_clear"))
            }
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addApplyChangesButton(pane: StaticPane, x: Int, y: Int) {
        val applyItem = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.legacy("menu.guild_banner.apply.name"))
            .lore(lang.legacy("menu.guild_banner.apply.place"))
            .lore(lang.legacy("menu.guild_banner.apply.click"))

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
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.set", "banner" to bannerToSave.type.name.lowercase().replace("_", " ")))

                    // Return the banner to player's inventory
                    val remaining = player.inventory.addItem(bannerToSave)
                    if (remaining.isNotEmpty()) {
                        // Inventory full, drop at feet
                        player.world.dropItem(player.location, bannerToSave)
                        player.sendMessage(lang.msg("menu.guild_banner.feedback.dropped"))
                    }

                    // Clear the slot and close menu
                    inventory.setItem(bannerSlot, ItemStack.of(Material.AIR))
                    player.closeInventory()
                } else {
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.set_failed"))
                }
            } else {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.place_first"))
            }
        }

        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.guild_banner.back.name"))
            .lore(lang.legacy("menu.guild_banner.back.description"))

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
            .name(lang.legacy("menu.guild_banner.copy.name"))
            .lore(lang.legacy("menu.guild_banner.copy.description"))

        if (bannerCopyFree) {
            copyItem.lore(lang.legacy("menu.guild_banner.copy.free"))
        } else if (useItemCost) {
            // Item-based cost
            try {
                val material = Material.valueOf(itemMaterial.uppercase())
                copyItem.lore(lang.legacy("menu.guild_banner.copy.item_cost", "amount" to itemAmount, "material" to material.name.lowercase().replace("_", " ")))
                copyItem.lore(lang.legacy("menu.guild_banner.copy.inventory"))
            } catch (e: IllegalArgumentException) {
                copyItem.lore(lang.legacy("menu.guild_banner.copy.invalid_material"))
            }
        } else {
            // Coin-based cost or physical currency
            if (chargeGuildBank && physicalCurrencyService.isPhysicalCurrencyEnabled()) {
                // Physical currency cost
                val physicalCost = config.bannerCopyPhysicalCost
                val materialName = physicalCurrencyService.getCurrencyMaterialName()
                copyItem.lore(lang.legacy("menu.guild_banner.copy.item_cost", "amount" to physicalCost, "material" to materialName.lowercase().replace("_", " ")))
                copyItem.lore(lang.legacy("menu.guild_banner.copy.guild_vault"))
            } else {
                // Virtual economy cost
                copyItem.lore(lang.legacy("menu.guild_banner.copy.coin_cost", "amount" to bannerCopyCost))
                copyItem.lore(if (chargeGuildBank) lang.legacy("menu.guild_banner.copy.guild_bank") else lang.legacy("menu.guild_banner.copy.personal"))

                // Add fee information to lore if charging guild bank
                if (chargeGuildBank) {
                    val fee = bankService.calculateWithdrawalFee(guild.id, bannerCopyCost)
                        if (fee > 0) {
                            val totalCostForDisplay = bannerCopyCost + fee
                            copyItem.lore(lang.legacy("menu.guild_banner.copy.total", "total" to totalCostForDisplay, "fee" to fee))
                        }
                }
            }
        }

        val guiItem = GuiItem(copyItem) {
            // Check if guild has a banner and deserialize it
            val bannerData = guild.banner
            if (bannerData == null) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.no_banner"))
                return@GuiItem
            }

            // Try to deserialize the banner
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem == null) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.load_failed"))
                return@GuiItem
            }

            // Check if player has permission
            if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.no_copy_permission"))
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
                        player.sendMessage(lang.msg("menu.guild_banner.feedback.insufficient_items", "amount" to itemAmount, "material" to material.name.lowercase().replace("_", " ")))
                        return@GuiItem
                    }

                    // Remove items from player inventory
                    playerInventory.removeItem(requiredItem)
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.paid_items", "amount" to itemAmount, "material" to material.name.lowercase().replace("_", " ")))

                    true
                } catch (e: IllegalArgumentException) {
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.invalid_material"))
                    false
                }
            } else if (chargeGuildBank) {
                // Coin-based payment from guild bank or physical currency
                if (physicalCurrencyService.isPhysicalCurrencyEnabled()) {
                    // Use physical currency
                    val physicalCost = configService.loadConfig().guild.bannerCopyPhysicalCost
                    val currentBalance = physicalCurrencyService.calculateVaultCurrencyValue(guild)

                    if (currentBalance < physicalCost) {
                        player.sendMessage(lang.msg("menu.guild_banner.feedback.insufficient_vault", "need" to physicalCost, "have" to currentBalance))
                        return@GuiItem
                    }

                    val deductSuccess = physicalCurrencyService.deductCurrency(guild, physicalCost, "Banner copy purchase")
                    if (!deductSuccess) {
                        player.sendMessage(lang.msg("menu.guild_banner.feedback.vault_deduct_failed"))
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
                        player.sendMessage(lang.msg("menu.guild_banner.feedback.insufficient_bank", "need" to totalCost, "have" to guildBalance))
                        return@GuiItem
                    }

                    bankService.deductFromGuildBank(guild.id, totalCost, "Banner copy purchase")
                }
            } else {
                // Coin-based payment from player balance
                val cost = bannerCopyCost
                val playerBalance = bankService.getPlayerBalance(player.uniqueId)
                if (playerBalance < cost.toInt()) {
                    player.sendMessage(lang.msg("menu.guild_banner.feedback.insufficient_coins", "need" to cost, "have" to playerBalance))
                    return@GuiItem
                }
                bankService.withdrawPlayer(player.uniqueId, cost, "Banner copy purchase")
            }

            if (!success) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.payment_failed"))
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
                player.sendMessage(lang.msg("menu.guild_banner.feedback.dropped"))
            }

            if (bannerCopyFree) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.free_copy"))
            } else if (useItemCost) {
                // Item cost message already sent above
                player.sendMessage(lang.msg("menu.guild_banner.feedback.copy_received"))
            } else {
                val cost = bannerCopyCost
                player.sendMessage(lang.msg("menu.guild_banner.feedback.copy_purchased", "amount" to cost))
            }
            player.sendMessage(lang.msg("menu.guild_banner.feedback.added"))
        }

        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

