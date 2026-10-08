package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildStartStep
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

internal class GuildGettingStartedMenu(navigator: MenuNavigator, private val player: Player, guildId: UUID?) : Menu, KoinComponent {
    private val navigation = GuildGettingStartedNavigation(navigator, player, guildId)
    private val lang: LangService by inject()
    override fun open() {
        val current = navigation.snapshot() ?: run { player.sendMessage(lang.msg("onboarding.unavailable")); return }
        val title = lang.guiTitle("onboarding.title")
        val gui = ChestGui(3, current.guild?.let { MenuTitleBuilder.build(it.guiTheme, 3, title) } ?: title)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click.isShiftClick) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 3)
        val overview = if (current.guild == null) lang.gui("onboarding.join_first") else
            lang.gui("onboarding.overview", "members" to current.members, "capacity" to current.capacity)
        pane.addItem(GuiItem(ItemStack.of(Material.BOOK).name(lang.gui("onboarding.title"))
            .lore(text = loreLines(overview))), 4, 0)
        current.steps.forEachIndexed { index, step -> addStep(pane, index, step) }
        button(pane, 0, Material.BOOK, lang.gui("onboarding.help")) { navigation.help() }
        button(pane, 2, Material.CLOCK, lang.gui("onboarding.refresh")) { open() }
        button(pane, 4, Material.ARROW, lang.gui("menu.common.item.back.name")) { navigation.back() }
        button(pane, 6, Material.PAPER, lang.gui("onboarding.dismiss")) { navigation.dismiss() }
        gui.addPane(pane)
        gui.show(player)
    }

    private fun addStep(pane: StaticPane, index: Int, step: GuildStartStep) {
        val lines = listOf(navigation.stateName(step.state)) + loreLines(navigation.details(step.topic)) +
            if (step.reason.startsWith("onboarding.details.")) emptyList() else loreLines(navigation.reason(step), NamedTextColor.YELLOW)
        val item = NexoItemProvider.getItemStackOrFallback("lg_book") { ItemStack.of(Material.BOOK) }
            .name(navigation.title(step.topic))
            .lore(text = lines)
        pane.addItem(GuiItem(item) { navigation.select(step.topic) }, index, 1)
    }

    private fun button(pane: StaticPane, x: Int, material: Material, label: net.kyori.adventure.text.Component, action: () -> Unit) {
        pane.addItem(GuiItem(ItemStack.of(material).name(label)) { action() }, x, 2)
    }
    private fun loreLines(component: Component, color: NamedTextColor = NamedTextColor.GRAY): List<Component> {
        val lines = mutableListOf<String>()
        var line = ""
        PlainTextComponentSerializer.plainText().serialize(component).split(Regex("\\s+")).forEach { word ->
            if (line.isNotEmpty() && line.length + word.length + 1 > 48) {
                lines += line; line = ""
            }
            line = if (line.isEmpty()) word else "$line $word"
        }
        if (line.isNotEmpty()) lines += line
        return lines.map { Component.text(it).style(component.style()).color(color) }
    }
    override fun passData(data: Any?) = Unit
}
