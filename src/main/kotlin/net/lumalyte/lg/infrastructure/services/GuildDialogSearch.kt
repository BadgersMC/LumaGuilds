package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import net.kyori.adventure.text.Component

private const val QUERY_LIMIT = 64
internal const val BOARD_CONTROL_WIDTH = 280
internal const val BOARD_QUERY_KEY = "guild_query"
internal const val BOARD_CATEGORY_KEY = "guild_category"

/** A bounded form snapshot. Only its guarded submit action may apply changed fields. */
internal class GuildDialogSearch(
    val label: Component,
    val initial: String,
    val categoryLabel: Component,
    val category: String,
    val categories: List<Pair<String, Component>>,
    val submitLabel: Component,
    val submit: (String, String) -> Unit,
) {
    fun inputs(): List<DialogInput> =
        listOf(
            DialogInput.text(BOARD_QUERY_KEY, label).width(BOARD_CONTROL_WIDTH)
                .initial(initial)
                .maxLength(QUERY_LIMIT)
                .build(),
            DialogInput.singleOption(
                BOARD_CATEGORY_KEY,
                BOARD_CONTROL_WIDTH,
                categories.map { (id, text) -> SingleOptionDialogInput.OptionEntry.create(id, text, id == category) },
                categoryLabel,
                true,
            ),
        )

    fun accept(
        query: String?,
        selected: String?,
    ): Boolean {
        if (query == null || categories.none { it.first == selected }) return false
        submit(query.trim().take(QUERY_LIMIT), requireNotNull(selected))
        return true
    }
}
