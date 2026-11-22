package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository

/** Lazy collaborators for the shared party-chat command handler. */
data class PartyChatCommandDependencies(
    val lang: Lazy<LangService>,
    val chatService: Lazy<ChatService>,
    val partyService: Lazy<PartyService>,
    val guildService: Lazy<GuildService>,
    val memberService: Lazy<MemberService>,
    val partyRepository: Lazy<PartyRepository>,
    val preferenceRepository: Lazy<PlayerPartyPreferenceRepository>,
    val configService: Lazy<ConfigService>,
)
