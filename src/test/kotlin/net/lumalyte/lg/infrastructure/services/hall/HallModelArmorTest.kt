package net.lumalyte.lg.infrastructure.services.hall

import net.lumalyte.lg.domain.rewards.HallArmorMaterial
import net.lumalyte.lg.domain.rewards.HallArmorPiece
import kotlin.test.Test
import kotlin.test.assertEquals

/** REQ-296: statue armor choices map onto BetterModel's vanilla equipment texture names. */
class HallModelArmorTest {
    @Test
    fun `types are the vanilla equipment texture names`() {
        val expected = mapOf(
            HallArmorMaterial.LEATHER to "leather", HallArmorMaterial.CHAINMAIL to "chainmail",
            HallArmorMaterial.COPPER to "copper", HallArmorMaterial.IRON to "iron", HallArmorMaterial.GOLDEN to "gold",
            HallArmorMaterial.DIAMOND to "diamond", HallArmorMaterial.NETHERITE to "netherite",
        )
        HallArmorMaterial.entries.forEach { assertEquals(expected.getValue(it), HallModelArmor.type(it)) }
    }

    @Test
    fun `undyed armor is white and leather keeps its dye or vanilla brown`() {
        assertEquals(HallModelArmor.WHITE, HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.DIAMOND)).tint)
        assertEquals(HallModelArmor.LEATHER, HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.LEATHER)).tint)
        assertEquals(0x123456, HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.LEATHER, dye = 0x123456)).tint)
    }

    @Test
    fun `trims carry pattern and palette, darker when they match the armor`() {
        val plain = HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.IRON))
        assertEquals(null, plain.trim); assertEquals(null, plain.palette)
        val coast = HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.IRON, "coast", "gold"))
        assertEquals("coast", coast.trim); assertEquals("gold", coast.palette)
        val same = HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.GOLDEN, "wild", "gold"))
        assertEquals("gold_darker", same.palette)
        val amethyst = HallModelArmor.layer(HallArmorPiece(HallArmorMaterial.DIAMOND, "eye", "amethyst"))
        assertEquals("amethyst", amethyst.palette)
    }
}
