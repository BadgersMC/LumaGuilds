package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Personal preferences are independent of guild management permissions. */
internal class PlayerChatSettingsMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
) : Menu,
    KoinComponent {
    private val service: ChatService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val settings = service.getVisibilitySettings(player.uniqueId)
        val gui = ChestGui(MENU_ROWS, lang.guiTitle("community.chat.settings"))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click.isShiftClick) it.isCancelled = true }
        val pane = StaticPane(0, 0, MENU_WIDTH, MENU_ROWS)
        addToggles(pane, settings)
        pane.addItem(
            GuiItem(ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))) {
                navigator.goBack()
            },
            BACK_COLUMN,
            2,
        )
        gui.addPane(pane)
        gui.show(player)
    }

    private fun addToggles(pane: StaticPane, settings: ChatVisibilitySettings) {
        add(
            pane,
            2,
            Toggle(lang.gui("community.chat.global_visibility"), settings.globalChatVisible) {
                it.copy(globalChatVisible = !it.globalChatVisible)
            },
        )
        add(
            pane,
            INDICATOR_COLUMN,
            Toggle(lang.gui("community.chat.indicator"), settings.destinationIndicator) {
                it.copy(destinationIndicator = !it.destinationIndicator)
            },
        )
    }

    private data class Toggle(
        val title: net.kyori.adventure.text.Component,
        val enabled: Boolean,
        val change: (ChatVisibilitySettings) -> ChatVisibilitySettings,
    )

    private fun add(pane: StaticPane, x: Int, toggle: Toggle) {
        pane.addItem(
            GuiItem(ItemStack.of(if (toggle.enabled) Material.LIME_DYE else Material.GRAY_DYE).name(toggle.title)) {
                val current = service.getVisibilitySettings(player.uniqueId)
                if (!service.updateVisibilitySettings(
                        player.uniqueId,
                        toggle.change(current),
                    )
                ) {
                    player.sendMessage(lang.msg("community.chat.failed"))
                }
                open()
            },
            x,
            1,
        )
    }

    override fun passData(data: Any?) = Unit

    private companion object {
        const val MENU_WIDTH = 9
        const val MENU_ROWS = 3
        const val INDICATOR_COLUMN = 6
        const val BACK_COLUMN = 4
    }
}
