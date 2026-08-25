package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration

/** Removes Minecraft's default italic item styling from a complete component tree. */
fun Component.nonItalic(): Component {
    val normalized = decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    return normalized.children(normalized.children().map(Component::nonItalic))
}
