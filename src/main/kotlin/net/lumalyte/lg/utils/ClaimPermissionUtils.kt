package net.lumalyte.lg.utils

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

/**
 * Associates claim permissions with a specific in-game item.
 *
 * @return ItemStack of the associated item for the given permission enum.
 */
fun ClaimPermission.getIcon(localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider, playerId: UUID): ItemStack {
    var item = when (this) {
        ClaimPermission.BUILD -> ItemStack.of(Material.DIAMOND_PICKAXE)
        ClaimPermission.HARVEST -> ItemStack.of(Material.WHEAT)
        ClaimPermission.CONTAINER -> ItemStack.of(Material.CHEST)
        ClaimPermission.DISPLAY -> ItemStack.of(Material.ARMOR_STAND)
        ClaimPermission.VEHICLE -> ItemStack.of(Material.MINECART)
        ClaimPermission.SIGN -> ItemStack.of(Material.OAK_SIGN)
        ClaimPermission.REDSTONE -> ItemStack.of(Material.LEVER)
        ClaimPermission.DOOR -> ItemStack.of(Material.ACACIA_DOOR)
        ClaimPermission.TRADE -> ItemStack.of(Material.EMERALD)
        ClaimPermission.HUSBANDRY -> ItemStack.of(Material.LEAD)
        ClaimPermission.DETONATE -> ItemStack.of(Material.TNT)
        ClaimPermission.EVENT -> ItemStack.of(Material.OMINOUS_BOTTLE)
        ClaimPermission.SLEEP -> ItemStack.of(Material.RED_BED)
        ClaimPermission.VIEW -> ItemStack.of(Material.LECTERN)
    }

    // Get localized name and lore using the keys from the domain enum
    item = item.name(localizationProvider.get(playerId, this.nameKey))
    item = item.lore(localizationProvider.get(playerId, this.loreKey))
    return item
}
