package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild member management menu using Cumulus SimpleForm
 * Provides options for managing guild members
 */
class BedrockGuildMemberManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val members = memberService.getGuildMembers(guild.id)
        val memberCount = members.size

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.member_management.title", "guild" to guild.name))
            .content(buildMemberContent(memberCount))
            .button(lang.raw("bedrock.member_management.button.list"))
            .button(lang.raw("bedrock.member_management.button.invite"))
            .button(lang.raw("bedrock.member_management.button.kick"))
            .button(lang.raw("bedrock.member_management.button.promote"))
            .button(lang.raw("bedrock.member_management.button.demote"))
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
        return lang.legacy("bedrock.member_management.content", "count" to memberCount)
    }

    private fun openMemberList() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberListMenu(
                menuNavigator,
                player,
                guild,
                logger
            )
        )
    }

    private fun openInviteMenu() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildInviteMenu(
                menuNavigator,
                player,
                guild,
                logger
            )
        )
    }

    private fun openKickMenu() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildKickMenu(
                menuNavigator,
                player,
                guild,
                logger
            )
        )
    }

    private fun openPromoteMenu() {
        bedrockNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildPromotionMenu(
                menuNavigator,
                player,
                guild,
                logger
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
                logger
            )
        )
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
