package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService

/** Compact the original shipped title without replacing an operator's translation. */
internal fun LangService.bankHistoryTitle(guildName: String, memberFilter: Boolean = false): String {
    val lang = this
    return if (raw("menu.bank.history.title") == "<white>Bank Transaction History - <guild>") {
        if (memberFilter) lang.guiTitle("menu.bank.history.member_filter.title")
        else lang.guiTitle("menu.bank.history.compact_title")
    } else {
        lang.guiTitle("menu.bank.history.title", "guild" to guildName)
    }
}
