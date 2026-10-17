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
    fun resolve(theme: GuiTheme): GuildDialogSkin? {
        val resolved = theme.resolved()
        return if (resolved.hasBackground) {
            parts(resolved)?.let {
                GuildDialogSkin(it, guildDialogAccent(resolved)) {
                    GuildDialogCards.resolve(resolver, resolved.name.lowercase())
                }
            }
        } else {
            null
        }
    }

    private fun parts(theme: GuiTheme): Map<String, Component>? {
        val parts = mutableMapOf<String, Component>()
        for (part in ART_PARTS) {
            val id = "guild_dialog_${theme.name.lowercase()}_$part"
            parts[part] = glyph(id) ?: return null
        }
        glyph("guild_dialog_${theme.name.lowercase()}_reference_header")?.let { parts["reference_header"] = it }
        return glyph("guild_dialog_spacer")?.let {
            parts["spacer"] = it
            parts
        }
    }

    private fun glyph(id: String): Component? {
        val glyph = resolveOptionalGlyph(resolver, id)
        return if (glyph != null && glyph.isDialogArt() && glyph.matchesId(id)) {
            Component.text(glyph.character, NamedTextColor.WHITE).font(Key.key(DIALOG_ART_FONT))
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
        } else {
            null
        }
    }
}

internal fun ResolvedNexoGlyph.isDialogArt(): Boolean =
    !isEmoji && font == DIALOG_ART_FONT && character.codePointCount(0, character.length) == 1

internal fun ResolvedNexoGlyph.matchesId(expected: String): Boolean = id == null || id == expected

/** Art occupies explicit native text lines and never changes the action hitboxes. */
internal class GuildDialogSkin(
    private val parts: Map<String, Component>,
    val accent: NamedTextColor,
    cards: () -> GuildDialogCards? = { null },
) {
    private val cardArt by lazy(cards)

    fun card(lines: List<Component>): Component? = cardArt?.render(lines)

    fun columns(left: List<Component>, right: List<Component>): Component? = cardArt?.renderColumns(left, right)

    fun referenceFrame(title: Component, subtitle: Component): Component? {
        val art = parts["reference_header"] ?: return null
        val rows = (listOf(title.colorIfAbsent(accent), subtitle)).flatMap {
            GuildDialogText.rows(it, 498) ?: return null
        }
        // Purpose-built header is 256x36. Longer localized text uses the native heading.
        if (rows.size > 2) return null
        val spacing = Key.key("lumaguilds:dialog_spacing_v2")
        fun pad(value: Int): Component = Component.text(buildString {
            for (bit in 0..9) if (value and (1 shl bit) != 0) append((0xE010 + bit).toChar())
        }, NamedTextColor.WHITE).font(spacing)
        val result = Component.text().append(art).append(Component.newline())
        for ((line, width) in rows) {
            val left = (514 - width) / 2
            result.append(pad(left)).append(line).append(pad(514 - width - left)).append(Component.newline())
        }
        return result.append(pad(514)).build()
    }

    fun referenceBody(
        @Suppress("UNUSED_PARAMETER") title: Component,
        subtitle: Component,
        content: List<DialogBody>,
    ): List<DialogBody> {
        // The native dialog already renders its title. Avoid a duplicate framed
        // heading and divider, which displace the useful content far below it.
        return if (subtitle == Component.empty()) {
            content
        } else {
            listOf(DialogBody.plainMessage(subtitle, DIALOG_ART_WIDTH)) + content
        }
    }

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
        return text.colorIfAbsent(accent)
    }

    private fun band(
        name: String,
        reserveLine: Boolean,
    ): DialogBody {
        val glyph = parts.getValue(name)
        val text = if (reserveLine) glyph.append(Component.newline()).append(parts.getValue("spacer")) else glyph
        return DialogBody.plainMessage(text, DIALOG_ART_WIDTH)
    }
}

internal fun guildDialogAccent(theme: GuiTheme): NamedTextColor =
    DIALOG_ACCENTS.getOrDefault(theme.resolved(), NamedTextColor.GOLD)
