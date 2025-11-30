package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.interaction.menus.bedrock.BedrockConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildBankMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildControlPanelMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildDisbandConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildInviteConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildKickConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildLeaveConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberListMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberRankConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildRankManagementMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildSelectionMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildSettingsMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockTagEditorMenu
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.GuildInfoMenu
import net.lumalyte.lg.interaction.menus.guild.GuildDisbandConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.GuildInviteConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.GuildKickConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.GuildLeaveConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberRankConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.TagEditorMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberListMenu
import net.lumalyte.lg.interaction.menus.guild.GuildSelectionMenu
import net.lumalyte.lg.interaction.menus.guild.PartyCreationMenu
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Factory for creating platform-specific menu implementations
 */
class MenuFactory : KoinComponent {

    private val platformDetectionService: PlatformDetectionService by inject()
    private val configService: net.lumalyte.lg.application.services.ConfigService by inject()
    private val logger: Logger by inject()

    /**
     * Determines if Bedrock menus should be used for a player based on configuration and platform detection
     * Includes enhanced error handling and logging
     */
    private fun shouldUseBedrockMenus(player: Player): Boolean {
        return try {
            val config = configService.loadConfig()

            // Check if Bedrock menus are enabled globally
            if (!config.bedrock.bedrockMenusEnabled) {
                logger.fine("Bedrock menus disabled globally for player ${player.name}")
                return false
            }

            // Check if Bedrock menus are forced for all players
            if (config.bedrock.forceBedrockMenus) {
                logger.fine("Bedrock menus forced for all players, including ${player.name}")
                return true
            }

            // Check platform detection with error handling
            val isBedrockPlayer = platformDetectionService.isBedrockPlayer(player)
            val isCumulusAvailable = platformDetectionService.isCumulusAvailable()

            val result = isBedrockPlayer && isCumulusAvailable

            logger.fine("Bedrock menu decision for ${player.name}: isBedrock=$isBedrockPlayer, cumulusAvailable=$isCumulusAvailable, result=$result")

            result

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error determining Bedrock menu availability for player ${player.name}: ${e.message}")
            logger.warning("Stack trace: ${e.stackTraceToString()}")

            // Default to Java menus on error
            false
        }
    }

    /**
     * Creates a confirmation menu appropriate for the player's platform
     */
    fun createConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        title: String,
        callback: () -> Unit
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock confirmation menu to provide enhanced Bedrock experience
            BedrockConfirmationMenu(menuNavigator, player, title, callback = callback, logger = logger)
        } else {
            // Fall back to Java confirmation menu
            ConfirmationMenu(menuNavigator, player, title, callback)
        }
    }

    /**
     * Creates an enhanced confirmation menu with custom message and callbacks
     */
    fun createConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        title: String,
        message: String,
        callback: () -> Unit,
        cancelCallback: (() -> Unit)? = null
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use enhanced Bedrock confirmation menu
            BedrockConfirmationMenu(
                menuNavigator = menuNavigator,
                player = player,
                title = title,
                message = message,
                callback = callback,
                cancelCallback = cancelCallback,
                logger = logger
            )
        } else {
            // Fall back to Java confirmation menu (basic version)
            ConfirmationMenu(menuNavigator, player, title, callback)
        }
    }

    /**
     * Creates a guild invite confirmation menu appropriate for the player's platform
     */
    fun createGuildInviteConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        targetPlayer: Player
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild invite confirmation menu
            BedrockGuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer, logger)
        } else {
            // Fall back to Java guild invite confirmation menu
            GuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer)
        }
    }

    /**
     * Creates a guild kick confirmation menu appropriate for the player's platform
     */
    fun createGuildKickConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        memberToKick: net.lumalyte.lg.domain.entities.Member
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild kick confirmation menu
            BedrockGuildKickConfirmationMenu(menuNavigator, player, guild, memberToKick, logger)
        } else {
            // Fall back to Java guild kick confirmation menu
            GuildKickConfirmationMenu(menuNavigator, player, guild, memberToKick)
        }
    }

    /**
     * Creates a guild member rank change confirmation menu appropriate for the player's platform
     */
    fun createGuildMemberRankConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        targetMember: net.lumalyte.lg.domain.entities.Member,
        newRank: net.lumalyte.lg.domain.entities.Rank
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild member rank confirmation menu
            BedrockGuildMemberRankConfirmationMenu(menuNavigator, player, guild, targetMember, newRank, logger)
        } else {
            // Fall back to Java guild member rank confirmation menu
            GuildMemberRankConfirmationMenu(menuNavigator, player, guild, targetMember, newRank)
        }
    }

    /**
     * Creates a guild disband confirmation menu appropriate for the player's platform
     */
    fun createGuildDisbandConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild disband confirmation menu
            BedrockGuildDisbandConfirmationMenu(menuNavigator, player, guild, logger)
        } else {
            // Fall back to Java guild disband confirmation menu
            GuildDisbandConfirmationMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild leave confirmation menu appropriate for the player's platform
     */
    fun createGuildLeaveConfirmationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild leave confirmation menu
            BedrockGuildLeaveConfirmationMenu(menuNavigator, player, guild, logger)
        } else {
            // Fall back to Java guild leave confirmation menu
            GuildLeaveConfirmationMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a tag editor menu appropriate for the player's platform
     */
    fun createTagEditorMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock tag editor menu with CustomForm
            BedrockTagEditorMenu(menuNavigator, player, guild, logger)
        } else {
            // Fall back to Java tag editor menu
            TagEditorMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild selection menu appropriate for the player's platform
     */
    fun createGuildSelectionMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        currentGuild: net.lumalyte.lg.domain.entities.Guild,
        selectedGuilds: MutableSet<java.util.UUID>
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild selection menu
            BedrockGuildSelectionMenu(menuNavigator, player, currentGuild, selectedGuilds, logger)
        } else {
            GuildSelectionMenu(menuNavigator, player, currentGuild, selectedGuilds)
        }
    }

    /**
     * Creates a guild info menu appropriate for the player's platform
     */
    fun createGuildInfoMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildInfoMenu to provide enhanced Bedrock experience
            // return BedrockGuildInfoMenu(menuNavigator, player, guild)
            GuildInfoMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            GuildInfoMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild member list menu appropriate for the player's platform
     */
    fun createGuildMemberListMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild member list menu with SimpleForm and player heads
            BedrockGuildMemberListMenu(menuNavigator, player, guild, logger)
        } else {
            GuildMemberListMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates an emoji selection menu appropriate for the player's platform
     */
    fun createEmojiSelectionMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        parentMenu: net.lumalyte.lg.interaction.menus.guild.GuildEmojiMenu
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockEmojiSelectionMenu to provide enhanced Bedrock experience
            // return BedrockEmojiSelectionMenu(menuNavigator, player, guild, parentMenu)
            net.lumalyte.lg.interaction.menus.guild.EmojiSelectionMenu(menuNavigator, player, guild, parentMenu) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.EmojiSelectionMenu(menuNavigator, player, guild, parentMenu)
        }
    }

    /**
     * Creates a party creation menu appropriate for the player's platform
     */
    fun createPartyCreationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockPartyCreationMenu to provide enhanced Bedrock experience
            // return BedrockPartyCreationMenu(menuNavigator, player, guild)
            PartyCreationMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            PartyCreationMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild emoji menu appropriate for the player's platform
     */
    fun createGuildEmojiMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildEmojiMenu to provide enhanced Bedrock experience
            // return BedrockGuildEmojiMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildEmojiMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildEmojiMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild control panel menu appropriate for the player's platform
     */
    fun createGuildControlPanelMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            BedrockGuildControlPanelMenu(menuNavigator, player, guild, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildControlPanelMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild settings menu appropriate for the player's platform
     */
    fun createGuildSettingsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild settings menu with CustomForm
            BedrockGuildSettingsMenu(menuNavigator, player, guild, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildSettingsMenu(menuNavigator, player, guild)
        }
    }



    /**
     * Creates a description editor menu appropriate for the player's platform
     */
    fun createDescriptionEditorMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockDescriptionEditorMenu to provide enhanced Bedrock experience
            // return BedrockDescriptionEditorMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.DescriptionEditorMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.DescriptionEditorMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild war management menu appropriate for the player's platform
     */
    fun createGuildWarManagementMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildWarManagementMenu(menuNavigator, player, guild, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildWarManagementMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild banner menu appropriate for the player's platform
     */
    fun createGuildBannerMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildBannerMenu to provide enhanced Bedrock experience
            // return BedrockGuildBannerMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildBannerMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBannerMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild mode menu appropriate for the player's platform
     */
    fun createGuildModeMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildModeMenu to provide enhanced Bedrock experience
            // return BedrockGuildModeMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildModeMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildModeMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild home menu appropriate for the player's platform
     */
    fun createGuildHomeMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildHomeMenu to provide enhanced Bedrock experience
            // return BedrockGuildHomeMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildHomeMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildHomeMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild rank management menu appropriate for the player's platform
     */
    fun createGuildRankManagementMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            BedrockGuildRankManagementMenu(menuNavigator, player, guild, null, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildRankManagementMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild war declaration menu appropriate for the player's platform
     */
    fun createGuildWarDeclarationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildWarDeclarationMenu to provide enhanced Bedrock experience
            // return BedrockGuildWarDeclarationMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildWarDeclarationMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildWarDeclarationMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a peace agreement menu appropriate for the player's platform
     */
    fun createPeaceAgreementMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockPeaceAgreementMenu to provide enhanced Bedrock experience
            // return BedrockPeaceAgreementMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.PeaceAgreementMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.PeaceAgreementMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild member management menu appropriate for the player's platform
     */
    fun createGuildMemberManagementMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildMemberManagementMenu to provide enhanced Bedrock experience
            // return BedrockGuildMemberManagementMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildMemberManagementMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildMemberManagementMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild party management menu appropriate for the player's platform
     */
    fun createGuildPartyManagementMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildPartyManagementMenu(menuNavigator, player, guild, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildPartyManagementMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a rank creation menu appropriate for the player's platform
     */
    fun createRankCreationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockRankCreationMenu to provide enhanced Bedrock experience
            // return BedrockRankCreationMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.RankCreationMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.RankCreationMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a rank edit menu appropriate for the player's platform
     */
    fun createRankEditMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        rank: net.lumalyte.lg.domain.entities.Rank
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockRankEditMenu to provide enhanced Bedrock experience
            // return BedrockRankEditMenu(menuNavigator, player, guild, rank)
            net.lumalyte.lg.interaction.menus.guild.RankEditMenu(menuNavigator, player, guild, rank) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.RankEditMenu(menuNavigator, player, guild, rank)
        }
    }

    /**
     * Creates a guild relations menu appropriate for the player's platform
     */
    fun createGuildRelationsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildRelationsMenu(menuNavigator, player, guild, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildRelationsMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild bank menu appropriate for the player's platform
     */
    fun createGuildBankMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // Use Bedrock guild bank menu with CustomForm and sliders
            BedrockGuildBankMenu(menuNavigator, player, guild, logger)
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBankMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild bank statistics menu appropriate for the player's platform
     */
    fun createGuildBankStatisticsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildBankStatisticsMenu to provide enhanced Bedrock experience
            // return BedrockGuildBankStatisticsMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildBankStatisticsMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBankStatisticsMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild member contributions menu appropriate for the player's platform
     */
    fun createGuildMemberContributionsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildMemberContributionsMenu to provide enhanced Bedrock experience
            // return BedrockGuildMemberContributionsMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildMemberContributionsMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildMemberContributionsMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild bank transaction history menu appropriate for the player's platform
     */
    fun createGuildBankTransactionHistoryMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildBankTransactionHistoryMenu to provide enhanced Bedrock experience
            // return BedrockGuildBankTransactionHistoryMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildBankTransactionHistoryMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBankTransactionHistoryMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild bank security menu appropriate for the player's platform
     */
    fun createGuildBankSecurityMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildBankSecurityMenu to provide enhanced Bedrock experience
            // return BedrockGuildBankSecurityMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildBankSecurityMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBankSecurityMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild bank automation menu appropriate for the player's platform
     */
    fun createGuildBankAutomationMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildBankAutomationMenu to provide enhanced Bedrock experience
            // return BedrockGuildBankAutomationMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildBankAutomationMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBankAutomationMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild bank budget menu appropriate for the player's platform
     */
    fun createGuildBankBudgetMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildBankBudgetMenu to provide enhanced Bedrock experience
            // return BedrockGuildBankBudgetMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildBankBudgetMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildBankBudgetMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild statistics menu appropriate for the player's platform
     */
    fun createGuildStatisticsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildStatisticsMenu to provide enhanced Bedrock experience
            // return BedrockGuildStatisticsMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildStatisticsMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildStatisticsMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild invite menu appropriate for the player's platform
     */
    fun createGuildInviteMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildInviteMenu to provide enhanced Bedrock experience
            // return BedrockGuildInviteMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildInviteMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildInviteMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild member rank menu appropriate for the player's platform
     */
    fun createGuildMemberRankMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        member: net.lumalyte.lg.domain.entities.Member
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildMemberRankMenu to provide enhanced Bedrock experience
            // return BedrockGuildMemberRankMenu(menuNavigator, player, guild, member)
            net.lumalyte.lg.interaction.menus.guild.GuildMemberRankMenu(menuNavigator, player, guild, member) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildMemberRankMenu(menuNavigator, player, guild, member)
        }
    }

    /**
     * Creates a guild kick menu appropriate for the player's platform
     */
    fun createGuildKickMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildKickMenu to provide enhanced Bedrock experience
            // return BedrockGuildKickMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildKickMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildKickMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild promotion menu appropriate for the player's platform
     */
    fun createGuildPromotionMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildPromotionMenu to provide enhanced Bedrock experience
            // return BedrockGuildPromotionMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildPromotionMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildPromotionMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a guild rank list menu appropriate for the player's platform
     */
    fun createGuildRankListMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockGuildRankListMenu to provide enhanced Bedrock experience
            // return BedrockGuildRankListMenu(menuNavigator, player, guild)
            net.lumalyte.lg.interaction.menus.guild.GuildRankListMenu(menuNavigator, player, guild) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.guild.GuildRankListMenu(menuNavigator, player, guild)
        }
    }

    /**
     * Creates a claim list menu appropriate for the player's platform
     */
    fun createClaimListMenu(
        menuNavigator: MenuNavigator,
        player: Player
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimListMenu to provide enhanced Bedrock experience
            // return BedrockClaimListMenu(menuNavigator, player)
            net.lumalyte.lg.interaction.menus.misc.ClaimListMenu(menuNavigator, player) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.misc.ClaimListMenu(menuNavigator, player)
        }
    }

    /**
     * Creates a claim icon menu appropriate for the player's platform
     */
    fun createClaimIconMenu(
        player: Player,
        menuNavigator: MenuNavigator,
        claim: Any? // Will be Claim when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimIconMenu to provide enhanced Bedrock experience
            // return BedrockClaimIconMenu(player, menuNavigator, claim)
            net.lumalyte.lg.interaction.menus.management.ClaimIconMenu(player, menuNavigator, claim as? net.lumalyte.lg.domain.entities.Claim) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimIconMenu(player, menuNavigator, claim as? net.lumalyte.lg.domain.entities.Claim)
        }
    }

    /**
     * Creates a claim trust menu appropriate for the player's platform
     */
    fun createClaimTrustMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        claim: Any? // Will be Claim when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimTrustMenu to provide enhanced Bedrock experience
            // return BedrockClaimTrustMenu(menuNavigator, player, claim)
            net.lumalyte.lg.interaction.menus.management.ClaimTrustMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimTrustMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim)
        }
    }

    /**
     * Creates a claim flag menu appropriate for the player's platform
     */
    fun createClaimFlagMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        claim: Any? // Will be Claim when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimFlagMenu to provide enhanced Bedrock experience
            // return BedrockClaimFlagMenu(menuNavigator, player, claim)
            net.lumalyte.lg.interaction.menus.management.ClaimFlagMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimFlagMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim)
        }
    }

    /**
     * Creates a claim wide permissions menu appropriate for the player's platform
     */
    fun createClaimWidePermissionsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        claim: Any? // Will be Claim when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimWidePermissionsMenu to provide enhanced Bedrock experience
            // return BedrockClaimWidePermissionsMenu(menuNavigator, player, claim)
            net.lumalyte.lg.interaction.menus.management.ClaimWidePermissionsMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimWidePermissionsMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim)
        }
    }

    /**
     * Creates a claim player menu appropriate for the player's platform
     */
    fun createClaimPlayerMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        claim: Any? // Will be Claim when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimPlayerMenu to provide enhanced Bedrock experience
            // return BedrockClaimPlayerMenu(menuNavigator, player, claim)
            net.lumalyte.lg.interaction.menus.management.ClaimPlayerMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimPlayerMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim)
        }
    }

    /**
     * Creates a claim player permissions menu appropriate for the player's platform
     */
    fun createClaimPlayerPermissionsMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        claim: Any?, // Will be Claim when claims are available
        targetPlayer: org.bukkit.OfflinePlayer?
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimPlayerPermissionsMenu to provide enhanced Bedrock experience
            // return BedrockClaimPlayerPermissionsMenu(menuNavigator, player, claim, targetPlayer)
            net.lumalyte.lg.interaction.menus.management.ClaimPlayerPermissionsMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim, targetPlayer) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimPlayerPermissionsMenu(menuNavigator, player, claim as? net.lumalyte.lg.domain.entities.Claim, targetPlayer)
        }
    }

    /**
     * Creates a claim naming menu appropriate for the player's platform
     */
    fun createClaimNamingMenu(
        player: Player,
        menuNavigator: MenuNavigator,
        location: org.bukkit.Location
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimNamingMenu to provide enhanced Bedrock experience
            // return BedrockClaimNamingMenu(player, menuNavigator, location)
            net.lumalyte.lg.interaction.menus.management.ClaimNamingMenu(player, menuNavigator, location) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimNamingMenu(player, menuNavigator, location)
        }
    }

    /**
     * Creates an edit tool menu appropriate for the player's platform
     */
    fun createEditToolMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        partition: Any? // Will be Partition when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockEditToolMenu to provide enhanced Bedrock experience
            // return BedrockEditToolMenu(menuNavigator, player, partition)
            net.lumalyte.lg.interaction.menus.misc.EditToolMenu(menuNavigator, player, partition as? net.lumalyte.lg.domain.entities.Partition) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.misc.EditToolMenu(menuNavigator, player, partition as? net.lumalyte.lg.domain.entities.Partition)
        }
    }

    /**
     * Creates a claim transfer menu appropriate for the player's platform
     */
    fun createClaimTransferMenu(
        menuNavigator: MenuNavigator,
        claim: Any?, // Will be Claim when claims are available
        player: Player
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimTransferMenu to provide enhanced Bedrock experience
            // return BedrockClaimTransferMenu(menuNavigator, claim, player)
            net.lumalyte.lg.interaction.menus.management.ClaimTransferMenu(menuNavigator, claim as? net.lumalyte.lg.domain.entities.Claim, player) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimTransferMenu(menuNavigator, claim as? net.lumalyte.lg.domain.entities.Claim, player)
        }
    }

    /**
     * Creates a claim creation menu appropriate for the player's platform
     */
    fun createClaimCreationMenu(
        player: Player,
        menuNavigator: MenuNavigator,
        location: org.bukkit.Location
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimCreationMenu to provide enhanced Bedrock experience
            // return BedrockClaimCreationMenu(player, menuNavigator, location)
            net.lumalyte.lg.interaction.menus.management.ClaimCreationMenu(player, menuNavigator, location) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimCreationMenu(player, menuNavigator, location)
        }
    }

    /**
     * Creates a claim transfer naming menu appropriate for the player's platform
     */
    fun createClaimTransferNamingMenu(
        menuNavigator: MenuNavigator,
        claim: Any?, // Will be Claim when claims are available
        player: Player
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimTransferNamingMenu to provide enhanced Bedrock experience
            // return BedrockClaimTransferNamingMenu(menuNavigator, claim, player)
            net.lumalyte.lg.interaction.menus.management.ClaimTransferNamingMenu(menuNavigator, claim as? net.lumalyte.lg.domain.entities.Claim, player) // Fallback for now
        } else {
            net.lumalyte.lg.interaction.menus.management.ClaimTransferNamingMenu(menuNavigator, claim as? net.lumalyte.lg.domain.entities.Claim, player)
        }
    }

    /**
     * Creates a claim management menu appropriate for the player's platform
     */
    fun createClaimManagementMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        claim: Any? // Will be Claim when claims are available
    ): Menu {
        return if (shouldUseBedrockMenus(player)) {
            // TODO: Create BedrockClaimManagementMenu to provide enhanced Bedrock experience
            // return BedrockClaimManagementMenu(menuNavigator, player, claim)
            // For now, return a placeholder or fallback
            throw NotImplementedError("Bedrock claim menus not yet implemented")
        } else {
            // TODO: Return Java claim management menu when claims are available
            throw NotImplementedError("Claim menus not yet implemented")
        }
    }

    /**
     * Generic method to create any menu type based on platform
     */
    inline fun <reified T : Menu> createMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        vararg args: Any?
    ): Menu {
        return when (T::class) {
            ConfirmationMenu::class -> createConfirmationMenu(
                menuNavigator,
                player,
                args[0] as String,
                args[1] as () -> Unit
            )
            GuildSelectionMenu::class -> createGuildSelectionMenu(
                menuNavigator,
                player,
                args[0] as net.lumalyte.lg.domain.entities.Guild,
                args[1] as MutableSet<java.util.UUID>
            )
            GuildInfoMenu::class -> createGuildInfoMenu(
                menuNavigator,
                player,
                args[0] as net.lumalyte.lg.domain.entities.Guild
            )
            GuildMemberListMenu::class -> createGuildMemberListMenu(
                menuNavigator,
                player,
                args[0] as net.lumalyte.lg.domain.entities.Guild
            )
            PartyCreationMenu::class -> createPartyCreationMenu(
                menuNavigator,
                player,
                args[0] as net.lumalyte.lg.domain.entities.Guild
            )
            else -> {
                // Fallback to Java implementation
                throw IllegalArgumentException("Unsupported menu type: ${T::class.simpleName}")
            }
        }
    }
}

