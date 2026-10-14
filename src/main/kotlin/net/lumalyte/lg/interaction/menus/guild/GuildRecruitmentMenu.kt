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

    /** Refreshes from current service state; failures never open a stale private inbox. */
    override fun open(): Unit = controller.run {
        val input = controller.input
        if (input != null) {
            if (net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.supportsDialog(player)) {
                player.closeInventory()
                val navigation = navigator.currentNavigationToken()
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
                return@run
            }
            player.closeInventory()
            player.sendMessage(lang.msg("recruitment.feedback.input", "field" to input.label))
            val navigation = navigator.currentNavigationToken()
            val handler =
                object : ChatInputHandler {
                    override fun onChatInput(player: Player, input: String) {
                        if (player.isOnline && navigator.isNavigationCurrent(navigation)) {
                            controller.acceptInput(
                                input,
                            )
                        }
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
                2400L,
            )
        } else {
            val view = controller.view()
            val capacity = if (view.quickFilters.isEmpty()) 45 else 36
            val offset = if (view.quickFilters.isEmpty()) 0 else 1
            val pages = maxOf(1, (view.items.size + capacity - 1) / capacity)
            controller.page = controller.page.coerceIn(0, pages - 1)
            val gui =
                ChestGui(
                    6,
                    MenuTitleBuilder.build(controller.theme(), 6, "${view.title} ${controller.page + 1}/$pages"),
                )
            gui.setOnTopClick { it.isCancelled = true }
            gui.setOnBottomClick { it.isCancelled = true }
            gui.setOnGlobalDrag { it.isCancelled = true }
            val pane = StaticPane(0, 0, 9, 6)
            view.quickFilters.forEachIndexed { index, button -> add(pane, button, index, 0) }
            view.items.drop(controller.page * capacity).take(capacity).forEachIndexed {
                    index,
                    button,
                ->
                add(pane, button, index % 9, index / 9 + offset)
            }
            view.navigation.take(7).forEachIndexed { index, button -> add(pane, button, index + 1, 5) }
            if (controller.page > 0) {
                add(
                    pane,
                    RecruitmentButton(lang.raw("recruitment.previous"), icon = "lg_page_prev") {
                        controller.page = (controller.page - 1).coerceAtLeast(0)
                        open()
                    },
                    0,
                    5,
                )
            }
            if (controller.page + 1 < pages) {
                add(
                    pane,
                    RecruitmentButton(lang.raw("recruitment.next"), icon = "lg_page_next") {
                        controller.page = (controller.page + 1).coerceAtMost(pages - 1)
                        open()
                    },
                    8,
                    5,
                )
            }
            gui.addPane(pane)
            gui.show(player)
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
        button.details.flatMap { it.chunked(44) }.take(16).forEach {
            item.lore(Component.text(it).color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
        }
        pane.addItem(GuiItem(item) { controller.run(button.action) }, x, y)
    }

    /** This menu has no external mutable payload. */
    override fun passData(data: Any?) = Unit
}
