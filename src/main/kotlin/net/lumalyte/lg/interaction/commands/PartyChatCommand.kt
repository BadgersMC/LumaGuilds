package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.badgersmc.nexus.i18n.LangService
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

    private val lang: LangService by inject()
    private val chatService: ChatService by inject()
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val partyRepository: PartyRepository by inject()
    private val preferenceRepository: PlayerPartyPreferenceRepository by inject()
    private val configService: ConfigService by inject()

    private fun checkPartiesEnabled(player: Player): Boolean {
        if (!configService.loadConfig().partiesEnabled) {
            player.sendMessage(lang.msg("command.migrated.party_chat.checkpartiesenabled.parties_are_disabled_on_this_server"))
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
                    player.sendMessage(lang.msg("command.migrated.party_chat.switch.switched_to_global_chat"))
                    player.sendMessage(lang.msg("command.migrated.party_chat.switch.your_messages_will_now_go_to_global"))
                    player.sendMessage(lang.msg("command.migrated.party_chat.switch.use_pc_switch_partyname_to_switch_back"))
                } else {
                    player.sendMessage(lang.msg("command.migrated.party_chat.switch.failed_to_switch_to_global_chat"))
                }
            } else {
                player.sendMessage(lang.msg("command.migrated.party_chat.switch.you_are_already_using_global_chat"))
            }
            return
        }

        // Try to find a party with this name
        val targetParty = findPartyByName(playerId, targetName)

        if (targetParty != null) {
            switchToParty(player, targetParty)
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switch.party_not_found", "target_name" to targetName))
            player.sendMessage(lang.msg("command.migrated.party_chat.switch.use_pc_switch_to_see_available_parties"))
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
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.you_are_not_in_any_guild"))
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
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.no_available_parties_to_switch_to"))
            return
        }

        val currentParty = getCurrentActiveParty(playerId)
        val isUsingGlobal = preferenceRepository.getByPlayerId(playerId) == null

        player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.available_parties"))

        // Show GLOBAL option
        if (isUsingGlobal) {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.global_current"))
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.global"))
        }

        accessibleParties.forEach { party ->
            val isCurrent = currentParty?.id == party.id
            val marker = if (isCurrent) lang.legacy("command.migrated.party_chat.switchlist.blank_line") else lang.legacy("command.migrated.party_chat.switchlist.blank_line_2")
            val currentTag = if (isCurrent) lang.legacy("command.migrated.party_chat.switchlist.current") else ""
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.blank_line_3", "marker" to marker, "unnamed" to (party.name ?: lang.legacy("command.migrated.party_chat.common.unnamed")), "current_tag" to currentTag))
        }

        player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.blank_line_4"))
        player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.use_pc_switch_name_to_switch"))
    }

    @Subcommand("toggle")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onToggle(player: Player) {
        if (!checkPartiesEnabled(player)) return

        val playerId = player.uniqueId

        // Get current visibility state before toggling
        val currentSettings = chatService.getVisibilitySettings(playerId)
        val wasVisible = currentSettings.partyChatVisible

        // Toggle party chat visibility setting
        val newVisibilityState = chatService.toggleChatVisibility(playerId, ChatChannel.PARTY)

        // Show appropriate message based on new state
        if (newVisibilityState) {
            player.sendMessage(lang.msg("command.migrated.party_chat.toggle.party_chat_on"))
            player.sendMessage(lang.msg("command.migrated.party_chat.toggle.you_will_now_see_party_messages_in"))
            player.sendMessage(lang.msg("command.migrated.party_chat.toggle.use_pc_switch_party_to_also_send"))
        } else {
            // When toggling OFF, also clear the active party preference so the player's
            // messages go to global chat. Leaving the preference while visibility is off
            // causes "Failed to send party message!" because the sender is filtered out
            // of their own recipient list.
            val hadActiveParty = preferenceRepository.getByPlayerId(playerId) != null
            if (hadActiveParty) {
                val removed = preferenceRepository.removeByPlayerId(playerId)
                if (!removed) {
                    player.sendMessage(lang.msg("command.migrated.party_chat.switch.failed_to_switch_to_global_chat"))
                    return
                }
            }
            player.sendMessage(lang.msg("command.migrated.party_chat.toggle.party_chat_off"))
            player.sendMessage(lang.msg("command.migrated.party_chat.toggle.you_will_no_longer_see_party_messages"))
            player.sendMessage(lang.msg("command.migrated.party_chat.toggle.your_messages_now_go_to_global_chat"))
        }
    }

    @Subcommand("help")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onHelp(player: Player) {
        if (!checkPartiesEnabled(player)) return

        player.sendMessage(lang.msg("command.migrated.party_chat.help.party_chat_commands"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_show_current_party_info"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_message_send_message_to_current_party"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_switch_list_available_parties"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_switch_name_switch_to_party_or"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_toggle_toggle_seeing_party_messages_visibility"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_clear_return_to_default_party"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.pc_help_show_this_help"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.blank_line"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.use_pc_switch_global_to_send_to"))
        player.sendMessage(lang.msg("command.migrated.party_chat.help.party_preferences_persist_across_restarts"))
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
                player.sendMessage(lang.msg("command.migrated.party_chat.clear.returned_to_global_chat"))
                player.sendMessage(lang.msg("command.migrated.party_chat.clear.your_messages_will_now_go_to_global"))
            } else {
                player.sendMessage(lang.msg("command.migrated.party_chat.clear.failed_to_clear_party_preference"))
            }
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switch.you_are_already_using_global_chat"))
        }

        // Show current party info after clearing
        showCurrentPartyInfo(player)
    }

    private fun showCurrentPartyInfo(player: Player) {
        val playerId = player.uniqueId
        val hasStoredPreference = preferenceRepository.getByPlayerId(playerId) != null
        val party = getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.you_are_currently_using_global_chat"))
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.your_messages_go_to_global_chat_not"))

            // Show available parties
            val playerGuildIds = getPlayerGuildIds(playerId)
            if (playerGuildIds.isNotEmpty()) {
                val activeParties = playerGuildIds.flatMap { guildId: UUID ->
                    partyService.getActivePartiesForGuild(guildId)
                }.toSet()

                if (activeParties.isNotEmpty()) {
                    player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.available_parties", "size" to activeParties.size))
                    player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_to_see_and_join"))
                }
            }
            return
        }

        // Verify the party is still active
        if (!party.isActive()) {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.your_active_party_is_no_longer_available"))
            preferenceRepository.removeByPlayerId(playerId) // Clear the invalid reference
            return
        }

        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.current_party"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.name", "unnamed" to (party.name ?: lang.legacy("command.migrated.party_chat.common.unnamed"))))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.guilds", "size" to party.guildIds.size))
        val restrictions = if (party.hasRoleRestrictions()) {
            lang.legacy("command.migrated.party_chat.showcurrentpartyinfo.role_restricted")
        } else {
            lang.legacy("command.migrated.party_chat.showcurrentpartyinfo.open_to_all")
        }
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.restrictions", "all" to restrictions))
        val expiry = party.expiresAt?.let {
            lang.legacy(
                "command.migrated.party_chat.showcurrentpartyinfo.expires_in_hours",
                "hours" to java.time.Duration.between(java.time.Instant.now(), it).toHours(),
            )
        } ?: lang.legacy("command.migrated.party_chat.showcurrentpartyinfo.never")
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.expires_h", "h" to expiry))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.status_active_you_are_sending_to_this"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.blank_line"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_message_to_send_a_message"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_to_see_other_parties"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_global_to_return_to"))
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
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.switched_to_party", "unnamed" to (party.name ?: lang.legacy("command.migrated.party_chat.common.unnamed"))))
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.all_your_messages_will_now_go_to"))
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.use_pc_to_see_current_party_info"))
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_global_to_return_to"))
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.this_preference_will_persist_across_server_restarts"))
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.failed_to_save_party_preference"))
        }
    }

    private fun sendPartyMessage(player: Player, message: String) {
        val playerId = player.uniqueId
        val party = getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_not_in_an_active_party"))
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.use_pc_partyname_to_switch_to_a"))
            return
        }

        // Verify the party is still active
        if (!party.isActive()) {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.your_active_party_is_no_longer_available"))
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.the_party_may_have_been_disbanded_or"))
            preferenceRepository.removeByPlayerId(playerId) // Clear the invalid reference
            return
        }

        // Check if player can join this party (role restrictions)
        val playerGuilds = getPlayerGuildIds(playerId)
        val playerGuildId = playerGuilds.firstOrNull() ?: run {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.you_are_not_in_any_guild"))
            return
        }
        val playerRankId = getPlayerRankInGuild(playerId, playerGuildId)

        if (playerRankId != null && !party.canPlayerJoin(playerRankId)) {
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_don_t_have_permission_to_chat"))
            return
        }

        // Check if player is banned from this party/channel
        if (party.isPlayerBanned(playerId)) {
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_banned_from_this_channel"))
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.contact_a_moderator_to_appeal"))
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
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_muted_in_this_channel"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.time_remaining_h_m", "hours" to hours, "minutes" to minutes))
            } else {
                // Permanent mute
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_permanently_muted_in_this_channel"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.contact_a_moderator_to_appeal"))
            }
            return
        }

        // Route the message through the chat service
        val success = chatService.routeMessage(playerId, message, ChatChannel.PARTY)

        if (!success) {
            // Check if there are any online party members to provide better feedback
            val onlineMembers = partyService.getOnlinePartyMembers(party.id)
            if (onlineMembers.isEmpty()) {
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.no_party_members_are_currently_online_to"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.your_message_was_not_sent_because_there"))
            } else {
                // Other reason for failure
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.failed_to_send_party_message"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.some_party_members_may_have_party_chat"))
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
