package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
) {
    fun show(view: RecruitmentView): Boolean {
        val size = pageSize()
        val pages = maxOf(1, (view.items.size + size - 1) / size)
        controller.page = controller.page.coerceIn(0, pages - 1)
        val entries = view.items.drop(controller.page * size).take(size)
        val back =
            GuildDialogAction(
                Component.text(controller.lang.raw("recruitment.dialog.back")),
                Component.empty(),
            ) { parent() }
        return present(
            GuildDialogPage(
                Component.text("${view.title} ${controller.page + 1}/$pages"),
                searchContext() + entryBodies(entries),
                pageActions(view, pages),
                back,
                columns = if (controller.screen == RecruitmentScreen.BROWSE) BROWSE_COLUMNS else 1,
            ),
        )
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

    private fun pageActions(view: RecruitmentView, pages: Int): List<GuildDialogAction> {
        val actions = mutableListOf<GuildDialogAction>()
        val entries = view.items.drop(controller.page * pageSize()).take(pageSize())
        for (button in entries.filter { it.isInteractive() }) actions += action(button)
        for (button in view.quickFilters) actions += action(button, detailsInLabel = true)
        if (controller.page > 0) {
            actions += pageAction(-1, Component.text(controller.lang.raw("recruitment.previous")))
        }
        if (controller.page + 1 < pages) {
            actions += pageAction(1, Component.text(controller.lang.raw("recruitment.next")))
        }
        actions += navigationAction()
        return actions
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

    private fun entryBodies(entries: List<RecruitmentButton>): List<GuildDialogItem> {
        return entries.filter { !it.isInteractive() || it.details.isNotEmpty() }
            .map { it.nativeBody(controller.screen) }
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
        return GuildDialogAction(
            Component.text(prefix + button.label + state),
            Component.text(button.details.joinToString("\n")),
        ) { controller.run(button.action) }
    }

    private fun pageAction(delta: Int, label: Component): GuildDialogAction {
        return GuildDialogAction(label, Component.empty()) {
            controller.page += delta
            controller.refresh()
        }
    }

    private fun parent() {
        if (controller.screen in ROOT_SCREENS) return callbacks.back()
        controller.showScreen(PARENTS[controller.screen] ?: RecruitmentScreen.MANAGE)
    }

    private fun pageSize(): Int = if (controller.screen in CHOICE_SCREENS) CHOICE_PAGE_SIZE else PAGE_SIZE

    private companion object {
        const val PAGE_SIZE = 3
        const val BROWSE_COLUMNS = 2
        const val CHOICE_PAGE_SIZE = 8
        val CHOICE_SCREENS = setOf(RecruitmentScreen.MANAGE, RecruitmentScreen.METHOD, RecruitmentScreen.SETUP)
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
    )
}

internal data class RecruitmentDialogCallbacks(
    val valid: () -> Boolean,
    val back: () -> Unit,
    val close: () -> Unit,
)
