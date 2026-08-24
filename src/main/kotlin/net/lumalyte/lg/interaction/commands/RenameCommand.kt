package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.plain

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimNameResult
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.getValue

@CommandAlias("claim")
class RenameCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val updateClaimName: UpdateClaimName by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("rename")
    @CommandPermission("lumaguilds.command.claim.rename")
    fun onRename(player: Player, name: String) {
        // Gets the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId

        // Update name and notify player of result
        val result = updateClaimName.execute(partition.claimId, name)
        val message = when (result) {
            is UpdateClaimNameResult.Success -> lang.msg(
                "command.claim.rename.success",
                "claim" to getClaimName(playerId, claimId),
                "name" to name,
            )
            is UpdateClaimNameResult.NameAlreadyExists -> lang.msg(
                "command.claim.rename.already_exists",
                "name" to name,
            )
            is UpdateClaimNameResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is UpdateClaimNameResult.InputTextInvalid -> {
                val firstError = result.errors.firstOrNull()
                when (firstError) {
                    is TextValidationErrorResult.ExceededCharacterLimit -> lang.msg(
                        "command.claim.rename.exceed_limit",
                        "length" to name.count(),
                        "limit" to firstError.maxCharacters,
                    )
                    is TextValidationErrorResult.InvalidCharacters -> lang.msg(
                        "command.claim.rename.invalid_character",
                        "characters" to firstError.invalidCharacters,
                    )
                    is TextValidationErrorResult.ContainsBlacklistedWord -> lang.msg(
                        "command.claim.rename.blacklisted_word",
                        "word" to firstError.blacklistedWord,
                    )
                    is TextValidationErrorResult.NoCharactersProvided -> lang.msg("command.claim.rename.blank")
                    null -> lang.msg("general.error")
                }
            }
            is UpdateClaimNameResult.StorageError -> lang.msg("general.error")
        }

        // Output to player chat
        player.sendMessage(message)
    }

    /**
     * Helper function to retrieve the claim name or a default error message if not found.
     */
    private fun getClaimName(playerId: UUID, claimId: UUID): String {
        return getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")
    }
}
