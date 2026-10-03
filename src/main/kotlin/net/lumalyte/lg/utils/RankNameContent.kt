package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** Legacy color/format codes are presentation; limits and identity use visible text. */
object RankNameContent {
    const val MAX_VISIBLE_LENGTH = 24
    const val MAX_RAW_LENGTH = 255
    private val codes = Regex("(?i)[&§]x(?:[&§][0-9a-f]){6}|[&§]#[0-9a-f]{6}|[&§][0-9a-fk-or]")
    private val serializer = LegacyComponentSerializer.builder()
        .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build()

    fun plain(name: String): String = codes.replace(name, "")
    fun valid(name: String): Boolean {
        val visible = plain(name)
        return name.length <= MAX_RAW_LENGTH && visible.length in 1..MAX_VISIBLE_LENGTH &&
            visible.isNotBlank() && visible.matches(Regex("[a-zA-Z0-9 _-]+"))
    }
    fun component(name: String): Component = serializer.deserialize(name.replace('§', '&'))
    fun miniMessage(name: String): String = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        .serialize(component(name))
    fun legacy(name: String): String = LegacyComponentSerializer.builder()
        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build().serialize(component(name))
}
