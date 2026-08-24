package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
    private val rankService: net.lumalyte.lg.application.services.RankService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.guild_home.title", "guild" to guild.name)))
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

        // Per-home access config buttons (row 3) — managers only
        addHomeAccessButtons(pane)

        // Ally home teleport buttons (if perk unlocked)
        if (progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS)) {
            addAllyHomeButtons(pane, 0, 5)
            addAllyHomeAccessButton(pane)
        }

        // Back button (shift down if ally homes shown)
        val backRow = if (progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS)) 5 else 5
        addBackButton(pane, 8, backRow)

        gui.show(player)
    }

    private fun addHomeSlotsDisplay(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        val slotsItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.guild_home.slots.name"))
            .lore(lang.legacy("menu.guild_home.slots.count", "count" to allHomes.size, "total" to availableSlots))
            .lore(lang.legacy("menu.common.blank"))

        if (allHomes.hasHomes()) {
            allHomes.homes.forEach { entry ->
                val name = entry.key
                val home = entry.value
                val marker = if (name == "main") lang.raw("menu.guild_home.slots.main") else ""
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
                slotsItem.lore(lang.legacy("menu.guild_home.slots.row", "home" to name, "marker" to marker, "world" to worldName))
            }
        } else {
            slotsItem.lore(lang.legacy("menu.guild_home.slots.none"))
        }

        slotsItem.lore(lang.legacy("menu.common.blank"))
        if (allHomes.size < availableSlots) {
            slotsItem.lore(lang.legacy("menu.guild_home.slots.additional"))
        } else {
            slotsItem.lore(lang.legacy("menu.guild_home.slots.maximum"))
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
                .name(lang.legacy("menu.guild_home.set.main.name"))
                .lore(lang.legacy("menu.guild_home.set.main.description"))
                .lore(lang.legacy("menu.guild_home.set.main.command"))

            val mainGuiItem = GuiItem(setMainItem) {
                setGuildHome("main")
            }
            pane.addItem(mainGuiItem, x, y)
        }

        // Set Additional Home button (if slots available)
        if (allHomes.size < availableSlots) {
            val setAdditionalItem = ItemStack.of(Material.LIME_WOOL)
                .name(lang.legacy("menu.guild_home.set.additional.name"))
                .lore(lang.legacy("menu.guild_home.set.additional.description"))
                .lore(lang.legacy("menu.guild_home.set.additional.command"))
                .lore(lang.legacy("menu.guild_home.set.additional.available", "count" to availableSlots - allHomes.size))

            val additionalGuiItem = GuiItem(setAdditionalItem) {
                // This would open a menu to input home name, but for now let's use a simple approach
                player.sendMessage(lang.msg("menu.guild_home.feedback.sethome_command"))
                player.sendMessage(lang.msg("menu.guild_home.feedback.sethome_example"))
            }
            pane.addItem(additionalGuiItem, x + 2, y)
        }

        // Remove Homes button
        if (allHomes.hasHomes()) {
            val removeItem = ItemStack.of(Material.RED_WOOL)
                .name(lang.legacy("menu.guild_home.remove.name"))
                .lore(lang.legacy("menu.guild_home.remove.description"))

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
                .name(lang.legacy("menu.guild_home.teleport.cancel.name"))
                .lore(lang.legacy("menu.guild_home.teleport.cancel.description"))
                .lore(lang.legacy("menu.guild_home.teleport.cancel.remaining", "seconds" to (teleportationService.getRemainingSeconds(player.uniqueId) ?: 0)))

            val cancelGuiItem = GuiItem(cancelItem) {
                teleportationService.cancelTeleport(player.uniqueId)
                player.sendMessage(lang.msg("menu.guild_home.feedback.teleport_cancelled"))
                open() // Refresh menu
            }
            pane.addItem(cancelGuiItem, x, y)
        } else if (allHomes.hasHomes()) {
            // Show teleport to main home button
            val mainHome = allHomes.defaultHome
            if (mainHome != null) {
                val teleportItem = ItemStack.of(Material.ENDER_PEARL)
                    .name(lang.legacy("menu.guild_home.teleport.main.name"))
                    .lore(lang.legacy("menu.guild_home.teleport.main.description"))
                    .lore(lang.legacy("menu.guild_home.world", "world" to (Bukkit.getWorld(mainHome.worldId)?.name ?: lang.raw("general.unknown"))))
                    .lore(lang.legacy("menu.guild_home.teleport.main.countdown"))

                val teleportGuiItem = GuiItem(teleportItem) {
                    startTeleportCountdown(mainHome)
                }
                pane.addItem(teleportGuiItem, x, y)
            }

            // Show list homes button if there are multiple homes
            if (allHomes.size > 1) {
                val listItem = ItemStack.of(Material.COMPASS)
                    .name(lang.legacy("menu.guild_home.list.name"))
                    .lore(lang.legacy("menu.guild_home.list.description"))
                    .lore(lang.legacy("menu.guild_home.list.command"))

                val listGuiItem = GuiItem(listItem) {
                    showHomesList()
                }
                pane.addItem(listGuiItem, x + 2, y)
            }
        } else {
            // No homes set
            val noHomeItem = ItemStack.of(Material.GRAY_DYE)
                .name(lang.legacy("menu.guild_home.teleport.none.name"))
                .lore(lang.legacy("menu.guild_home.teleport.none.description"))

            pane.addItem(GuiItem(noHomeItem), x, y)
        }
    }

    private fun addHomeAccessButtons(pane: StaticPane) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME)) return
        val homes = guildService.getHomes(guild.id).homes.entries.toList()
        homes.take(9).forEachIndexed { idx, (homeName, _) ->
            val item = ItemStack.of(Material.IRON_DOOR)
                .name(lang.legacy("menu.guild_home.access.name", "home" to homeName))
                .lore(lang.legacy("menu.guild_home.access.description"))
                .lore(lang.legacy("menu.guild_home.access.click"))
            pane.addItem(GuiItem(item) {
                menuNavigator.openMenu(menuFactory.createHomeAccessMenu(menuNavigator, player, guild, homeName))
            }, idx, 3)
        }
    }

    private fun addAllyHomeAccessButton(pane: StaticPane) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME)) return
        val allyAccessItem = ItemStack.of(Material.IRON_DOOR)
            .name(lang.legacy("menu.guild_home.ally_access.name"))
            .lore(lang.legacy("menu.guild_home.ally_access.description"))
            .lore(lang.legacy("menu.guild_home.access.click"))
        pane.addItem(GuiItem(allyAccessItem) {
            menuNavigator.openMenu(menuFactory.createAllyHomeAccessMenu(menuNavigator, player, guild))
        }, 7, 5)
    }

    private fun addAllyHomeButtons(pane: StaticPane, x: Int, y: Int) {
        val allyHomes = guildService.getAllyHomes(guild.id)
        if (allyHomes.isEmpty()) {
            val noAllyItem = ItemStack.of(Material.GRAY_DYE)
                .name(lang.legacy("menu.guild_home.ally.none.name"))
                .lore(lang.legacy("menu.guild_home.ally.none.perk"))
                .lore(lang.legacy("menu.guild_home.ally.none.home"))

            pane.addItem(GuiItem(noAllyItem), x, y)
            return
        }

        var slot = x
        for ((guildName, home) in allyHomes) {
            if (slot >= 7) break // Max 7 ally homes on row
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
            val targetGuild = guildService.getGuildByName(guildName)
            val allowed = targetGuild != null &&
                guildService.canUseAllyHome(player.uniqueId, guild.id, targetGuild.id)
            val allyItem = ItemStack.of(if (allowed) Material.ENDER_EYE else Material.BARRIER)
                .name(if (allowed) lang.legacy("menu.guild_home.ally.name", "guild" to guildName) else lang.legacy("menu.guild_home.ally.locked", "guild" to guildName))
                .lore(lang.legacy("menu.guild_home.ally.description"))
                .lore(lang.legacy("menu.guild_home.world", "world" to worldName))
                .lore(lang.legacy("menu.common.blank"))
                .lore(if (allowed) lang.legacy("menu.guild_home.ally.teleport") else lang.legacy("menu.guild_home.ally.denied"))

            val guiItem = GuiItem(allyItem) {
                if (!allowed) {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.ally_denied"))
                    player.sendMessage(lang.msg("menu.guild_home.feedback.ally_reason"))
                    return@GuiItem
                }
                startTeleportCountdown(home)
            }
            pane.addItem(guiItem, slot, y)
            slot++
        }
    }

    private fun showRemoveHomesMenu() {
        val allHomes = guildService.getHomes(guild.id)
        if (!allHomes.hasHomes()) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.no_homes_remove"))
            return
        }

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4, lang.legacy("menu.guild_home.remove.title")))
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
                    .name(lang.legacy("menu.guild_home.remove.home", "home" to name))
                    .lore(lang.legacy("menu.guild_home.world", "world" to (Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown"))))
                    .lore(lang.legacy("menu.common.blank"))
                    .lore(lang.legacy("menu.guild_home.remove.click"))

                val removeGuiItem = GuiItem(removeItem) {
                    val success = guildService.removeHome(guild.id, name, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("menu.guild_home.feedback.removed", "home" to name))
                        showRemoveHomesMenu() // Refresh menu
                    } else {
                        player.sendMessage(lang.msg("menu.guild_home.feedback.remove_failed", "home" to name))
                    }
                }
                pane.addItem(removeGuiItem, slot % 9, slot / 9)
                slot++
            }
        }

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.guild_home.remove.back.name"))
            .lore(lang.legacy("menu.guild_home.remove.back.description"))

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
            player.sendMessage(lang.msg("menu.guild_home.feedback.no_homes"))
            return
        }

        player.sendMessage(lang.msg("menu.guild_home.list.header"))
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            val marker = if (name == "main") lang.raw("menu.guild_home.slots.main") else ""
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
            player.sendMessage(lang.msg("menu.guild_home.slots.row", "home" to name, "marker" to marker, "world" to worldName))
        }
        player.sendMessage(lang.msg("menu.guild_home.list.command"))
        player.sendMessage(lang.msg("menu.guild_home.list.footer"))
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
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_set"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
            open() // Reopen menu to show current state
            return
        }

        val success = guildService.setHome(guild.id, homeName, home, player.uniqueId)
        if (success) {
            val homeLabel = if (homeName == "main") lang.raw("menu.guild_home.feedback.main_home") else lang.legacy("menu.guild_home.feedback.named_home", "home" to homeName)
            player.sendMessage(lang.msg("menu.guild_home.feedback.set", "home" to homeLabel))
            player.sendMessage(lang.msg("menu.guild_home.feedback.teleport_command", "home" to if (homeName == "main") "" else homeName))

            // Refresh the guild data and reopen menu
            guild = guildService.getGuild(guild.id) ?: guild
            open()
        } else {
            player.sendMessage(lang.msg("menu.guild_home.feedback.set_failed"))
            open() // Reopen menu to show current state
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.guild_home.back.name"))
            .lore(lang.legacy("menu.guild_home.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun startTeleportCountdown(home: GuildHome) {
        val world = Bukkit.getWorld(home.worldId)
        if (world == null) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.world_missing"))
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
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_teleport"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
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

