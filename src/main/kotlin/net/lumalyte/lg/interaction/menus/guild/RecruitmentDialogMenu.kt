package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.infrastructure.services.CLICKABLE_CARDS
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
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
    fun show(view: RecruitmentView): Boolean {
        val pages = maxOf(1, (view.items.size + controller.dialogPageSize() - 1) / controller.dialogPageSize())
        controller.page = controller.page.coerceIn(0, pages - 1)
        val entries = view.items.drop(controller.page * controller.dialogPageSize()).take(controller.dialogPageSize())
        return present(
            GuildDialogPage(
                Component.text("${view.title} ${controller.page + 1}/$pages"),
                searchContext() + bodies(entries),
                pageActions(view, pages),
                GuildDialogAction(
                    Component.text(controller.lang.raw("recruitment.dialog.back")),
                    Component.empty(),
                    ::parent,
                ),
                columns = 1,
                theme = controller.theme(),
                search = if (controller.screen == RecruitmentScreen.BROWSE) controller.boardSearch() else null,
            ),
        )
    }

    private fun isCardLink(button: RecruitmentButton): Boolean =
        clickableCards && controller.screen == RecruitmentScreen.BROWSE &&
            button.guild != null && button.isInteractive()

    private fun bodies(entries: List<RecruitmentButton>): List<GuildDialogItem> =
        entries.filter { !it.isInteractive() || it.details.isNotEmpty() || isCardLink(it) }
            .map { button ->
                button.nativeBody(controller.screen).copy(onClick = if (isCardLink(button)) action(button) else null)
            }

    private fun searchContext(): List<GuildDialogItem> {
        if (controller.screen != RecruitmentScreen.BROWSE || controller.query.isBlank()) return emptyList()
        return listOf(
            GuildDialogItem(
                NexoItemProvider.getItemStackOrFallback(RecruitmentIcons.TAG) { ItemStack.of(Material.NAME_TAG) },
                Component.text(controller.lang.raw("recruitment.search") + ": " + controller.query),
            ),
        )
    }

    private fun pageActions(
        view: RecruitmentView,
        pages: Int,
    ): List<GuildDialogAction> {
        val actions = mutableListOf<GuildDialogAction>()
        val entries = view.items.drop(controller.page * controller.dialogPageSize()).take(controller.dialogPageSize())
        for (button in entries.filter { it.isInteractive() && !isCardLink(it) }) actions += action(button)
        actions += extraActions(view)
        if (controller.page > 0) {
            actions += pageAction(-1, Component.text(controller.lang.raw("recruitment.previous")))
        }
        if (controller.page + 1 < pages) {
            actions += pageAction(1, Component.text(controller.lang.raw("recruitment.next")))
        }
        actions += navigationAction()
        return actions
    }

    private fun extraActions(view: RecruitmentView): List<GuildDialogAction> {
        if (controller.screen != RecruitmentScreen.BROWSE) {
            return view.quickFilters.map { action(it, detailsInLabel = true) }
        }
        val direct =
            setOf(
                controller.lang.raw("recruitment.filter"),
                controller.lang.raw("recruitment.mine"),
                controller.lang.raw("recruitment.manage"),
            )
        val tools = setOf(controller.lang.raw("recruitment.sort"), controller.lang.raw("recruitment.reset"))
        return view.navigation.filter { it.label in direct && it.isInteractive() }.map { action(it) } +
            view.quickFilters.filter { it.label in tools }.map { action(it, detailsInLabel = true) }
    }

    private fun navigationAction(close: Boolean = false): GuildDialogAction {
        val label =
            if (close) {
                controller.lang.raw("recruitment.dialog.close")
            } else {
                controller.lang.raw("recruitment.dialog.navigation")
            }
        return GuildDialogAction(
            Component.text(label),
            Component.empty(),
            if (close) callbacks.close else ::showNavigation,
        )
    }

    private fun showNavigation() {
        val view = controller.view()
        val actions = view.navigation.filter { it.isInteractive() }.map { action(it) } + navigationAction(true)
        present(
            GuildDialogPage(
                Component.text(controller.lang.raw("recruitment.dialog.navigation")),
                emptyList(),
                actions,
                GuildDialogAction(
                    Component.text(controller.lang.raw("recruitment.dialog.back")),
                    Component.empty(),
                    controller.refresh,
                ),
                theme = controller.theme(),
            ),
        )
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
            Component.text(label),
            Component.text(button.details.joinToString("\n")),
        ) { controller.run(button.action) }
    }

    private fun pageAction(
        delta: Int,
        label: Component,
    ): GuildDialogAction {
        return GuildDialogAction(label, Component.empty()) {
            controller.page += delta
            controller.refresh()
        }
    }

    private fun parent() {
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
private const val CHOICE_PAGE_SIZE = 8
private val CHOICE_SCREENS = setOf(RecruitmentScreen.MANAGE, RecruitmentScreen.METHOD, RecruitmentScreen.SETUP)

private fun RecruitmentController.dialogPageSize(): Int = if (screen in CHOICE_SCREENS) CHOICE_PAGE_SIZE else PAGE_SIZE

private fun RecruitmentButton.nativeBody(screen: RecruitmentScreen): GuildDialogItem {
    val lines = if (screen == RecruitmentScreen.BROWSE) details.take(BROWSE_DETAILS) else details
    val heading =
        if (!isInteractive() || guild != null) {
            Component.text(guild?.name ?: label, NamedTextColor.GOLD).append(Component.newline())
        } else {
            Component.empty()
        }
    return GuildDialogItem(
        guild?.let(GuildBannerItemResolver::resolveForDisplay)
            ?: NexoItemProvider.getItemStackOrFallback(icon) { ItemStack.of(Material.BOOK) },
        heading.append(Component.text(lines.joinToString("\n"), NamedTextColor.GRAY)),
        cardLines = if (screen == RecruitmentScreen.BROWSE && guild != null) cardLines(lines) else null,
    )
}

private const val CARD_LINE_LENGTH = 30

private fun RecruitmentButton.cardLines(lines: List<String>): List<Component> =
    listOf(Component.text(guild?.name ?: label, NamedTextColor.GOLD)) +
        lines.flatMap { line ->
            line.codePoints().toArray().toList().chunked(CARD_LINE_LENGTH).map { points ->
                Component.text(buildString { points.forEach(::appendCodePoint) }, NamedTextColor.GRAY)
            }
        }

internal data class RecruitmentDialogCallbacks(
    val valid: () -> Boolean,
    val back: () -> Unit,
    val close: () -> Unit,
)
