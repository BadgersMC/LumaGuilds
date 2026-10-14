package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val BOARD_COLUMNS = 9
private const val BOARD_ROWS = 6
private const val BOARD_CAPACITY = 45
private const val FILTERED_CAPACITY = 36
private const val NAVIGATION_ROW = 5
private const val NAVIGATION_CAPACITY = 7
private const val INPUT_LIFETIME_TICKS = 2_400L
private const val LORE_COLUMNS = 44
private const val LORE_ROWS = 16

/** Inventory recruitment board with stable pagination and private chat input. */
internal class GuildRecruitmentMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
) : Menu, KoinComponent {
    private val controller = RecruitmentController(player, ::open) {
        val token = navigator.currentNavigationToken()
        val guard: () -> Boolean = { navigator.isNavigationCurrent(token) }
        guard
    }
    private val inputListener: ChatInputListener by inject()
    private val lang: LangService by inject()
    private val emojis: NexoEmojiService by inject()

    /** Refreshes current state without exposing a stale private inbox. */
    override fun open() {
        controller.run {
            val input = controller.input
            if (input == null) showBoard(controller.view()) else showInput(input)
        }
    }

    private fun showInput(input: RecruitmentInput) {
        player.closeInventory()
        val navigation = navigator.currentNavigationToken()
        if (net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.supportsDialog(player)) {
            net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.input(
                player,
                input.label,
                input.value,
                lang.raw("recruitment.submit_input"),
                lang.raw("recruitment.cancel_input"),
                { navigator.isNavigationCurrent(navigation) && controller.input === input },
                { controller.acceptInput(it) },
                { controller.cancelInput() },
            )
        } else {
            showChatInput(input, navigation)
        }
    }

    private fun showChatInput(input: RecruitmentInput, navigation: Long) {
        player.sendMessage(lang.msg("recruitment.feedback.input", "field" to input.label))
        val handler = object : ChatInputHandler {
            override fun onChatInput(player: Player, input: String) {
                if (player.isOnline && navigator.isNavigationCurrent(navigation)) controller.acceptInput(input)
            }
            override fun onCancel(player: Player) {
                if (player.isOnline && navigator.isNavigationCurrent(navigation)) controller.cancelInput()
            }
        }
        inputListener.startInputMode(player, handler)
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            net.lumalyte.lg.common.PluginKeys.getPlugin(),
            Runnable {
                if (inputListener.stopInputMode(player, handler) && player.isOnline) {
                    player.sendMessage(lang.msg("recruitment.feedback.input_expired"))
                }
            },
            INPUT_LIFETIME_TICKS,
        )
    }

    private fun showBoard(view: RecruitmentView) {
        val filtered = view.quickFilters.isNotEmpty()
        val capacity = if (filtered) FILTERED_CAPACITY else BOARD_CAPACITY
        val offset = if (filtered) 1 else 0
        val pages = maxOf(1, (view.items.size + capacity - 1) / capacity)
        controller.page = controller.page.coerceIn(0, pages - 1)
        val gui = ChestGui(
            BOARD_ROWS,
            MenuTitleBuilder.build(controller.theme(), BOARD_ROWS, "${view.title} ${controller.page + 1}/$pages"),
        )
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        val pane = StaticPane(0, 0, BOARD_COLUMNS, BOARD_ROWS)
        view.quickFilters.forEachIndexed { index, button -> add(pane, button, index, 0) }
        view.items.drop(controller.page * capacity).take(capacity).forEachIndexed { index, button ->
            add(pane, button, index % BOARD_COLUMNS, index / BOARD_COLUMNS + offset)
        }
        view.navigation.take(NAVIGATION_CAPACITY).forEachIndexed { index, button ->
            add(pane, button, index + 1, NAVIGATION_ROW)
        }
        addPagination(pane, pages)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun addPagination(pane: StaticPane, pages: Int) {
        if (controller.page > 0) {
            add(
                pane,
                RecruitmentButton(lang.raw("recruitment.previous"), icon = "lg_page_prev") {
                    controller.page = (controller.page - 1).coerceAtLeast(0)
                    open()
                },
                0,
                NAVIGATION_ROW,
            )
        }
        if (controller.page + 1 < pages) {
            add(
                pane,
                RecruitmentButton(lang.raw("recruitment.next"), icon = "lg_page_next") {
                    controller.page = (controller.page + 1).coerceAtMost(pages - 1)
                    open()
                },
                BOARD_COLUMNS - 1,
                NAVIGATION_ROW,
            )
        }
    }

    private fun add(pane: StaticPane, button: RecruitmentButton, x: Int, y: Int) {
        val base =
            button.guild?.let(GuildBannerItemResolver::resolveForDisplay)
                ?: NexoItemProvider.getItemStackOrFallback(button.icon) { ItemStack.of(Material.BOOK) }
        val emoji =
            button.guild?.let {
                MiniMessage.miniMessage().deserialize(emojis.emojiToFontTag(it.emoji)).append(Component.space())
            } ?: Component.empty()
        val item = base.name(emoji.append(GuiTextStyler.style(Component.text(button.label))))
        button.details.flatMap { it.chunked(LORE_COLUMNS) }.take(LORE_ROWS).forEach {
            item.lore(Component.text(it).color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
        }
        pane.addItem(GuiItem(item) { controller.run(button.action) }, x, y)
    }

    /** This menu has no external mutable payload. */
    override fun passData(data: Any?) = Unit
}
