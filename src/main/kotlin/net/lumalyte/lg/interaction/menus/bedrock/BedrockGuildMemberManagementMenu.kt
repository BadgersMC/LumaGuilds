package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import java.util.logging.Logger

/**
 * Bedrock Edition guild member management menu using Cumulus SimpleForm
 * Provides options for managing guild members
 */
class BedrockGuildMemberManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    dependencies: BedrockMenuDependencies,
) : BaseBedrockMenu(menuNavigator, player, logger, dependencies) {

    private val memberService: MemberService by services.memberService
    private val lang: LangService by services.langService

    override fun getForm(): Form {
        val members = memberService.getGuildMembers(guild.id)
        val memberCount = members.size

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.member_management.title", "guild" to guild.name))
            .content(buildMemberContent(memberCount))
            .button(lang.bedrock("bedrock.member_management.button.list"))
            .button(lang.bedrock("bedrock.member_management.button.invite"))
            .button(lang.bedrock("bedrock.member_management.button.kick"))
            .button(lang.bedrock("bedrock.member_management.button.promote"))
            .button(lang.bedrock("bedrock.member_management.button.demote"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> openMemberList()
                    1 -> openInviteMenu()
                    2 -> openKickMenu()
                    3 -> openPromoteMenu()
                    4 -> openDemoteMenu()
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildMemberContent(memberCount: Int): String {
        return lang.bedrock("bedrock.member_management.content", "count" to memberCount)
    }

    private fun openMemberList() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberListMenu(
                menuNavigator,
                player,
                guild,
                logger,
                dependencies = bedrockDependencies,
            )
        )
    }

    private fun openInviteMenu() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildInviteMenu(
                menuNavigator,
                player,
                guild,
                logger,
                dependencies = bedrockDependencies,
            )
        )
    }

    private fun openKickMenu() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildKickMenu(
                menuNavigator,
                player,
                guild,
                logger,
                dependencies = bedrockDependencies,
            )
        )
    }

    private fun openPromoteMenu() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildPromotionMenu(
                menuNavigator,
                player,
                guild,
                logger,
                dependencies = bedrockDependencies,
            )
        )
    }

    private fun openDemoteMenu() {
        // For now, use promotion menu (it handles both)
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildPromotionMenu(
                menuNavigator,
                player,
                guild,
                logger,
                dependencies = bedrockDependencies,
            )
        )
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
