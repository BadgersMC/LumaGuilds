package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.UUID

@CommandAlias("pc|pchat|partychat")
class PartyChatCommand : BaseCommand(), KoinComponent {

    private val chatService: ChatService by inject()
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val partyRepository: PartyRepository by inject()
    private val preferenceRepository: PlayerPartyPreferenceRepository by inject()
    private val configService: ConfigService by inject()

    private fun checkPartiesEnabled(player: Player): Boolean {
        if (!configService.loadConfig().partiesEnabled) {
            player.sendMessage("§c❌ Parties are disabled on this server!")
            return false
        }
        return true
    }

    @Default
    @CommandPermission("lumaguilds.partychat")
    fun onPartyChat(player: Player) {
        if (!checkPartiesEnabled(player)) return

        // No arguments - show current party info
        showCurrentPartyInfo(player)
    }

    @Default
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("@parties switch help toggle clear")
    fun onPartyChat(player: Player, firstArg: String) {
        if (!checkPartiesEnabled(player)) return

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
    @CommandPermission("lumaguilds.partychat")
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

    @Subcommand("switch")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("GLOBAL|@parties")
    fun onSwitch(player: Player, targetName: String) {
        if (!checkPartiesEnabled(player)) return

        val playerId = player.uniqueId

        // Handle GLOBAL option - clear party preference to use global chat
        if (targetName.equals("GLOBAL", ignoreCase = true)) {
            val hadActiveParty = preferenceRepository.getByPlayerId(playerId) != null

            if (hadActiveParty) {
                val success = preferenceRepository.removeByPlayerId(playerId)
                if (success) {
                    player.sendMessage("§a✅ Switched to GLOBAL chat")
                    player.sendMessage("§7Your messages will now go to global chat, not party chat")
                    player.sendMessage("§7Use §f/pc switch <partyname> §7to switch back to a party")
                } else {
                    player.sendMessage("§c❌ Failed to switch to global chat!")
                }
            } else {
                player.sendMessage("§7You are already using global chat")
            }
            return
        }

        // Try to find a party with this name
        val targetParty = findPartyByName(playerId, targetName)

        if (targetParty != null) {
            switchToParty(player, targetParty)
        } else {
            player.sendMessage("§c❌ Party not found: §f$targetName")
            player.sendMessage("§7Use §f/pc switch §7to see available parties")
        }
    }

    @Subcommand("switch")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onSwitchList(player: Player) {
        if (!checkPartiesEnabled(player)) return

        val playerId = player.uniqueId
        val playerGuildIds = getPlayerGuildIds(playerId)

        if (playerGuildIds.isEmpty()) {
            player.sendMessage("§c❌ You are not in any guild!")
            return
        }

        val activeParties = playerGuildIds.flatMap { guildId: UUID ->
            partyService.getActivePartiesForGuild(guildId)
        }.toSet()

        // Filter out parties the player is banned from
        val accessibleParties = activeParties.filter { party ->
            !party.isPlayerBanned(playerId)
        }

        if (accessibleParties.isEmpty()) {
            player.sendMessage("§c❌ No available parties to switch to!")
            return
        }

        val currentParty = getCurrentActiveParty(playerId)
        val isUsingGlobal = preferenceRepository.getByPlayerId(playerId) == null

        player.sendMessage("§6=== Available Parties ===")

        // Show GLOBAL option
        if (isUsingGlobal) {
            player.sendMessage("§a▶ GLOBAL §7(current)")
        } else {
            player.sendMessage("§7  GLOBAL")
        }

        accessibleParties.forEach { party ->
            val isCurrent = currentParty?.id == party.id
            val marker = if (isCurrent) "§a▶" else "§7 "
            val currentTag = if (isCurrent) " §7(current)" else ""
            player.sendMessage("$marker §f${party.name ?: "Unnamed"}$currentTag")
        }

        player.sendMessage("§6========================")
        player.sendMessage("§7Use §f/pc switch <name> §7to switch")
    }

    @Subcommand("toggle")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onToggle(player: Player) {
        if (!checkPartiesEnabled(player)) return

        val playerId = player.uniqueId

        // Note: Toggle controls visibility, not which channel you're sending to
        // Use /pc switch GLOBAL to send to global chat instead of party chat

        // Get current visibility state before toggling
        val currentSettings = chatService.getVisibilitySettings(playerId)
        val wasVisible = currentSettings.partyChatVisible

        // Toggle party chat visibility setting
        val newVisibilityState = chatService.toggleChatVisibility(playerId, ChatChannel.PARTY)

        // Show appropriate message based on new state
        if (newVisibilityState) {
            player.sendMessage("§a✅ Party chat visibility: §nON")
            player.sendMessage("§7You will now SEE party messages in your chat")
            player.sendMessage("§7This does not affect which channel you send to")
        } else {
            player.sendMessage("§e⚠️ Party chat visibility: §nOFF")
            player.sendMessage("§7You will no longer see party messages in your chat")
            player.sendMessage("§7This does not affect which channel you send to")
            player.sendMessage("§7Use §f/pc switch GLOBAL §7to send messages to global chat")
        }
    }

    @Subcommand("help")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onHelp(player: Player) {
        if (!checkPartiesEnabled(player)) return

        player.sendMessage("§6=== Party Chat Commands ===")
        player.sendMessage("§f/pc §7- Show current party info")
        player.sendMessage("§f/pc <message> §7- Send message to current party")
        player.sendMessage("§f/pc switch §7- List available parties")
        player.sendMessage("§f/pc switch <name> §7- Switch to party or GLOBAL")
        player.sendMessage("§f/pc toggle §7- Toggle seeing party messages (visibility)")
        player.sendMessage("§f/pc clear §7- Return to default party")
        player.sendMessage("§f/pc help §7- Show this help")
        player.sendMessage("§6=========================")
        player.sendMessage("§7Use §f/pc switch GLOBAL §7to send to global chat!")
        player.sendMessage("§7Party preferences persist across restarts!")
    }

    @Subcommand("clear")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onClear(player: Player) {
        val playerId = player.uniqueId
        val hadActiveParty = preferenceRepository.getByPlayerId(playerId) != null

        if (hadActiveParty) {
            val success = preferenceRepository.removeByPlayerId(playerId)
            if (success) {
                player.sendMessage("§a✅ Returned to GLOBAL chat")
                player.sendMessage("§7Your messages will now go to global chat")
            } else {
                player.sendMessage("§c❌ Failed to clear party preference!")
            }
        } else {
            player.sendMessage("§7You are already using global chat")
        }

        // Show current party info after clearing
        showCurrentPartyInfo(player)
    }

    private fun showCurrentPartyInfo(player: Player) {
        val playerId = player.uniqueId
        val hasStoredPreference = preferenceRepository.getByPlayerId(playerId) != null
        val party = getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage("§a✅ You are currently using §nGLOBAL§a chat")
            player.sendMessage("§7Your messages go to global chat, not party chat")

            // Show available parties
            val playerGuildIds = getPlayerGuildIds(playerId)
            if (playerGuildIds.isNotEmpty()) {
                val activeParties = playerGuildIds.flatMap { guildId: UUID ->
                    partyService.getActivePartiesForGuild(guildId)
                }.toSet()

                if (activeParties.isNotEmpty()) {
                    player.sendMessage("§7Available parties: §f${activeParties.size}")
                    player.sendMessage("§7Use §f/pc switch §7to see and join a party")
                }
            }
            return
        }

        // Verify the party is still active
        if (!party.isActive()) {
            player.sendMessage("§c❌ Your active party is no longer available!")
            preferenceRepository.removeByPlayerId(playerId) // Clear the invalid reference
            return
        }

        player.sendMessage("§6=== Current Party ===")
        player.sendMessage("§7Name: §f${party.name ?: "Unnamed"}")
        player.sendMessage("§7Guilds: §f${party.guildIds.size}")
        player.sendMessage("§7Restrictions: §f${if (party.hasRoleRestrictions()) "Role-restricted" else "Open to all"}")
        player.sendMessage("§7Expires: §f${party.expiresAt?.let { "in ${java.time.Duration.between(java.time.Instant.now(), it).toHours()}h" } ?: "Never"}")
        player.sendMessage("§7Status: §aActive (you are sending to this party)")
        player.sendMessage("§6=====================")
        player.sendMessage("§7Use §f/pc <message> §7to send a message")
        player.sendMessage("§7Use §f/pc switch §7to see other parties")
        player.sendMessage("§7Use §f/pc switch GLOBAL §7to return to global chat")
    }

    private fun findPartyByName(playerId: UUID, partyName: String): net.lumalyte.lg.domain.entities.Party? {
        // Get all active parties for the player's guilds
        val playerGuildIds = getPlayerGuildIds(playerId)
        val activeParties = playerGuildIds.flatMap { guildId: UUID ->
            partyService.getActivePartiesForGuild(guildId)
        }.toSet()

        // Filter out parties the player is banned from
        val accessibleParties = activeParties.filter { party ->
            !party.isPlayerBanned(playerId)
        }

        // Find party by exact name match (case insensitive)
        return accessibleParties.find { party ->
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
            player.sendMessage("§7All your messages will now go to this party chat!")
            player.sendMessage("§7Use §f/pc §7to see current party info")
            player.sendMessage("§7Use §f/pc switch GLOBAL §7to return to global chat")
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

        // Check if player is banned from this party/channel
        if (party.isPlayerBanned(playerId)) {
            player.sendMessage("§c❌ You are banned from this channel!")
            player.sendMessage("§7Contact a moderator to appeal.")
            return
        }

        // Check if player is muted in this party/channel
        if (party.isPlayerMuted(playerId)) {
            val muteExpiration = party.mutedPlayers[playerId]
            if (muteExpiration != null) {
                // Temporary mute - show remaining time
                val remaining = Duration.between(Instant.now(), muteExpiration)
                val hours = remaining.toHours()
                val minutes = remaining.toMinutes() % 60
                player.sendMessage("§c❌ You are muted in this channel!")
                player.sendMessage("§7Time remaining: §f${hours}h ${minutes}m")
            } else {
                // Permanent mute
                player.sendMessage("§c❌ You are permanently muted in this channel!")
                player.sendMessage("§7Contact a moderator to appeal.")
            }
            return
        }

        // Route the message through the chat service
        val success = chatService.routeMessage(playerId, message, ChatChannel.PARTY)

        if (!success) {
            // Check if there are any online party members to provide better feedback
            val onlineMembers = partyService.getOnlinePartyMembers(party.id)
            if (onlineMembers.isEmpty()) {
                player.sendMessage("§e⚠️ No party members are currently online to receive your message")
                player.sendMessage("§7Your message was not sent because there are no recipients")
            } else {
                // Other reason for failure
                player.sendMessage("§c❌ Failed to send party message!")
                player.sendMessage("§7Some party members may have party chat visibility disabled")
            }
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
        // Check if player has explicitly switched to a party
        val preference = preferenceRepository.getByPlayerId(playerId)
        if (preference != null) {
            val party = partyRepository.getById(preference.partyId)
            if (party != null && party.isActive()) {
                return party
            } else {
                // Party no longer exists or is inactive, remove the preference
                preferenceRepository.removeByPlayerId(playerId)
            }
        }

        // No automatic party assignment - players are in GLOBAL chat by default
        return null
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
