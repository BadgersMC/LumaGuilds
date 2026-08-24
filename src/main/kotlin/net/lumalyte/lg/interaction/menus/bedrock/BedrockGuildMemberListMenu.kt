package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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

/**
 * Bedrock Edition guild member list menu using Cumulus SimpleForm
 * Displays guild members with player heads and action buttons
 */
class BedrockGuildMemberListMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val members = memberService.getGuildMembers(guild.id).toList()

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.member_list.title", "guild" to guild.name))
            .content(createMemberListContent(members))
            .addButtonWithImage(
                config,
                createMemberListText(members),
                config.guildMembersIconUrl,
                config.guildMembersIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.member_list.button.invite"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.member_list.button.refresh"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.raw("bedrock.member_list.button.back"),
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
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                false
            }
        }

        return lang.legacy(
            "bedrock.member_list.content",
            "member_count" to memberCount,
            "online_count" to onlineCount
        )
    }

    private fun createMemberListText(members: List<Member>): String {
        if (members.isEmpty()) {
            return lang.raw("bedrock.member_list.no_members")
        }

        val memberTexts = members.take(10).map { m ->
            val playerName = getPlayerName(m)
            val onlineStatus = if (isPlayerOnline(m)) {
                lang.raw("bedrock.member_list.status.online")
            } else {
                lang.raw("bedrock.member_list.status.offline")
            }
            val rank = rankService.getRank(m.rankId)?.name ?: lang.raw("bedrock.member_list.unknown")
            lang.legacy(
                "bedrock.member_list.member_row",
                "status" to onlineStatus,
                "player" to playerName,
                "rank" to rank
            )
        }

        val text = memberTexts.joinToString("\n")
        return if (members.size > 10) {
            "$text\n${lang.legacy("bedrock.member_list.more", "count" to members.size - 10)}"
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
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error processing guild member list form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.member_list.error.processing"))
            navigateBack()
        }
    }

    private fun handleMemberSelection(members: List<Member>) {
        if (members.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.member_list.empty"))
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
            player.sendMessage(lang.msg("bedrock.member_list.error.permission_denied"))
            return
        }

        // Navigate to invite menu
        val inviteMenu = BedrockGuildInviteMenu(menuNavigator, player, guild, logger)
        openMenu(inviteMenu)
    }

    private fun handleRefresh() {
        // Refresh the form by reopening it
        reopen()
    }

    private fun getPlayerName(member: Member): String {
        return try {
            player.server.getOfflinePlayer(member.playerId).name ?: lang.raw("bedrock.member_list.unknown")
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            lang.raw("bedrock.member_list.unknown")
        }
    }

    private fun isPlayerOnline(member: Member): Boolean {
        return try {
            player.server.getPlayer(member.playerId)?.isOnline == true
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
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
