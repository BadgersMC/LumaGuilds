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
import net.lumalyte.lg.domain.values.PartyChatIntent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * REQ-221: /pc. A guild has one party between guilds, so nothing needs naming:
 * /pc toggles party chat, /pc on|off|mute|help, and any other text is sent once.
 */
@CommandAlias("pc|pchat|partychat")
@CommandPermission("lumaguilds.partychat")
class PartyChatCommand : BaseCommand(), KoinComponent {

    private val lang: LangService by inject()
    private val chatService: ChatService by inject()
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val partyRepository: PartyRepository by inject()
    private val preferenceRepository: PlayerPartyPreferenceRepository by inject()
    private val configService: ConfigService by inject()
    private val guildChat: net.lumalyte.lg.interaction.listeners.GuildChatListener by inject()

    private val actions by lazy {
        net.lumalyte.lg.application.services.PartyActions(partyService, guildService) {
            Duration.ofHours(configService.loadConfig().party.defaultPartyDurationHours.toLong())
        }
    }

    @Default
    @CommandCompletion("on|off|switch|mute|help @guildparty")
    fun onPartyChat(player: Player, vararg args: String) {
        if (!configService.loadConfig().partiesEnabled) {
            player.sendMessage(lang.msg("command.migrated.party_chat.checkpartiesenabled.parties_are_disabled_on_this_server"))
            return
        }
        when (val intent = PartyChatIntent.parse(args.toList())) {
            PartyChatIntent.Toggle -> if (preferenceRepository.getByPlayerId(player.uniqueId) != null) partyChatOff(player) else turnOn(player)
            PartyChatIntent.On -> turnOn(player)
            PartyChatIntent.Off -> partyChatOff(player)
            PartyChatIntent.Mute -> toggleMute(player)
            PartyChatIntent.Help -> help(player)
            is PartyChatIntent.Message -> guildParty(player)?.let { sendPartyMessage(player, intent.text, it) }
        }
    }

    /** The player's guild's party, or null after telling them why there is none. */
    internal fun guildParty(player: Player): Party? {
        val guildIds = getPlayerGuildIds(player.uniqueId)
        if (guildIds.isEmpty()) {
            player.sendMessage(lang.msg("party_chat.no_guild"))
            return null
        }
        val party = guildIds.firstNotNullOfOrNull { actions.partyOf(it) }
        if (party == null) {
            player.sendMessage(lang.msg("party_chat.no_party"))
            return null
        }
        if (party.isPlayerBanned(player.uniqueId)) {
            player.sendMessage(lang.msg("command.migrated.party_chat.sendpartymessage.you_are_banned_from_this_channel"))
            return null
        }
        return party
    }

    private fun turnOn(player: Player) {
        val party = guildParty(player) ?: return
        if (preferenceRepository.getByPlayerId(player.uniqueId)?.partyId == party.id) {
            player.sendMessage(lang.msg("party_chat.already_on", "party" to partyName(party)))
            return
        }
        switchToParty(player, party)
    }

    private fun toggleMute(player: Player) {
        val visible = chatService.toggleChatVisibility(player.uniqueId, ChatChannel.PARTY)
        if (!visible) preferenceRepository.removeByPlayerId(player.uniqueId)
        player.sendMessage(if (visible) lang.msg("party_chat.unmuted") else lang.msg("party_chat.muted"))
    }

    private fun help(player: Player) {
        player.sendMessage(lang.msg("party_chat.help.header"))
        player.sendMessage(lang.msg("party_chat.help.toggle"))
        player.sendMessage(lang.msg("party_chat.help.message"))
        player.sendMessage(lang.msg("party_chat.help.on_off"))
        player.sendMessage(lang.msg("party_chat.help.mute"))
    }

    private fun partyName(party: Party) = party.name ?: lang.raw("command.migrated.party_chat.common.unnamed")

    internal fun partyChatOff(player: Player) {
        if (preferenceRepository.getByPlayerId(player.uniqueId) == null) {
            player.sendMessage(lang.msg("party_chat.already_off"))
            return
        }
        if (preferenceRepository.removeByPlayerId(player.uniqueId)) {
            player.sendMessage(lang.msg("party_chat.turned_off"))
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.clear.failed_to_clear_party_preference"))
        }
    }

    internal fun switchToParty(player: Player, party: net.lumalyte.lg.domain.entities.Party) {
        val playerId = player.uniqueId

        // Store the active party preference persistently
        val preference = PlayerPartyPreference(playerId, party.id)
        val success = preferenceRepository.save(preference)

        if (success) {
            // REQ-222: turning party chat on shows party messages again.
            if (!chatService.getVisibilitySettings(playerId).partyChatVisible) {
                chatService.toggleChatVisibility(playerId, ChatChannel.PARTY)
            }
            player.sendMessage(lang.msg("party_chat.turned_on", "party" to partyName(party)))
            // REQ-209: one chat at a time.
            if (guildChat.leaveGuildChannels(player)) {
                player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.guild_chat_off"))
            }
        } else {
            player.sendMessage(lang.msg("command.migrated.party_chat.switchtoparty.failed_to_save_party_preference"))
        }
    }

    /** Sends one message to [target], or to the player's current party chat when [target] is null. */
    internal fun sendPartyMessage(player: Player, message: String, target: net.lumalyte.lg.domain.entities.Party? = null) {
        val playerId = player.uniqueId
        val party = target ?: getCurrentActiveParty(playerId)

        if (party == null) {
            player.sendMessage(lang.msg("party_chat.no_party"))
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

}
