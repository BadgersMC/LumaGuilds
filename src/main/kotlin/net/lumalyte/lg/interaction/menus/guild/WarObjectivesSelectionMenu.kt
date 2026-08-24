package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.GuiTheme
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.WarObjective
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarObjectivesSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val currentObjectives: MutableSet<WarObjective>,
    private val callback: (Set<WarObjective>) -> Unit
) : Menu, KoinComponent {

    private val configService: ConfigService by inject()
    private val lang: LangService by inject()
    private val tempObjectives = currentObjectives.toMutableSet()

    override fun open() {
        val claimsEnabled = configService.loadConfig().claimsEnabled

        val gui = ChestGui(5, MenuTitleBuilder.build(GuiTheme.NEUTRAL, 5, lang.guiTitle("menu.war_objectives.title")))
        val pane = StaticPane(0, 0, 9, 5)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        gui.addPane(pane)

        // Row 0: Header info
        addInfoItem(pane, 4, 0)

        // Row 1-2: Objective types
        addKillsObjectiveItem(pane, 1, 1)
        addTimeSurvivalObjectiveItem(pane, 3, 1)

        if (claimsEnabled) {
            addClaimsObjectiveItem(pane, 5, 1)
        }

        // Row 3: Action buttons
        addSaveButton(pane, 3, 3)
        addCancelButton(pane, 5, 3)

        // Row 4: Back button
        addBackButton(pane, 4, 4)

        gui.show(player)
    }

    private fun addInfoItem(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.war_objectives.info.name"))
            .lore(lang.gui("menu.war_objectives.info.description"))
            .lore(lang.gui("menu.war_objectives.info.win_condition"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.info.current", "count" to tempObjectives.size))

        pane.addItem(GuiItem(item) {}, x, y)
    }

    private fun addKillsObjectiveItem(pane: StaticPane, x: Int, y: Int) {
        val killObjective = tempObjectives.firstOrNull { it.type == ObjectiveType.KILLS }
        val hasObjective = killObjective != null
        val currentValue = killObjective?.targetValue ?: 10

        val item = ItemStack.of(if (hasObjective) Material.DIAMOND_SWORD else Material.IRON_SWORD)
            .name(if (hasObjective) lang.gui("menu.war_objectives.kills.selected") else lang.gui("menu.war_objectives.kills.name"))
            .lore(lang.gui("menu.war_objectives.kills.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.kills.current", "count" to currentValue))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.common.available_targets"))
            .lore(lang.gui("menu.war_objectives.kills.target_5"))
            .lore(lang.gui("menu.war_objectives.kills.target_10"))
            .lore(lang.gui("menu.war_objectives.kills.target_25"))
            .lore(lang.gui("menu.war_objectives.kills.target_50"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (hasObjective) lang.gui("menu.war_objectives.common.cycle_target") else lang.gui("menu.war_objectives.common.add"))
            .lore(if (hasObjective) lang.gui("menu.war_objectives.common.remove") else lang.gui("menu.common.blank"))

        val guiItem = GuiItem(item) { event ->
            if (event.isRightClick && hasObjective) {
                // Remove objective
                tempObjectives.removeIf { it.type == ObjectiveType.KILLS }
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)
            } else {
                // Cycle or add objective
                val killTargets = listOf(5, 10, 25, 50)
                val currentIndex = killTargets.indexOf(currentValue)
                val nextIndex = if (currentIndex == -1 || currentIndex >= killTargets.size - 1) 0 else currentIndex + 1
                val newTarget = killTargets[nextIndex]

                tempObjectives.removeIf { it.type == ObjectiveType.KILLS }
                tempObjectives.add(WarObjective(
                    type = ObjectiveType.KILLS,
                    targetValue = newTarget,
                    description = PlainTextComponentSerializer.plainText().serialize(
                        lang.msg("menu.war_objectives.kills.objective_description", "count" to newTarget)
                    )
                ))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
            }
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTimeSurvivalObjectiveItem(pane: StaticPane, x: Int, y: Int) {
        val timeObjective = tempObjectives.firstOrNull { it.type == ObjectiveType.TIME_SURVIVAL }
        val hasObjective = timeObjective != null
        val currentValue = timeObjective?.targetValue ?: 24 // hours

        val item = ItemStack.of(if (hasObjective) Material.CLOCK else Material.STONE)
            .name(if (hasObjective) lang.gui("menu.war_objectives.survival.selected") else lang.gui("menu.war_objectives.survival.name"))
            .lore(lang.gui("menu.war_objectives.survival.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.survival.current", "count" to currentValue))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.survival.available"))
            .lore(lang.gui("menu.war_objectives.survival.duration_12"))
            .lore(lang.gui("menu.war_objectives.survival.duration_24"))
            .lore(lang.gui("menu.war_objectives.survival.duration_48"))
            .lore(lang.gui("menu.war_objectives.survival.duration_72"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (hasObjective) lang.gui("menu.war_objectives.common.cycle_duration") else lang.gui("menu.war_objectives.common.add"))
            .lore(if (hasObjective) lang.gui("menu.war_objectives.common.remove") else lang.gui("menu.common.blank"))

        val guiItem = GuiItem(item) { event ->
            if (event.isRightClick && hasObjective) {
                tempObjectives.removeIf { it.type == ObjectiveType.TIME_SURVIVAL }
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)
            } else {
                val durations = listOf(12, 24, 48, 72)
                val currentIndex = durations.indexOf(currentValue)
                val nextIndex = if (currentIndex == -1 || currentIndex >= durations.size - 1) 0 else currentIndex + 1
                val newTarget = durations[nextIndex]

                tempObjectives.removeIf { it.type == ObjectiveType.TIME_SURVIVAL }
                tempObjectives.add(WarObjective(
                    type = ObjectiveType.TIME_SURVIVAL,
                    targetValue = newTarget,
                    description = PlainTextComponentSerializer.plainText().serialize(
                        lang.msg("menu.war_objectives.survival.objective_description", "count" to newTarget)
                    )
                ))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
            }
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addClaimsObjectiveItem(pane: StaticPane, x: Int, y: Int) {
        val claimObjective = tempObjectives.firstOrNull { it.type == ObjectiveType.CLAIMS_CAPTURED }
        val hasObjective = claimObjective != null
        val currentValue = claimObjective?.targetValue ?: 3

        val item = ItemStack.of(if (hasObjective) Material.GOLDEN_PICKAXE else Material.WOODEN_PICKAXE)
            .name(if (hasObjective) lang.gui("menu.war_objectives.claims.selected") else lang.gui("menu.war_objectives.claims.name"))
            .lore(lang.gui("menu.war_objectives.claims.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.claims.current", "count" to currentValue))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_objectives.common.available_targets"))
            .lore(lang.gui("menu.war_objectives.claims.target_1"))
            .lore(lang.gui("menu.war_objectives.claims.target_3"))
            .lore(lang.gui("menu.war_objectives.claims.target_5"))
            .lore(lang.gui("menu.war_objectives.claims.target_10"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (hasObjective) lang.gui("menu.war_objectives.common.cycle_target") else lang.gui("menu.war_objectives.common.add"))
            .lore(if (hasObjective) lang.gui("menu.war_objectives.common.remove") else lang.gui("menu.common.blank"))

        val guiItem = GuiItem(item) { event ->
            if (event.isRightClick && hasObjective) {
                tempObjectives.removeIf { it.type == ObjectiveType.CLAIMS_CAPTURED }
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)
            } else {
                val targets = listOf(1, 3, 5, 10)
                val currentIndex = targets.indexOf(currentValue)
                val nextIndex = if (currentIndex == -1 || currentIndex >= targets.size - 1) 0 else currentIndex + 1
                val newTarget = targets[nextIndex]

                tempObjectives.removeIf { it.type == ObjectiveType.CLAIMS_CAPTURED }
                tempObjectives.add(WarObjective(
                    type = ObjectiveType.CLAIMS_CAPTURED,
                    targetValue = newTarget,
                    description = PlainTextComponentSerializer.plainText().serialize(
                        lang.msg("menu.war_objectives.claims.objective_description", "count" to newTarget)
                    )
                ))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
            }
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addSaveButton(pane: StaticPane, x: Int, y: Int) {
        val canSave = tempObjectives.isNotEmpty()

        val item = ItemStack.of(if (canSave) Material.LIME_WOOL else Material.GRAY_WOOL)
            .name(if (canSave) lang.gui("menu.war_objectives.save.name") else lang.gui("menu.war_objectives.save.disabled"))
            .lore(if (canSave) {
                lang.gui("menu.war_objectives.save.selected", "count" to tempObjectives.size)
            } else {
                lang.gui("menu.war_objectives.save.requirement")
            })
            .lore(lang.gui("menu.common.blank"))
            .lore(if (canSave) lang.gui("menu.war_objectives.save.click") else lang.gui("menu.war_objectives.save.hint"))

        val guiItem = GuiItem(item) {
            if (canSave) {
                callback(tempObjectives.toSet())
                player.sendMessage(lang.msg("menu.war_objectives.feedback.updated"))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                menuNavigator.goBack()
            } else {
                player.sendMessage(lang.msg("menu.war_objectives.feedback.required"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.war_objectives.cancel.name"))
            .lore(lang.gui("menu.war_objectives.cancel.description"))

        val guiItem = GuiItem(item) {
            player.sendMessage(lang.msg("menu.war_objectives.feedback.discarded"))
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.war_objectives.back.name"))
            .lore(lang.gui("menu.war_objectives.back.description"))

        val guiItem = GuiItem(item) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        // Not needed for this menu
    }
}
