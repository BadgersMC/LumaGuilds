package net.lumalyte.lg.infrastructure.services.hall

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.lumalyte.lg.domain.rewards.HallArmorMaterial
import net.lumalyte.lg.domain.rewards.HallArmorPart
import net.lumalyte.lg.domain.rewards.HallArmorPiece
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern

/** REQ-271: turns statue armor choices into items and copies a player's worn armor into choices. */
internal object HallArmorItems {
    private val pieceName = mapOf(
        HallArmorPart.HEAD to "HELMET", HallArmorPart.CHEST to "CHESTPLATE",
        HallArmorPart.LEGS to "LEGGINGS", HallArmorPart.FEET to "BOOTS",
    )
    val slots = mapOf(
        HallArmorPart.HEAD to EquipmentSlot.HEAD, HallArmorPart.CHEST to EquipmentSlot.CHEST,
        HallArmorPart.LEGS to EquipmentSlot.LEGS, HallArmorPart.FEET to EquipmentSlot.FEET,
    )

    fun material(part: HallArmorPart, material: HallArmorMaterial): Material =
        Material.matchMaterial("${material.name}_${pieceName.getValue(part)}") ?: Material.IRON_HELMET

    /** The statue's item for [part]: the guild's choice, or the tier default with no trim. */
    fun item(part: HallArmorPart, piece: HallArmorPiece?, tier: Int): ItemStack {
        val chosen = piece ?: HallArmorPiece(HallArmorMaterial.forTier(tier))
        val item = ItemStack.of(material(part, chosen.material))
        trim(chosen)?.let { trim -> item.editMeta(ArmorMeta::class.java) { it.trim = trim } }
        chosen.dye?.let { rgb -> item.editMeta(LeatherArmorMeta::class.java) { it.setColor(Color.fromRGB(rgb)) } }
        return item
    }

    fun trim(piece: HallArmorPiece): ArmorTrim? {
        val pattern = piece.trimPattern?.let { trimPattern(it) } ?: return null
        val material = piece.trimMaterial?.let { trimMaterial(it) } ?: return null
        return ArmorTrim(material, pattern)
    }

    fun trimPatterns(): List<String> = runCatching {
        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).keyStream().map { it.key }.sorted().toList()
    }.getOrDefault(emptyList())

    fun trimMaterials(): List<String> = runCatching {
        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).keyStream().map { it.key }.sorted().toList()
    }.getOrDefault(emptyList())

    private fun trimPattern(key: String): TrimPattern? = runCatching {
        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(NamespacedKey.minecraft(key))
    }.getOrNull()

    private fun trimMaterial(key: String): TrimMaterial? = runCatching {
        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(NamespacedKey.minecraft(key))
    }.getOrNull()

    /** The look of a worn item (material, vanilla trim, leather dye), or null for anything that isn't vanilla armor. */
    fun copy(part: HallArmorPart, worn: ItemStack?): HallArmorPiece? {
        if (worn == null || worn.type.isAir) return null
        val suffix = "_" + pieceName.getValue(part)
        val name = worn.type.name.takeIf { it.endsWith(suffix) }?.removeSuffix(suffix) ?: return null
        val material = HallArmorMaterial.parse(name) ?: return null
        val meta = worn.itemMeta
        val trim = (meta as? ArmorMeta)?.trim
        val pattern = trim?.let { RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).getKey(it.pattern) }
            ?.takeIf { it.namespace == NamespacedKey.MINECRAFT }?.key
        val trimMat = trim?.let { RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).getKey(it.material) }
            ?.takeIf { it.namespace == NamespacedKey.MINECRAFT }?.key
        val dye = (meta as? LeatherArmorMeta)?.color?.asRGB()?.takeIf { material.dyeable }
        return runCatching {
            if (pattern != null && trimMat != null) HallArmorPiece(material, pattern, trimMat, dye) else HallArmorPiece(material, dye = dye)
        }.getOrNull()
    }
}
