package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class GuildHomeMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val progressionService: net.lumalyte.lg.application.services.ProgressionService by inject()
    private val teleportationService: net.lumalyte.lg.infrastructure.services.TeleportationService by inject()

    override fun open() {
        val gui = ChestGui(6, "§6Guild Homes - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Home slots and status
        addHomeSlotsDisplay(pane, 0, 0)

        // Set home buttons
        addSetHomeButtons(pane, 0, 2)

        // Teleport buttons
        addTeleportButtons(pane, 0, 4)

        // Ally home teleport buttons (if perk unlocked)
        if (progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.application.services.PerkType.ALLY_HOME_ACCESS)) {
            addAllyHomeButtons(pane, 0, 5)
        }

        // Back button (shift down if ally homes shown)
        val backRow = if (progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.application.services.PerkType.ALLY_HOME_ACCESS)) 5 else 5
        addBackButton(pane, 8, backRow)

        gui.show(player)
    }

    private fun addHomeSlotsDisplay(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        val slotsItem = ItemStack.of(Material.BOOK)
            .name("§e🏠 Guild Home Slots")
            .lore("§7Homes Set: §f${allHomes.size}§7/${availableSlots}")
            .lore("§7")

        if (allHomes.hasHomes()) {
            allHomes.homes.forEach { entry ->
                val name = entry.key
                val home = entry.value
                val marker = if (name == "main") "§e[MAIN]" else ""
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
                slotsItem.lore("§7• §f$name $marker §7- §f$worldName")
            }
        } else {
            slotsItem.lore("§7No homes set yet")
        }

        slotsItem.lore("§7")
        if (allHomes.size < availableSlots) {
            slotsItem.lore("§aClick to set additional homes")
        } else {
            slotsItem.lore("§cMaximum slots reached")
        }

        val guiItem = GuiItem(slotsItem)
        pane.addItem(guiItem, x, y)
    }

    private fun addSetHomeButtons(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        // Set Main Home button
        if (!allHomes.homes.containsKey("main")) {
            val setMainItem = ItemStack.of(Material.GREEN_WOOL)
                .name("§aSet Main Home")
                .lore("§7Set your current location as main home")
                .lore("§7Allows §6/guild home §7teleportation")

            val mainGuiItem = GuiItem(setMainItem) {
                setGuildHome("main")
            }
            pane.addItem(mainGuiItem, x, y)
        }

        // Set Additional Home button (if slots available)
        if (allHomes.size < availableSlots) {
            val setAdditionalItem = ItemStack.of(Material.LIME_WOOL)
                .name("§eSet Additional Home")
                .lore("§7Set a named home location")
                .lore("§7Allows §6/guild home <name> §7teleportation")
                .lore("§7Available slots: §f${availableSlots - allHomes.size}")

            val additionalGuiItem = GuiItem(setAdditionalItem) {
                // This would open a menu to input home name, but for now let's use a simple approach
                player.sendMessage("§6Use §e/guild sethome <name> §6to set additional homes")
                player.sendMessage("§7Example: §e/guild sethome shop")
            }
            pane.addItem(additionalGuiItem, x + 2, y)
        }

        // Remove Homes button
        if (allHomes.hasHomes()) {
            val removeItem = ItemStack.of(Material.RED_WOOL)
                .name("§cRemove Homes")
                .lore("§7Remove guild home locations")

            val removeGuiItem = GuiItem(removeItem) {
                showRemoveHomesMenu()
            }
            pane.addItem(removeGuiItem, x + 4, y)
        }
    }

    private fun addTeleportButtons(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val hasActiveTeleport = teleportationService.hasActiveTeleport(player.uniqueId)

        if (hasActiveTeleport) {
            // Show cancel teleport button
            val cancelItem = ItemStack.of(Material.CLOCK)
                .name("§eCancel Teleport")
                .lore("§7Teleportation in progress...")
                .lore("§7Remaining: §f${teleportationService.getRemainingSeconds(player.uniqueId) ?: 0} seconds")

            val cancelGuiItem = GuiItem(cancelItem) {
                teleportationService.cancelTeleport(player.uniqueId)
                player.sendMessage("§c❌ Teleportation canceled!")
                open() // Refresh menu
            }
            pane.addItem(cancelGuiItem, x, y)
        } else if (allHomes.hasHomes()) {
            // Show teleport to main home button
            val mainHome = allHomes.defaultHome
            if (mainHome != null) {
                val teleportItem = ItemStack.of(Material.ENDER_PEARL)
                    .name("§bTeleport to Main Home")
                    .lore("§7Click to start teleportation countdown")
                    .lore("§7World: §f${Bukkit.getWorld(mainHome.worldId)?.name ?: "Unknown"}")
                    .lore("§7Countdown: §f5 seconds §7(don't move!)")

                val teleportGuiItem = GuiItem(teleportItem) {
                    startTeleportCountdown(mainHome)
                }
                pane.addItem(teleportGuiItem, x, y)
            }

            // Show list homes button if there are multiple homes
            if (allHomes.size > 1) {
                val listItem = ItemStack.of(Material.COMPASS)
                    .name("§eList All Homes")
                    .lore("§7View all available homes")
                    .lore("§7Use §6/guild home <name> §7to teleport")

                val listGuiItem = GuiItem(listItem) {
                    showHomesList()
                }
                pane.addItem(listGuiItem, x + 2, y)
            }
        } else {
            // No homes set
            val noHomeItem = ItemStack.of(Material.GRAY_DYE)
                .name("§7No Homes Set")
                .lore("§7Set a home location first")

            pane.addItem(GuiItem(noHomeItem), x, y)
        }
    }

    private fun addAllyHomeButtons(pane: StaticPane, x: Int, y: Int) {
        val allyHomes = guildService.getAllyHomes(guild.id)
        if (allyHomes.isEmpty()) {
            val noAllyItem = ItemStack.of(Material.GRAY_DYE)
                .name("§7No Ally Homes Available")
                .lore("§7Allied guilds must also have this perk")
                .lore("§7and have a home set")

            pane.addItem(GuiItem(noAllyItem), x, y)
            return
        }

        var slot = x
        for ((guildName, home) in allyHomes) {
            if (slot >= 7) break // Max 7 ally homes on row
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
            val allyItem = ItemStack.of(Material.ENDER_EYE)
                .name("§d⚔ $guildName")
                .lore("§7Ally guild home")
                .lore("§7World: §f$worldName")
                .lore("§7")
                .lore("§eClick to teleport")

            val guiItem = GuiItem(allyItem) {
                startTeleportCountdown(home)
            }
            pane.addItem(guiItem, slot, y)
            slot++
        }
    }

    private fun showRemoveHomesMenu() {
        val allHomes = guildService.getHomes(guild.id)
        if (!allHomes.hasHomes()) {
            player.sendMessage("§c❌ No homes to remove.")
            return
        }

        val gui = ChestGui(4, "§cRemove Guild Homes")
        val pane = StaticPane(0, 0, 9, 4)

        // Prevent moving items in the top or bottom inventory (same as main menu)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        
        // List homes for removal
        var slot = 0
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            if (slot < 27) { // Max 27 slots
                val removeItem = ItemStack.of(Material.RED_WOOL)
                    .name("§cRemove '$name'")
                    .lore("§7World: §f${Bukkit.getWorld(home.worldId)?.name ?: "Unknown"}")
                    .lore("§7")
                    .lore("§eClick to remove this home")

                val removeGuiItem = GuiItem(removeItem) {
                    val success = guildService.removeHome(guild.id, name, player.uniqueId)
                    if (success) {
                        player.sendMessage("§a✅ Home '$name' removed!")
                        showRemoveHomesMenu() // Refresh menu
                    } else {
                        player.sendMessage("§c❌ Failed to remove home '$name'.")
                    }
                }
                pane.addItem(removeGuiItem, slot % 9, slot / 9)
                slot++
            }
        }

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name("§eBack to Home Menu")
            .lore("§7Return to home management")

        val backGuiItem = GuiItem(backItem) {
            open() // Return to main home menu
        }
        pane.addItem(backGuiItem, 4, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun showHomesList() {
        val allHomes = guildService.getHomes(guild.id)
        if (!allHomes.hasHomes()) {
            player.sendMessage("§c❌ No homes set.")
            return
        }

        player.sendMessage("§6=== Guild Homes ===")
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            val marker = if (name == "main") "§e[MAIN]" else ""
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
            player.sendMessage("§7• §f$name $marker §7- §f$worldName")
        }
        player.sendMessage("§7Use §6/guild home <name> §7to teleport")
        player.sendMessage("§6==================")
    }

    private fun setGuildHome(homeName: String = "main") {
        val location = player.location
        val home = GuildHome(
            worldId = location.world.uid,
            position = net.lumalyte.lg.domain.values.Position3D(
                location.x.toInt(), location.y.toInt(), location.z.toInt()
            )
        )

        // Check if location is safe (if safety check is enabled)
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(location)) {
            player.sendMessage("§c❌ This location is not safe to set as guild home!")
            player.sendMessage("§7Try setting your home on solid ground with space above you.")
            open() // Reopen menu to show current state
            return
        }

        val success = guildService.setHome(guild.id, homeName, home, player.uniqueId)
        if (success) {
            val homeLabel = if (homeName == "main") "main home" else "home '$homeName'"
            player.sendMessage("§a✅ Guild $homeLabel set to your current location!")
            player.sendMessage("§7Members can now use §6/guild home ${if (homeName == "main") "" else homeName}§7to teleport here.")

            // Refresh the guild data and reopen menu
            guild = guildService.getGuild(guild.id) ?: guild
            open()
        } else {
            player.sendMessage("§c❌ Failed to set guild home. Please try again.")
            open() // Reopen menu to show current state
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name("§eBack to Control Panel")
            .lore("§7Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun startTeleportCountdown(home: GuildHome) {
        val world = Bukkit.getWorld(home.worldId)
        if (world == null) {
            player.sendMessage("§c❌ Could not find the world for guild home.")
            return
        }

        val targetLocation = org.bukkit.Location(
            world,
            home.position.x.toDouble() + 0.5,
            home.position.y.toDouble(),
            home.position.z.toDouble() + 0.5,
            player.location.yaw,
            player.location.pitch
        )

        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(targetLocation)) {
            player.sendMessage("§c❌ Guild home location is not safe to teleport to!")
            player.sendMessage("§7Try setting your home on solid ground with space above you.")
            return
        }

        teleportationService.startTeleport(player, targetLocation)
    }

    private fun isLocationSafe(location: org.bukkit.Location): Boolean {
        val block = location.block
        val blockBelow = location.clone().subtract(0.0, 1.0, 0.0).block
        val blockAbove = location.clone().add(0.0, 1.0, 0.0).block

        // Define dangerous materials that should prevent teleportation
        val dangerousMaterials = setOf(
            org.bukkit.Material.LAVA,
            org.bukkit.Material.FIRE,
            org.bukkit.Material.SOUL_FIRE,
            org.bukkit.Material.CACTUS,
            org.bukkit.Material.SWEET_BERRY_BUSH,
            org.bukkit.Material.POINTED_DRIPSTONE,
            org.bukkit.Material.MAGMA_BLOCK
        )

        // Check if location has safe ground and space to stand
        val hasSafeGround = blockBelow.type.isSolid || blockBelow.type == org.bukkit.Material.GRASS_BLOCK ||
                           blockBelow.type == org.bukkit.Material.DIRT || blockBelow.type == org.bukkit.Material.COARSE_DIRT ||
                           blockBelow.type == org.bukkit.Material.PODZOL || blockBelow.type == org.bukkit.Material.SAND ||
                           blockBelow.type == org.bukkit.Material.RED_SAND || blockBelow.type == org.bukkit.Material.GRAVEL ||
                           blockBelow.type == org.bukkit.Material.STONE || blockBelow.type == org.bukkit.Material.COBBLESTONE

        val hasSpaceToStand = !block.type.isSolid || block.type == org.bukkit.Material.SHORT_GRASS ||
                             block.type == org.bukkit.Material.TALL_GRASS || block.type == org.bukkit.Material.FERN ||
                             block.type == org.bukkit.Material.LARGE_FERN

        val hasHeadSpace = !blockAbove.type.isSolid || blockAbove.type == org.bukkit.Material.SHORT_GRASS ||
                          blockAbove.type == org.bukkit.Material.TALL_GRASS || blockAbove.type == org.bukkit.Material.FERN ||
                          blockAbove.type == org.bukkit.Material.LARGE_FERN

        val noDangerousBlocks = !dangerousMaterials.contains(blockBelow.type) &&
                               !dangerousMaterials.contains(block.type) &&
                               !dangerousMaterials.contains(blockAbove.type)

        return hasSafeGround && hasSpaceToStand && hasHeadSpace && noDangerousBlocks
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

