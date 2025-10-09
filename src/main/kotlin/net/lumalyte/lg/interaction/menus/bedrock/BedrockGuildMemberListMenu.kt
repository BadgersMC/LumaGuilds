package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.util.FormImage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger
import java.util.UUID
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild member list menu using Cumulus SimpleForm
 * Displays guild members with player heads and action buttons
 */
class BedrockGuildMemberListMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val members = memberService.getGuildMembers(guild.id).toList()

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.guild.members")} - ${guild.name}")
            .content(createMemberListContent(members))
            .addButtonWithImage(
                config,
                createMemberListText(members),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                "${bedrockLocalization.getBedrockString(player, "guild.members.item.invite.name")}",
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                "Refresh",
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                bedrockLocalization.getBedrockString(player, "form.button.back"),
                config.backIconUrl,
                config.backIconPath
            )
            .validResultHandler { response ->
                handleFormResponse(response, members)
            }
            .closedOrInvalidResultHandler { _, _ ->
                navigateBack()
            }
            .build()
    }

    private fun createMemberListContent(members: List<Member>): String {
        val memberCount = members.size
        val onlineCount = members.count { member ->
            try {
                player.server.getPlayer(member.playerId)?.isOnline == true
            } catch (e: Exception) {
                false
            }
        }

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.members.description")}
            |
            |Members: $memberCount ($onlineCount online)
            |Select a member below to manage them.
            |
            |Tap on a member to view options.
        """.trimMargin()
    }

    private fun createMemberListText(members: List<Member>): String {
        if (members.isEmpty()) {
            return bedrockLocalization.getBedrockString(player, "guild.members.title")
        }

        val memberTexts = members.take(10).map { m ->
            val playerName = getPlayerName(m)
            val onlineStatus = if (isPlayerOnline(m)) "[ONLINE]" else "[OFFLINE]"
            val rank = rankService.getRank(m.rankId)?.name ?: "Unknown"
            "$onlineStatus <white>$playerName <gray>($rank)"
        }

        val text = memberTexts.joinToString("\n")
        return if (members.size > 10) {
            "$text\n<gray>... and ${members.size - 10} more"
        } else {
            text
        }
    }


    private fun handleFormResponse(
        response: org.geysermc.cumulus.response.SimpleFormResponse,
        members: List<Member>
    ) {
        try {
            onFormResponseReceived()

            when (response.clickedButtonId()) {
                0 -> handleMemberSelection(members) // Member list button
                1 -> handleInvitePlayer() // Invite button
                2 -> handleRefresh() // Refresh button
                3 -> navigateBack() // Back button
            }

        } catch (e: Exception) {
            logger.warning("Error processing guild member list form response: ${e.message}")
            player.sendMessage("<red>[ERROR] ${bedrockLocalization.getBedrockString(player, "form.error.processing")}")
            navigateBack()
        }
    }

    private fun handleMemberSelection(members: List<Member>) {
        if (members.isEmpty()) {
            player.sendMessage("<gray>${bedrockLocalization.getBedrockString(player, "guild.members.title")}")
            navigateBack()
            return
        }

        // For now, show member management menu
        // TODO: Implement detailed member selection with individual actions
        showMemberManagementMenu()
    }

    private fun showMemberManagementMenu() {
        val memberManagementMenu = menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild)
        menuNavigator.openMenu(memberManagementMenu)
    }

    private fun handleInvitePlayer() {
        // Check permissions
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>[ERROR] Permission denied")
            return
        }

        // TODO: Implement invite player functionality
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Invite feature coming soon")
        navigateBack()
    }

    private fun handleRefresh() {
        // Refresh the form by reopening it
        reopen()
    }

    private fun getPlayerName(member: Member): String {
        return try {
            player.server.getOfflinePlayer(member.playerId).name ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun isPlayerOnline(member: Member): Boolean {
        return try {
            player.server.getPlayer(member.playerId)?.isOnline == true
        } catch (e: Exception) {
            false
        }
    }

    override fun shouldCacheForm(): Boolean = true

    override fun createCacheKey(): String {
        return "${this::class.simpleName}:${player.uniqueId}:${guild.id}:${System.currentTimeMillis() / 60000}" // Cache for 1 minute
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }
}

