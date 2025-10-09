package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild control panel menu - demonstrates Bedrock UI is working
 */
class BedrockGuildControlPanelMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.control.panel.title")} - ${guild.name}")
            .content("""
                |${bedrockLocalization.getBedrockString(player, "guild.control.panel.welcome", player.name)}
                |
                |${bedrockLocalization.getBedrockString(player, "menu.notice")}
                |
                |${bedrockLocalization.getBedrockString(player, "guild.control.panel.description")}
            """.trimMargin())
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.members"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.settings"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.bank"),
                config.guildBankIconUrl,
                config.guildBankIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.ranks"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.tag"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.stats"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.info"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.mode"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.rank.list"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.invite.player"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.kick.member"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.promote.member"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.progression"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.homes"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.emoji"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.parties"),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.wars"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.relations"),
                config.guildSettingsIconUrl,
                config.guildSettingsIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "guild.control.panel.close"),
                config.closeIconUrl,
                config.closeIconPath
            )
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()

                when (clickedButton) {
                    0 -> { // Members
                        bedrockNavigator.openMenu(BedrockGuildMemberListMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    1 -> { // Settings
                        bedrockNavigator.openMenu(BedrockGuildSettingsMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    2 -> { // Bank
                        bedrockNavigator.openMenu(BedrockGuildBankMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    3 -> { // Rank Management
                        bedrockNavigator.openMenu(BedrockGuildRankManagementMenu(menuNavigator, player, guild, null, logger, messageService))
                    }
                    4 -> { // Tag Editor
                        bedrockNavigator.openMenu(BedrockTagEditorMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    5 -> { // Statistics
                        bedrockNavigator.openMenu(BedrockGuildStatisticsMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    6 -> { // Guild Info
                        bedrockNavigator.openMenu(BedrockGuildInfoMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    7 -> { // Guild Mode
                        bedrockNavigator.openMenu(BedrockGuildModeMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    8 -> { // Rank List
                        bedrockNavigator.openMenu(BedrockGuildRankListMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    9 -> { // Invite Player
                        bedrockNavigator.openMenu(BedrockGuildInviteMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    10 -> { // Kick Member
                        bedrockNavigator.openMenu(BedrockGuildKickMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    11 -> { // Promote Member
                        bedrockNavigator.openMenu(BedrockGuildPromotionMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    12 -> { // Progression
                        bedrockNavigator.openMenu(BedrockGuildProgressionInfoMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    13 -> { // Homes
                        bedrockNavigator.openMenu(BedrockGuildHomeMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    14 -> { // Emoji
                        bedrockNavigator.openMenu(BedrockGuildEmojiMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    15 -> { // Parties
                        bedrockNavigator.openMenu(BedrockGuildPartyManagementMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    16 -> { // Wars
                        bedrockNavigator.openMenu(BedrockGuildWarManagementMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    17 -> { // Relations
                        bedrockNavigator.openMenu(BedrockGuildRelationsMenu(menuNavigator, player, guild, logger, messageService))
                    }
                    18 -> { // Close
                        bedrockNavigator.goBack()
                    }
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.control.panel.closed"))
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
            logger.warning("Failed to create Java fallback menu for guild control panel: ${e.message}")
            null
        }
    }
}

