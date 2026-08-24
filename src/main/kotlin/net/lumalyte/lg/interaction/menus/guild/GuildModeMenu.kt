package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.badgersmc.nexus.i18n.LangService

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
import net.kyori.adventure.text.Component
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
    private val lang: LangService by inject()

    override fun open() {
        val mainConfig = configService.loadConfig()
        val config = mainConfig.guild

        // Check if mode switching is enabled
        if (!config.modeSwitchingEnabled) {
            // Mode switching is disabled - show informational menu
            showDisabledModeMenu()
            return
        }

        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.guild_mode.title")))
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
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.guild_mode.title")))
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
            .name(lang.gui("menu.guild_mode.current.name"))
            .lore(lang.gui("menu.guild_mode.current.mode", "mode" to modeDisplayName(guild.mode)))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_mode.current.changed", "time" to (guild.modeChangedAt?.let { formatTimeAgo(it) } ?: lang.gui("menu.guild_mode.time.never"))))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_mode.disabled.message"))
            .lore(lang.gui("menu.guild_mode.disabled.description"))

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
                .name(lang.gui("menu.guild_mode.peaceful.name"))
                .lore(lang.gui("menu.guild_mode.peaceful.benefits"))

            // Only show claim-related PvP benefit if claims are enabled
            if (claimsEnabled) {
                peacefulItem.lore(lang.gui("menu.guild_mode.peaceful.no_pvp"))
            }
            peacefulItem.lore(lang.gui("menu.guild_mode.peaceful.safe_trading"))
                .lore(lang.gui("menu.guild_mode.peaceful.no_wars"))
                .lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.guild_mode.peaceful.cooldown", "days" to config.modeSwitchCooldownDays))

            val canSwitch = canSwitchToPeaceful(guild, config.modeSwitchCooldownDays)
            val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
            val canSwitchConsideringWar = canSwitch && !hasActiveWar

            if (!canSwitchConsideringWar) {
                peacefulItem.lore(lang.gui("menu.common.blank"))
                        .lore(lang.gui("menu.guild_mode.switch.unavailable"))
                if (hasActiveWar) {
                    peacefulItem.lore(lang.gui("menu.guild_mode.peaceful.active_war"))
                } else {
                    peacefulItem.lore(lang.gui("menu.guild_mode.switch.reason", "reason" to getCooldownMessage(guild, config.modeSwitchCooldownDays)))
                }
            } else {
                peacefulItem.lore(lang.gui("menu.common.blank"))
                        .lore(lang.gui("menu.guild_mode.peaceful.click"))
            }

            val peacefulGuiItem = GuiItem(peacefulItem) {
                val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
                if (canSwitchConsideringWar) {
                    val success = guildService.setMode(guild.id, GuildMode.PEACEFUL, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("menu.guild_mode.feedback.peaceful_success"))
                        // Refresh guild data and return to settings
                        guild = guildService.getGuild(guild.id) ?: guild
                        menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
                    } else {
                        player.sendMessage(lang.msg("menu.guild_mode.feedback.failed"))
                    }
                } else {
                    if (hasActiveWar) {
                        player.sendMessage(lang.msg("menu.guild_mode.feedback.active_war"))
                    } else {
                        player.sendMessage(lang.msg("menu.guild_mode.feedback.blocked", "reason" to getCooldownMessage(guild, config.modeSwitchCooldownDays, styled = false)))
                    }
                }
            }
            pane.addItem(peacefulGuiItem, 2, 1)
        }

        // Hostile Mode Option
        if (guild.mode != GuildMode.HOSTILE) {
            val hostileItem = ItemStack.of(Material.RED_WOOL)
                .name(lang.gui("menu.guild_mode.hostile.name"))
                .lore(lang.gui("menu.guild_mode.hostile.benefits"))

            // Only show claim-related PvP benefit if claims are enabled
            if (claimsEnabled) {
                hostileItem.lore(lang.gui("menu.guild_mode.hostile.pvp"))
            }
            hostileItem.lore(lang.gui("menu.guild_mode.hostile.wars"))
                .lore(lang.gui("menu.guild_mode.hostile.competitive"))
                .lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.guild_mode.hostile.lock", "days" to config.hostileModeMinimumDays))

            val canSwitch = canSwitchToHostile(guild, config.hostileModeMinimumDays)
            if (!canSwitch) {
                hostileItem.lore(lang.gui("menu.common.blank"))
                        .lore(lang.gui("menu.guild_mode.switch.unavailable"))
                        .lore(lang.gui("menu.guild_mode.switch.reason", "reason" to getHostileLockMessage(guild, config.hostileModeMinimumDays)))
            } else {
                hostileItem.lore(lang.gui("menu.common.blank"))
                        .lore(lang.gui("menu.guild_mode.hostile.click"))
            }

            val hostileGuiItem = GuiItem(hostileItem) {
                if (canSwitch) {
                    val success = guildService.setMode(guild.id, GuildMode.HOSTILE, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("menu.guild_mode.feedback.hostile_success"))
                        // Refresh guild data and return to settings
                        guild = guildService.getGuild(guild.id) ?: guild
                        menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
                    } else {
                        player.sendMessage(lang.msg("menu.guild_mode.feedback.failed"))
                    }
                } else {
                    player.sendMessage(lang.msg("menu.guild_mode.feedback.blocked", "reason" to getHostileLockMessage(guild, config.hostileModeMinimumDays, styled = false)))
                }
            }
            pane.addItem(hostileGuiItem, 6, 1)
        }

        // Current Mode Display
        val currentModeItem = ItemStack.of(
            if (guild.mode == GuildMode.PEACEFUL) Material.GREEN_WOOL else Material.RED_WOOL
        )
            .name(lang.gui("menu.guild_mode.current.name"))
            .lore(lang.gui("menu.guild_mode.current.mode", "mode" to modeDisplayName(guild.mode)))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_mode.current.changed", "time" to (guild.modeChangedAt?.let { formatTimeAgo(it) } ?: lang.gui("menu.guild_mode.time.never"))))

        pane.addItem(GuiItem(currentModeItem), 4, 1)
    }

    private fun addBackButton(pane: StaticPane) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.guild_mode.back.name"))
            .lore(lang.gui("menu.guild_mode.back.description"))

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

    private fun getCooldownMessage(guild: Guild, cooldownDays: Int, styled: Boolean = true): Component {
        if (guild.modeChangedAt == null) return renderModeText("menu.guild_mode.cooldown.no_changes", styled = styled)

        val cooldownEnd = guild.modeChangedAt.plus(Duration.ofDays(cooldownDays.toLong()))
        val remaining = Duration.between(Instant.now(), cooldownEnd)

        if (remaining.isNegative) return renderModeText("menu.guild_mode.cooldown.expired", styled = styled)

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return renderModeText("menu.guild_mode.cooldown.peaceful", "days" to days, "hours" to hours, styled = styled)
    }

    private fun getHostileLockMessage(guild: Guild, minimumDays: Int, styled: Boolean = true): Component {
        if (guild.modeChangedAt == null) return renderModeText("menu.guild_mode.cooldown.no_changes", styled = styled)

        val lockEnd = guild.modeChangedAt.plus(Duration.ofDays(minimumDays.toLong()))
        val remaining = Duration.between(Instant.now(), lockEnd)

        if (remaining.isNegative) return renderModeText("menu.guild_mode.cooldown.lock_expired", styled = styled)

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return renderModeText("menu.guild_mode.cooldown.hostile", "days" to days, "hours" to hours, styled = styled)
    }

    private fun formatTimeAgo(instant: Instant): Component {
        val duration = Duration.between(instant, Instant.now())
        val days = duration.toDays()
        val hours = duration.toHours() % 24

        return when {
            days > 0 -> lang.gui("menu.guild_mode.time.days_ago", "days" to days, "hours" to hours)
            hours > 0 -> lang.gui("menu.guild_mode.time.hours_ago", "hours" to hours)
            else -> lang.gui("menu.guild_mode.time.recently")
        }
    }

    private fun modeDisplayName(mode: GuildMode): Component = when (mode) {
        GuildMode.PEACEFUL -> lang.gui("menu.guild_mode.mode.peaceful")
        GuildMode.HOSTILE -> lang.gui("menu.guild_mode.mode.hostile")
    }

    private fun renderModeText(
        key: String,
        vararg placeholders: Pair<String, Any?>,
        styled: Boolean,
    ): Component {
        val component = lang.msg(key, *placeholders)
        return if (styled) GuiTextStyler.style(component) else component
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

