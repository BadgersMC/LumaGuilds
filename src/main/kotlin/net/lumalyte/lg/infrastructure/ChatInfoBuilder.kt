package net.lumalyte.lg.infrastructure

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID


class ChatInfoBuilder(private val lang: LangService, private val playerId: UUID,
                      private val title: Component) {
    constructor(lang: LangService, playerId: UUID, title: String) : this(lang, playerId, Component.text(title))
    private var elements = Component.text()

    init {
        elements.append(Component.text("-----", NamedTextColor.WHITE))
        elements.append(Component.space()).append(title.colorIfAbsent(NamedTextColor.DARK_AQUA)).append(Component.space())
        elements.append(Component.text("-----", NamedTextColor.WHITE))
    }

    fun addHeader(text: String) {
        addHeader(Component.text(text))
    }

    fun addHeader(text: Component) {
        newLine()
        elements.append(text.colorIfAbsent(NamedTextColor.BLUE))
    }

    fun addParagraph(text: String) {
        addParagraph(Component.text(text))
    }

    fun addParagraph(text: Component) {
        newLine()
        elements.append(text.colorIfAbsent(NamedTextColor.GRAY))
    }

    fun addRow(text: String) {
        addRow(Component.text(text))
    }

    fun addRow(text: Component) {
        newLine()
        elements.append(text.colorIfAbsent(NamedTextColor.WHITE))
    }

    fun addIndexed(index: Int, text: String) {
        addIndexed(index, Component.text(text))
    }

    fun addIndexed(index: Int, text: Component) {
        newLine()
        val indexedRow = lang.msg(
            "command.info_box.index",
            "index" to index,
            "text" to PLAIN.serialize(text),
        )
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

    private companion object {
        val PLAIN = PlainTextComponentSerializer.plainText()
    }
}
