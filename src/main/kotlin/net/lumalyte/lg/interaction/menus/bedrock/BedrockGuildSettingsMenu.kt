package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuiTheme
import net.lumalyte.lg.utils.GuildDescriptionContent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.inject
import java.time.Duration
import java.util.logging.Logger

/**
 * Bedrock Edition guild settings menu using Cumulus CustomForm.
 *
 * Season 2 settings use the same persisted GuildService mutations as Java. Controls
 * guarded by MANAGE_GUILD_SETTINGS are read-only when the viewer lacks permission,
 * and authorization is checked again when a submitted response is executed.
 */
internal enum class RankVisibilityUpdate { NONE, WRITE, ALREADY_APPLIED, CONFLICT }

internal fun rankVisibilityUpdate(rendered: Boolean, submitted: Boolean, persisted: Boolean): RankVisibilityUpdate = when {
    submitted == rendered -> RankVisibilityUpdate.NONE
    persisted == submitted -> RankVisibilityUpdate.ALREADY_APPLIED
    persisted == rendered -> RankVisibilityUpdate.WRITE
    else -> RankVisibilityUpdate.CONFLICT
}

class BedrockGuildSettingsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private var guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val authorization by lazy { BedrockGuildAuthorization(guildService) }
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()
    private val chatRankSettings: net.lumalyte.lg.application.services.GuildChatRankSettingsService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        guild = guildService.getGuild(guild.id) ?: guild

        val config = getBedrockConfig()
        val settingsIcon = BedrockFormUtils.createFormImage(
            config,
            config.guildSettingsIconUrl,
            config.guildSettingsIconPath
        )
        val canManageSettings = authorization.canManageGuildSettings(player.uniqueId, guild.id)
        val renderedChatRanksVisible = chatRankSettings.ranksVisible(guild.id)

        val builder = CustomForm.builder()
            .title(lang.bedrock("bedrock.settings.title", "guild" to guild.name))
            .apply { settingsIcon?.let { icon(it) } }
            .label(createInfoSection())
            .input(
                lang.bedrock("bedrock.settings.name.label"),
                lang.bedrock("bedrock.settings.name.placeholder"),
                guild.name
            )
            .input(
                lang.bedrock("bedrock.settings.description.label"),
                lang.bedrock("bedrock.settings.description.placeholder"),
                guild.description ?: ""
            )
            .dropdown(
                lang.bedrock("bedrock.settings.mode.label"),
                listOf(
                    lang.bedrock("bedrock.settings.mode.peaceful"),
                    lang.bedrock("bedrock.settings.mode.hostile")
                ),
                if (guild.mode == GuildMode.PEACEFUL) 0 else 1
            )

        if (canManageSettings) {
            builder
                .toggle(lang.bedrock("menu.guild_settings.item.access.name"), guild.isOpen)
                .toggle(lang.bedrock("menu.guild_settings.item.tracking.name"), guild.trackingEnabled)
                .toggle(lang.bedrock("guild_rank_customization.toggle.name"), renderedChatRanksVisible)
                .dropdown(
                    lang.bedrock("menu.guild_settings.item.theme.name"),
                    GuiTheme.entries.map(GuiTheme::displayName),
                    GuiTheme.entries.indexOf(guild.guiTheme).coerceAtLeast(0)
                )
        } else {
            builder.label(createSeasonTwoReadOnlySection())
        }

        return builder
            .label(createValidationSection())
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) {
                        handleFormResponse(response, canManageSettings, renderedChatRanksVisible)
                    }
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) navigateBack()
                })
            }
            .build()
    }

    private fun createInfoSection(): String {
        val mode = when (guild.mode) {
            GuildMode.PEACEFUL -> lang.bedrock("bedrock.settings.mode.peaceful")
            GuildMode.HOSTILE -> lang.bedrock("bedrock.settings.mode.hostile")
        }
        return if (guild.mode == GuildMode.PEACEFUL) {
            lang.bedrock(
                "bedrock.settings.info.peaceful",
                "created" to guild.createdAt.toString(),
                "mode" to mode
            )
        } else {
            lang.bedrock(
                "bedrock.settings.info.hostile",
                "created" to guild.createdAt.toString(),
                "mode" to mode
            )
        }
    }

    private fun createSeasonTwoReadOnlySection(): String {
        val access = if (guild.isOpen) {
            lang.bedrock("menu.guild_settings.item.access.lore.current.open")
        } else {
            lang.bedrock("menu.guild_settings.item.access.lore.current.closed")
        }
        val tracking = if (guild.trackingEnabled) {
            lang.bedrock("menu.guild_settings.item.tracking.lore.current.enabled")
        } else {
            lang.bedrock("menu.guild_settings.item.tracking.lore.current.disabled")
        }
        val theme = lang.bedrock(
            "menu.guild_settings.item.theme.lore.current",
            "theme" to guild.guiTheme.displayName
        )
        return listOf(
            lang.bedrock("bedrock.settings.error.no_settings_permission"),
            access,
            tracking,
            theme
        ).joinToString("\n")
    }

    private fun createValidationSection(): String =
        lang.bedrock("bedrock.settings.validation.section")

    private fun handleFormResponse(
        response: org.geysermc.cumulus.response.CustomFormResponse,
        renderedManagementControls: Boolean,
        renderedChatRanksVisible: Boolean,
    ) {
        try {
            onFormResponseReceived()

            val newName = response.next() as? String ?: guild.name
            val newDescription = (response.next() as? String ?: guild.description ?: "").trim()
            val modeIndex = response.next() as? Int ?: if (guild.mode == GuildMode.PEACEFUL) 0 else 1

            val submittedOpen = if (renderedManagementControls) {
                response.next() as? Boolean ?: guild.isOpen
            } else {
                guild.isOpen
            }
            val submittedTracking = if (renderedManagementControls) {
                response.next() as? Boolean ?: guild.trackingEnabled
            } else {
                guild.trackingEnabled
            }
            val submittedChatRanks = if (renderedManagementControls) {
                response.next() as? Boolean ?: renderedChatRanksVisible
            } else {
                renderedChatRanksVisible
            }
            val submittedTheme = if (renderedManagementControls) {
                val themeIndex = response.next() as? Int
                    ?: GuiTheme.entries.indexOf(guild.guiTheme).coerceAtLeast(0)
                GuiTheme.entries.getOrElse(themeIndex) { guild.guiTheme }
            } else {
                guild.guiTheme
            }

            val hasGuildSettingsPermission =
                authorization.canManageGuildSettings(player.uniqueId, guild.id)
            val hasDescriptionPermission = guildService.hasPermission(
                player.uniqueId,
                guild.id,
                RankPermission.MANAGE_DESCRIPTION
            )
            val hasModePermission = guildService.hasPermission(
                player.uniqueId,
                guild.id,
                RankPermission.MANAGE_MODE
            )

            val validationErrors = mutableListOf<String>()

            if (newName != guild.name && hasGuildSettingsPermission) {
                validateGuildName(newName)?.let(validationErrors::add)
            } else if (newName != guild.name) {
                validationErrors.add(lang.bedrock("bedrock.settings.error.no_settings_permission"))
            }

            if (newDescription != (guild.description ?: "") && hasDescriptionPermission) {
                validateGuildDescription(newDescription)?.let(validationErrors::add)
            } else if (newDescription != (guild.description ?: "")) {
                validationErrors.add(lang.bedrock("bedrock.settings.error.no_description_permission"))
            }

            val newMode = if (modeIndex == 0) GuildMode.PEACEFUL else GuildMode.HOSTILE
            if (newMode != guild.mode && hasModePermission) {
                validateModeChange(newMode)?.let(validationErrors::add)
            } else if (newMode != guild.mode) {
                validationErrors.add(lang.bedrock("bedrock.settings.error.no_mode_permission"))
            }

            val managementChanged =
                submittedOpen != guild.isOpen ||
                    submittedTracking != guild.trackingEnabled ||
                    submittedTheme != guild.guiTheme ||
                    submittedChatRanks != renderedChatRanksVisible
            if (managementChanged && !hasGuildSettingsPermission) {
                validationErrors.add(lang.bedrock("bedrock.settings.error.no_settings_permission"))
            }

            if (validationErrors.isNotEmpty()) {
                showValidationErrors(validationErrors)
                return
            }

            applySettings(
                newName = newName,
                newDescription = newDescription,
                newMode = newMode,
                newOpen = submittedOpen,
                newTracking = submittedTracking,
                newTheme = submittedTheme,
                newChatRanks = submittedChatRanks,
                renderedChatRanksVisible = renderedChatRanksVisible,
                hasGuildSettingsPermission = hasGuildSettingsPermission,
                hasDescriptionPermission = hasDescriptionPermission,
                hasModePermission = hasModePermission
            )
        } catch (e: Exception) {
            logger.warning("Error processing guild settings form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.settings.error.processing"))
            navigateBack()
        }
    }

    private fun validateGuildName(name: String): String? {
        if (name.length < 3) {
            return lang.bedrock("bedrock.settings.validation.name_too_short", "minimum" to 3)
        }
        if (name.length > 32) {
            return lang.bedrock("bedrock.settings.validation.name_too_long", "maximum" to 32)
        }
        if (name.contains("\n")) {
            return lang.bedrock("bedrock.settings.validation.name_no_newlines")
        }
        return null
    }

    private fun validateGuildDescription(description: String): String? =
        when (val failure = GuildDescriptionContent.validationFailure(description)) {
            is GuildDescriptionContent.Failure.TooLong ->
                lang.bedrock(
                    "bedrock.settings.validation.description_too_long",
                    "maximum" to GuildDescriptionContent.MAX_LENGTH,
                )
            is GuildDescriptionContent.Failure.InteractiveTag ->
                lang.bedrock(
                    "bedrock.settings.validation.description_interactive_tag",
                    "tag" to failure.tagName,
                )
            is GuildDescriptionContent.Failure.InvalidFormat ->
                lang.bedrock("bedrock.settings.validation.description_invalid_format")
            null -> null
        }

    private fun validateModeChange(newMode: GuildMode): String? {
        val config = configService.loadConfig()
        if (!config.guild.peacefulModeEnabled) {
            return lang.bedrock("bedrock.settings.error.mode_disabled")
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
                return lang.bedrock(
                    "bedrock.settings.error.mode_cooldown",
                    "days" to remaining.toDays(),
                    "hours" to remaining.toHours() % 24
                )
            }
        }
        return null
    }

    private fun showValidationErrors(errors: List<String>) {
        val errorMessage = errors.joinToString("\n") {
            lang.bedrock("bedrock.settings.validation.row", "error" to it)
        }
        player.sendMessage(lang.msg("bedrock.settings.validation.title"))
        player.sendMessage(lang.msg("bedrock.settings.validation.errors", "errors" to errorMessage))
        player.sendMessage(lang.msg("bedrock.settings.validation.retry"))
        player.sendMessage(lang.msg("bedrock.settings.validation.cancel"))
        reopen()
    }

    private fun applySettings(
        newName: String,
        newDescription: String,
        newMode: GuildMode,
        newOpen: Boolean,
        newTracking: Boolean,
        newTheme: GuiTheme,
        newChatRanks: Boolean,
        renderedChatRanksVisible: Boolean,
        hasGuildSettingsPermission: Boolean,
        hasDescriptionPermission: Boolean,
        hasModePermission: Boolean
    ) {
        val changes = mutableListOf<String>()
        var allSuccessful = true

        if (hasGuildSettingsPermission) {
            when (rankVisibilityUpdate(renderedChatRanksVisible, newChatRanks, chatRankSettings.ranksVisible(guild.id))) {
                RankVisibilityUpdate.NONE -> Unit
                RankVisibilityUpdate.ALREADY_APPLIED ->
                    changes.add(lang.bedrock("guild_rank_customization.toggle.saved"))
                RankVisibilityUpdate.WRITE -> {
                    if (chatRankSettings.setRanksVisible(guild.id, newChatRanks, player.uniqueId)) {
                        changes.add(lang.bedrock("guild_rank_customization.toggle.saved"))
                    } else {
                        allSuccessful = false
                        player.sendMessage(lang.msg("guild_rank_customization.toggle.failed"))
                    }
                }
                RankVisibilityUpdate.CONFLICT -> {
                    allSuccessful = false
                    logger.warning("Guild chat rank visibility changed concurrently for ${guild.id}; refusing stale overwrite")
                    player.sendMessage(lang.msg("guild_rank_customization.toggle.failed"))
                }
            }
        }

        if (newName != guild.name && hasGuildSettingsPermission) {
            if (guildService.renameGuild(guild.id, newName, player.uniqueId)) {
                guild = guild.copy(name = newName)
                changes.add(lang.bedrock("bedrock.settings.change.name", "name" to newName))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.settings.error.name_save_failed"))
            }
        }

        val normalizedDescription = newDescription.ifEmpty { null }
        if (normalizedDescription != guild.description && hasDescriptionPermission) {
            if (guildService.setDescription(guild.id, normalizedDescription, player.uniqueId)) {
                guild = guild.copy(description = normalizedDescription)
                changes.add(lang.bedrock("bedrock.settings.change.description"))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.settings.error.description_save_failed"))
            }
        }

        if (newMode != guild.mode && hasModePermission) {
            if (guildService.setMode(guild.id, newMode, player.uniqueId)) {
                guild = guild.copy(mode = newMode)
                val mode = if (newMode == GuildMode.PEACEFUL) {
                    lang.bedrock("bedrock.settings.mode.peaceful")
                } else {
                    lang.bedrock("bedrock.settings.mode.hostile")
                }
                changes.add(lang.bedrock("bedrock.settings.change.mode", "mode" to mode))
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("bedrock.settings.error.mode_save_failed"))
            }
        }

        if (hasGuildSettingsPermission && newOpen != guild.isOpen) {
            if (guildService.setOpen(guild.id, newOpen, player.uniqueId)) {
                guild = guild.copy(isOpen = newOpen)
                changes.add(
                    if (newOpen) {
                        lang.bedrock("menu.guild_settings.feedback.access_open")
                    } else {
                        lang.bedrock("menu.guild_settings.feedback.access_closed")
                    }
                )
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("menu.guild_settings.feedback.access_failure"))
            }
        }

        if (hasGuildSettingsPermission && newTracking != guild.trackingEnabled) {
            if (guildService.setTrackingEnabled(guild.id, newTracking, player.uniqueId)) {
                guild = guild.copy(trackingEnabled = newTracking)
                changes.add(
                    if (newTracking) {
                        lang.bedrock("menu.guild_settings.feedback.tracking_enabled")
                    } else {
                        lang.bedrock("menu.guild_settings.feedback.tracking_disabled")
                    }
                )
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("menu.guild_settings.feedback.tracking_failure"))
            }
        }

        if (hasGuildSettingsPermission && newTheme != guild.guiTheme) {
            if (guildService.setGuiTheme(guild.id, newTheme, player.uniqueId)) {
                guild = guild.copy(guiTheme = newTheme)
                changes.add(
                    lang.bedrock(
                        "menu.guild_settings.feedback.theme_changed",
                        "theme" to newTheme.displayName
                    )
                )
            } else {
                allSuccessful = false
                player.sendMessage(lang.msg("menu.guild_settings.feedback.theme_change_failed"))
            }
        }

        if (changes.isNotEmpty()) {
            if (allSuccessful) {
                player.sendMessage(lang.msg("bedrock.settings.success.title"))
                changes.forEach {
                    player.sendMessage(lang.msg("bedrock.settings.success.row", "change" to it))
                }
            } else {
                player.sendMessage(lang.msg("bedrock.settings.success.partial"))
            }
        } else {
            player.sendMessage(lang.msg("bedrock.settings.success.no_changes"))
        }

        navigateBack()
    }

    override fun shouldCacheForm(): Boolean = false

    override fun createCacheKey(): String =
        "${this::class.simpleName}:${player.uniqueId}:${guild.id}"

    override fun handleResponse(player: Player, response: Any?) {
        onFormResponseReceived()
    }
}
