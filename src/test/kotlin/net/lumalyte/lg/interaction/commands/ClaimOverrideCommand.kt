package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.results.player.ToggleClaimOverrideResult
import org.bukkit.entity.Player
import kotlin.getValue

@CommandAlias("claimoverride")
class ClaimOverrideCommand(
    lang: Lazy<LangService>,
    toggleClaimOverride: Lazy<ToggleClaimOverride>,
) : BaseCommand() {
    private val lang: LangService by lang
    private val toggleClaimOverride: ToggleClaimOverride by toggleClaimOverride

    @Default
    @CommandPermission("lumaguilds.command.claimoverride")
    fun onClaimOverride(player: Player) {
        val result = toggleClaimOverride.execute(player.uniqueId)

        // Execute claim override action and output result to player
        val messageKey = when (result) {
            is ToggleClaimOverrideResult.Success -> {
                if (result.isOverrideEnabled) "command.claim_override.enabled"
                else "command.claim_override.disabled"
            }
            is ToggleClaimOverrideResult.PlayerNotFound,
            is ToggleClaimOverrideResult.StorageError -> "general.error"
        }
        player.sendMessage(lang.msg(messageKey))
    }
}
