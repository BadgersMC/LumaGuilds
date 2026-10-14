package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.utils.GuiTheme

/** Read-only details obtained after actor authorization; no decision or payment actions. */
internal data class RecruitmentBookContent(
    val title: String,
    val lines: List<String>,
    val returnLabel: String,
    val theme: net.lumalyte.lg.utils.GuiTheme = net.lumalyte.lg.utils.GuiTheme.DEFAULT,
    val guild: net.lumalyte.lg.domain.entities.Guild? = null,
    val context: String = "",
)

/** Native titles can use the selected palette, although the client owns the dialog frame. */
internal fun RecruitmentBookContent.accent(): NamedTextColor = when (theme.resolved()) {
    GuiTheme.FROSTBOUND -> NamedTextColor.AQUA
    GuiTheme.VERDANT -> NamedTextColor.GREEN
    GuiTheme.VOIDLIGHT -> NamedTextColor.LIGHT_PURPLE
    GuiTheme.OBSIDIAN -> NamedTextColor.GRAY
    GuiTheme.CHRISTMAS -> NamedTextColor.RED
    else -> NamedTextColor.GOLD
}
