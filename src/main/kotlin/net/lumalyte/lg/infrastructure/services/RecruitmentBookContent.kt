package net.lumalyte.lg.infrastructure.services

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
internal fun RecruitmentBookContent.accent(): net.kyori.adventure.text.format.NamedTextColor =
    when (theme.resolved()) {
        net.lumalyte.lg.utils.GuiTheme.FROSTBOUND -> net.kyori.adventure.text.format.NamedTextColor.AQUA
        net.lumalyte.lg.utils.GuiTheme.VERDANT -> net.kyori.adventure.text.format.NamedTextColor.GREEN
        net.lumalyte.lg.utils.GuiTheme.VOIDLIGHT -> net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
        net.lumalyte.lg.utils.GuiTheme.OBSIDIAN -> net.kyori.adventure.text.format.NamedTextColor.GRAY
        net.lumalyte.lg.utils.GuiTheme.CHRISTMAS -> net.kyori.adventure.text.format.NamedTextColor.RED
        else -> net.kyori.adventure.text.format.NamedTextColor.GOLD
    }
