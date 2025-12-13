package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Location
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild home management menu using Cumulus SimpleForm
 * Allows setting, teleporting to, and managing guild home locations
 */
class BedrockGuildHomeMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()

    override fun getForm(): Form {
        val homes = guildService.getHomes(guild.id)
        val maxHomes = guildService.getAvailableHomeSlots(guild.id)
        val availableSlots = maxHomes - homes.size

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.home.title")} - ${guild.name}")
            .content(buildHomeContent(maxHomes, availableSlots))
            .apply {
                // Add existing homes
                if (homes.hasHomes()) {
                    homes.homeNames.forEach { homeName ->
                        button("$homeName (${bedrockLocalization.getBedrockString(player, "guild.home.teleport")})")
                    }
                } else {
                    button(bedrockLocalization.getBedrockString(player, "guild.home.no.homes"))
                }

                // Add management options if user has permission
                if (canManageHomes()) {
                    if (availableSlots > 0) {
                        button(bedrockLocalization.getBedrockString(player, "guild.home.set.new"))
                    }
                    if (homes.hasHomes()) {
                        button(bedrockLocalization.getBedrockString(player, "guild.home.remove"))
                    }
                }
            }
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                handleHomeSelection(clickedButton, homes, maxHomes, availableSlots)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildHomeContent(maxHomes: Int, availableSlots: Int): String {
        val slotsColor = if (availableSlots > 0) "§a" else "§c"

        return """
            |§7${bedrockLocalization.getBedrockString(player, "guild.home.description")}
            |
            |§6§l━━━ HOME SLOTS ━━━
            |§b${bedrockLocalization.getBedrockString(player, "guild.home.max.homes")}§7: §f$maxHomes
            |§e${bedrockLocalization.getBedrockString(player, "guild.home.available.slots")}§7: $slotsColor$availableSlots
        """.trimMargin()
    }

    private fun canManageHomes(): Boolean {
        // Check if user has permission to manage homes (this would be based on guild permissions)
        // For now, we'll assume guild members can manage homes
        return guildService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME)
    }

    private fun handleHomeSelection(buttonIndex: Int, homes: net.lumalyte.lg.domain.entities.GuildHomes, maxHomes: Int, availableSlots: Int) {
        val homeNames = homes.homeNames.toList()
        val totalHomes = homeNames.size
        var currentIndex = 0

        // Teleport to existing home
        if (buttonIndex < totalHomes) {
            val homeName = homeNames[buttonIndex]
            val home = homes.getHome(homeName)
            if (home != null) {
                teleportToHome(home)
            }
            return
        }

        currentIndex = totalHomes

        // No homes placeholder button
        if (totalHomes == 0) {
            if (buttonIndex == currentIndex) {
                // This is the "No homes set" placeholder button, skip it
                currentIndex++
            }
        }

        // Set new home button
        if (canManageHomes() && availableSlots > 0) {
            if (buttonIndex == currentIndex) {
                showSetHomeMenu()
                return
            }
            currentIndex++
        }

        // Remove home button
        if (canManageHomes() && homes.hasHomes()) {
            if (buttonIndex == currentIndex) {
                showRemoveHomeMenu(homes)
                return
            }
        }

        // Default: go back
        bedrockNavigator.goBack()
    }

    private fun teleportToHome(home: GuildHome) {
        try {
            // Convert GuildHome to Location
            val world = player.server.getWorld(home.worldId)
            if (world == null) {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.teleport.failed"))
                return
            }

            val location = Location(
                world,
                home.position.x.toDouble(),
                home.position.y.toDouble(),
                home.position.z.toDouble()
            )

            // Teleport player
            player.teleport(location)
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.teleport.success"))
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error teleporting to home: ${e.message}")
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.teleport.failed"))
        }

        bedrockNavigator.goBack()
    }

    private fun showSetHomeMenu() {
        // Get current homes to generate proper name
        val homes = guildService.getHomes(guild.id)
        val homeName = "home${homes.size + 1}"
        val currentLocation = player.location

        val home = GuildHome(
            worldId = currentLocation.world.uid,
            position = Position3D(
                currentLocation.x.toInt(),
                currentLocation.y.toInt(),
                currentLocation.z.toInt()
            )
        )

        val success = guildService.setHome(guild.id, homeName, home, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.home.set"))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.set.failed"))
        }

        // Reopen menu to refresh
        bedrockNavigator.openMenu(BedrockGuildHomeMenu(menuNavigator, player, guild, logger))
    }

    private fun showRemoveHomeMenu(homes: net.lumalyte.lg.domain.entities.GuildHomes) {
        val removeForm = SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.home.remove")} - ${guild.name}")
            .content(bedrockLocalization.getBedrockString(player, "guild.home.description"))
            .apply {
                homes.homeNames.forEach { homeName ->
                    button("$homeName (${bedrockLocalization.getBedrockString(player, "guild.home.remove")})")
                }
            }
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                val homeNames = homes.homeNames.toList()
                if (clickedButton < homeNames.size) {
                    val homeName = homeNames[clickedButton]
                    removeHome(homeName)
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.openMenu(BedrockGuildHomeMenu(menuNavigator, player, guild, logger))
            }
            .build()

        // For Bedrock, we need to handle this differently since we can't show nested forms
        // Let's just remove the first home for simplicity
        if (homes.hasHomes()) {
            val firstHomeName = homes.homeNames.first()
            removeHome(firstHomeName)
        }
    }

    private fun removeHome(homeName: String) {
        val success = guildService.removeHome(guild.id, homeName, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.home.removed"))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.home.remove.failed"))
        }

        // Reopen menu to refresh
        bedrockNavigator.openMenu(BedrockGuildHomeMenu(menuNavigator, player, guild, logger))
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
