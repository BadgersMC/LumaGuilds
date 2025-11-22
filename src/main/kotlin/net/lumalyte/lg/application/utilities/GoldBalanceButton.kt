package net.lumalyte.lg.application.utilities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.application.services.ConfigService
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Utility class for creating and managing the Gold Balance Button.
 * This special item is displayed in slot 0 of guild vaults and shows the currency balance.
 *
 * The currency material is configured via vault.physical_currency_material (defaults to RAW_GOLD).
 * Uses a 1:1 ratio for simplicity.
 */
object GoldBalanceButton : KoinComponent {

    private var plugin: JavaPlugin? = null
    private var goldButtonKey: NamespacedKey? = null
    private val configService: ConfigService by inject()

    /**
     * Initializes the GoldBalanceButton with the plugin instance.
     * Must be called during plugin startup.
     *
     * @param pluginInstance The plugin instance.
     */
    fun initialize(pluginInstance: JavaPlugin) {
        plugin = pluginInstance
        goldButtonKey = NamespacedKey(pluginInstance, "guild_gold_balance")
    }

    /**
     * Gets the plugin instance, throwing an exception if not initialized.
     */
    private fun getPlugin(): JavaPlugin {
        return plugin ?: error("GoldBalanceButton not initialized - call initialize() first")
    }

    /**
     * Gets the gold button key, throwing an exception if not initialized.
     */
    private fun getGoldButtonKey(): NamespacedKey {
        return goldButtonKey ?: error("GoldBalanceButton not initialized - call initialize() first")
    }

    /**
     * Gets the configured currency material from config.
     */
    private fun getCurrencyMaterial(): Material {
        val config = configService.loadConfig()
        val materialName = config.vault.physicalCurrencyMaterial
        return try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            getPlugin().logger.warning("Invalid physical_currency_material '${materialName}' in config, defaulting to RAW_GOLD")
            Material.RAW_GOLD
        }
    }

    /**
     * Gets a friendly name for the currency material.
     */
    private fun getCurrencyName(): String {
        val material = getCurrencyMaterial()
        // Convert ENUM_NAME to "Enum Name"
        return material.name.split("_")
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    /**
     * Creates the Gold Balance Button ItemStack with the current balance displayed.
     *
     * @param balance The total currency balance.
     * @return The Gold Balance Button ItemStack.
     */
    fun createItem(balance: Long): ItemStack {
        val currencyMaterial = getCurrencyMaterial()
        val currencyName = getCurrencyName()

        val item = ItemStack(currencyMaterial, 1)
        val meta = item.itemMeta ?: return item

        // Set display name
        meta.displayName(
            Component.text("💰 Guild Currency", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Set lore with formatted balance
        val lore = mutableListOf<Component>()

        lore.add(Component.empty())
        lore.add(
            Component.text("Current Balance:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        if (balance > 0) {
            lore.add(
                Component.text("  ⭐ $balance $currencyName", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
            )
        } else {
            lore.add(
                Component.text("  No currency stored", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        }

        lore.add(Component.empty())
        lore.add(
            Component.text("Left Click", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" to deposit $currencyName", NamedTextColor.GRAY))
        )
        lore.add(
            Component.text("Right Click", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" to withdraw $currencyName", NamedTextColor.GRAY))
        )
        lore.add(
            Component.text("Shift + Left Click", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" to deposit all", NamedTextColor.GRAY))
        )
        lore.add(Component.empty())
        lore.add(
            Component.text("⚠ Only $currencyName accepted", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(Component.empty())
        lore.add(
            Component.text("This item cannot be removed", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true)
        )

        meta.lore(lore)

        // Mark with PDC to identify as gold button
        meta.persistentDataContainer.set(getGoldButtonKey(), PersistentDataType.BYTE, 1.toByte())

        item.itemMeta = meta
        return item
    }

    /**
     * Checks if an ItemStack is a Gold Balance Button.
     *
     * @param item The item to check (can be null).
     * @return true if the item is a Gold Balance Button.
     */
    fun isGoldButton(item: ItemStack?): Boolean {
        if (item == null) return false

        val currencyMaterial = getCurrencyMaterial()
        if (item.type != currencyMaterial) {
            return false
        }

        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(getGoldButtonKey(), PersistentDataType.BYTE)
    }

    /**
     * Formats a currency balance for display.
     * Since we use 1:1 ratio, this just returns the amount.
     *
     * @param balance The total balance.
     * @return The balance as a Long.
     */
    fun formatGoldBalance(balance: Long): Long {
        return balance
    }

    /**
     * Calculates the total currency value of an ItemStack.
     * ONLY accepts the configured currency material (1:1 ratio).
     *
     * @param item The ItemStack to evaluate.
     * @return The total value (amount), or 0 if not the correct currency.
     */
    fun calculateGoldValue(item: ItemStack?): Long {
        if (item == null || item.type == Material.AIR) return 0L

        val currencyMaterial = getCurrencyMaterial()

        // ONLY accept configured currency material
        return if (item.type == currencyMaterial) {
            item.amount.toLong()
        } else {
            0L
        }
    }

    /**
     * Converts a balance amount to ItemStacks of the configured currency.
     * Creates as many stacks as needed (max 64 per stack).
     *
     * @param balance The total amount.
     * @return List of currency ItemStacks.
     */
    fun convertToItems(balance: Long): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        var remaining = balance
        val currencyMaterial = getCurrencyMaterial()

        // Create currency stacks (max stack size 64)
        while (remaining > 0) {
            val stackSize = minOf(remaining, 64)
            items.add(ItemStack(currencyMaterial, stackSize.toInt()))
            remaining -= stackSize
        }

        return items
    }
}
