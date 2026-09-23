package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

object GuildDescriptionContent {
    const val MAX_LENGTH = 200

    sealed interface Failure {
        data class TooLong(val length: Int) : Failure
        data class InteractiveTag(val tagName: String) : Failure
        data class InvalidFormat(val message: String?) : Failure
    }

    private val disallowedTags = listOf("click", "hover", "insertion")
    private val disallowedPattern = Regex(
        """<\s*/?\s*(${disallowedTags.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val invitePattern = Regex(
        """https://(?:www\.)?(?:discord\.gg/[A-Za-z0-9_-]+|discord\.com/invite/[A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val miniMessage = MiniMessage.builder()
        .tags(
            TagResolver.resolver(
                StandardTags.color(),
                StandardTags.decorations(),
                StandardTags.gradient(),
                StandardTags.rainbow(),
                StandardTags.reset(),
            )
        )
        .build()

    fun validationFailure(description: String?): Failure? {
        if (description == null) return null
        if (description.length > MAX_LENGTH) return Failure.TooLong(description.length)

        val disallowed = disallowedPattern.find(description)
        if (disallowed != null) {
            return Failure.InteractiveTag(disallowed.groupValues[1].lowercase())
        }

        return runCatching { miniMessage.deserialize(description) }
            .exceptionOrNull()
            ?.let { Failure.InvalidFormat(it.message) }
    }

    fun discordInvites(description: String?): List<String> =
        if (description.isNullOrEmpty()) {
            emptyList()
        } else {
            invitePattern.findAll(description)
                .map { it.value }
                .distinct()
                .toList()
        }

    fun render(description: String?): Component {
        val source = description?.takeIf { it.isNotEmpty() } ?: return Component.empty()

        val matches = invitePattern.findAll(source).toList()
        if (matches.isEmpty()) {
            return runCatching { miniMessage.deserialize(source) }
                .getOrElse { Component.text(source) }
        }

        var tagged = source
        val inviteTags = TagResolver.builder()
        matches.asReversed().forEachIndexed { reverseIndex, match ->
            val index = matches.lastIndex - reverseIndex
            val tagName = inviteTag(index)
            tagged = tagged.replaceRange(match.range, "<$tagName>")
            inviteTags.resolver(Placeholder.component(tagName, inviteComponent(match.value)))
        }

        return runCatching { miniMessage.deserialize(tagged, inviteTags.build()) }
            .getOrElse { Component.text(source) }
    }

    fun plainText(description: String?): String =
        PlainTextComponentSerializer.plainText().serialize(render(description))

    private fun inviteTag(index: Int): String = "lg_discord_invite_$index"

    private fun inviteComponent(invite: String): Component =
        Component.text(invite)
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.openUrl(invite))
            .hoverEvent(HoverEvent.showText(Component.text("Open Discord invite")))
}
