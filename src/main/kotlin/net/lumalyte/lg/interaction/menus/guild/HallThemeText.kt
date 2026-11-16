package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.infrastructure.i18n.gui
import org.bukkit.Material

/** REQ-260: shared wording and icons for Hall themes on Java and Bedrock. */
internal object HallThemeText {
    fun icon(theme: HallTheme): Material = when (theme) {
        HallTheme.GILDED -> Material.TOTEM_OF_UNDYING
        HallTheme.EMBER -> Material.FIRE_CHARGE
        HallTheme.WINGED -> Material.ELYTRA
        HallTheme.TIDAL -> Material.HEART_OF_THE_SEA
        HallTheme.RADIANT -> Material.GLOWSTONE_DUST
        HallTheme.CROWNED -> Material.NETHER_STAR
    }

    fun cosmeticLines(lang: LangService, theme: HallTheme): List<Component> =
        listOf(lang.gui("hall.pick.intro")) + HallSlot.entries.map { slot ->
            lang.gui("hall.pick.line", "slot" to slot.label, "value" to theme.cosmetic(slot))
        }

    fun bedrockCosmetics(lang: LangService, theme: HallTheme): String =
        HallSlot.entries.joinToString(", ") { theme.cosmetic(it) }.let {
            lang.bedrock("hall.pick.bedrock_cosmetics", "list" to it)
        }
}
