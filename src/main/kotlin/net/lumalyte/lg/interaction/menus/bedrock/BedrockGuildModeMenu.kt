package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

/**
 * Bedrock Edition guild mode selection menu using Cumulus SimpleForm
 * Allows switching between Peaceful and Hostile modes with cooldowns and restrictions
 */
class BedrockGuildModeMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val warService: WarService by inject()

    override fun getForm(): Form {
        val mainConfig = configService.loadConfig()
        val guildConfig = mainConfig.guild

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.mode.title")} - ${guild.name}")
            .content(buildModeContent(guildConfig))
            .apply {
                // Add switch buttons based on current mode and restrictions
                if (guild.mode != GuildMode.PEACEFUL) {
                    addPeacefulButton(guildConfig)
                }
                if (guild.mode != GuildMode.HOSTILE) {
                    addHostileButton(guildConfig)
                }
            }
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                handleModeSwitch(clickedButton, guildConfig)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildModeContent(guildConfig: GuildConfig): String {
        val currentModeText = when (guild.mode) {
            GuildMode.PEACEFUL -> bedrockLocalization.getBedrockString(player, "guild.mode.peaceful")
            GuildMode.HOSTILE -> bedrockLocalization.getBedrockString(player, "guild.mode.hostile")
        }

        val lastChangedText = guild.modeChangedAt?.let { formatTimeAgo(it) }
            ?: bedrockLocalization.getBedrockString(player, "guild.mode.never")

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.mode.description")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.mode.current")}: $currentModeText
            |${bedrockLocalization.getBedrockString(player, "guild.mode.last.changed")}: $lastChangedText
            |
            |${bedrockLocalization.getBedrockString(player, "guild.mode.peaceful.benefits")}
            |• ${bedrockLocalization.getBedrockString(player, "guild.mode.peaceful.no.pvp")}
            |• ${bedrockLocalization.getBedrockString(player, "guild.mode.peaceful.safe.trading")}
            |• ${bedrockLocalization.getBedrockString(player, "guild.mode.peaceful.no.wars")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.mode.hostile.benefits")}
            |• ${bedrockLocalization.getBedrockString(player, "guild.mode.hostile.pvp")}
            |• ${bedrockLocalization.getBedrockString(player, "guild.mode.hostile.wars")}
            |• ${bedrockLocalization.getBedrockString(player, "guild.mode.hostile.competitive")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.mode.cooldown")}: ${guildConfig.modeSwitchCooldownDays} ${bedrockLocalization.getBedrockString(player, "guild.mode.days")}
        """.trimMargin()
    }

    private fun SimpleForm.Builder.addPeacefulButton(guildConfig: GuildConfig) {
        val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
        val canSwitch = canSwitchToPeaceful(guild, guildConfig.modeSwitchCooldownDays) && !hasActiveWar

        val buttonText = if (canSwitch) {
            bedrockLocalization.getBedrockString(player, "guild.mode.switch.to.peaceful")
        } else {
            "${bedrockLocalization.getBedrockString(player, "guild.mode.switch.to.peaceful")} (${bedrockLocalization.getBedrockString(player, "guild.mode.cannot.switch")})"
        }

        button(buttonText)
    }

    private fun SimpleForm.Builder.addHostileButton(guildConfig: GuildConfig) {
        val canSwitch = canSwitchToHostile(guild, guildConfig.hostileModeMinimumDays)

        val buttonText = if (canSwitch) {
            bedrockLocalization.getBedrockString(player, "guild.mode.switch.to.hostile")
        } else {
            "${bedrockLocalization.getBedrockString(player, "guild.mode.switch.to.hostile")} (${bedrockLocalization.getBedrockString(player, "guild.mode.cannot.switch")})"
        }

        button(buttonText)
    }

    private fun handleModeSwitch(buttonIndex: Int, guildConfig: GuildConfig) {
        val targetMode = when (guild.mode) {
            GuildMode.PEACEFUL -> GuildMode.HOSTILE
            GuildMode.HOSTILE -> GuildMode.PEACEFUL
        }

        // Adjust button index based on available options
        val actualButtonIndex = if (guild.mode == GuildMode.PEACEFUL) {
            // Only hostile button available
            buttonIndex
        } else {
            // Only peaceful button available, or both if neither matches
            buttonIndex
        }

        when (actualButtonIndex) {
            0 -> {
                if (targetMode == GuildMode.PEACEFUL) {
                    switchToPeaceful(guildConfig)
                } else {
                    switchToHostile(guildConfig)
                }
            }
            1 -> {
                if (targetMode == GuildMode.HOSTILE && guild.mode == GuildMode.PEACEFUL) {
                    switchToHostile(guildConfig)
                }
            }
        }
    }

    private fun switchToPeaceful(guildConfig: GuildConfig) {
        val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
        val canSwitch = canSwitchToPeaceful(guild, guildConfig.modeSwitchCooldownDays) && !hasActiveWar

        if (!canSwitch) {
            if (hasActiveWar) {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.mode.active.war"))
            } else {
                player.sendMessage(getCooldownMessage(guild, guildConfig.modeSwitchCooldownDays))
            }
            return
        }

        val success = guildService.setMode(guild.id, GuildMode.PEACEFUL, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.mode.switched.success"))
            // Return to settings or control panel
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.mode.switched.failed"))
        }
    }

    private fun switchToHostile(guildConfig: GuildConfig) {
        val canSwitch = canSwitchToHostile(guild, guildConfig.hostileModeMinimumDays)

        if (!canSwitch) {
            player.sendMessage(getHostileLockMessage(guild, guildConfig.hostileModeMinimumDays))
            return
        }

        val success = guildService.setMode(guild.id, GuildMode.HOSTILE, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.mode.switched.success"))
            // Return to settings or control panel
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.mode.switched.failed"))
        }
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

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
