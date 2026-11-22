package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import net.lumalyte.lg.domain.values.ChatChannel
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.interaction.commands.PartyChatCommand
import net.lumalyte.lg.interaction.commands.PartyChatCommandDependencies
import net.lumalyte.lg.interaction.commands.PartyChatHandler
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-1 behavior parity: each scenario runs the transitional ACF adapter and the
 * inactive Nexus adapter against identical fresh state and requires identical player output,
 * persisted party preference, routed chat and visibility mutations.
 */
class NexusPartyChatCommandParityTest {

    private data class Transcript(
        val messages: List<String>,
        val preference: UUID?,
        val routed: List<String>,
        val visibilityToggles: Int,
    )

    private class Fixture(partiesEnabled: Boolean) {
        val sent = mutableListOf<String>()
        val routed = mutableListOf<String>()
        var toggles = 0
        var partyVisible = true
        val guild = Guild(id = GUILD_ID, name = "Guild", createdAt = EPOCH)
        var guilds: Set<Guild> = setOf(guild)
        var parties: Set<Party> = emptySet()
        var routeSucceeds = true
        var onlineMembers: Set<UUID> = emptySet()
        var rankId: UUID? = null

        val player = mockk<Player>(relaxed = true).also { player ->
            every { player.uniqueId } returns PLAYER_ID
            every { player.sendMessage(any<Component>()) } answers {
                sent += PlainTextComponentSerializer.plainText().serialize(firstArg<Component>())
            }
        }
        private val lang = mockk<LangService>().also { lang ->
            every { lang.msg(any(), *anyVararg()) } answers {
                val placeholders = args.drop(1).flatMap { (it as? Array<*>)?.toList() ?: listOf(it) }
                Component.text(if (placeholders.isEmpty()) firstArg<String>() else "${firstArg<String>()}$placeholders")
            }
            every { lang.raw(any()) } answers { "raw:${firstArg<String>()}" }
        }
        private val chatService = mockk<ChatService>().also { chat ->
            every { chat.routeMessage(PLAYER_ID, any(), ChatChannel.PARTY) } answers {
                routed += secondArg<String>()
                routeSucceeds
            }
            every { chat.getVisibilitySettings(PLAYER_ID) } answers {
                ChatVisibilitySettings(PLAYER_ID, partyChatVisible = partyVisible)
            }
            every { chat.toggleChatVisibility(PLAYER_ID, ChatChannel.PARTY) } answers {
                toggles++
                partyVisible = !partyVisible
                partyVisible
            }
        }
        private val partyService = mockk<PartyService>().also { service ->
            every { service.getActivePartiesForGuild(any()) } answers {
                parties.filter { it.includesGuild(firstArg()) && it.isActive() }.toSet()
            }
            every { service.getOnlinePartyMembers(any()) } answers { onlineMembers }
        }
        private val guildService = mockk<GuildService>().also { service ->
            every { service.getPlayerGuilds(PLAYER_ID) } answers { guilds }
        }
        private val memberService = mockk<MemberService>().also { service ->
            every { service.getMember(PLAYER_ID, any()) } answers {
                rankId?.let { Member(PLAYER_ID, secondArg(), it, EPOCH) }
            }
        }
        private val partyRepository = mockk<PartyRepository>().also { repository ->
            every { repository.getById(any()) } answers { parties.find { it.id == firstArg<UUID>() } }
        }
        val preferences = FakePreferences()
        private val configService = mockk<ConfigService>().also { config ->
            every { config.loadConfig() } returns MainConfig(partiesEnabled = partiesEnabled)
        }

        val dependencies = PartyChatCommandDependencies(
            lang = lazyOf(lang),
            chatService = lazyOf(chatService),
            partyService = lazyOf(partyService),
            guildService = lazyOf(guildService),
            memberService = lazyOf(memberService),
            partyRepository = lazyOf(partyRepository),
            preferenceRepository = lazyOf(preferences),
            configService = lazyOf(configService),
        )

        fun party(
            name: String?,
            banned: Boolean = false,
            muted: Boolean = false,
            restrictedTo: UUID? = null,
        ): Party = Party(
            id = UUID.nameUUIDFromBytes("party:$name".toByteArray()),
            name = name,
            guildIds = setOf(GUILD_ID),
            leaderId = LEADER_ID,
            createdAt = EPOCH,
            restrictedRoles = restrictedTo?.let(::setOf),
            mutedPlayers = if (muted) mapOf(PLAYER_ID to null) else emptyMap(),
            bannedPlayers = if (banned) setOf(PLAYER_ID) else emptySet(),
        ).also { parties = parties + it }

        fun prefer(party: Party) {
            preferences.stored[PLAYER_ID] = PlayerPartyPreference(PLAYER_ID, party.id, EPOCH)
        }

        fun transcript() = Transcript(sent.toList(), preferences.stored[PLAYER_ID]?.partyId, routed.toList(), toggles)
    }

    private class FakePreferences : PlayerPartyPreferenceRepository {
        val stored = mutableMapOf<UUID, PlayerPartyPreference>()
        var saveSucceeds = true
        var removeSucceeds = true

        override fun getByPlayerId(playerId: UUID) = stored[playerId]
        override fun save(preference: PlayerPartyPreference): Boolean {
            if (saveSucceeds) stored[preference.playerId] = preference
            return saveSucceeds
        }
        override fun removeByPlayerId(playerId: UUID): Boolean {
            if (removeSucceeds) stored.remove(playerId)
            return removeSucceeds
        }
        override fun getAll() = stored.values.toSet()
        override fun removeInvalidPreferences(validPartyIds: Set<UUID>) = 0
    }

    private fun parity(
        partiesEnabled: Boolean = true,
        setup: Fixture.() -> Unit = {},
        acf: PartyChatCommand.(Player) -> Unit,
        nexus: NexusPartyChatCommand.(Player) -> Unit,
    ): Transcript {
        val legacy = Fixture(partiesEnabled).apply(setup)
        PartyChatCommand(legacy.dependencies).acf(legacy.player)
        val ported = Fixture(partiesEnabled).apply(setup)
        NexusPartyChatCommand(PartyChatHandler(ported.dependencies)).nexus(ported.player)
        assertEquals(legacy.transcript(), ported.transcript(), "ACF and Nexus adapters diverged")
        return ported.transcript()
    }

    // --- Disabled-party guard -------------------------------------------------------------

    @Test
    fun `disabled parties refuse every guarded route identically`() {
        val disabled = listOf("command.migrated.party_chat.checkpartiesenabled.parties_are_disabled_on_this_server")
        val setup: Fixture.() -> Unit = { prefer(party("Raid")) }
        assertEquals(disabled, parity(false, setup, { onPartyChat(it) }, { execute(it, null) }).messages)
        assertEquals(disabled, parity(false, setup, { onPartyChat(it, "Raid") }, { execute(it, "Raid") }).messages)
        assertEquals(disabled, parity(false, setup, { onPartyChat(it, "hi") }, { execute(it, "hi") }).messages)
        assertEquals(disabled, parity(false, setup, { onSwitchList(it) }, { switch(it, null) }).messages)
        assertEquals(disabled, parity(false, setup, { onSwitch(it, "GLOBAL") }, { switch(it, "GLOBAL") }).messages)
        assertEquals(disabled, parity(false, setup, { onToggle(it) }, { toggle(it) }).messages)
        assertEquals(disabled, parity(false, setup, { onHelp(it) }, { help(it) }).messages)
    }

    @Test
    fun `clear stays unguarded when parties are disabled`() {
        val result = parity(false, { prefer(party("Raid")) }, { onClear(it) }, { clear(it) })
        assertEquals(null, result.preference)
        assertEquals("command.migrated.party_chat.clear.returned_to_global_chat", result.messages.first())
    }

    /**
     * Framework difference: ACF's multi-word vararg default skipped the guard, Nexus has one
     * greedy default route and applies it uniformly (no message is routed while disabled).
     */
    @Test
    fun `nexus guards multi-word messages while parties are disabled`() {
        val fixture = Fixture(false).apply { prefer(party("Raid")) }
        NexusPartyChatCommand(PartyChatHandler(fixture.dependencies)).execute(fixture.player, "hello party")
        assertEquals(
            listOf("command.migrated.party_chat.checkpartiesenabled.parties_are_disabled_on_this_server"),
            fixture.sent,
        )
        assertTrue(fixture.routed.isEmpty())
    }

    // --- Default route ---------------------------------------------------------------------

    @Test
    fun `bare pc shows global info with available party count`() {
        val result = parity(setup = { party("Raid"); party("Mine") }, acf = { onPartyChat(it) }, nexus = { execute(it, null) })
        assertEquals(
            listOf(
                "command.migrated.party_chat.showcurrentpartyinfo.you_are_currently_using_global_chat",
                "command.migrated.party_chat.showcurrentpartyinfo.your_messages_go_to_global_chat_not",
                "command.migrated.party_chat.showcurrentpartyinfo.available_parties[(size, 2)]",
                "command.migrated.party_chat.showcurrentpartyinfo.use_pc_switch_to_see_and_join",
            ),
            result.messages,
        )
    }

    @Test
    fun `blank greedy input behaves like bare pc`() {
        val legacy = Fixture(true).apply { party("Raid") }
        PartyChatCommand(legacy.dependencies).onPartyChat(legacy.player)
        val ported = Fixture(true).apply { party("Raid") }
        NexusPartyChatCommand(PartyChatHandler(ported.dependencies)).execute(ported.player, "   ")
        assertEquals(legacy.transcript(), ported.transcript())
    }

    @Test
    fun `bare pc shows current party details`() {
        val result = parity(setup = { prefer(party("Raid")) }, acf = { onPartyChat(it) }, nexus = { execute(it, null) })
        assertEquals("command.migrated.party_chat.showcurrentpartyinfo.current_party", result.messages.first())
        assertTrue(result.messages.contains("command.migrated.party_chat.showcurrentpartyinfo.name[(unnamed, Raid)]"))
    }

    @Test
    fun `single party name switches and persists preference case-insensitively`() {
        val result = parity(setup = { party("Raid") }, acf = { onPartyChat(it, "rAiD") }, nexus = { execute(it, "rAiD") })
        assertEquals(UUID.nameUUIDFromBytes("party:Raid".toByteArray()), result.preference)
        assertEquals("command.migrated.party_chat.switchtoparty.switched_to_party[(unnamed, Raid)]", result.messages.first())
        assertTrue(result.routed.isEmpty())
    }

    @Test
    fun `single word that is not a party is sent as a message`() {
        val result = parity(
            setup = { prefer(party("Raid")) },
            acf = { onPartyChat(it, "hello") },
            nexus = { execute(it, "hello") },
        )
        assertEquals(listOf("hello"), result.routed)
        assertTrue(result.messages.isEmpty(), "no success echo")
    }

    @Test
    fun `multi-word message starting with a party name is routed, not a switch`() {
        val result = parity(
            setup = { val raid = party("Raid"); party("Mine"); prefer(raid) },
            acf = { onPartyChat(it, "Mine", "is", "open") },
            nexus = { execute(it, "Mine is open") },
        )
        assertEquals(listOf("Mine is open"), result.routed)
        assertEquals(UUID.nameUUIDFromBytes("party:Raid".toByteArray()), result.preference)
    }

    @Test
    fun `message without active party explains how to switch`() {
        val result = parity(acf = { onPartyChat(it, "hello", "all") }, nexus = { execute(it, "hello all") })
        assertEquals(
            listOf(
                "command.migrated.party_chat.sendpartymessage.you_are_not_in_an_active_party",
                "command.migrated.party_chat.sendpartymessage.use_pc_partyname_to_switch_to_a",
            ),
            result.messages,
        )
        assertTrue(result.routed.isEmpty())
    }

    @Test
    fun `banned muted and rank-restricted senders are refused before routing`() {
        val banned = parity(setup = { prefer(party("Raid", banned = true)) }, acf = { onPartyChat(it, "hey") }, nexus = { execute(it, "hey") })
        assertEquals("command.migrated.party_chat.sendpartymessage.you_are_banned_from_this_channel", banned.messages.first())
        assertTrue(banned.routed.isEmpty())

        val muted = parity(setup = { prefer(party("Raid", muted = true)) }, acf = { onPartyChat(it, "hey") }, nexus = { execute(it, "hey") })
        assertEquals("command.migrated.party_chat.sendpartymessage.you_are_permanently_muted_in_this_channel", muted.messages.first())
        assertTrue(muted.routed.isEmpty())

        val restricted = parity(
            setup = { prefer(party("Raid", restrictedTo = UUID.randomUUID())); rankId = UUID.nameUUIDFromBytes("rank".toByteArray()) },
            acf = { onPartyChat(it, "hey") },
            nexus = { execute(it, "hey") },
        )
        assertEquals(listOf("command.migrated.party_chat.sendpartymessage.you_don_t_have_permission_to_chat"), restricted.messages)
        assertTrue(restricted.routed.isEmpty())
    }

    @Test
    fun `failed routing reports offline members`() {
        val result = parity(
            setup = { prefer(party("Raid")); routeSucceeds = false },
            acf = { onPartyChat(it, "anyone") },
            nexus = { execute(it, "anyone") },
        )
        assertEquals("command.migrated.party_chat.sendpartymessage.no_party_members_are_currently_online_to", result.messages.first())
    }

    @Test
    fun `banned party name is not switchable and is sent as message instead`() {
        val result = parity(setup = { party("Raid", banned = true) }, acf = { onPartyChat(it, "Raid") }, nexus = { execute(it, "Raid") })
        assertEquals(null, result.preference)
        assertEquals("command.migrated.party_chat.sendpartymessage.you_are_not_in_an_active_party", result.messages.first())
    }

    // --- switch ------------------------------------------------------------------------------

    @Test
    fun `switch without target lists accessible parties with current markers`() {
        val result = parity(
            setup = { val raid = party("Raid"); party("Mine"); party("Hidden", banned = true); prefer(raid) },
            acf = { onSwitchList(it) },
            nexus = { switch(it, null) },
        )
        assertEquals("command.migrated.party_chat.switchlist.available_parties", result.messages.first())
        assertTrue("command.migrated.party_chat.switchlist.global" in result.messages)
        assertTrue("command.migrated.party_chat.switchlist.party_current[(party, Raid)]" in result.messages)
        assertTrue("command.migrated.party_chat.switchlist.party[(party, Mine)]" in result.messages)
        assertTrue(result.messages.none { "Hidden" in it })
    }

    @Test
    fun `switch list without guild explains membership`() {
        val result = parity(setup = { guilds = emptySet() }, acf = { onSwitchList(it) }, nexus = { switch(it, null) })
        assertEquals(listOf("command.migrated.party_chat.switchlist.you_are_not_in_any_guild"), result.messages)
    }

    @Test
    fun `switch GLOBAL clears stored preference`() {
        val result = parity(setup = { prefer(party("Raid")) }, acf = { onSwitch(it, "global") }, nexus = { switch(it, "global") })
        assertEquals(null, result.preference)
        assertEquals("command.migrated.party_chat.switch.switched_to_global_chat", result.messages.first())
    }

    @Test
    fun `switch GLOBAL without preference reports already global`() {
        val result = parity(acf = { onSwitch(it, "GLOBAL") }, nexus = { switch(it, "GLOBAL") })
        assertEquals(listOf("command.migrated.party_chat.switch.you_are_already_using_global_chat"), result.messages)
    }

    @Test
    fun `switch GLOBAL storage failure keeps preference`() {
        val result = parity(
            setup = { prefer(party("Raid")); preferences.removeSucceeds = false },
            acf = { onSwitch(it, "GLOBAL") },
            nexus = { switch(it, "GLOBAL") },
        )
        assertEquals(listOf("command.migrated.party_chat.switch.failed_to_switch_to_global_chat"), result.messages)
        assertEquals(UUID.nameUUIDFromBytes("party:Raid".toByteArray()), result.preference)
    }

    @Test
    fun `switch to named party persists and unknown name reports not found`() {
        val ok = parity(setup = { party("Raid") }, acf = { onSwitch(it, "Raid") }, nexus = { switch(it, "Raid") })
        assertEquals(UUID.nameUUIDFromBytes("party:Raid".toByteArray()), ok.preference)

        val failed = parity(
            setup = { party("Raid"); preferences.saveSucceeds = false },
            acf = { onSwitch(it, "Raid") },
            nexus = { switch(it, "Raid") },
        )
        assertEquals(listOf("command.migrated.party_chat.switchtoparty.failed_to_save_party_preference"), failed.messages)

        val missing = parity(acf = { onSwitch(it, "Nope") }, nexus = { switch(it, "Nope") })
        assertEquals(
            listOf(
                "command.migrated.party_chat.switch.party_not_found[(target_name, Nope)]",
                "command.migrated.party_chat.switch.use_pc_switch_to_see_available_parties",
            ),
            missing.messages,
        )
    }

    // --- toggle / clear / help -----------------------------------------------------------

    @Test
    fun `toggle off clears active party and toggle on keeps it`() {
        val off = parity(setup = { prefer(party("Raid")) }, acf = { onToggle(it) }, nexus = { toggle(it) })
        assertEquals(1, off.visibilityToggles)
        assertEquals(null, off.preference)
        assertEquals("command.migrated.party_chat.toggle.party_chat_off", off.messages.first())

        val on = parity(
            setup = { prefer(party("Raid")); partyVisible = false },
            acf = { onToggle(it) },
            nexus = { toggle(it) },
        )
        assertEquals(UUID.nameUUIDFromBytes("party:Raid".toByteArray()), on.preference)
        assertEquals("command.migrated.party_chat.toggle.party_chat_on", on.messages.first())
    }

    @Test
    fun `toggle off with failing preference removal reports failure`() {
        val result = parity(
            setup = { prefer(party("Raid")); preferences.removeSucceeds = false },
            acf = { onToggle(it) },
            nexus = { toggle(it) },
        )
        assertEquals(listOf("command.migrated.party_chat.switch.failed_to_switch_to_global_chat"), result.messages)
    }

    @Test
    fun `clear returns to global then shows info`() {
        val result = parity(setup = { prefer(party("Raid")) }, acf = { onClear(it) }, nexus = { clear(it) })
        assertEquals(null, result.preference)
        assertEquals(
            listOf(
                "command.migrated.party_chat.clear.returned_to_global_chat",
                "command.migrated.party_chat.clear.your_messages_will_now_go_to_global",
                "command.migrated.party_chat.showcurrentpartyinfo.you_are_currently_using_global_chat",
            ),
            result.messages.take(3),
        )
        val failed = parity(
            setup = { prefer(party("Raid")); preferences.removeSucceeds = false },
            acf = { onClear(it) },
            nexus = { clear(it) },
        )
        assertEquals("command.migrated.party_chat.clear.failed_to_clear_party_preference", failed.messages.first())
    }

    @Test
    fun `help lists every legacy line in order`() {
        val result = parity(acf = { onHelp(it) }, nexus = { help(it) })
        assertEquals(11, result.messages.size)
        assertEquals("command.migrated.party_chat.help.party_chat_commands", result.messages.first())
        assertEquals("command.migrated.party_chat.help.party_preferences_persist_across_restarts", result.messages.last())
    }

    // --- Suggestions -----------------------------------------------------------------------

    @Test
    fun `party suggestions are GLOBAL then sorted accessible names`() {
        val fixture = Fixture(true).apply {
            party("Zeta"); party("Alpha"); party(null); party("Hidden", banned = true)
        }
        val handler = PartyChatHandler(fixture.dependencies)
        assertEquals(listOf("GLOBAL", "Alpha", "Zeta"), handler.partySuggestions(PLAYER_ID))
        assertEquals(listOf("GLOBAL"), handler.partySuggestions(null))
        fixture.guilds = emptySet()
        assertEquals(listOf("GLOBAL"), handler.partySuggestions(PLAYER_ID))
    }

    @Test
    fun `nexus suggestion provider filters by typed prefix for the executing player`() {
        val fixture = Fixture(true).apply { party("Raid"); party("Mine") }
        val handler = PartyChatHandler(fixture.dependencies)
        val provider = NexusPartyChatSuggestions.provider { handler }

        fun suggest(sender: CommandSender, typed: String): List<String> {
            val source = mockk<CommandSourceStack>()
            every { source.executor } returns (sender as? Player)
            every { source.sender } returns sender
            val context = mockk<CommandContext<CommandSourceStack>>()
            every { context.source } returns source
            val input = "pc switch $typed"
            val builder = SuggestionsBuilder(input, input.length - typed.length)
            return provider.getSuggestions(context, builder).get().list.map { it.text }
        }

        assertEquals(listOf("GLOBAL", "Mine", "Raid"), suggest(fixture.player, ""))
        assertEquals(listOf("Raid"), suggest(fixture.player, "ra"))
        assertEquals(listOf("GLOBAL"), suggest(mockk<CommandSender>(relaxed = true), "g"))
    }

    private companion object {
        val PLAYER_ID: UUID = UUID.nameUUIDFromBytes("player".toByteArray())
        val GUILD_ID: UUID = UUID.nameUUIDFromBytes("guild".toByteArray())
        val LEADER_ID: UUID = UUID.nameUUIDFromBytes("leader".toByteArray())
        val EPOCH: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
