package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
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

class GuildHomeMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()

    // Teleportation tracking
    private data class TeleportSession(
        val player: Player,
        val targetLocation: org.bukkit.Location,
        val startLocation: org.bukkit.Location,
        var countdownTask: BukkitRunnable? = null,
        var remainingSeconds: Int = 5
    )

    private val activeTeleports = mutableMapOf<UUID, TeleportSession>()

    override fun open() {
        val gui = ChestGui(3, "§6Guild Home - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Current home status
        addCurrentHomeDisplay(pane, 0, 0)

        // Set home button
        addSetHomeButton(pane, 2, 0)

        // Teleport to home button
        addTeleportHomeButton(pane, 4, 0)

        // Remove home button
        addRemoveHomeButton(pane, 6, 0)

        // Back button
        addBackButton(pane, 4, 2)

        gui.show(player)
    }

    private fun addCurrentHomeDisplay(pane: StaticPane, x: Int, y: Int) {
        val currentHome = guildService.getHome(guild.id)
        val homeItem = ItemStack(if (currentHome != null) Material.COMPASS else Material.BARRIER)
            .name("§eCurrent Guild Home")
            .lore(if (currentHome != null) {
                listOf(
                    "§7World: §f${Bukkit.getWorld(currentHome.worldId)?.name ?: "Unknown"}",
                    "§7Location: §f${currentHome.position.x}, ${currentHome.position.y}, ${currentHome.position.z}",
                    "§7Status: §aSet"
                )
            } else {
                listOf("§7Status: §cNot Set")
            })

        val guiItem = GuiItem(homeItem)
        pane.addItem(guiItem, x, y)
    }

    private fun addSetHomeButton(pane: StaticPane, x: Int, y: Int) {
        val setHomeItem = ItemStack(Material.GREEN_WOOL)
            .name("§aSet Guild Home")
            .lore("§7Set your current location")
            .lore("§7as the guild's home point")
            .lore("§7Allows §6/guild home §7teleportation")

        val guiItem = GuiItem(setHomeItem) {
            val location = player.location
            val home = GuildHome(
                worldId = location.world.uid,
                position = net.lumalyte.lg.domain.values.Position3D(
                    location.x.toInt(), location.y.toInt(), location.z.toInt()
                )
            )

            val success = guildService.setHome(guild.id, home, player.uniqueId)
            if (success) {
                player.sendMessage("§a✅ Guild home set to your current location!")
                player.sendMessage("§7Members can now use §6/guild home §7to teleport here.")

                // Refresh the guild data and reopen menu
                guild = guildService.getGuild(guild.id) ?: guild
                open()
            } else {
                player.sendMessage("§c❌ Failed to set guild home. Please try again.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTeleportHomeButton(pane: StaticPane, x: Int, y: Int) {
        val currentHome = guildService.getHome(guild.id)
        val hasActiveTeleport = activeTeleports.containsKey(player.uniqueId)

        val teleportItem = ItemStack(
            when {
                hasActiveTeleport -> Material.CLOCK
                currentHome != null -> Material.ENDER_PEARL
                else -> Material.GRAY_DYE
            }
        ).name(
            when {
                hasActiveTeleport -> "§eTeleporting... (${activeTeleports[player.uniqueId]?.remainingSeconds ?: 0}s)"
                currentHome != null -> "§bTeleport to Home"
                else -> "§7Teleport to Home"
            }
        ).lore(
            when {
                hasActiveTeleport -> listOf(
                    "§7Teleportation in progress...",
                    "§7Don't move or teleportation will cancel",
                    "§7Remaining: §f${activeTeleports[player.uniqueId]?.remainingSeconds ?: 0} seconds"
                )
                currentHome != null -> listOf(
                    "§7Click to start teleportation countdown",
                    "§7World: §f${Bukkit.getWorld(currentHome.worldId)?.name ?: "Unknown"}",
                    "§7Location: §f${currentHome.position.x.toInt()}, ${currentHome.position.y.toInt()}, ${currentHome.position.z.toInt()}",
                    "§7Countdown: §f5 seconds §7(don't move!)"
                )
                else -> listOf("§7No home set to teleport to")
            }
        )

        val guiItem = GuiItem(teleportItem) {
            val home = guildService.getHome(guild.id)
            if (home != null) {
                if (activeTeleports.containsKey(player.uniqueId)) {
                    // Cancel existing teleport
                    cancelTeleport(player.uniqueId)
                    player.sendMessage("§c❌ Teleportation canceled!")
                    open() // Refresh menu
                } else {
                    // Start new teleport
                    startTeleportCountdown(home)
                }
            } else {
                player.sendMessage("§c❌ No guild home is set.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRemoveHomeButton(pane: StaticPane, x: Int, y: Int) {
        val currentHome = guildService.getHome(guild.id)
        val removeItem = ItemStack(if (currentHome != null) Material.BARRIER else Material.GRAY_DYE)
            .name("§cRemove Guild Home")
            .lore(if (currentHome != null) {
                listOf(
                    "§7Click to remove the guild home",
                    "§7This will prevent §6/guild home §7teleportation",
                    "§7until a new home is set"
                )
            } else {
                listOf("§7No home set to remove")
            })

        val guiItem = GuiItem(removeItem) {
            if (currentHome != null) {
                val confirmationMenu = ConfirmationMenu(
                    menuNavigator = menuNavigator,
                    player = player,
                    title = "§cConfirm Remove Guild Home",
                    callbackAction = {
                        removeGuildHome()
                    }
                )
                menuNavigator.openMenu(confirmationMenu)
            } else {
                player.sendMessage("§c❌ No guild home is set to remove.")
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun removeGuildHome() {
        val success = guildService.removeHome(guild.id, player.uniqueId)
        if (success) {
            player.sendMessage("§a✅ Guild home removed!")
            player.sendMessage("§7Members can no longer use §6/guild home §7until a new home is set.")

            // Refresh the guild data and reopen menu
            guild = guildService.getGuild(guild.id) ?: guild
            open()
        } else {
            player.sendMessage("§c❌ Failed to remove guild home. Please try again.")
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack to Control Panel")
            .lore("§7Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
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
            home.position.x.toDouble(),
            home.position.y.toDouble(),
            home.position.z.toDouble(),
            player.location.yaw,
            player.location.pitch
        )

        // Check if target location is safe
        if (!isLocationSafe(targetLocation)) {
            player.sendMessage("§c❌ Guild home location is not safe to teleport to!")
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

        player.sendMessage("§e⏰ Teleportation countdown started! Don't move for 5 seconds...")
        player.sendActionBar(Component.text("§eTeleporting to guild home in §f5§e seconds..."))

        val countdownTask = object : BukkitRunnable() {
            override fun run() {
                val currentSession = activeTeleports[player.uniqueId] ?: return

                // Check if player moved
                if (hasPlayerMoved(currentSession)) {
                    cancelTeleport(player.uniqueId)
                    player.sendMessage("§c❌ Teleportation canceled - you moved!")
                    return
                }

                currentSession.remainingSeconds--

                if (currentSession.remainingSeconds <= 0) {
                    // Teleport the player
                    player.teleport(currentSession.targetLocation)
                    player.sendMessage("§a✅ Teleported to guild home!")
                    player.sendActionBar(Component.text("§aTeleported to guild home!"))

                    // Clean up
                    activeTeleports.remove(player.uniqueId)
                } else {
                    // Update action bar
                    player.sendActionBar(Component.text("§eTeleporting to guild home in §f${currentSession.remainingSeconds}§e seconds..."))
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

        // Check if location has solid ground and space to stand
        return blockBelow.type.isSolid &&
               !block.type.isSolid &&
               !blockAbove.type.isSolid &&
               !blockBelow.type.toString().contains("LAVA") &&
               !blockBelow.type.toString().contains("FIRE")
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
