package net.lumalyte.lg.infrastructure.i18n

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor

/** Surface-aware typography for translated inventory text. */
object GuiTextStyler {
    private val smallCaps = mapOf(
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ', 'e' to 'ᴇ', 'f' to 'ꜰ',
        'g' to 'ɢ', 'h' to 'ʜ', 'i' to 'ɪ', 'j' to 'ᴊ', 'k' to 'ᴋ', 'l' to 'ʟ',
        'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ', 'p' to 'ᴘ', 'q' to 'q', 'r' to 'ʀ',
        's' to 'ꜱ', 't' to 'ᴛ', 'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'x' to 'x',
        'y' to 'ʏ', 'z' to 'ᴢ',
    )
    private val blackShadow = ShadowColor.shadowColor(0xFF000000.toInt())

    fun style(component: Component, protectedText: Set<String> = emptySet()): Component =
        shadow(smallCaps(component, protectedText))

    fun shadow(component: Component): Component = component.shadowColor(blackShadow)

    fun smallCaps(component: Component, protectedText: Set<String> = emptySet()): Component {
        val converted = if (component is TextComponent) {
            component.content(smallCapsText(component.content(), protectedText))
        } else {
            component
        }
        return converted.children(converted.children().map { smallCaps(it, protectedText) })
    }

    private fun smallCap(character: Char): Char =
        if (character.isLetter() && character.code < 128) {
            smallCaps[character.lowercaseChar()] ?: character
        } else {
            character
        }

    private fun smallCapsText(text: String, protectedText: Set<String>): String {
        if (protectedText.isEmpty()) return text.map(::smallCap).joinToString("")

        val protectedValues = protectedText.filter(String::isNotEmpty).sortedByDescending(String::length)
        return buildString {
            var index = 0
            while (index < text.length) {
                val protected = protectedValues.firstOrNull { text.startsWith(it, index) }
                if (protected != null) {
                    append(protected)
                    index += protected.length
                } else {
                    append(smallCap(text[index]))
                    index++
                }
            }
        }
    }
}
