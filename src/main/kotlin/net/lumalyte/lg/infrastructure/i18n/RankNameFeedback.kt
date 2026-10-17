package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

/** Nexus stringifies placeholders, so serialize the nested validation message first. */
internal fun LangService.rankNameError(error: Component): Component {
    val lang = this
    return lang.msg(
        "menu.rank_edit.feedback.invalid_name",
        "error" to MiniMessage.miniMessage().serialize(error),
    )
}
