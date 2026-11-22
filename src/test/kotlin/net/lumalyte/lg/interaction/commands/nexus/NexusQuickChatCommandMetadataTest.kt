package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusQuickChatCommandMetadataTest {
    private val roots: List<Pair<KClass<*>, String>> = listOf(
        NexusQuickGuildChatCommand::class to "gc",
        NexusQuickAllyChatCommand::class to "gac",
        NexusQuickModChatCommand::class to "gmc",
    )

    @Test
    fun `quick chat roots expose one player-only default route with optional greedy message`() {
        val definitions = scanGuildCommandDefinitions()
        for ((type, rootName) in roots) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == type }, rootName)
            assertEquals(rootName, definition.annotation.name)
            assertTrue(definition.annotation.aliases.isEmpty(), "$rootName must not gain aliases")

            assertEquals(1, definition.subcommands.size, "$rootName must have exactly one route")
            val route = assertNotNull(definition.subcommands.singleOrNull { it.path.isEmpty() }, rootName)
            assertEquals("lumaguilds.guild.chat", route.permission)
            assertTrue(route.isPlayerOnly)

            assertEquals(1, route.parameters.count { it.isContext }, "$rootName player context")
            assertEquals(1, route.parameters.count { it.isArg }, "$rootName message arg")
            val message = route.parameters.single { it.isArg }
            assertEquals("message", message.name)
            assertTrue(message.isOptional)
        }
    }

    @Test
    fun `message is the last handler parameter and is a greedy String`() {
        for ((type, rootName) in roots) {
            val handler = type.java.declaredMethods.single { it.name == "execute" }
            assertEquals(listOf(Player::class.java, String::class.java), handler.parameterTypes.toList(), rootName)
            val last = handler.parameterAnnotations.last()
            assertTrue(last.any { it is Greedy }, "$rootName message must be @Greedy")
            assertTrue(handler.parameterAnnotations.dropLast(1).none { params -> params.any { it is Greedy } })
        }
    }

    // --- Pure handler contract (paths that return before RoseChat dispatch) ---

    private val lang = mockk<LangService>()
    private val guildService = mockk<GuildService>()
    private val penaltyService = mockk<PenaltyService>()
    private val memberService = mockk<MemberService>()
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()
    private val guild = Guild(id = UUID.randomUUID(), name = "A", createdAt = Instant.now())

    init {
        every { player.uniqueId } returns playerId
        every { lang.msg(any<String>()) } answers { Component.text(firstArg<String>()) }
    }

    private fun msg(key: String) = Component.text(key)

    @Test
    fun `gc without guild sends not-member and nothing else`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        NexusQuickGuildChatCommand(lang, guildService, penaltyService).execute(player, null)
        verify(exactly = 1) { player.sendMessage(msg("guild.not_member")) }
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun `gc with null or blank message shows legacy usage in order`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        val command = NexusQuickGuildChatCommand(lang, guildService, penaltyService)
        for (input in listOf(null, "", "   ")) {
            command.execute(player, input)
        }
        verify(exactly = 3) {
            player.sendMessage(msg("command.migrated.quick_guild_chat.default.quick_guild_chat"))
        }
        verifyOrder {
            player.sendMessage(msg("command.migrated.quick_guild_chat.default.quick_guild_chat"))
            player.sendMessage(msg("command.migrated.quick_guild_chat.default.use_gc_message_to_send_a_single"))
            player.sendMessage(msg("command.migrated.quick_ally_chat.default.your_chat_channel_won_t_change_you"))
            player.sendMessage(msg("command.migrated.quick_guild_chat.default.to_toggle_permanent_guild_chat_mode_use"))
        }
        verify(exactly = 0) { penaltyService.isGuildMuted(any()) }
    }

    @Test
    fun `gc message from muted guild is refused before dispatch`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        every { penaltyService.isGuildMuted(guild.id) } returns true
        NexusQuickGuildChatCommand(lang, guildService, penaltyService).execute(player, "hello there")
        verify(exactly = 1) {
            player.sendMessage(msg("command.migrated.quick_guild_chat.message.your_guild_is_muted_guild_chat_is"))
        }
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun `gac with no message shows legacy usage in order`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        NexusQuickAllyChatCommand(lang, guildService).execute(player, null)
        verifyOrder {
            player.sendMessage(msg("command.migrated.quick_ally_chat.default.quick_ally_chat"))
            player.sendMessage(msg("command.migrated.quick_ally_chat.default.use_gac_message_to_send_a_single"))
            player.sendMessage(msg("command.migrated.quick_ally_chat.default.your_chat_channel_won_t_change_you"))
            player.sendMessage(msg("command.migrated.quick_ally_chat.default.to_toggle_permanent_ally_chat_mode_use"))
        }
    }

    @Test
    fun `gac without guild sends not-member`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        NexusQuickAllyChatCommand(lang, guildService).execute(player, "hi")
        verify(exactly = 1) { player.sendMessage(msg("guild.not_member")) }
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun `gmc non-moderator is refused for usage and message`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        every { memberService.hasPermission(playerId, guild.id, RankPermission.MODERATE_CHAT) } returns false
        val command = NexusQuickModChatCommand(lang, guildService, memberService)
        command.execute(player, null)
        command.execute(player, "hi")
        verify(exactly = 2) { player.sendMessage(msg("command.quick_mod_chat.only_moderators")) }
        verify(exactly = 2) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun `gmc moderator with no message shows legacy usage in order`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        every { memberService.hasPermission(playerId, guild.id, RankPermission.MODERATE_CHAT) } returns true
        NexusQuickModChatCommand(lang, guildService, memberService).execute(player, " ")
        verifyOrder {
            player.sendMessage(msg("command.migrated.quick_mod_chat.default.quick_mod_chat"))
            player.sendMessage(msg("command.migrated.quick_mod_chat.default.use_gmc_message_to_send_a_message"))
            player.sendMessage(msg("command.migrated.quick_mod_chat.default.only_guild_moderators_will_see_your_message"))
            player.sendMessage(msg("command.migrated.quick_mod_chat.default.to_toggle_mod_chat_mode_use_g"))
        }
    }
}
