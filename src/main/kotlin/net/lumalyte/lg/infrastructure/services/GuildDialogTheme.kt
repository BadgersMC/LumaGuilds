package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.registry.data.dialog.body.DialogBody
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.utils.GuiTheme

private const val DIALOG_ART_FONT = "lumaguilds:dialogs"
private const val DIALOG_ART_WIDTH = 280
private val ART_PARTS = listOf("header", "divider", "footer", "marker")
private val DIALOG_ACCENTS =
    mapOf(
        GuiTheme.FROSTBOUND to NamedTextColor.AQUA,
        GuiTheme.VERDANT to NamedTextColor.GREEN,
        GuiTheme.VOIDLIGHT to NamedTextColor.LIGHT_PURPLE,
        GuiTheme.OBSIDIAN to NamedTextColor.GRAY,
        GuiTheme.CHRISTMAS to NamedTextColor.RED,
    )

/** Optional artwork is resolved as a complete snapshot; missing assets retain the plain UI. */
internal class GuildDialogTheme(
    private val resolver: NexoGlyphResolver = NexoGlyphResolver { NexoPublicGlyphResolver.resolve(it) },
) {
    fun resolve(theme: GuiTheme, packLoaded: Boolean): GuildDialogSkin? {
        val resolved = theme.resolved()
        if (!packLoaded || !resolved.hasBackground) return null
        val parts = parts(resolved) ?: return null
        return GuildDialogSkin(parts, guildDialogAccent(resolved))
    }

    private fun parts(theme: GuiTheme): Map<String, Component>? {
        val parts = mutableMapOf<String, Component>()
        for (part in ART_PARTS) {
            val id = "guild_dialog_${theme.name.lowercase()}_$part"
            parts[part] = glyph(id) ?: return null
        }
        parts["spacer"] = glyph("guild_dialog_spacer") ?: return null
        return parts
    }

    private fun glyph(id: String): Component? {
        val glyph = resolveOptionalGlyph(resolver, id) ?: return null
        if (!glyph.isDialogArt()) return null
        if (glyph.id != null && glyph.id != id) return null
        return Component.text(glyph.character, NamedTextColor.WHITE).font(Key.key(DIALOG_ART_FONT))
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
    }
}

private fun ResolvedNexoGlyph.isDialogArt(): Boolean {
    return !isEmoji && font == DIALOG_ART_FONT && character.codePointCount(0, character.length) == 1
}

/** Art occupies explicit native text lines and never changes the action hitboxes. */
internal class GuildDialogSkin(private val parts: Map<String, Component>, val accent: NamedTextColor) {
    fun body(content: List<DialogBody>): List<DialogBody> {
        return buildList {
            add(band("header", true))
            content.forEachIndexed { index, body ->
                if (index > 0) add(band("divider", false))
                add(body)
            }
            add(band("footer", true))
        }
    }

    fun label(text: Component): Component {
        return Component.empty().append(parts.getValue("marker")).append(Component.space())
            .append(text.colorIfAbsent(accent))
    }

    private fun band(name: String, reserveLine: Boolean): DialogBody {
        val glyph = parts.getValue(name)
        val text = if (reserveLine) glyph.append(Component.newline()).append(parts.getValue("spacer")) else glyph
        return DialogBody.plainMessage(text, DIALOG_ART_WIDTH)
    }
}

internal fun guildDialogAccent(theme: GuiTheme): NamedTextColor {
    return DIALOG_ACCENTS.getOrDefault(theme.resolved(), NamedTextColor.GOLD)
}
