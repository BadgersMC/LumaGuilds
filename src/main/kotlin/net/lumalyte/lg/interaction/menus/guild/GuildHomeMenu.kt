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
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildHomeMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // Teleportation tracking
    private     data class TeleportSession(
        val player: Player,
        val targetLocation: org.bukkit.Location,
        val startLocation: org.bukkit.Location,
        var countdownTask: BukkitRunnable? = null,
        var remainingSeconds: Int = 5
    )

    private val activeTeleports = mutableMapOf<UUID, TeleportSession>()

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Guild Homes - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Home slots and status
        addHomeSlotsDisplay(pane, 0, 0)

        // Set home buttons
        addSetHomeButtons(pane, 0, 2)

        // Teleport buttons
        addTeleportButtons(pane, 0, 4)

        // Back button
        addBackButton(pane, 4, 5)

        gui.show(player)
    }

    private fun addHomeSlotsDisplay(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        val slotsItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<yellow>🏠 Guild Home Slots")
            .addAdventureLore(player, messageService, "<gray>Homes Set: <white>${allHomes.size}<gray>/${availableSlots}")
            .addAdventureLore(player, messageService, "<gray>")

        if (allHomes.hasHomes()) {
            allHomes.homes.forEach { entry ->
                val name = entry.key
                val home = entry.value
                val marker = if (name == "main") "<yellow>[MAIN]" else ""
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
                slotsItem.addAdventureLore(player, messageService, "<gray>• <white>$name $marker <gray>- <white>$worldName")
            }
        } else {
            slotsItem.addAdventureLore(player, messageService, "<gray>No homes set yet")
        }

        slotsItem.addAdventureLore(player, messageService, "<gray>")
        if (allHomes.size < availableSlots) {
            slotsItem.addAdventureLore(player, messageService, "<green>Click to set additional homes")
        } else {
            slotsItem.addAdventureLore(player, messageService, "<red>Maximum slots reached")
        }

        val guiItem = GuiItem(slotsItem)
        pane.addItem(guiItem, x, y)
    }

    private fun addSetHomeButtons(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        // Set Main Home button
        if (!allHomes.homes.containsKey("main")) {
            val setMainItem = ItemStack(Material.GREEN_WOOL)
                .setAdventureName(player, messageService, "<green>Set Main Home")
                .addAdventureLore(player, messageService, "<gray>Set your current location as main home")
                .addAdventureLore(player, messageService, "<gray>Allows <gold>/guild home <gray>teleportation")

            val mainGuiItem = GuiItem(setMainItem) {
                setGuildHome("main")
            }
            pane.addItem(mainGuiItem, x, y)
        }

        // Set Additional Home button (if slots available)
        if (allHomes.size < availableSlots) {
            val setAdditionalItem = ItemStack(Material.LIME_WOOL)
                .setAdventureName(player, messageService, "<yellow>Set Additional Home")
                .addAdventureLore(player, messageService, "<gray>Set a named home location")
                .addAdventureLore(player, messageService, "<gray>Allows <gold>/guild home <name> <gray>teleportation")
                .addAdventureLore(player, messageService, "<gray>Available slots: <white>${availableSlots - allHomes.size}")

            val additionalGuiItem = GuiItem(setAdditionalItem) {
                // This would open a menu to input home name, but for now let's use a simple approach
                AdventureMenuHelper.sendMessage(player, messageService, "<gold>Use <yellow>/guild sethome <name> <gold>to set additional homes")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Example: <yellow>/guild sethome shop")
            }
            pane.addItem(additionalGuiItem, x + 2, y)
        }

        // Remove Homes button
        if (allHomes.hasHomes()) {
            val removeItem = ItemStack(Material.RED_WOOL)
                .setAdventureName(player, messageService, "<red>Remove Homes")
                .addAdventureLore(player, messageService, "<gray>Remove guild home locations")

            val removeGuiItem = GuiItem(removeItem) {
                showRemoveHomesMenu()
            }
            pane.addItem(removeGuiItem, x + 4, y)
        }
    }

    private fun addTeleportButtons(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val hasActiveTeleport = activeTeleports.containsKey(player.uniqueId)

        if (hasActiveTeleport) {
            // Show cancel teleport button
            val cancelItem = ItemStack(Material.CLOCK)
                .setAdventureName(player, messageService, "<yellow>Cancel Teleport")
                .addAdventureLore(player, messageService, "<gray>Teleportation in progress...")
                .addAdventureLore(player, messageService, "<gray>Remaining: <white>${activeTeleports[player.uniqueId]?.remainingSeconds ?: 0} seconds")

            val cancelGuiItem = GuiItem(cancelItem) {
                cancelTeleport(player.uniqueId)
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Teleportation canceled!")
                open() // Refresh menu
            }
            pane.addItem(cancelGuiItem, x, y)
        } else if (allHomes.hasHomes()) {
            // Show teleport to main home button
            val mainHome = allHomes.defaultHome
            if (mainHome != null) {
                val teleportItem = ItemStack(Material.ENDER_PEARL)
                    .setAdventureName(player, messageService, "<aqua>Teleport to Main Home")
                    .addAdventureLore(player, messageService, "<gray>Click to start teleportation countdown")
                    .lore("<gray>World: <white>${Bukkit.getWorld(mainHome.worldId)?.name ?: "Unknown"}")
                    .addAdventureLore(player, messageService, "<gray>Countdown: <white>5 seconds <gray>(don't move!)")

                val teleportGuiItem = GuiItem(teleportItem) {
                    startTeleportCountdown(mainHome)
                }
                pane.addItem(teleportGuiItem, x, y)
            }

            // Show list homes button if there are multiple homes
            if (allHomes.size > 1) {
                val listItem = ItemStack(Material.COMPASS)
                    .setAdventureName(player, messageService, "<yellow>List All Homes")
                    .addAdventureLore(player, messageService, "<gray>View all available homes")
                    .addAdventureLore(player, messageService, "<gray>Use <gold>/guild home <name> <gray>to teleport")

                val listGuiItem = GuiItem(listItem) {
                    showHomesList()
                }
                pane.addItem(listGuiItem, x + 2, y)
            }
        } else {
            // No homes set
            val noHomeItem = ItemStack(Material.GRAY_DYE)
                .setAdventureName(player, messageService, "<gray>No Homes Set")
                .addAdventureLore(player, messageService, "<gray>Set a home location first")

            pane.addItem(GuiItem(noHomeItem), x, y)
        }
    }

    private fun showRemoveHomesMenu() {
        val allHomes = guildService.getHomes(guild.id)
        if (!allHomes.hasHomes()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No homes to remove.")
            return
        }

        // Create a simple removal menu
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<red><red>Remove Guild Homes"))
        val pane = StaticPane(0, 0, 9, 4)

        // CRITICAL SECURITY: Prevent ALL inventory interactions to stop item duplication exploits
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        // List homes for removal
        var slot = 0
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            if (slot < 27) { // Max 27 slots
                val removeItem = ItemStack(Material.RED_WOOL)
                    .setAdventureName(player, messageService, "<red>Remove '$name'")
                    .lore("<gray>World: <white>${Bukkit.getWorld(home.worldId)?.name ?: "Unknown"}")
                    .addAdventureLore(player, messageService, "<gray>Location: <white>${home.position.x}, ${home.position.y}, ${home.position.z}")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<yellow>Click to remove this home")

                val removeGuiItem = GuiItem(removeItem) {
                    val success = guildService.removeHome(guild.id, name, player.uniqueId)
                    if (success) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Home '$name' removed!")
                        showRemoveHomesMenu() // Refresh menu
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to remove home '$name'.")
                    }
                }
                pane.addItem(removeGuiItem, slot % 9, slot / 9)
                slot++
            }
        }

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back to Home Menu")
            .addAdventureLore(player, messageService, "<gray>Return to home management")

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
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No homes set.")
            return
        }

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== Guild Homes ===")
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            val marker = if (name == "main") "<yellow>[MAIN]" else ""
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>• <white>$name $marker <gray>- <white>$worldName (${home.position.x.toInt()}, ${home.position.y.toInt()}, ${home.position.z.toInt()})")
        }
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Use <gold>/guild home <name> <gray>to teleport")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>==================")
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
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ This location is not safe to set as guild home!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Try setting your home on solid ground with space above you.")
            open() // Reopen menu to show current state
            return
        }

        val success = guildService.setHome(guild.id, homeName, home, player.uniqueId)
        if (success) {
            val homeLabel = if (homeName == "main") "main home" else "home '$homeName'"
            AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Guild $homeLabel set to your current location!")
            player.sendMessage("<gray>Members can now use <gold>/guild home ${if (homeName == "main") "" else homeName}<gray>to teleport here.")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Location: <white>${location.blockX}, ${location.blockY}, ${location.blockZ}")

            // Refresh the guild data and reopen menu
            guild = guildService.getGuild(guild.id) ?: guild
            open()
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to set guild home. Please try again.")
            open() // Reopen menu to show current state
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back to Control Panel")
            .addAdventureLore(player, messageService, "<gray>Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun startTeleportCountdown(home: GuildHome) {
        val world = Bukkit.getWorld(home.worldId)
        if (world == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Could not find the world for guild home.")
            return
        }

        val targetLocation = org.bukkit.Location(
            world,
            home.position.x.toDouble(),
            home.position.y.toDouble(),
            home.position.z.toDouble(),
            player.location.yaw,
            player.location.pitch
        )

        // Check if target location is safe (if safety check is enabled)
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(targetLocation)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Guild home location is not safe to teleport to!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Try setting your home on solid ground with space above you.")
            return
        }

        // Cancel any existing teleport
        cancelTeleport(player.uniqueId)

        val session = TeleportSession(
            player = player,
            targetLocation = targetLocation,
            startLocation = player.location.clone(),
            remainingSeconds = 5
        )

        activeTeleports[player.uniqueId] = session

        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>⏰ Teleportation countdown started! Don't move for 5 seconds...")
        player.sendActionBar(Component.text("<yellow>Teleporting to guild home in <white>5<yellow> seconds..."))

        val countdownTask = object : BukkitRunnable() {
            override fun run() {
                val currentSession = activeTeleports[player.uniqueId] ?: return

                // Check if player moved
                if (hasPlayerMoved(currentSession)) {
                    cancelTeleport(player.uniqueId)
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Teleportation canceled - you moved!")
                    return
                }

                currentSession.remainingSeconds--

                if (currentSession.remainingSeconds <= 0) {
                    // Teleport the player
                    player.teleport(currentSession.targetLocation)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Teleported to guild home!")
                    player.sendActionBar(Component.text("<green>Teleported to guild home!"))

                    // Clean up
                    activeTeleports.remove(player.uniqueId)
                } else {
                    // Update action bar
                    player.sendActionBar(Component.text("<yellow>Teleporting to guild home in <white>${currentSession.remainingSeconds}<yellow> seconds..."))
                }
            }
        }

        session.countdownTask = countdownTask
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
            ?: return // Plugin not found, cannot schedule countdown
        countdownTask.runTaskTimer(plugin, 20L, 20L) // Every second
    }

    private fun cancelTeleport(playerId: UUID) {
        val session = activeTeleports[playerId] ?: return

        session.countdownTask?.cancel()
        activeTeleports.remove(playerId)
    }

    private fun hasPlayerMoved(session: TeleportSession): Boolean {
        val currentLocation = session.player.location
        val startLocation = session.startLocation

        // Check if player moved more than 0.1 blocks in any direction
        return Math.abs(currentLocation.x - startLocation.x) > 0.1 ||
               Math.abs(currentLocation.y - startLocation.y) > 0.1 ||
               Math.abs(currentLocation.z - startLocation.z) > 0.1 ||
               currentLocation.world != startLocation.world
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
    }}

