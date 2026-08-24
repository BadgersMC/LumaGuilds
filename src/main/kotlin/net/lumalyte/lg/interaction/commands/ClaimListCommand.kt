package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import org.bukkit.entity.Player
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.math.ceil

@CommandAlias("claimlist")
class ClaimListCommand : BaseCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val listPlayerClaims: ListPlayerClaims by inject()
    private val getClaimBlockCount: GetClaimBlockCount by inject()

    @Default
    @CommandPermission("lumaguilds.command.claimlist")
    @CommandCompletion("@nothing @players")
    @Syntax("[count] [player]")
    fun onClaimList(player: Player, @Default("1") page: Int) {
        // Retrieve the list of claims associated with the player
        val playerClaims = listPlayerClaims.execute(player.uniqueId)

        // Notify if player doesn't have any claims
        if (playerClaims.isEmpty()) {
            player.sendMessage(lang.msg("command.claim_list.no_claims"))
            return
        }

        // Notify if player specifies an invalid page
        if (page * 10 - 9 > playerClaims.count() || page < 1) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        // Create page listing claims with their coordinate and block count
        val chatInfo = ChatInfoBuilder(lang, player.uniqueId,
            lang.legacy("command.claim_list.header"))
        val totalClaims = playerClaims.size
        val startIndex = page * 10
        val endIndex = minOf(startIndex + 10, totalClaims)
        playerClaims.subList(startIndex, endIndex).forEachIndexed { index, claim ->
            val name = claim.name.ifEmpty { claim.id.toString().take(7) }
            val blockCount = getClaimBlockCount.execute(claim.id)
            val rowString = lang.legacy(
                "command.claim_list.row",
                "claim" to name,
                "x" to claim.position.x,
                "y" to claim.position.y,
                "z" to claim.position.z,
                "blocks" to blockCount,
            )
            chatInfo.addIndexed(index, rowString)
        }

        // Send the page of claims to player
        val totalPages = ceil(totalClaims / 10.0).toInt()
        player.sendMessage(chatInfo.createPaged(page, totalPages))
    }
}
