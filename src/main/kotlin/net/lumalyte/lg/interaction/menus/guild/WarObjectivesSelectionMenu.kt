package net.lumalyte.lg.interaction.menus.guild

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
    private val tempObjectives = currentObjectives.toMutableSet()

    override fun open() {
        val claimsEnabled = configService.loadConfig().claimsEnabled

        val gui = ChestGui(5, "§6⚔ War Objectives")
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
            .name("§e📖 Objective Info")
            .lore("§7Select at least one objective for the war.")
            .lore("§7The first guild to complete any objective wins!")
            .lore("")
            .lore("§7Current Objectives: §f${tempObjectives.size}")

        pane.addItem(GuiItem(item) {}, x, y)
    }

    private fun addKillsObjectiveItem(pane: StaticPane, x: Int, y: Int) {
        val killObjective = tempObjectives.firstOrNull { it.type == ObjectiveType.KILLS }
        val hasObjective = killObjective != null
        val currentValue = killObjective?.targetValue ?: 10

        val item = ItemStack.of(if (hasObjective) Material.DIAMOND_SWORD else Material.IRON_SWORD)
            .name(if (hasObjective) "§a✓ §cKills Objective" else "§7Kills Objective")
            .lore("§7Kill enemy guild members")
            .lore("")
            .lore("§7Current target: §f$currentValue kills")
            .lore("")
            .lore("§7Available targets:")
            .lore("  §8▪ §f5 kills §7- Quick skirmish")
            .lore("  §8▪ §f10 kills §7- Standard battle")
            .lore("  §8▪ §f25 kills §7- Extended war")
            .lore("  §8▪ §f50 kills §7- Epic campaign")
            .lore("")
            .lore(if (hasObjective) "§eLeft-click to cycle target" else "§eLeft-click to add")
            .lore(if (hasObjective) "§cRight-click to remove" else "")

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
                    description = "Kill $newTarget enemy players"
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
            .name(if (hasObjective) "§a✓ §eTime Survival" else "§7Time Survival")
            .lore("§7Survive for a set duration")
            .lore("")
            .lore("§7Current target: §f$currentValue hours")
            .lore("")
            .lore("§7Available durations:")
            .lore("  §8▪ §f12 hours §7- Half day")
            .lore("  §8▪ §f24 hours §7- One day")
            .lore("  §8▪ §f48 hours §7- Two days")
            .lore("  §8▪ §f72 hours §7- Three days")
            .lore("")
            .lore(if (hasObjective) "§eLeft-click to cycle duration" else "§eLeft-click to add")
            .lore(if (hasObjective) "§cRight-click to remove" else "")

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
                    description = "Survive for $newTarget hours"
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
            .name(if (hasObjective) "§a✓ §6Claims Captured" else "§7Claims Captured")
            .lore("§7Capture enemy territory")
            .lore("")
            .lore("§7Current target: §f$currentValue claims")
            .lore("")
            .lore("§7Available targets:")
            .lore("  §8▪ §f1 claim §7- Raid")
            .lore("  §8▪ §f3 claims §7- Invasion")
            .lore("  §8▪ §f5 claims §7- Conquest")
            .lore("  §8▪ §f10 claims §7- Domination")
            .lore("")
            .lore(if (hasObjective) "§eLeft-click to cycle target" else "§eLeft-click to add")
            .lore(if (hasObjective) "§cRight-click to remove" else "")

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
                    description = "Capture $newTarget enemy claims"
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
            .name(if (canSave) "§a✅ Save Objectives" else "§c❌ No Objectives Selected")
            .lore(if (canSave) {
                "§7Selected: §f${tempObjectives.size} objective(s)"
            } else {
                "§7You must select at least one objective"
            })
            .lore("")
            .lore(if (canSave) "§eClick to save and return" else "§7Select an objective first")

        val guiItem = GuiItem(item) {
            if (canSave) {
                callback(tempObjectives.toSet())
                player.sendMessage("§a✅ War objectives updated!")
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                menuNavigator.goBack()
            } else {
                player.sendMessage("§c❌ You must select at least one objective!")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.BARRIER)
            .name("§c✖ Cancel")
            .lore("§7Discard changes")

        val guiItem = GuiItem(item) {
            player.sendMessage("§7Changes discarded.")
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.ARROW)
            .name("§e← Back")
            .lore("§7Return without saving")

        val guiItem = GuiItem(item) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        // Not needed for this menu
    }
}
