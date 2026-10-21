package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.ShadowColor
import net.lumalyte.lg.utils.GuiTheme
import kotlin.math.ceil

/** Decorative underlay only. Existing body widgets, banners and callbacks remain native. */
internal class GuildDialogSurface private constructor(theme: GuiTheme, private val version: Int) {
    private val font = Key.key("lumaguilds:dialog_surface_${theme.name.lowercase()}" + if (version == 2) "_v2" else "")
    private fun glyph(code: Int): Component = Component.text(code.toChar(), NamedTextColor.WHITE).font(font)
        .shadowColor(ShadowColor.none())
        .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
    private fun pad(pixels: Int): Component = Component.text(buildString {
        for (bit in 0..10) if (pixels and (1 shl bit) != 0) append((0xE010 + bit).toChar())
    }, NamedTextColor.WHITE).font(font)

    /** Null means exact native layout cannot be established; never guess over input controls. */
    fun heading(title: Component, subtitle: Component, content: List<DialogBody>): Component? {
        val rows = listOf(title.colorIfAbsent(guildDialogAccent(theme)), subtitle)
            .filterNot { it == Component.empty() }
            .flatMap { GuildDialogText.rows(it, 700) ?: return null }
        val headingRows = rows.size + 2
        val heights = content.map { bodyHeight(it) ?: return null }
        val total = headingRows * 9 + 8 + heights.sum() + heights.size * 10 - 4
        val count = stripCount(total) ?: return null
        if (headingRows > count) return null
        val result = Component.text()
        for (index in 0 until count) {
            val code = when (index) {
                0 -> 0xE400
                headingRows - 1 -> 0xE300 + index
                in 1 until headingRows -> 0xE200 + index
                else -> 0xE100 + index
            }
            // 180px cells fit the vanilla 256px atlas. A -1px seam correction
            // preserves the original 361px advance and all native widget positions.
            result.append(glyph(code))
            if (version == 2) result.append(glyph(0xE002)).append(glyph(code + 0x800))
            result.append(glyph(0xE000))
        }
        result.append(pad(361)).append(Component.newline())
        for ((line, halfWidth) in rows) {
            val width = ceil(halfWidth / 2.0).toInt()
            val left = (361 - width) / 2
            result.append(pad(left)).append(line).append(pad(361 - width - left)).append(Component.newline())
        }
        return result.append(pad(361)).build()
    }

    companion object {
        const val WIDTH = 369 // 361px advance plus the native 4px inset on each side.
        fun stripCount(height: Int): Int? = ((height + 8) / 9).takeIf { height > 0 && it in 1..128 }

        fun resolve(theme: GuiTheme, resolver: NexoGlyphResolver = NexoPublicGlyphResolver): GuildDialogSurface? {
            val selected = theme.resolved()
            if (!selected.hasBackground) return null
            for (version in listOf(2, 1)) {
                val id = "guild_dialog_surface_${selected.name.lowercase()}_v$version"
                val marker = resolveOptionalGlyph(resolver, id) ?: continue
                if (marker.matchesId(id) && marker.font == "lumaguilds:dialog_surface_contract" &&
                    !marker.isEmoji && marker.character.codePointCount(0, marker.character.length) == 1
                ) return GuildDialogSurface(selected, version)
            }
            return null
        }

        fun bodyHeight(body: DialogBody): Int? = when (body) {
            is PlainMessageDialogBody -> textHeight(body)
            is ItemDialogBody -> {
                val description = body.description()
                if (description == null) body.height() else textHeight(description)?.coerceAtLeast(body.height())
            }
            else -> null
        }

        private fun textHeight(body: PlainMessageDialogBody): Int? {
            val rows = GuildDialogText.rows(body.contents(), (body.width() - 8) * 2) { font, code ->
                when (font) {
                    "lumaguilds:dialogs" -> 514 // shared 256px card/header bitmap plus 1px advance
                    "lumaguilds:dialog_icons" -> 22
                    "lumaguilds:dialog_spacing_v2" -> when (code) {
                        0xE000 -> -514
                        0xE001 -> 8
                        in 0xE010..0xE019 -> 1 shl (code - 0xE010)
                        else -> null
                    }
                    else -> null
                }
            } ?: return null
            return rows.size * 9 + 8
        }
    }
}
