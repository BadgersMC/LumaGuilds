package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val mainConfig = configService.loadConfig()
        val guildConfig = mainConfig.guild

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.mode.title", "guild" to guild.name))
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
        val lastChangedText = guild.modeChangedAt?.let { formatTimeAgo(it) }
            ?: lang.bedrock("bedrock.mode.time.never")

        return if (guild.mode == GuildMode.PEACEFUL) {
            lang.bedrock(
                "bedrock.mode.content.peaceful",
                "last_changed" to lastChangedText,
                "cooldown_days" to guildConfig.modeSwitchCooldownDays
            )
        } else {
            lang.bedrock(
                "bedrock.mode.content.hostile",
                "last_changed" to lastChangedText,
                "cooldown_days" to guildConfig.modeSwitchCooldownDays
            )
        }
    }

    private fun SimpleForm.Builder.addPeacefulButton(guildConfig: GuildConfig) {
        val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }
        val canSwitch = canSwitchToPeaceful(guild, guildConfig.modeSwitchCooldownDays) && !hasActiveWar

        val buttonText = if (canSwitch) {
            lang.bedrock("bedrock.mode.button.peaceful")
        } else {
            lang.bedrock("bedrock.mode.button.peaceful_locked")
        }

        button(buttonText)
    }

    private fun SimpleForm.Builder.addHostileButton(guildConfig: GuildConfig) {
        val canSwitch = canSwitchToHostile(guild, guildConfig.hostileModeMinimumDays)

        val buttonText = if (canSwitch) {
            lang.bedrock("bedrock.mode.button.hostile")
        } else {
            lang.bedrock("bedrock.mode.button.hostile_locked")
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
                player.sendMessage(lang.msg("bedrock.mode.feedback.active_war"))
            } else {
                player.sendMessage(getCooldownMessage(guild, guildConfig.modeSwitchCooldownDays))
            }
            return
        }

        val success = guildService.setMode(guild.id, GuildMode.PEACEFUL, player.uniqueId)
        if (success) {
            player.sendMessage(lang.msg("bedrock.mode.feedback.switched_peaceful"))
            // Return to settings or control panel
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.mode.feedback.switch_failed"))
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
            player.sendMessage(lang.msg("bedrock.mode.feedback.switched_hostile"))
            // Return to settings or control panel
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.mode.feedback.switch_failed"))
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
        if (guild.modeChangedAt == null) return lang.bedrock("bedrock.mode.time.no_previous_changes")

        val cooldownEnd = guild.modeChangedAt.plus(Duration.ofDays(cooldownDays.toLong()))
        val remaining = Duration.between(Instant.now(), cooldownEnd)

        if (remaining.isNegative) return lang.bedrock("bedrock.mode.time.cooldown_expired")

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return lang.bedrock("bedrock.mode.time.until_peaceful", "days" to days, "hours" to hours)
    }

    private fun getHostileLockMessage(guild: Guild, minimumDays: Int): String {
        if (guild.modeChangedAt == null) return lang.bedrock("bedrock.mode.time.no_previous_changes")

        val lockEnd = guild.modeChangedAt.plus(Duration.ofDays(minimumDays.toLong()))
        val remaining = Duration.between(Instant.now(), lockEnd)

        if (remaining.isNegative) return lang.bedrock("bedrock.mode.time.lock_expired")

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return lang.bedrock("bedrock.mode.time.until_hostile", "days" to days, "hours" to hours)
    }

    private fun formatTimeAgo(instant: Instant): String {
        val duration = Duration.between(instant, Instant.now())
        val days = duration.toDays()
        val hours = duration.toHours() % 24

        return when {
            days > 0 -> lang.bedrock("bedrock.mode.time.days_ago", "days" to days, "hours" to hours)
            hours > 0 -> lang.bedrock("bedrock.mode.time.hours_ago", "hours" to hours)
            else -> lang.bedrock("bedrock.mode.time.recently")
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
