package net.lumalyte.lg.utils

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import net.lumalyte.lg.domain.values.Flag
import java.util.UUID

/**
 * Associates claim flags with a specific in-game item.
 *
 * @return ItemStack of the associated item for the given flag enum.
 */
fun Flag.getIcon(localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider, playerId: UUID): ItemStack {
    var item = when (this) {
        Flag.EXPLOSION -> ItemStack.of(Material.TNT)
        Flag.FIRE -> ItemStack.of(Material.FLINT_AND_STEEL)
        Flag.MOB -> ItemStack.of(Material.CREEPER_HEAD)
        Flag.PISTON -> ItemStack.of(Material.PISTON)
        Flag.FLUID -> ItemStack.of(Material.WATER_BUCKET)
        Flag.TREE -> ItemStack.of(Material.OAK_SAPLING)
        Flag.SCULK -> ItemStack.of(Material.SCULK_CATALYST)
        Flag.DISPENSER -> ItemStack.of(Material.DISPENSER)
        Flag.SPONGE -> ItemStack.of(Material.SPONGE)
        Flag.LIGHTNING -> ItemStack.of(Material.LIGHTNING_ROD)
        Flag.FALLING_BLOCK -> ItemStack.of(Material.ANVIL)
        Flag.PASSIVE_ENTITY_VEHICLE -> ItemStack.of(Material.OAK_BOAT)
    }

    // Get localized name and lore using the keys from the domain enum
    item = item.name(localizationProvider.get(playerId, this.nameKey))
    item = item.lore(localizationProvider.get(playerId, this.loreKey))
    return item
}
