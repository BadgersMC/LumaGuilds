package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

@CommandAlias("pc|pchat|partychat")
class PartyChatCommand : BaseCommand(), KoinComponent {

    private val chatService: ChatService by inject()
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val partyRepository: PartyRepository by inject()
    private val preferenceRepository: PlayerPartyPreferenceRepository by inject()

    @Default
    @CommandPermission("bellclaims.partychat")
    fun onPartyChat(player: Player) {
        // No arguments - show current party info
        showCurrentPartyInfo(player)
    }

    @Default
    @CommandPermission("bellclaims.partychat")
    fun onPartyChat(player: Player, firstArg: String) {
        val playerId = player.uniqueId

        // Try to find a party with this name first
        val targetParty = findPartyByName(playerId, firstArg)

        if (targetParty != null) {
            // Switch to the specified party
            switchToParty(player, targetParty)
        } else {
            // Treat as a message to send to current party
            sendPartyMessage(player, firstArg)
        }
    }

    @Default
    @CommandPermission("bellclaims.partychat")
    fun onPartyChat(player: Player, firstArg: String, vararg restArgs: String) {
        val playerId = player.uniqueId
        val fullMessage = (arrayOf(firstArg) + restArgs).joinToString(" ")

        // Try to find a party with the first argument as name
        val targetParty = findPartyByName(playerId, firstArg)

        if (targetParty != null && restArgs.isEmpty()) {
            // Switch to the specified party (single argument)
            switchToParty(player, targetParty)
        } else {
            // Send the full message to current party
            sendPartyMessage(player, fullMessage)
        }
    }

    @Subcommand("toggle")
    @CommandPermission("bellclaims.partychat")
    fun onToggle(player: Player) {
        val playerId = player.uniqueId
        val currentParty = getCurrentActiveParty(playerId)

        if (currentParty == null) {
            player.sendMessage("§c❌ You are not in any active party!")
            return
        }

        // Toggle party chat visibility setting
        val toggled = chatService.toggleChatVisibility(playerId, ChatChannel.PARTY)

        if (toggled) {
            player.sendMessage("§a✅ Party chat is now §nvisible§a in main chat")
            player.sendMessage("§7Party messages will appear in your regular chat")
        } else {
            player.sendMessage("§e⚠️ Party chat is now §nhidden§e from main chat")
            player.sendMessage("§7Use §f/pc <message> §7to send party messages")
        }
    }

    @Subcommand("help")
    @CommandPermission("bellclaims.partychat")
    fun onHelp(player: Player) {
        player.sendMessage("§6=== Party Chat Commands ===")
        player.sendMessage("§f/pc §7- Show current party info")
        player.sendMessage("§f/pc <message> §7- Send message to current party")
        player.sendMessage("§f/pc <partyname> §7- Switch to different party")
        player.sendMessage("§f/pc toggle §7- Toggle party chat visibility")
        player.sendMessage("§f/pc clear §7- Return to default party")
        player.sendMessage("§f/pc help §7- Show this help")
        player.sendMessage("§6=========================")
        player.sendMessage("§7Party preferences persist across restarts!")
    }

    @Subcommand("clear")
    @CommandPermission("bellclaims.partychat")
    fun onClear(player: Player) {
        val playerId = player.uniqueId
        val hadActiveParty = preferenceRepository.getByPlayerId(playerId) != null

        if (hadActiveParty) {
            val success = preferenceRepository.removeByPlayerId(playerId)
            if (success) {
                player.sendMessage("§a✅ Returned to default party chat")
                player.sendMessage("§7Your messages will now go to your default party")
            } else {
                player.sendMessage("§c❌ Failed to clear party preference!")
            }
        } else {
            player.sendMessage("§7You are already using your default party")
        }

        // Show current party info after clearing
        showCurrentPartyInfo(player)
    }

    private fun showCurrentPartyInfo(player: Player) {
        val playerId = player.uniqueId
        val hasStoredPreference = preferenceRepository.getByPlayerId(playerId) != null
        val party = getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage("§c❌ You are not in any active party!")
            player.sendMessage("§7Use §f/pc <partyname> §7to switch to a party")
            return
        }

        // Verify the party is still active
        if (!party.isActive()) {
            player.sendMessage("§c❌ Your active party is no longer available!")
            preferenceRepository.removeByPlayerId(playerId) // Clear the invalid reference
            return
        }

        val defaultParty = partyService.getActivePartyForPlayer(playerId)

        player.sendMessage("§6=== Current Party ===")
        player.sendMessage("§7Name: §f${party.name ?: "Unnamed"}")
        player.sendMessage("§7Guilds: §f${party.guildIds.size}")
        player.sendMessage("§7Restrictions: §f${if (party.hasRoleRestrictions()) "Role-restricted" else "Open to all"}")
        player.sendMessage("§7Expires: §f${party.expiresAt?.let { "in ${java.time.Duration.between(java.time.Instant.now(), it).toHours()}h" } ?: "Never"}")
        if (hasStoredPreference) {
            player.sendMessage("§7Status: §aActive (switched)")
            if (defaultParty != null && defaultParty.id != party.id) {
                player.sendMessage("§7Default: §f${defaultParty.name ?: "Unnamed"}")
            }
        } else {
            player.sendMessage("§7Status: §bDefault party")
        }
        player.sendMessage("§6=====================")
        player.sendMessage("§7Use §f/pc <message> §7to send a message")
        player.sendMessage("§7Use §f/pc <partyname> §7to switch parties")
        if (hasStoredPreference) {
            player.sendMessage("§7Use §f/pc clear §7to return to default")
        }
    }

    private fun findPartyByName(playerId: UUID, partyName: String): net.lumalyte.lg.domain.entities.Party? {
        // Get all active parties for the player's guilds
        val playerGuildIds = getPlayerGuildIds(playerId)
        val activeParties = playerGuildIds.flatMap { guildId: UUID ->
            partyService.getActivePartiesForGuild(guildId)
        }.toSet()

        // Find party by exact name match (case insensitive)
        return activeParties.find { party ->
            party.name?.equals(partyName, ignoreCase = true) ?: false
        }
    }

    private fun switchToParty(player: Player, party: net.lumalyte.lg.domain.entities.Party) {
        val playerId = player.uniqueId

        // Store the active party preference persistently
        val preference = PlayerPartyPreference(playerId, party.id)
        val success = preferenceRepository.save(preference)

        if (success) {
            player.sendMessage("§a✅ Switched to party: §f${party.name ?: "Unnamed"}")
            player.sendMessage("§7You can now send messages to this party with §f/pc <message>")
            player.sendMessage("§7Use §f/pc §7to see current party info")
            player.sendMessage("§7Use §f/pc clear §7to return to default")
            player.sendMessage("§7§oThis preference will persist across server restarts!")
        } else {
            player.sendMessage("§c❌ Failed to save party preference!")
        }
    }

    private fun sendPartyMessage(player: Player, message: String) {
        val playerId = player.uniqueId
        val party = getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage("§c❌ You are not in an active party!")
            player.sendMessage("§7Use §f/pc <partyname> §7to switch to a party first")
            return
        }

        // Verify the party is still active
        if (!party.isActive()) {
            player.sendMessage("§c❌ Your active party is no longer available!")
            player.sendMessage("§7The party may have been disbanded or expired.")
            preferenceRepository.removeByPlayerId(playerId) // Clear the invalid reference
            return
        }

        // Check if player can join this party (role restrictions)
        val playerGuilds = getPlayerGuildIds(playerId)
        val playerGuildId = playerGuilds.firstOrNull() ?: run {
            player.sendMessage("§c❌ You are not in any guild!")
            return
        }
        val playerRankId = getPlayerRankInGuild(playerId, playerGuildId)

        if (playerRankId != null && !party.canPlayerJoin(playerRankId)) {
            player.sendMessage("§c❌ You don't have permission to chat in this party!")
            return
        }

        // Route the message through the chat service
        val success = chatService.routeMessage(playerId, message, ChatChannel.PARTY)

        if (!success) {
            player.sendMessage("§c❌ Failed to send party message!")
        }
        // Note: No success message is sent to avoid spam
    }

    private fun getPlayerGuildIds(playerId: UUID): Set<UUID> {
        return guildService.getPlayerGuilds(playerId).map { it.id }.toSet()
    }

    private fun getPlayerRankInGuild(playerId: UUID, guildId: UUID): UUID? {
        val member = memberService.getMember(playerId, guildId)
        return member?.rankId
    }

    private fun getCurrentActiveParty(playerId: UUID): net.lumalyte.lg.domain.entities.Party? {
        // First check if player has a stored preference
        val preference = preferenceRepository.getByPlayerId(playerId)
        if (preference != null) {
            val party = partyRepository.getById(preference.partyId)
            if (party != null && party.isActive()) {
                return party
            } else {
                // Party no longer exists or is inactive, remove the preference
                preferenceRepository.removeByPlayerId(playerId)
                // Continue to fallback below
            }
        }

        // Fallback to default party behavior
        return partyService.getActivePartyForPlayer(playerId)
    }

    private fun clearInvalidPartyReferences() {
        val allParties = partyRepository.getAll().filter { it.isActive() }
        val validPartyIds = allParties.map { it.id }.toSet()

        val removedCount = preferenceRepository.removeInvalidPreferences(validPartyIds)

        if (removedCount > 0) {
            // Could log this for debugging
        }
    }
}
