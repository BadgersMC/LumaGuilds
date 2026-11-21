package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import org.bukkit.entity.Player
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import kotlin.getValue
import kotlin.math.ceil

@CommandAlias("claimlist")
class ClaimListCommand(
    lang: Lazy<LangService>,
    listPlayerClaims: Lazy<ListPlayerClaims>,
    getClaimBlockCount: Lazy<GetClaimBlockCount>,
) : BaseCommand() {
    private val lang: LangService by lang
    private val listPlayerClaims: ListPlayerClaims by listPlayerClaims
    private val getClaimBlockCount: GetClaimBlockCount by getClaimBlockCount

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

        val bounds = pageBounds(playerClaims.size, page)
        if (bounds == null) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        // Create page listing claims with their coordinate and block count
        val chatInfo = ChatInfoBuilder(lang, player.uniqueId,
            lang.msg("command.claim_list.header"))
        val totalClaims = playerClaims.size
        bounds.forEach { index ->
            val claim = playerClaims[index]
            val name = claim.name.ifEmpty { claim.id.toString().take(7) }
            val blockCount = getClaimBlockCount.execute(claim.id)
            val rowString = lang.msg(
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
