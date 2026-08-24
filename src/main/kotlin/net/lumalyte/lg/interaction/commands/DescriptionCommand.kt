package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription
import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimAttributeResult
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

@CommandAlias("claim")
class DescriptionCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val updateClaimDescription: UpdateClaimDescription by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("description")
    @CommandPermission("lumaguilds.command.claim.description")
    fun onDescription(player: Player, description: String) {
        // Gets the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId

        // Update description and notify player of result
        val result = updateClaimDescription.execute(partition.claimId, description)
        val message = when (result) {
            is UpdateClaimAttributeResult.Success -> lang.msg(
                "command.claim.description.success",
                "claim" to getClaimName(playerId, claimId),
            )
            is UpdateClaimAttributeResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is UpdateClaimAttributeResult.InputTextInvalid -> {
                val firstError = result.errors.firstOrNull()
                when (firstError) {
                    is TextValidationErrorResult.ExceededCharacterLimit -> lang.msg(
                        "command.claim.description.exceed_limit",
                        "length" to description.count(),
                        "limit" to firstError.maxCharacters,
                    )
                    is TextValidationErrorResult.InvalidCharacters -> lang.msg(
                        "command.claim.description.invalid_character",
                        "characters" to firstError.invalidCharacters,
                    )
                    is TextValidationErrorResult.ContainsBlacklistedWord -> lang.msg(
                        "command.claim.description.blacklisted_word",
                        "word" to firstError.blacklistedWord,
                    )
                    is TextValidationErrorResult.NoCharactersProvided -> lang.msg("command.claim.description.blank")
                    null -> lang.msg("general.error")
                }
            }
            is UpdateClaimAttributeResult.StorageError -> lang.msg("general.error")
        }

        // Output to player chat
        player.sendMessage(message)
    }

    /**
     * Helper function to retrieve the claim name or a default error message if not found.
     */
    private fun getClaimName(playerId: UUID, claimId: UUID): String {
        return getClaimDetails.execute(claimId)?.name ?: lang.legacy("general.name_error")
    }
}
