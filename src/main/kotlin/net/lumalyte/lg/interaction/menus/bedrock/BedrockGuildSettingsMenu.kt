package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.util.logging.Logger

/**
 * Bedrock Edition guild settings menu using Cumulus CustomForm
 * Provides comprehensive guild configuration options with validation
 */
class BedrockGuildSettingsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val settingsIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.legacy("bedrock.settings.title", "guild" to guild.name))
            .apply { settingsIcon?.let { icon(it) } }
            .label(createInfoSection())
            .input(
                lang.raw("bedrock.settings.name.label"),
                lang.raw("bedrock.settings.name.placeholder"),
                guild.name
            )
            .input(
                lang.raw("bedrock.settings.description.label"),
                lang.raw("bedrock.settings.description.placeholder"),
                guild.description ?: ""
            )
            .dropdown(
                lang.raw("bedrock.settings.mode.label"),
                listOf(
                    lang.raw("bedrock.settings.mode.peaceful"),
                    lang.raw("bedrock.settings.mode.hostile")
                ),
                if (guild.mode == GuildMode.PEACEFUL) 0 else 1
            )
            .label(createValidationSection())
            .validResultHandler { response ->
                handleFormResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                // Handle form closed without submission
                navigateBack()
            }
            .build()
    }

    private fun createInfoSection(): String {
        val mode = when (guild.mode) {
            GuildMode.PEACEFUL -> lang.raw("bedrock.settings.mode.peaceful")
            GuildMode.HOSTILE -> lang.raw("bedrock.settings.mode.hostile")
        }
        return if (guild.mode == GuildMode.PEACEFUL) {
            lang.legacy("bedrock.settings.info.peaceful", "created" to guild.createdAt.toString(), "mode" to mode)
        } else {
            lang.legacy("bedrock.settings.info.hostile", "created" to guild.createdAt.toString(), "mode" to mode)
        }
    }

    private fun createValidationSection(): String {
        return lang.legacy("bedrock.settings.validation.section")
    }

    private fun handleFormResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            onFormResponseReceived()

            val newName = response.next() as? String ?: guild.name
            val newDescription = response.next() as? String ?: guild.description ?: ""
            val modeIndex = response.next() as? Int ?: 0

            // Validate permissions
            val hasGuildSettingsPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)
            val hasDescriptionPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)
            val hasModePermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MODE)

            val validationErrors = mutableListOf<String>()

            // Validate name if changed and user has permission
            if (newName != guild.name && hasGuildSettingsPermission) {
                validateGuildName(newName)?.let { validationErrors.add(it) }
            } else if (newName != guild.name && !hasGuildSettingsPermission) {
                validationErrors.add(lang.raw("bedrock.settings.error.no_settings_permission"))
            }

            // Validate description if changed and user has permission
            if (newDescription != (guild.description ?: "") && hasDescriptionPermission) {
                validateGuildDescription(newDescription)?.let { validationErrors.add(it) }
            } else if (newDescription != (guild.description ?: "") && !hasDescriptionPermission) {
                validationErrors.add(lang.raw("bedrock.settings.error.no_description_permission"))
            }

            // Check mode change permissions and cooldowns
            val newMode = if (modeIndex == 0) GuildMode.PEACEFUL else GuildMode.HOSTILE
            if (newMode != guild.mode && hasModePermission) {
                validateModeChange(newMode)?.let { validationErrors.add(it) }
            } else if (newMode != guild.mode && !hasModePermission) {
                validationErrors.add(lang.raw("bedrock.settings.error.no_mode_permission"))
            }

            // If there are validation errors, show them and reopen form
            if (validationErrors.isNotEmpty()) {
                showValidationErrors(validationErrors)
                return
            }

            // Apply changes
            applySettings(newName, newDescription, newMode, hasGuildSettingsPermission, hasDescriptionPermission, hasModePermission)

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error processing guild settings form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.settings.error.processing"))
            navigateBack()
        }
    }

    private fun validateGuildName(name: String): String? {
        if (name.length < 3) {
            return lang.legacy("bedrock.settings.validation.name_too_short", "minimum" to 3)
        }
        if (name.length > 32) {
            return lang.legacy("bedrock.settings.validation.name_too_long", "maximum" to 32)
        }
        if (name.contains("\n")) {
            return lang.raw("bedrock.settings.validation.name_no_newlines")
        }
        return null
    }

    private fun validateGuildDescription(description: String): String? {
        if (description.length > 256) {
            return lang.legacy("bedrock.settings.validation.description_too_long", "maximum" to 256)
        }
        return null
    }

    private fun validateModeChange(newMode: GuildMode): String? {
        val config = configService.loadConfig()

        if (!config.guild.peacefulModeEnabled) {
            return lang.raw("bedrock.settings.error.mode_disabled")
        }

        val modeChangedAt = guild.modeChangedAt
        if (modeChangedAt != null) {
            val cooldownEnd = if (newMode == GuildMode.PEACEFUL) {
                modeChangedAt.plus(Duration.ofDays(config.guild.modeSwitchCooldownDays.toLong()))
            } else {
                modeChangedAt.plus(Duration.ofDays(config.guild.hostileModeMinimumDays.toLong()))
            }

            if (java.time.Instant.now().isBefore(cooldownEnd)) {
                val remaining = java.time.Duration.between(java.time.Instant.now(), cooldownEnd)
                val days = remaining.toDays()
                val hours = remaining.toHours() % 24
                return lang.legacy("bedrock.settings.error.mode_cooldown", "days" to days, "hours" to hours)
            }
        }

        return null
    }

    private fun showValidationErrors(errors: List<String>) {
        val errorMessage = errors.joinToString("\n") { lang.legacy("bedrock.settings.validation.row", "error" to it) }

        // Send error message and reopen form
        player.sendMessage(lang.msg("bedrock.settings.validation.title"))
        player.sendMessage(lang.msg("bedrock.settings.validation.errors", "errors" to errorMessage))
        player.sendMessage(lang.msg("bedrock.settings.validation.retry"))
        player.sendMessage(lang.msg("bedrock.settings.validation.cancel"))

        // Reopen the form for retry
        reopen()
    }

    private fun applySettings(
        newName: String,
        newDescription: String,
        newMode: GuildMode,
        hasGuildSettingsPermission: Boolean,
        hasDescriptionPermission: Boolean,
        hasModePermission: Boolean
    ) {
        val changes = mutableListOf<String>()
        var allSuccessful = true

        // Apply name change
        if (newName != guild.name && hasGuildSettingsPermission) {
            val success = guildService.renameGuild(guild.id, newName, player.uniqueId)
            if (success) {
                changes.add(lang.legacy("bedrock.settings.change.name", "name" to newName))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.settings.error.name_save_failed"))
            }
        }

        // Apply description change (only if not blank and different from current)
        if (newDescription.isNotBlank() && newDescription != (guild.description ?: "") && hasDescriptionPermission) {
            val success = guildService.setDescription(guild.id, newDescription, player.uniqueId)
            if (success) {
                changes.add(lang.raw("bedrock.settings.change.description"))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.settings.error.description_save_failed"))
            }
        }

        // Apply mode change
        if (newMode != guild.mode && hasModePermission) {
            val success = guildService.setMode(guild.id, newMode, player.uniqueId)
            if (success) {
                val mode = if (newMode == GuildMode.PEACEFUL) {
                    lang.raw("bedrock.settings.mode.peaceful")
                } else {
                    lang.raw("bedrock.settings.mode.hostile")
                }
                changes.add(lang.legacy("bedrock.settings.change.mode", "mode" to mode))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.settings.error.mode_save_failed"))
            }
        }

        // Show results
        if (changes.isNotEmpty()) {
            if (allSuccessful) {
                player.sendMessage(lang.msg("bedrock.settings.success.title"))
                changes.forEach { player.sendMessage(lang.msg("bedrock.settings.success.row", "change" to it)) }
            } else {
                player.sendMessage(lang.msg("bedrock.settings.success.partial"))
            }
        } else {
            player.sendMessage(lang.msg("bedrock.settings.success.no_changes"))
        }

        navigateBack()
    }

    override fun shouldCacheForm(): Boolean = true

    override fun createCacheKey(): String {
        return "${this::class.simpleName}:${player.uniqueId}:${guild.id}"
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}
