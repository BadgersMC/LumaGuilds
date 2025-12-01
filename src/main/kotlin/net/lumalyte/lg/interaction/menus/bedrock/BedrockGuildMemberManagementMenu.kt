package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val members = memberService.getGuildMembers(guild.id)
        val memberCount = members.size

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.members.management.title")} - ${guild.name}")
            .content(buildMemberContent(memberCount))
            .button(bedrockLocalization.getBedrockString(player, "guild.members.view.list"))
            .button(bedrockLocalization.getBedrockString(player, "guild.members.invite"))
            .button(bedrockLocalization.getBedrockString(player, "guild.members.kick"))
            .button(bedrockLocalization.getBedrockString(player, "guild.members.promote"))
            .button(bedrockLocalization.getBedrockString(player, "guild.members.demote"))
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
        return """
            |${bedrockLocalization.getBedrockString(player, "guild.members.management.description")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.members.count")}: $memberCount
            |
            |${bedrockLocalization.getBedrockString(player, "guild.members.management.options")}:
        """.trimMargin()
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
