package net.lumalyte.lg.utils

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

/**
 * A restricted MiniMessage parser that allows only visual formatting tags.
 * Blocks interaction tags (<click>, <hover>, <insert>) that could be abused
 * to execute commands or inject text when clicked by elevated-permission players.
 *
 * Use this instead of MiniMessage.miniMessage() wherever user-controlled content
 * is parsed (guild descriptions, guild tags, etc.).
 *
 * Allowed: color, decoration, gradient, rainbow, reset, font, newline
 * Blocked: click, hover, insertion, keybind
 */
object SafeMiniMessage {

    private val INSTANCE: MiniMessage = MiniMessage.builder()
        .tags(
            TagResolver.builder()
                .resolvers(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.font(),
                    StandardTags.newline(),
                )
                .build()
        )
        .build()

    fun get(): MiniMessage = INSTANCE
}
