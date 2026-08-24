package net.lumalyte.lg.infrastructure

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.UUID


class ChatInfoBuilder(private val lang: LangService, private val playerId: UUID,
                      private val title: String) {
    private var elements = Component.text()

    init {
        elements.append(Component.text("-----", NamedTextColor.WHITE))
        elements.append(Component.text(" $title ", NamedTextColor.DARK_AQUA))
        elements.append(Component.text("-----", NamedTextColor.WHITE))
    }

    fun addHeader(text: String) {
        newLine()
        elements.append(Component.text(text, NamedTextColor.BLUE))
    }

    fun addParagraph(text: String) {
        newLine()
        elements.append(Component.text(text, NamedTextColor.GRAY))
    }

    fun addRow(text: String) {
        newLine()
        elements.append(Component.text(text, NamedTextColor.WHITE))
    }

    fun addIndexed(index: Int, text: String) {
        newLine()
        val indexedRow = lang.msg("command.info_box.index", "index" to index, "text" to text)
        elements.append(indexedRow)
    }

    fun addSpace() {
        newLine()
    }

    fun create(): Component {
        val finalisedElement = elements.append(Component.text("\n-----", NamedTextColor.WHITE))
        return finalisedElement.build()
    }

    fun createPaged(currentPage: Int, pages: Int): Component {
        val pageText = lang.msg(
            "command.info_box.paged", "current_page" to currentPage, "total_pages" to pages)
        val finalisedElement = elements.append(Component.text("\n-----", NamedTextColor.WHITE))
            .append(pageText)
        return finalisedElement.build()
    }

    private fun newLine() {
        elements.append(Component.text("\n"))
    }
}
