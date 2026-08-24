package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild kick menu using Cumulus SimpleForm
 * Shows list of kickable guild members and handles member removal
 */
class BedrockGuildKickMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val kickableMembers = getKickableMembers()

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.kick.title", "guild" to guild.name))
            .content(buildKickContent())
            .apply {
                if (kickableMembers.isEmpty()) {
                    button(lang.bedrock("bedrock.kick.no_members"))
                } else {
                    kickableMembers.forEach { member ->
                        val buttonText = createMemberButtonText(member)
                        button(buttonText)
                    }
                }
            }
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                handleMemberSelection(clickedButton, kickableMembers)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildKickContent(): String {
        return lang.bedrock("bedrock.kick.content")
    }

    private fun getKickableMembers(): List<Member> {
        return memberService.getGuildMembers(guild.id)
            .filter { it.playerId != player.uniqueId } // Can't kick yourself
            .sortedBy { it.playerId }
    }

    private fun createMemberButtonText(member: Member): String {
        val playerName = getPlayerName(member)
        val formatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.kick.date_format"))
        val joinedDate = formatter.format(member.joinedAt)

        return lang.bedrock("bedrock.kick.member", "player" to playerName, "joined" to joinedDate)
    }

    private fun getPlayerName(member: Member): String {
        return try {
            player.server.getOfflinePlayer(member.playerId).name ?: lang.bedrock("menu.common.unknown_player")
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            lang.bedrock("menu.common.unknown_player")
        }
    }

    private fun handleMemberSelection(buttonIndex: Int, kickableMembers: List<Member>) {
        if (buttonIndex >= kickableMembers.size) {
            bedrockNavigator.goBack()
            return
        }

        val selectedMember = kickableMembers[buttonIndex]
        showKickConfirmation(selectedMember)
    }

    private fun showKickConfirmation(member: Member) {
        bedrockNavigator.openMenu(BedrockGuildKickConfirmationMenu(
            menuNavigator,
            player,
            guild,
            member,
            logger
        ))
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
