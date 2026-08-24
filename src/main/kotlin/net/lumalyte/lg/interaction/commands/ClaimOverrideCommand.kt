package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.results.player.ToggleClaimOverrideResult
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

@CommandAlias("claimoverride")
class ClaimOverrideCommand: BaseCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val toggleClaimOverride: ToggleClaimOverride by inject()

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
