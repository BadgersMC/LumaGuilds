package net.lumalyte.lg.utils

/** Supplies a readable default without replacing a translated title's chosen color. */
internal object BankMenuTitle {
    private const val LEGACY_FORMAT_PREFIX = '\u00A7'

    fun forTheme(theme: GuiTheme, title: String): String {
        if (!theme.resolved().hasBackground || title.isEmpty()) return title

        var index = 0
        while (index + 1 < title.length && title[index] == LEGACY_FORMAT_PREFIX) {
            val code = title[index + 1].lowercaseChar()
            if (code in "0123456789abcdefrx") return title
            if (code !in "klmno") break
            index += 2
        }
        return "${LEGACY_FORMAT_PREFIX}6$title"
    }
}
