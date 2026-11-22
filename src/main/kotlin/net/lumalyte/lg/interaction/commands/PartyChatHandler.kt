package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Party chat behavior and stored preferences shared by Nexus routes and their completion provider.
 */
class PartyChatHandler(dependencies: PartyChatCommandDependencies) {

    private val lang: LangService by dependencies.lang
    private val chatService: ChatService by dependencies.chatService
    private val partyService: PartyService by dependencies.partyService
    private val guildService: GuildService by dependencies.guildService
    private val memberService: MemberService by dependencies.memberService
    private val partyRepository: PartyRepository by dependencies.partyRepository
    private val preferenceRepository: PlayerPartyPreferenceRepository by dependencies.preferenceRepository
    private val configService: ConfigService by dependencies.configService

    /** Sends the disabled notice and returns false when parties are disabled. */
    fun checkPartiesEnabled(player: Player): Boolean {
        if (!configService.loadConfig().partiesEnabled) {
            player.sendMessage(lang.msg("command.migrated.party_chat.checkpartiesenabled.parties_are_disabled_on_this_server"))
            return false
        }
        return true
    }

    /** Matches the legacy no-argument, single-word and vararg party-chat overloads. */
    fun defaultRoute(player: Player, input: String?) {
        val message = input.orEmpty().split(' ').filter(String::isNotEmpty).joinToString(" ")
        if (' ' in message) {
            // The legacy vararg route sent to the stored party even when party switching was disabled.
            sendPartyMessage(player, message)
            return
        }
        if (!checkPartiesEnabled(player)) return
        if (message.isBlank()) {
            showCurrentPartyInfo(player)
        } else {
            switchOrSend(player, message, message)
        }
    }

    /** Switches to [partyCandidate] when it names an accessible party, otherwise routes [message]. */
    fun switchOrSend(player: Player, partyCandidate: String?, message: String) {
        val targetParty = partyCandidate?.let { findPartyByName(player.uniqueId, it) }
        if (targetParty != null) {
            switchToParty(player, targetParty)
        } else {
            sendPartyMessage(player, message)
        }
    }

    /** Nexus `switch [target]`: no target lists parties, otherwise switches. */
    fun switchRoute(player: Player, target: String?) {
        if (target == null) switchList(player) else switchTo(player, target)
    }

    fun switchTo(player: Player, targetName: String) {
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

        val targetParty = findPartyByName(playerId, targetName)

        if (targetParty != null) {
            switchToParty(player, targetParty)
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switch.party_not_found", "target_name" to targetName))
            player.sendMessage(lang.msg("command.migrated.party_chat.switch.use_pc_switch_to_see_available_parties"))
        }
    }

    fun switchList(player: Player) {
        if (!checkPartiesEnabled(player)) return

        val playerId = player.uniqueId
        val playerGuildIds = getPlayerGuildIds(playerId)

        if (playerGuildIds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.you_are_not_in_any_guild"))
            return
        }

        val accessibleParties = accessibleParties(playerId, playerGuildIds)

        if (accessibleParties.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.no_available_parties_to_switch_to"))
            return
        }

        val currentParty = getCurrentActiveParty(playerId)
        val isUsingGlobal = preferenceRepository.getByPlayerId(playerId) == null

        player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.available_parties"))

        if (isUsingGlobal) {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.global_current"))
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.global"))
        }

        accessibleParties.forEach { party ->
            val isCurrent = currentParty?.id == party.id
            val partyName = party.name ?: lang.raw("command.migrated.party_chat.common.unnamed")
            val row = if (isCurrent) {
                lang.msg("command.migrated.party_chat.switchlist.party_current", "party" to partyName)
            } else {
                lang.msg("command.migrated.party_chat.switchlist.party", "party" to partyName)
            }
            player.sendMessage(row)
        }

        player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.blank_line_4"))
        player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.use_pc_switch_name_to_switch"))
    }

    fun toggle(player: Player) {
        if (!checkPartiesEnabled(player)) return

        val playerId = player.uniqueId

        // Legacy read preserved before toggling (settings may be lazily initialized by the service).
        chatService.getVisibilitySettings(playerId)

        // Toggle party chat visibility setting
        val newVisibilityState = chatService.toggleChatVisibility(playerId, ChatChannel.PARTY)

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

    fun help(player: Player) {
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

    /** Legacy behavior: clear is intentionally not gated by `parties_enabled`. */
    fun clear(player: Player) {
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

        showCurrentPartyInfo(player)
    }

    /**
     * Completion values for the legacy ACF `@parties` id and the Nexus `parties` provider:
     * `GLOBAL` first, then sorted names of active, non-banned parties in the player's guilds.
     */
    fun partySuggestions(playerId: UUID?): List<String> {
        if (playerId == null) return listOf(GLOBAL)
        val playerGuildIds = getPlayerGuildIds(playerId)
        if (playerGuildIds.isEmpty()) return listOf(GLOBAL)
        val partyNames = accessibleParties(playerId, playerGuildIds).mapNotNull { it.name }.sorted()
        return listOf(GLOBAL) + partyNames
    }

    fun showCurrentPartyInfo(player: Player) {
        val playerId = player.uniqueId
        val party = getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.you_are_currently_using_global_chat"))
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.your_messages_go_to_global_chat_not"))

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

        if (!party.isActive()) {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.your_active_party_is_no_longer_available"))
            preferenceRepository.removeByPlayerId(playerId)
            return
        }

        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.current_party"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.name", "unnamed" to (party.name ?: lang.raw("command.migrated.party_chat.common.unnamed"))))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.guilds", "size" to party.guildIds.size))
        val restrictions = if (party.hasRoleRestrictions()) {
            lang.raw("command.migrated.party_chat.showcurrentpartyinfo.role_restricted")
        } else {
            lang.raw("command.migrated.party_chat.showcurrentpartyinfo.open_to_all")
        }
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.restrictions", "all" to restrictions))
        if (party.expiresAt != null) {
            player.sendMessage(
                lang.msg(
                    "command.migrated.party_chat.showcurrentpartyinfo.expires_in_hours_line",
                    "hours" to Duration.between(Instant.now(), party.expiresAt).toHours(),
                ),
            )
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.expires_never_line"))
        }
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.status_active_you_are_sending_to_this"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.blank_line"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_message_to_send_a_message"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_to_see_other_parties"))
        player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_global_to_return_to"))
    }

    private fun accessibleParties(playerId: UUID, playerGuildIds: Set<UUID>): List<Party> =
        playerGuildIds.flatMap { guildId: UUID -> partyService.getActivePartiesForGuild(guildId) }
            .toSet()
            .filter { party -> !party.isPlayerBanned(playerId) }

    private fun findPartyByName(playerId: UUID, partyName: String): Party? =
        accessibleParties(playerId, getPlayerGuildIds(playerId)).find { party ->
            party.name?.equals(partyName, ignoreCase = true) ?: false
        }

    private fun switchToParty(player: Player, party: Party) {
        val playerId = player.uniqueId

        val preference = PlayerPartyPreference(playerId, party.id)
        val success = preferenceRepository.save(preference)

        if (success) {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.switched_to_party", "unnamed" to (party.name ?: lang.raw("command.migrated.party_chat.common.unnamed"))))
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

        if (!party.isActive()) {
            player.sendMessage(lang.msg("command.migrated.party_chat.showcurrentpartyinfo.your_active_party_is_no_longer_available"))
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.the_party_may_have_been_disbanded_or"))
            preferenceRepository.removeByPlayerId(playerId)
            return
        }

        // Check if player can join this party (role restrictions)
        val playerGuildId = getPlayerGuildIds(playerId).firstOrNull() ?: run {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchlist.you_are_not_in_any_guild"))
            return
        }
        val playerRankId = memberService.getMember(playerId, playerGuildId)?.rankId

        if (playerRankId != null && !party.canPlayerJoin(playerRankId)) {
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_don_t_have_permission_to_chat"))
            return
        }

        if (party.isPlayerBanned(playerId)) {
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_banned_from_this_channel"))
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.contact_a_moderator_to_appeal"))
            return
        }

        if (party.isPlayerMuted(playerId)) {
            val muteExpiration = party.mutedPlayers[playerId]
            if (muteExpiration != null) {
                val remaining = Duration.between(Instant.now(), muteExpiration)
                val hours = remaining.toHours()
                val minutes = remaining.toMinutes() % 60
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_muted_in_this_channel"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.time_remaining_h_m", "hours" to hours, "minutes" to minutes))
            } else {
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_permanently_muted_in_this_channel"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.contact_a_moderator_to_appeal"))
            }
            return
        }

        val success = chatService.routeMessage(playerId, message, ChatChannel.PARTY)

        if (!success) {
            val onlineMembers = partyService.getOnlinePartyMembers(party.id)
            if (onlineMembers.isEmpty()) {
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.no_party_members_are_currently_online_to"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.your_message_was_not_sent_because_there"))
            } else {
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.failed_to_send_party_message"))
                player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.some_party_members_may_have_party_chat"))
            }
        }
        // Note: No success message is sent to avoid spam
    }

    private fun getPlayerGuildIds(playerId: UUID): Set<UUID> =
        guildService.getPlayerGuilds(playerId).map { it.id }.toSet()

    private fun getCurrentActiveParty(playerId: UUID): Party? {
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

    private companion object {
        const val GLOBAL = "GLOBAL"
    }
}
