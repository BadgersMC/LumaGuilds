package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.infrastructure.services.CLICKABLE_CARDS
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.infrastructure.services.guildDialogIconLabel
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Native presentation delegates all actions to the same reauthorized controller as inventories. */
internal class RecruitmentDialogMenu(
    private val controller: RecruitmentController,
    private val callbacks: RecruitmentDialogCallbacks,
    private val dialog: GuildDialogPresenter = GuildMenuDialog(),
    private val clickableCards: Boolean = CLICKABLE_CARDS,
) {
    fun show(rawView: RecruitmentView): Boolean {
        val view =
            if (controller.screen == RecruitmentScreen.MANAGE) {
                rawView.copy(
                    items =
                        rawView.items.filterNot {
                            it.label in managementToolLabels()
                        },
                )
            } else {
                rawView
            }
        val pages = maxOf(1, (view.items.size + controller.dialogPageSize() - 1) / controller.dialogPageSize())
        controller.page = controller.page.coerceIn(0, pages - 1)
        val entries =
            if (controller.screen == RecruitmentScreen.FILTER) {
                emptyList()
            } else {
                view.items.drop(controller.page * controller.dialogPageSize()).take(controller.dialogPageSize())
            }
        return present(
            GuildDialogPage(
                Component.text("${pageTitle(view)} ${controller.page + 1}/$pages"),
                bodies(entries),
                pageActions(view, pages),
                GuildDialogAction(
                    guildDialogIconLabel(
                        Component.text(
                            controller.lang.raw(
                                if (controller.screen == RecruitmentScreen.BROWSE) {
                                    "recruitment.dialog.close"
                                } else {
                                    "recruitment.dialog.back"
                                },
                            ),
                        ),
                        RecruitmentIcons.BACK,
                    ),
                    Component.empty(),
                    if (controller.screen == RecruitmentScreen.BROWSE) callbacks.close else ::parent,
                ),
                columns = 2,
                theme = controller.theme(),
                search = if (controller.screen == RecruitmentScreen.BROWSE) controller.boardSearch() else null,
                subtitle =
                    if (controller.screen == RecruitmentScreen.BROWSE) {
                        Component.text(controller.lang.raw("dialog_v2.board_subtitle"))
                    } else {
                        Component.empty()
                    },
                bodyActions =
                    when (controller.screen) {
                        RecruitmentScreen.BROWSE -> categoryActions()
                        RecruitmentScreen.FILTER ->
                            filterActions() +
                                GuildDialogAction(
                                    Component.text(controller.lang.raw("dialog_v2.more_filters")),
                                    Component.empty(),
                                ) {
                                    advancedFilters(rawView)
                                }
                        RecruitmentScreen.MANAGE ->
                            listOf(
                                GuildDialogAction(
                                    Component.text(controller.lang.raw("dialog_v2.more_listing_options")),
                                    Component.empty(),
                                ) {
                                    managementTools(rawView)
                                },
                            )
                        else -> emptyList()
                    },
                showCategoryInput = false,
            ),
        )
    }

    private fun pageTitle(view: RecruitmentView): String =
        when (controller.screen) {
            RecruitmentScreen.PROFILE -> controller.selected?.let { controller.guilds.getGuild(it)?.name } ?: view.title
            RecruitmentScreen.FILTER -> controller.lang.raw("recruitment.filter")
            RecruitmentScreen.INBOX -> controller.lang.raw("recruitment.inbox")
            RecruitmentScreen.MINE -> controller.lang.raw("recruitment.mine")
            RecruitmentScreen.CONFIRM -> controller.lang.raw("dialog_v2.confirm_fee")
            else -> view.title
        }

    private fun managementToolLabels(): Set<String> =
        setOf(
            controller.lang.raw("recruitment.setup"),
            controller.lang.raw("recruitment.settings"),
            controller.lang.raw("recruitment.browse"),
            controller.lang.raw("recruitment.mine"),
        )

    private fun managementTools(view: RecruitmentView) {
        present(
            GuildDialogPage(
                Component.text(controller.lang.raw("dialog_v2.more_listing_options")),
                emptyList(),
                view.items.filter { it.label in managementToolLabels() && it.isInteractive() }.map { action(it) },
                GuildDialogAction(
                    Component.text(controller.lang.raw("recruitment.dialog.back")),
                    Component.empty(),
                    controller.refresh,
                ),
                columns = 2,
                theme = controller.theme(),
            ),
        )
    }

    private fun advancedFilters(view: RecruitmentView) {
        present(
            GuildDialogPage(
                Component.text(controller.lang.raw("dialog_v2.more_filters")),
                emptyList(),
                view.items.filter { it.isInteractive() }.map { action(it, detailsInLabel = true) } +
                    view.navigation.filter {
                        it.label == controller.lang.raw("recruitment.reminders") && it.isInteractive()
                    }.map { action(it) },
                GuildDialogAction(
                    Component.text(controller.lang.raw("recruitment.dialog.back")),
                    Component.empty(),
                    controller.refresh,
                ),
                columns = 2,
                theme = controller.theme(),
            ),
        )
    }

    private fun isCardLink(button: RecruitmentButton): Boolean =
        clickableCards && button.isInteractive() && (
            controller.screen == RecruitmentScreen.BROWSE && button.guild != null ||
                controller.screen in setOf(RecruitmentScreen.INBOX, RecruitmentScreen.MINE)
        )

    private fun bodies(entries: List<RecruitmentButton>): List<GuildDialogItem> =
        entries.filter {
            !it.isInteractive() || isCardLink(it) ||
                controller.screen == RecruitmentScreen.BROWSE && it.guild != null ||
                controller.screen == RecruitmentScreen.CONFIRM && it.details.isNotEmpty()
        }
            .map { button ->
                button.nativeBody(controller.screen).copy(onClick = if (isCardLink(button)) action(button) else null)
            }

    private fun categoryActions(): List<GuildDialogAction> =
        listOf("all", "building", "pvp", "quests", "social").map { category ->
            val key =
                when (category) {
                    "all" -> "recruitment.any"
                    "quests" -> "recruitment.category_quests"
                    "social" -> "recruitment.category_social"
                    else -> "recruitment.$category"
                }
            val label = controller.lang.raw(key)
            val selected = (controller.interest ?: "all") == category
            GuildDialogAction(Component.text(if (selected) "[$label]" else label), Component.empty()) {
                controller.interest = category.takeUnless { it == "all" }
                controller.resetBrowse()
            }
        }

    private fun filterActions(): List<GuildDialogAction> =
        buildList {
            for ((key, values) in listOf(
                "fee" to listOf("Any", "Free", "Paid"),
                "level" to listOf("All", "30+", "Under 30"),
                "sort" to listOf("Name", "Online", "Level"),
            )) {
                val filterKey = "dialog_v2.filter_$key"
                values.forEachIndexed { index, value ->
                    val selected =
                        when (key) {
                            "fee" -> controller.feeFilter == index
                            "level" -> controller.levelFilter == index
                            else -> controller.sorting == listOf(2, 3, 1)[index]
                        }
                    add(
                        GuildDialogAction(
                            Component.text(
                                "${controller.lang.raw(
                                    filterKey,
                                )}: ${if (selected) "[$value]" else value}",
                            ),
                            Component.empty(),
                        ) {
                            when (key) {
                                "fee" -> controller.feeFilter = index
                                "level" -> controller.levelFilter = index
                                else -> controller.sorting = listOf(2, 3, 1)[index]
                            }
                            controller.browsePage = 0
                            controller.refresh()
                        },
                    )
                }
            }
        }

    private fun pageActions(
        view: RecruitmentView,
        pages: Int,
    ): List<GuildDialogAction> {
        val actions = mutableListOf<GuildDialogAction>()
        if (controller.screen == RecruitmentScreen.FILTER) {
            return listOf(
                GuildDialogAction(Component.text(controller.lang.raw("dialog_v2.show_results")), Component.empty()) {
                    controller.showScreen(RecruitmentScreen.BROWSE)
                },
                GuildDialogAction(Component.text(controller.lang.raw("recruitment.reset")), Component.empty()) {
                    controller.feeFilter = 0
                    controller.levelFilter = 0
                    controller.sorting = 2
                    controller.query = ""
                    controller.interest = null
                    controller.browsePage = 0
                    controller.refresh()
                },
            )
        }
        val entries = view.items.drop(controller.page * controller.dialogPageSize()).take(controller.dialogPageSize())
        for (button in entries.filter { it.isInteractive() && !isCardLink(it) }) actions += action(button)
        actions += extraActions(view)
        if (controller.page > 0) {
            actions += pageAction(-1, Component.text(controller.lang.raw("recruitment.previous")))
        }
        if (controller.page + 1 < pages) {
            actions += pageAction(1, Component.text(controller.lang.raw("recruitment.next")))
        }
        return actions
    }

    private fun extraActions(view: RecruitmentView): List<GuildDialogAction> {
        if (controller.screen != RecruitmentScreen.BROWSE) {
            val filters =
                if (controller.screen == RecruitmentScreen.FILTER) controller.quickFilters() else view.quickFilters
            return filters.map { action(it, detailsInLabel = true) }
        }
        val mine = controller.lang.raw("recruitment.mine")
        val applications =
            view.navigation.firstOrNull { it.label == mine && it.isInteractive() }
                ?: RecruitmentButton(mine, icon = RecruitmentIcons.DESCRIPTION) {
                    controller.showScreen(RecruitmentScreen.MINE)
                }
        val filter =
            view.navigation.firstOrNull { it.label == controller.lang.raw("recruitment.filter") && it.isInteractive() }
                ?: RecruitmentButton(controller.lang.raw("recruitment.filter")) {
                    controller.showScreen(RecruitmentScreen.FILTER)
                }
        return listOf(action(filter), action(applications)) +
            view.navigation.filter {
                it.label == controller.lang.raw("recruitment.manage") && it.isInteractive()
            }.map { action(it) }
    }

    private fun present(page: GuildDialogPage): Boolean {
        return dialog.show(controller.player, page, callbacks.valid) {
            controller.player.closeDialog()
            controller.player.closeInventory()
            controller.player.sendMessage(controller.lang.msg("recruitment.feedback.failed"))
        }
    }

    private fun action(
        button: RecruitmentButton,
        prefix: String = "",
        detailsInLabel: Boolean = false,
    ): GuildDialogAction {
        val state = if (detailsInLabel) button.details.firstOrNull()?.let { ": $it" }.orEmpty() else ""
        val label =
            if (controller.screen == RecruitmentScreen.BROWSE && button.guild != null) {
                controller.lang.raw("recruitment.open_guild").replace("%guild%", button.guild.name)
            } else {
                prefix + button.label + state
            }
        return GuildDialogAction(
            guildDialogIconLabel(Component.text(label), button.icon),
            Component.text(button.details.joinToString("\n")),
        ) { controller.run(button.action) }
    }

    private fun pageAction(
        delta: Int,
        label: Component,
    ): GuildDialogAction {
        val icon = if (delta > 0) RecruitmentIcons.PAGE_NEXT else RecruitmentIcons.PAGE_PREV
        return GuildDialogAction(guildDialogIconLabel(label, icon), Component.empty()) {
            controller.page += delta
            controller.refresh()
        }
    }

    private fun parent() {
        if (controller.screen == RecruitmentScreen.MANAGE) return controller.showScreen(RecruitmentScreen.BROWSE)
        if (controller.screen == RecruitmentScreen.METHOD) return controller.showScreen(controller.methodReturn)
        if (controller.screen in ROOT_SCREENS) return callbacks.back()
        controller.showScreen(PARENTS[controller.screen] ?: RecruitmentScreen.MANAGE)
    }

    private companion object {
        val ROOT_SCREENS = setOf(RecruitmentScreen.BROWSE, RecruitmentScreen.MANAGE)
        val PARENTS = parentScreens()

        private fun parentScreens(): Map<RecruitmentScreen, RecruitmentScreen> {
            return mapOf(
                RecruitmentScreen.FILTER to RecruitmentScreen.BROWSE,
                RecruitmentScreen.PROFILE to RecruitmentScreen.BROWSE,
                RecruitmentScreen.DISCORD to RecruitmentScreen.PROFILE,
                RecruitmentScreen.CONFIRM to RecruitmentScreen.PROFILE,
                RecruitmentScreen.APPLICATION to RecruitmentScreen.MINE,
                RecruitmentScreen.REVIEW to RecruitmentScreen.INBOX,
                RecruitmentScreen.REVIEW_CONFIRM to RecruitmentScreen.REVIEW,
                RecruitmentScreen.METHOD to RecruitmentScreen.SETTINGS,
            )
        }
    }
}

private const val BROWSE_DETAILS = 4
private const val PAGE_SIZE = 3
private const val BROWSE_PAGE_SIZE = 3
private const val CHOICE_PAGE_SIZE = 8
private val CHOICE_SCREENS = setOf(RecruitmentScreen.MANAGE, RecruitmentScreen.METHOD, RecruitmentScreen.SETUP)

private fun RecruitmentController.dialogPageSize(): Int =
    when {
        screen == RecruitmentScreen.BROWSE -> BROWSE_PAGE_SIZE
        screen in CHOICE_SCREENS -> CHOICE_PAGE_SIZE
        else -> PAGE_SIZE
    }

private fun RecruitmentButton.nativeBody(screen: RecruitmentScreen): GuildDialogItem {
    val lines = if (screen == RecruitmentScreen.BROWSE) details.take(BROWSE_DETAILS) else details
    val heading =
        if (!isInteractive() || guild != null) {
            Component.text(guild?.name ?: label, NamedTextColor.GOLD).append(Component.newline())
        } else {
            Component.empty()
        }
    return GuildDialogItem(
        if (guild != null && !showBanner) {
            ItemStack.of(Material.AIR)
        } else {
            guild?.let(GuildBannerItemResolver::resolveForDisplay)
                ?: NexoItemProvider.getItemStackOrFallback(icon) { ItemStack.of(Material.BOOK) }
        },
        heading.append(Component.text(lines.joinToString("\n"), NamedTextColor.GRAY)),
        cardLines = cardSummary ?: cardLines(lines),
    )
}

private fun RecruitmentButton.cardLines(lines: List<String>): List<Component> =
    listOf(Component.text(guild?.name ?: label, NamedTextColor.GOLD)) +
        lines.map { Component.text(it, NamedTextColor.GRAY) }

internal data class RecruitmentDialogCallbacks(
    val valid: () -> Boolean,
    val back: () -> Unit,
    val close: () -> Unit,
)
