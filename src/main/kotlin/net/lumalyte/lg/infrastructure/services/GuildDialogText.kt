package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration

/** Numeric vanilla font metrics, in half pixels. Unknown fonts never get an estimated overlay. */
internal object GuildDialogText {
    private val advances by lazy {
        requireNotNull(javaClass.getResourceAsStream("/dialogs/default-font-advances.tsv")).bufferedReader().useLines {
            it.filterNot { line -> line.startsWith("#") }.associate { line ->
                val (code, width, bold) = line.split('\t')
                code.toInt(16) to (width.toInt() to bold.toInt())
            }
        }
    }

    private data class Glyph(val text: String, val style: Style, val advance: Int)

    private fun flatten(
        component: Component,
        icons: Boolean = false,
        extraAdvance: (String, Int) -> Int? = { _, _ -> null },
    ): List<Glyph>? {
        val result = mutableListOf<Glyph>()

        fun visit(
            node: Component,
            inherited: Style,
        ): Boolean {
            if (node !is TextComponent) return false
            val style = node.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            val font = style.font()?.asString()
            if (style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) return false
            for (code in node.content().codePoints().toArray()) {
                val width =
                    when {
                        code == '\n'.code -> 0
                        icons && font == "lumaguilds:dialog_icons" -> 22
                        font == null || font == "minecraft:default" -> advances[code]?.first ?: return false
                        else -> extraAdvance(font, code) ?: return false
                    }
                val bold =
                    if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
                        advances[code]?.second ?: 2
                    } else {
                        0
                    }
                result += Glyph(String(Character.toChars(code)), style, width + bold)
            }
            return node.children().all { visit(it, style) }
        }
        return if (visit(component, Style.empty())) result else null
    }

    /** Wrap before Minecraft can split an overlaid row; every returned line retains its styles/events. */
    fun rows(
        component: Component,
        maximum: Int,
        extraAdvance: (String, Int) -> Int? = { _, _ -> null },
    ): List<Pair<Component, Int>>? {
        val glyphs = flatten(component, extraAdvance = extraAdvance) ?: return null
        val rows = mutableListOf<Pair<Component, Int>>()
        val pending = mutableListOf<Glyph>()

        fun finish(count: Int = pending.size) {
            val line = Component.text()
            val group = pending.take(count)
            group.forEach { line.append(Component.text(it.text).style(it.style)) }
            rows += line.build() to group.sumOf { it.advance }
            repeat(count) { pending.removeAt(0) }
        }
        for (glyph in glyphs) {
            if (glyph.text == "\n") {
                finish()
                continue
            }
            if (glyph.advance > maximum) return null
            while (pending.sumOf { it.advance } + glyph.advance > maximum) {
                val space = pending.indexOfLast { it.text == " " }
                finish(if (space > 0) space + 1 else pending.size)
            }
            pending += glyph
        }
        finish()
        return rows
    }

    /** Native buttons are one line; keep the complete label in their tooltip. */
    fun buttonLabel(
        component: Component,
        maximum: Int,
    ): Component {
        val glyphs = flatten(component, icons = true) ?: return component
        if (glyphs.sumOf { it.advance } <= maximum && glyphs.none { it.text == "\n" }) return component
        val result = Component.text()
        var width = 0
        for (glyph in glyphs) {
            if (glyph.text == "\n" || width + glyph.advance > maximum - 18) break
            result.append(Component.text(glyph.text).style(glyph.style))
            width += glyph.advance
        }
        return result.append(Component.text("...")).build()
    }
}
