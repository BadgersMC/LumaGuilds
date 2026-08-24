package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

fun LangService.gui(
    key: String,
    vararg placeholders: Pair<String, Any?>,
): Component {
    val miniMessage = MiniMessage.miniMessage()
    val protectedText = placeholders.mapNotNullTo(mutableSetOf()) { (_, value) ->
        (value as? String)
    }
    val renderablePlaceholders = placeholders.map { (name, value) ->
        name to when (value) {
            is Component -> miniMessage.serialize(value)
            null -> ""
            else -> value
        }
    }.toTypedArray()
    return GuiTextStyler.style(msg(key, *renderablePlaceholders), protectedText)
}

/** Compatibility output for InventoryFramework's legacy String-only title API. */
fun LangService.guiTitle(
    key: String,
    vararg placeholders: Pair<String, Any?>,
): String = LegacyComponentSerializer.legacySection().serialize(gui(key, *placeholders))

/** Plain Unicode typography for Bedrock form APIs, which do not accept Adventure Components. */
fun LangService.bedrock(
    key: String,
    vararg placeholders: Pair<String, Any?>,
): String = PlainTextComponentSerializer.plainText().serialize(gui(key, *placeholders))

/** Plain text for legacy String-only non-GUI APIs; chat-capable APIs should use [LangService.msg]. */
fun LangService.plain(
    key: String,
    vararg placeholders: Pair<String, Any?>,
): String = PlainTextComponentSerializer.plainText().serialize(msg(key, *placeholders))
