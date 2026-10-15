package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Native presentation delegates all actions to the same reauthorized controller as inventories. */
internal class RecruitmentDialogMenu(
    private val controller: RecruitmentController,
    private val navigationValid: () -> Boolean,
    private val exit: () -> Unit,
    private val closeMenus: () -> Unit,
    private val dialog: net.lumalyte.lg.infrastructure.services.GuildDialogPresenter = GuildMenuDialog(),
) {

    fun show(view: RecruitmentView): Boolean {
        val pages = maxOf(1, (view.items.size + PAGE_SIZE - 1) / PAGE_SIZE)
        controller.page = controller.page.coerceIn(0, pages - 1)
        val entries = view.items.drop(controller.page * PAGE_SIZE).take(PAGE_SIZE)
        val actions = entries.mapIndexed { index, button -> action(button, "${index + 1}. ") }.toMutableList()
        actions += view.quickFilters.map { action(it, detailsInLabel = true) }
        if (controller.page > 0) actions += pageAction(-1, Component.text(controller.lang.raw("recruitment.previous")))
        if (controller.page + 1 < pages) actions += pageAction(1, Component.text(controller.lang.raw("recruitment.next")))
        actions += GuildDialogAction(Component.text(controller.lang.raw("recruitment.dialog.navigation")), Component.empty()) { showNavigation() }
        val body = entries.mapIndexed { index, button ->
            val details = if (controller.screen == RecruitmentScreen.BROWSE) button.details.take(4) else button.details
            GuildDialogItem(
                button.guild?.let(GuildBannerItemResolver::resolveForDisplay)
                    ?: NexoItemProvider.getItemStackOrFallback(button.icon) { ItemStack.of(Material.BOOK) },
                Component.text("${index + 1}. ${button.label}", NamedTextColor.GOLD)
                    .append(Component.newline())
                    .append(Component.text(details.joinToString("\n"), NamedTextColor.GRAY)),
            )
        }
        val back = GuildDialogAction(Component.text(controller.lang.raw("recruitment.dialog.back")), Component.empty()) { parent() }
        return present(
            GuildDialogPage(Component.text("${view.title} ${controller.page + 1}/$pages"), body, actions, back),
        )
    }

    private fun showNavigation() {
        val view = controller.view()
        val actions = view.navigation.map { action(it) } +
            GuildDialogAction(Component.text(controller.lang.raw("recruitment.dialog.close")), Component.empty(), closeMenus)
        present(
            GuildDialogPage(
                Component.text(controller.lang.raw("recruitment.dialog.navigation")), emptyList(), actions,
                GuildDialogAction(Component.text(controller.lang.raw("recruitment.dialog.back")), Component.empty(), controller.refresh),
            ),
        )
    }

    private fun present(page: GuildDialogPage): Boolean {
        return dialog.show(controller.player, page, navigationValid) {
            controller.player.closeInventory()
            controller.player.sendMessage(controller.lang.msg("recruitment.feedback.failed"))
        }
    }

    private fun action(button: RecruitmentButton, prefix: String = "", detailsInLabel: Boolean = false): GuildDialogAction {
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
        val next = when (controller.screen) {
            RecruitmentScreen.BROWSE, RecruitmentScreen.MANAGE -> return exit()
            RecruitmentScreen.FILTER, RecruitmentScreen.PROFILE -> RecruitmentScreen.BROWSE
            RecruitmentScreen.DISCORD, RecruitmentScreen.CONFIRM -> RecruitmentScreen.PROFILE
            RecruitmentScreen.APPLICATION -> RecruitmentScreen.MINE
            RecruitmentScreen.REVIEW -> RecruitmentScreen.INBOX
            RecruitmentScreen.REVIEW_CONFIRM -> RecruitmentScreen.REVIEW
            RecruitmentScreen.METHOD -> RecruitmentScreen.SETTINGS
            else -> RecruitmentScreen.MANAGE
        }
        controller.showScreen(next)
    }

    private companion object {
        const val PAGE_SIZE = 3
    }
}
