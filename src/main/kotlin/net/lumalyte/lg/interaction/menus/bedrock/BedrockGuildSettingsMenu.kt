package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.ModalForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild settings menu using Cumulus CustomForm
 * Provides comprehensive guild configuration options with validation
 */
class BedrockGuildSettingsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val settingsIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.guild.settings")} - ${guild.name}")
            .apply { settingsIcon?.let { icon(it) } }
            .label(createInfoSection())
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.settings.name.label",
                "guild.settings.name.placeholder",
                guild.name
            )
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.settings.description.label",
                "guild.settings.description.placeholder",
                guild.description ?: ""
            )
            .addLocalizedDropdown(
                player, bedrockLocalization,
                "guild.settings.mode.label",
                createModeOptionKeys(),
                getCurrentModeValue()
            )
            .addLocalizedToggle(
                player, bedrockLocalization,
                "guild.settings.home.auto.set",
                false // Auto-home setting not implemented yet
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
        return """
            |${bedrockLocalization.getBedrockString(player, "guild.settings.description")}
            |
            |Created: ${guild.createdAt.toString()}
            |Level: 1 | Experience: 0/800
            |
            |Configure your guild's basic information below.
        """.trimMargin()
    }

    private fun createModeOptionKeys(): List<String> {
        return listOf(
            "guild.settings.mode.peaceful",
            "guild.settings.mode.hostile"
        )
    }

    private fun getCurrentModeValue(): String {
        return when (guild.mode) {
            GuildMode.PEACEFUL -> bedrockLocalization.getBedrockString(player, "guild.settings.mode.peaceful")
            GuildMode.HOSTILE -> bedrockLocalization.getBedrockString(player, "guild.settings.mode.hostile")
        }
    }

    private fun createValidationSection(): String {
        return """
            |${bedrockLocalization.getBedrockString(player, "form.validation.title")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.guild.name.too.short")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.guild.name.too.long")}
            |• ${bedrockLocalization.getBedrockString(player, "validation.guild.description.too.long")}
        """.trimMargin()
    }

    private fun handleFormResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            onFormResponseReceived()

            val newName = response.next() as? String ?: guild.name
            val newDescription = response.next() as? String ?: guild.description ?: ""
            val modeIndex = response.next() as? Int ?: 0
            val autoHomeEnabled = response.next() as? Boolean ?: false

            // Validate permissions
            val hasGuildSettingsPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)
            val hasDescriptionPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)
            val hasModePermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MODE)

            val validationErrors = mutableListOf<String>()

            // Validate name if changed and user has permission
            if (newName != guild.name && hasGuildSettingsPermission) {
                validateGuildName(newName)?.let { validationErrors.add(it) }
            } else if (newName != guild.name && !hasGuildSettingsPermission) {
                validationErrors.add(localize("guild.settings.error.no.guild.settings.permission"))
            }

            // Validate description if changed and user has permission
            if (newDescription != (guild.description ?: "") && hasDescriptionPermission) {
                validateGuildDescription(newDescription)?.let { validationErrors.add(it) }
            } else if (newDescription != (guild.description ?: "") && !hasDescriptionPermission) {
                validationErrors.add(localize("guild.settings.error.no.description.permission"))
            }

            // Check mode change permissions and cooldowns
            val newMode = if (modeIndex == 0) GuildMode.PEACEFUL else GuildMode.HOSTILE
            if (newMode != guild.mode && hasModePermission) {
                validateModeChange(newMode)?.let { validationErrors.add(it) }
            } else if (newMode != guild.mode && !hasModePermission) {
                validationErrors.add(localize("guild.settings.error.no.mode.permission"))
            }

            // If there are validation errors, show them and reopen form
            if (validationErrors.isNotEmpty()) {
                showValidationErrors(validationErrors)
                return
            }

            // Apply changes
            applySettings(newName, newDescription, newMode, autoHomeEnabled, hasGuildSettingsPermission, hasDescriptionPermission, hasModePermission)

        } catch (e: Exception) {
            logger.warning("Error processing guild settings form response: ${e.message}")
            player.sendMessage("<red>[ERROR] ${localize("form.error.processing")}")
            navigateBack()
        }
    }

    private fun validateGuildName(name: String): String? {
        if (name.length < 3) {
            return localize("guild.settings.validation.name.too.short", 3)
        }
        if (name.length > 32) {
            return localize("guild.settings.validation.name.too.long", 32)
        }
        if (name.contains("\n")) {
            return localize("guild.settings.validation.name.no.newlines")
        }
        return null
    }

    private fun validateGuildDescription(description: String): String? {
        if (description.length > 256) {
            return localize("guild.settings.validation.description.too.long", 256)
        }
        return null
    }

    private fun validateModeChange(newMode: GuildMode): String? {
        val config = configService.loadConfig()

        if (!config.guild.peacefulModeEnabled) {
            return localize("guild.settings.error.mode.disabled")
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
                return localize("guild.settings.error.mode.cooldown", days, hours)
            }
        }

        return null
    }

    private fun showValidationErrors(errors: List<String>) {
        val errorMessage = errors.joinToString("\n") { "• $it" }

        // Send error message and reopen form
        player.sendMessage("<red>[ERROR] ${localize("form.validation.errors.title")}")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>$errorMessage")
        player.sendMessage("<yellow>${localize("form.button.retry")}")
        player.sendMessage("<red>${localize("form.button.cancel")}")

        // Reopen the form for retry
        reopen()
    }

    private fun applySettings(
        newName: String,
        newDescription: String,
        newMode: GuildMode,
        autoHomeEnabled: Boolean,
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
                changes.add(localize("guild.settings.change.name", newName))
            } else {
                allSuccessful = false
                player.sendMessage("<red>[ERROR] ${localize("guild.settings.error.name.save.failed")}")
            }
        }

        // Apply description change
        if (newDescription != (guild.description ?: "") && hasDescriptionPermission) {
            val success = guildService.setDescription(guild.id, newDescription, player.uniqueId)
            if (success) {
                changes.add(localize("guild.settings.change.description"))
            } else {
                allSuccessful = false
                player.sendMessage("<red>[ERROR] ${localize("guild.settings.error.description.save.failed")}")
            }
        }

        // Apply mode change
        if (newMode != guild.mode && hasModePermission) {
            val success = guildService.setMode(guild.id, newMode, player.uniqueId)
            if (success) {
                changes.add(localize("guild.settings.change.mode", newMode.name))
            } else {
                allSuccessful = false
                player.sendMessage("<red>[ERROR] ${localize("guild.settings.error.mode.save.failed")}")
            }
        }

        // Apply auto-home setting (placeholder - auto-home not implemented)
        if (autoHomeEnabled) {
            // TODO: Implement auto-home setting when available
            changes.add(localize("guild.settings.change.auto.home", "requested"))
        }

        // Show results
        if (changes.isNotEmpty()) {
            if (allSuccessful) {
                player.sendMessage("<green>✅ ${localize("guild.settings.success.title")}")
                changes.forEach { AdventureMenuHelper.sendMessage(player, messageService, "<gray>• $it") }
            } else {
                player.sendMessage("<yellow>[WARNING] ${localize("guild.settings.partial.success")}")
            }
        } else {
            player.sendMessage("<gray>${localize("guild.settings.no.changes")}")
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

