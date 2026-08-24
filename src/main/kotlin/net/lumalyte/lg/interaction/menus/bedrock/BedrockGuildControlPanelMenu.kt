package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild control panel menu - demonstrates Bedrock UI is working
 */
class BedrockGuildControlPanelMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

    private data class MenuButton(
        val label: String,
        val iconUrl: String,
        val iconPath: String,
        val handler: () -> Unit
    )

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val mainConfig = configService.loadConfig()

        // Build button list conditionally
        val buttons = mutableListOf<MenuButton>()

        // Always present buttons
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.members"), config.guildMembersIconUrl, config.guildMembersIconPath) {
            bedrockNavigator.openMenu(BedrockGuildMemberListMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.settings"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildSettingsMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.bank"), config.guildBankIconUrl, config.guildBankIconPath) {
            bedrockNavigator.openMenu(BedrockGuildBankMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.ranks"), config.editIconUrl, config.editIconPath) {
            bedrockNavigator.openMenu(BedrockGuildRankManagementMenu(menuNavigator, player, guild, null, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.tag"), config.editIconUrl, config.editIconPath) {
            bedrockNavigator.openMenu(BedrockTagEditorMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.statistics"), config.editIconUrl, config.editIconPath) {
            bedrockNavigator.openMenu(BedrockGuildStatisticsMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.information"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildInfoMenu(menuNavigator, player, guild, logger))
        })

        // Conditionally add mode button
        if (mainConfig.guild.modeSwitchingEnabled) {
            buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.mode"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
                bedrockNavigator.openMenu(BedrockGuildModeMenu(menuNavigator, player, guild, logger))
            })
        }

        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.rank_list"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildRankListMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.invite"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildInviteMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.kick"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildKickMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.change_rank"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildPromotionMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.progression"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildProgressionInfoMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.homes"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildHomeMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.emoji"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildEmojiMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.wars"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildWarManagementMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.relations"), config.guildSettingsIconUrl, config.guildSettingsIconPath) {
            bedrockNavigator.openMenu(BedrockGuildRelationsMenu(menuNavigator, player, guild, logger))
        })
        buttons.add(MenuButton(lang.raw("bedrock.control_panel.button.close"), config.closeIconUrl, config.closeIconPath) {
            bedrockNavigator.goBack()
        })

        // Build form with dynamic buttons
        var formBuilder = SimpleForm.builder()
            .title(lang.legacy("bedrock.control_panel.title", "guild" to guild.name))
            .content(lang.legacy("bedrock.control_panel.content", "player" to player.name))

        // Add all buttons
        for (button in buttons) {
            formBuilder = formBuilder.addButtonWithImage(
                config,
                button.label,
                button.iconUrl,
                button.iconPath
            )
        }

        // Add handlers
        return formBuilder
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                if (clickedButton in buttons.indices) {
                    buttons[clickedButton].handler()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.control_panel.closed"))
            })
            .build()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }

    override fun createFallbackJavaMenu(): net.lumalyte.lg.interaction.menus.Menu? {
        return try {
            val javaMenuClass = Class.forName("net.lumalyte.lg.interaction.menus.guild.GuildControlPanelMenu")
            val constructor = javaMenuClass.getConstructor(
                net.lumalyte.lg.interaction.menus.MenuNavigator::class.java,
                org.bukkit.entity.Player::class.java,
                net.lumalyte.lg.domain.entities.Guild::class.java
            )
            constructor.newInstance(menuNavigator, player, guild) as net.lumalyte.lg.interaction.menus.Menu
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Failed to create Java fallback menu for guild control panel: ${e.message}")
            null
        }
    }
}
