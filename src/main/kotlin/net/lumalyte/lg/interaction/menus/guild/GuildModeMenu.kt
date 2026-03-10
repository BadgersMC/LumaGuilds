package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant

class GuildModeMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val warService: WarService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val mainConfig = configService.loadConfig()
        val config = mainConfig.guild

        // Check if mode switching is enabled
        if (!config.modeSwitchingEnabled) {
            // Mode switching is disabled - show informational menu
            showDisabledModeMenu()
            return
        }

        val gui = ChestGui(3, "§6Change Guild Mode")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Add mode options
        addModeOptions(pane)

        // Add back button
        addBackButton(pane)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun showDisabledModeMenu() {
        val gui = ChestGui(3, "§6Guild Mode - Disabled")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Current mode display
        val currentModeItem = ItemStack.of(
            if (guild.mode == GuildMode.PEACEFUL) Material.GREEN_WOOL else Material.RED_WOOL
        )
            .name("§f📊 CURRENT MODE")
            .lore("§7Mode: §f${guild.mode.name}")
            .lore("§7")
            .lore("§7Changed: §f${guild.modeChangedAt?.let { formatTimeAgo(it) } ?: "Never"}")
            .lore("§7")
            .lore("§c❌ Mode switching is disabled by server configuration")
            .lore("§7Guilds cannot change between Peaceful and Hostile modes")

        pane.addItem(GuiItem(currentModeItem), 4, 1)

        // Add back button
        addBackButton(pane)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addModeOptions(pane: StaticPane) {
        val mainConfig = configService.loadConfig()
        val config = mainConfig.guild
        val claimsEnabled = mainConfig.claimsEnabled

        // Peaceful Mode Option
        if (guild.mode != GuildMode.PEACEFUL) {
            val peacefulItem = ItemStack.of(Material.GREEN_WOOL)
                .name("§a☮ SWITCH TO PEACEFUL")
                .lore("§7Peaceful mode benefits:")

            // Only show claim-related PvP benefit if claims are enabled
            if (claimsEnabled) {
                peacefulItem.lore("§7• No PvP in guild claims")
            }
            peacefulItem.lore("§7• Safe trading environment")
                .lore("§7• Prevents war declarations")
                .lore("§7")
                .lore("§c⚠ Cooldown: ${config.modeSwitchCooldownDays} days")

            val canSwitch = canSwitchToPeaceful(guild, config.modeSwitchCooldownDays)
            val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
            val canSwitchConsideringWar = canSwitch && !hasActiveWar

            if (!canSwitchConsideringWar) {
                peacefulItem.lore("§7")
                        .lore("§c❌ Cannot switch yet")
                if (hasActiveWar) {
                    peacefulItem.lore("§c⚔ Active war in progress")
                } else {
                    peacefulItem.lore("§c${getCooldownMessage(guild, config.modeSwitchCooldownDays)}")
                }
            } else {
                peacefulItem.lore("§7")
                        .lore("§e✅ Click to switch to Peaceful")
            }

            val peacefulGuiItem = GuiItem(peacefulItem) {
                val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
                if (canSwitchConsideringWar) {
                    val success = guildService.setMode(guild.id, GuildMode.PEACEFUL, player.uniqueId)
                    if (success) {
                        player.sendMessage("§a✅ Guild mode switched to PEACEFUL!")
                        // Refresh guild data and return to settings
                        guild = guildService.getGuild(guild.id) ?: guild
                        menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
                    } else {
                        player.sendMessage("§c❌ Failed to change guild mode. Check permissions.")
                    }
                } else {
                    if (hasActiveWar) {
                        player.sendMessage("§c❌ Cannot switch to peaceful mode during active war!")
                    } else {
                        player.sendMessage("§c❌ ${getCooldownMessage(guild, config.modeSwitchCooldownDays)}")
                    }
                }
            }
            pane.addItem(peacefulGuiItem, 2, 1)
        }

        // Hostile Mode Option
        if (guild.mode != GuildMode.HOSTILE) {
            val hostileItem = ItemStack.of(Material.RED_WOOL)
                .name("§c⚔ SWITCH TO HOSTILE")
                .lore("§7Hostile mode benefits:")

            // Only show claim-related PvP benefit if claims are enabled
            if (claimsEnabled) {
                hostileItem.lore("§7• PvP enabled in claims")
            }
            hostileItem.lore("§7• Can declare wars")
                .lore("§7• Competitive gameplay")
                .lore("§7")
                .lore("§c⚠ Lock: ${config.hostileModeMinimumDays} days")

            val canSwitch = canSwitchToHostile(guild, config.hostileModeMinimumDays)
            if (!canSwitch) {
                hostileItem.lore("§7")
                        .lore("§c❌ Cannot switch yet")
                        .lore("§c${getHostileLockMessage(guild, config.hostileModeMinimumDays)}")
            } else {
                hostileItem.lore("§7")
                        .lore("§e✅ Click to switch to Hostile")
            }

            val hostileGuiItem = GuiItem(hostileItem) {
                if (canSwitch) {
                    val success = guildService.setMode(guild.id, GuildMode.HOSTILE, player.uniqueId)
                    if (success) {
                        player.sendMessage("§a✅ Guild mode switched to HOSTILE!")
                        // Refresh guild data and return to settings
                        guild = guildService.getGuild(guild.id) ?: guild
                        menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
                    } else {
                        player.sendMessage("§c❌ Failed to change guild mode. Check permissions.")
                    }
                } else {
                    player.sendMessage("§c❌ ${getHostileLockMessage(guild, config.hostileModeMinimumDays)}")
                }
            }
            pane.addItem(hostileGuiItem, 6, 1)
        }

        // Current Mode Display
        val currentModeItem = ItemStack.of(
            if (guild.mode == GuildMode.PEACEFUL) Material.GREEN_WOOL else Material.RED_WOOL
        )
            .name("§f📊 CURRENT MODE")
            .lore("§7Mode: §f${guild.mode.name}")
            .lore("§7")
            .lore("§7Changed: §f${guild.modeChangedAt?.let { formatTimeAgo(it) } ?: "Never"}")

        pane.addItem(GuiItem(currentModeItem), 4, 1)
    }

    private fun addBackButton(pane: StaticPane) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name("§c⬅ BACK")
            .lore("§7Return to settings")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 4, 2)
    }

    private fun canSwitchToPeaceful(guild: Guild, cooldownDays: Int): Boolean {
        if (guild.modeChangedAt == null) return true

        val cooldownEnd = guild.modeChangedAt.plus(Duration.ofDays(cooldownDays.toLong()))
        return Instant.now().isAfter(cooldownEnd)
    }

    private fun canSwitchToHostile(guild: Guild, minimumDays: Int): Boolean {
        if (guild.mode != GuildMode.PEACEFUL) return true
        if (guild.modeChangedAt == null) return true

        val lockEnd = guild.modeChangedAt.plus(Duration.ofDays(minimumDays.toLong()))
        return Instant.now().isAfter(lockEnd)
    }

    private fun getCooldownMessage(guild: Guild, cooldownDays: Int): String {
        if (guild.modeChangedAt == null) return "No previous changes"

        val cooldownEnd = guild.modeChangedAt.plus(Duration.ofDays(cooldownDays.toLong()))
        val remaining = Duration.between(Instant.now(), cooldownEnd)

        if (remaining.isNegative) return "Cooldown expired"

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return "${days}d ${hours}h until you can switch to Peaceful"
    }

    private fun getHostileLockMessage(guild: Guild, minimumDays: Int): String {
        if (guild.modeChangedAt == null) return "No previous changes"

        val lockEnd = guild.modeChangedAt.plus(Duration.ofDays(minimumDays.toLong()))
        val remaining = Duration.between(Instant.now(), lockEnd)

        if (remaining.isNegative) return "Lock expired"

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return "${days}d ${hours}h until you can switch to Hostile"
    }

    private fun formatTimeAgo(instant: Instant): String {
        val duration = Duration.between(instant, Instant.now())
        val days = duration.toDays()
        val hours = duration.toHours() % 24

        return when {
            days > 0 -> "${days}d ${hours}h ago"
            hours > 0 -> "${hours}h ago"
            else -> "Recently"
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

