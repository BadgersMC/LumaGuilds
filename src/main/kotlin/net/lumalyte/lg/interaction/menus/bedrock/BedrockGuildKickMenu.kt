package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val kickableMembers = getKickableMembers()

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.kick.title")} - ${guild.name}")
            .content(buildKickContent())
            .apply {
                if (kickableMembers.isEmpty()) {
                    button(bedrockLocalization.getBedrockString(player, "guild.kick.no.members"))
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
        val memberCount = getKickableMembers().size
        return """
            |${bedrockLocalization.getBedrockString(player, "guild.kick.description")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.kick.select.member")}
            |${bedrockLocalization.getBedrockString(player, "guild.kick.confirmation.needed")}
        """.trimMargin()
    }

    private fun getKickableMembers(): List<Member> {
        return memberService.getGuildMembers(guild.id)
            .filter { it.playerId != player.uniqueId } // Can't kick yourself
            .sortedBy { it.playerId }
    }

    private fun createMemberButtonText(member: Member): String {
        val playerName = getPlayerName(member)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val joinedDate = formatter.format(member.joinedAt)

        return bedrockLocalization.getBedrockString(player, "guild.kick.member.info", playerName, joinedDate)
    }

    private fun getPlayerName(member: Member): String {
        return try {
            player.server.getPlayer(member.playerId)?.name ?: "Unknown Player"
        } catch (e: Exception) {
            "Unknown Player"
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
