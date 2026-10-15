package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.services.GuildDialogSearch

internal fun RecruitmentController.boardSearch(): GuildDialogSearch {
    val categories = mutableListOf(
        "all" to Component.text(lang.raw("recruitment.any")),
        "building" to Component.text(lang.raw("recruitment.building")),
        "pvp" to Component.text(lang.raw("recruitment.pvp")),
        "quests" to Component.text(lang.raw("recruitment.category_quests")),
        "social" to Component.text(lang.raw("recruitment.category_social")),
    )
    val selectedCategory = interest ?: "all"
    if (categories.none { it.first == selectedCategory }) {
        categories += selectedCategory to Component.text(selectedCategory)
    }
    return GuildDialogSearch(
        Component.text(lang.raw("recruitment.search")),
        query,
        Component.text(lang.raw("recruitment.category")),
        selectedCategory,
        categories,
        Component.text(lang.raw("recruitment.update_results")),
    ) { value, category ->
        run {
            query = value
            interest = category.takeUnless { it == "all" }
            resetBrowse()
        }
    }
}
