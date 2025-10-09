package net.lumalyte.lg.utils

import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.MenuItemConfig
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Utility class for building menu items from configuration with localization support.
 * Supports custom model data, materials, enchantments, and localized names/lore.
 */
class MenuItemBuilder(
    private val config: MainConfig,
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider
) {
    
    /**
     * Creates an ItemStack from a MenuItemConfig with localization.
     *
     * @param playerId The ID of the player for localization
     * @param itemConfig The configuration for the menu item
     * @param localizationKey Optional localization key to override the default name
     * @param loreKeys List of localization keys for lore lines
     * @param loreArgs Arguments for lore formatting
     * @return The configured ItemStack
     */
    fun createMenuItem(
        playerId: UUID,
        itemConfig: MenuItemConfig,
        localizationKey: String? = null,
        loreKeys: List<String> = emptyList(),
        vararg loreArgs: Any
    ): ItemStack {
        // Create base item
        val material = try {
            Material.valueOf(itemConfig.material.uppercase())
        } catch (e: IllegalArgumentException) {
            Material.STONE // Fallback material
        }
        
        // Use ItemStack extension functions to avoid deprecated methods
        var item = ItemStack(material)
        
        // Set display name using extension function (extension function now handles italic removal)
        val displayName = if (localizationKey != null) {
            localizationProvider.get(playerId, localizationKey)
        } else {
            itemConfig.name
        }
        item = item.name(displayName)
        
        // Set lore from localization keys using extension functions
        if (loreKeys.isNotEmpty()) {
            val loreLines = loreKeys.map { key ->
                "§7${localizationProvider.get(playerId, key, *loreArgs)}"
            }
            // Extension function will handle italic removal, but preserve existing color codes
            item = item.lore(loreLines)
        }
        
        // Set custom model data and enchantments manually
        val meta = item.itemMeta
        if (meta != null) {
            // Set custom model data if specified
            itemConfig.customModelData?.let { modelData ->
                @Suppress("DEPRECATION")
                meta.setCustomModelData(modelData)
            }
            
            // Add enchantment glow if specified
            if (itemConfig.enchanted) {
                meta.addEnchant(Enchantment.POWER, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
            
            item.itemMeta = meta
        }
        
        return item
    }
    
    /**
     * Creates a guild menu item using the configured guild menu item settings.
     */
    fun createGuildMenuItem(playerId: UUID, vararg loreArgs: Any): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.guildMenuItem,
            localizationKey = "menu.guild.item.info.name",
            loreKeys = listOf(
                "menu.guild.item.info.lore.name",
                "menu.guild.item.info.lore.level",
                "menu.guild.item.info.lore.members",
                "menu.guild.item.info.lore.balance",
                "menu.guild.item.info.lore.mode"
            ),
            loreArgs = loreArgs
        )
    }
    
    /**
     * Creates a bank menu item using the configured bank menu item settings.
     */
    fun createBankMenuItem(playerId: UUID, balance: Int): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.bankMenuItem,
            localizationKey = "menu.guild.item.bank.name",
            loreKeys = listOf("menu.guild.item.bank.lore", "menu.bank.item.balance.lore"),
            loreArgs = arrayOf(balance)
        )
    }
    
    /**
     * Creates a navigation back button.
     */
    fun createBackButton(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.backButton,
            localizationKey = "menu.common.item.back.name"
        )
    }
    
    /**
     * Creates a navigation next button.
     */
    fun createNextButton(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.nextButton,
            localizationKey = "menu.common.item.next.name"
        )
    }
    
    /**
     * Creates a confirmation button.
     */
    fun createConfirmButton(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.confirmButton,
            localizationKey = "menu.common.item.confirm.name"
        )
    }
    
    /**
     * Creates a cancel button.
     */
    fun createCancelButton(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.cancelButton,
            localizationKey = "menu.common.item.cancel.name"
        )
    }
    
    /**
     * Creates a close button.
     */
    fun createCloseButton(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.closeButton,
            localizationKey = "menu.common.item.close.name"
        )
    }
    
    /**
     * Creates an online status indicator.
     */
    fun createOnlineIndicator(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.onlineIndicator,
            localizationKey = "menu.members.item.member.lore.online"
        )
    }
    
    /**
     * Creates an offline status indicator.
     */
    fun createOfflineIndicator(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.offlineIndicator,
            localizationKey = "menu.members.item.member.lore.offline"
        )
    }
    
    /**
     * Creates a peaceful mode indicator.
     */
    fun createPeacefulModeIndicator(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.peacefulModeIndicator,
            localizationKey = "guild.mode.peaceful"
        )
    }
    
    /**
     * Creates a hostile mode indicator.
     */
    fun createHostileModeIndicator(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.hostileModeIndicator,
            localizationKey = "guild.mode.hostile"
        )
    }
    
    /**
     * Creates a rank icon based on the rank name.
     */
    fun createRankIcon(playerId: UUID, rankName: String, playerName: String): ItemStack {
        val itemConfig = when (rankName.lowercase()) {
            "owner" -> config.ui.ownerIcon
            "co-owner" -> config.ui.coOwnerIcon
            "admin" -> config.ui.adminIcon
            "mod" -> config.ui.modIcon
            else -> config.ui.memberIcon
        }
        
        return createMenuItem(
            playerId = playerId,
            itemConfig = itemConfig,
            loreKeys = listOf("menu.members.item.member.lore.rank"),
            loreArgs = arrayOf(rankName)
        ).also { item ->
            // Set player name as display name for member icons (need to modify the returned item)
        }.let { item ->
            if (itemConfig == config.ui.memberIcon) {
                item.name("§r$playerName")
            } else {
                item
            }
        }
    }
    
    /**
     * Creates a relations menu item.
     */
    fun createRelationsMenuItem(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.relationMenuItem,
            localizationKey = "menu.guild.item.relations.name",
            loreKeys = listOf("menu.guild.item.relations.lore")
        )
    }
    
    /**
     * Creates a wars menu item.
     */
    fun createWarsMenuItem(playerId: UUID, activeWars: Int): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.warMenuItem,
            localizationKey = "menu.guild.item.wars.name",
            loreKeys = listOf("menu.guild.item.wars.lore"),
            loreArgs = arrayOf(activeWars)
        )
    }
    
    /**
     * Creates a mode toggle menu item.
     */
    fun createModeMenuItem(playerId: UUID, currentMode: String): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.modeMenuItem,
            localizationKey = "menu.settings.item.mode.name",
            loreKeys = listOf("menu.settings.item.mode.lore.$currentMode")
        )
    }
    
    /**
     * Creates a home menu item.
     */
    fun createHomeMenuItem(playerId: UUID, hasHome: Boolean): ItemStack {
        val loreKey = if (hasHome) "menu.guild.item.home.lore" else "guild.home.not_set"
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.homeMenuItem,
            localizationKey = "menu.guild.item.home.name",
            loreKeys = listOf(loreKey)
        )
    }
    
    /**
     * Creates a leaderboard menu item.
     */
    fun createLeaderboardMenuItem(playerId: UUID, leaderboardType: String): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.leaderboardMenuItem,
            localizationKey = "menu.leaderboards.item.$leaderboardType.name"
        )
    }
    
    /**
     * Creates a chat menu item.
     */
    fun createChatMenuItem(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.chatMenuItem,
            localizationKey = "menu.guild.item.chat.name"
        )
    }
    
    /**
     * Creates a party menu item.
     */
    fun createPartyMenuItem(playerId: UUID): ItemStack {
        return createMenuItem(
            playerId = playerId,
            itemConfig = config.ui.partyMenuItem,
            localizationKey = "menu.guild.item.party.name"
        )
    }
}
