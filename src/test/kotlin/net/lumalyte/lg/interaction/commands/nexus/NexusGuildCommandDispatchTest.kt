package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.interaction.commands.GuildCommandHandlers
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-1: every `/guild` route executes through the actual pinned Nexus Brigadier
 * tree and reaches the shared framework-free [GuildCommandHandlers] with ACF-equivalent values.
 */
class NexusGuildCommandDispatchTest {

    private val handlers = mockk<GuildCommandHandlers>(relaxed = true)
    private val lang = mockk<LangService>(relaxed = true)

    /** Every suggestion key resolves to a provider that suggests its own key (wiring marker). */
    private val markerSuggestions: Map<String, SuggestionProvider<CommandSourceStack>> =
        NexusGuildCommandParityTest.suggestionKeys.associateWith { key ->
            SuggestionProvider { _, builder -> builder.suggest(key).buildFuture() }
        }

    private val guild by lazy {
        guildDispatcher(mapOf(GuildCommandHandlers::class to handlers, LangService::class to lang), markerSuggestions)
    }

    private fun player(vararg permissions: String, all: Boolean = false): Player = mockk<Player>(relaxed = true).also { p ->
        every { p.hasPermission(any<String>()) } answers { all || firstArg<String>() in permissions }
    }

    private fun source(sender: CommandSender): CommandSourceStack = mockk<CommandSourceStack>().also {
        every { it.sender } returns sender
    }

    @Test
    fun `Paper registrar attaches guild alias to the shared handler`() {
        val player = player(all = true)
        val dispatcher = guild.dispatcher
        assertEquals(1, dispatcher.execute("g", source(player)))
        assertEquals(1, dispatcher.execute("g rename Iron Order", source(player)))
        verify { handlers.onDefault(player) }
        verify { handlers.onRename(player, "Iron Order") }
    }
    private data class Case(val path: String, val command: String, val expect: GuildCommandHandlers.(Player) -> Unit)

    private val cases = listOf(
        Case("", "guild") { onDefault(it) },
        Case("create", "guild create Knights") { onCreate(it, "Knights", null) },
        Case("create", "guild create Knights white banner data") { onCreate(it, "Knights", "white banner data") },
        Case("rename", "guild rename My New Guild") { onRename(it, "My New Guild") },
        Case("sethome", "guild sethome") { onSetHome(it, null, null) },
        Case("sethome", "guild sethome base confirm now") { onSetHome(it, "base", "confirm now") },
        Case("home activate", "guild home activate Main Base") { onHomeActivate(it, "Main Base") },
        Case("home", "guild home") { onHome(it, null, null) },
        Case("home", "guild home base confirm") { onHome(it, "base", "confirm") },
        Case("homes", "guild homes") { onHomes(it) },
        Case("home unlock", "guild home unlock Old Base") { onHomeUnlock(it, "Old Base") },
        Case("removehome", "guild removehome Old Base") { onRemoveHome(it, "Old Base") },
        Case("setallyhome", "guild setallyhome") { onSetAllyHome(it) },
        Case("removeallyhome", "guild removeallyhome") { onRemoveAllyHome(it) },
        Case("allyhome", "guild allyhome Allies") { onAllyHome(it, "Allies", null) },
        Case("allyhome", "guild allyhome Allies confirm") { onAllyHome(it, "Allies", "confirm") },
        Case("ranks", "guild ranks") { onRanks(it) },
        Case("ranks chat", "guild ranks chat on") { onRanksChat(it, "on") },
        Case("emoji", "guild emoji") { onEmoji(it, null) },
        Case("emoji", "guild emoji :fire:") { onEmoji(it, ":fire:") },
        Case("mode", "guild mode peaceful") { onMode(it, "peaceful") },
        Case("history", "guild history Some Player") { onHistory(it, "Some Player") },
        Case("chatsettings", "guild chatsettings") { onChatSettings(it) },
        Case("chat", "guild chat") { onGuildChat(it) },
        Case("notifications", "guild notifications") { onNotifications(it, null) },
        Case("notify", "guild notify off") { onNotifications(it, "off") },
        Case("allychat", "guild allychat") { onAllyChat(it) },
        Case("modchat", "guild modchat") { onModChat(it) },
        Case("info", "guild info") { onInfo(it, null) },
        Case("info", "guild info Iron Order") { onInfo(it, "Iron Order") },
        Case("strikes", "guild strikes") { onStrikes(it, null) },
        Case("strikes", "guild strikes Iron Order") { onStrikes(it, "Iron Order") },
        Case("strikes punish", "guild strikes punish Iron Order") { onStrikesPunish(it, "Iron Order") },
        Case("disband", "guild disband") { onDisband(it) },
        Case("bank", "guild bank") { onBank(it) },
        Case("stall", "guild stall") { onStall(it) },
        Case("stalls", "guild stalls") { onStall(it) },
        Case("quests", "guild quests") { onQuests(it) },
        Case("quest", "guild quest") { onQuests(it) },
        Case("quests menu", "guild quests menu") { onQuestMenu(it) },
        Case("quest menu", "guild quest menu") { onQuestMenu(it) },
        Case("leaderboard", "guild leaderboard") { onLeaderboard(it) },
        Case("menu", "guild menu") { onMenu(it) },
        Case("invite", "guild invite Steve") { onInvite(it, "Steve") },
        Case("join", "guild join Iron Order") { onJoin(it, "Iron Order") },
        Case("accept", "guild accept Iron Order") { onJoin(it, "Iron Order") },
        Case("list", "guild list") { onList(it) },
        Case("lfg", "guild lfg") { onLfg(it) },
        Case("browse", "guild browse") { onLfg(it) },
        Case("recruitment", "guild recruitment") { onLfg(it) },
        Case("decline", "guild decline Iron Order") { onDecline(it, "Iron Order") },
        Case("block", "guild block Steve") { onBlock(it, "Steve") },
        Case("unblock", "guild unblock Steve") { onUnblock(it, "Steve") },
        Case("blocked", "guild blocked") { onBlocked(it) },
        Case("invites", "guild invites") { onInvites(it) },
        Case("kick", "guild kick OfflineAlex") { onKick(it, "OfflineAlex") },
        Case("leave", "guild leave") { onLeave(it) },
        Case("transfer", "guild transfer OfflineAlex") { onTransfer(it, "OfflineAlex") },
        Case("tag", "guild tag") { onTag(it, null) },
        Case("tag", "guild tag <red>Iron Order</red>") { onTag(it, "<red>Iron Order</red>") },
        Case("bannerman", "guild bannerman") { onBannerman(it) },
        Case("description", "guild description") { onDescription(it, null) },
        Case("desc", "guild desc We ride at dawn") { onDescription(it, "We ride at dawn") },
        Case("warbanner", "guild warbanner") { onWarBanner(it) },
        Case("warbanner tp", "guild warbanner tp") { onWarBannerTeleport(it) },
        Case("war", "guild war") { onWar(it) },
        Case("getvault", "guild getvault") { onVaultGet(it) },
        Case("vault", "guild vault") { onVault(it) },
        Case("start", "guild start") { onGettingStarted(it, null) },
        Case("gettingstarted", "guild gettingstarted 0f8fad5b-d9cb-469f-a165-70867728950e") {
            onGettingStarted(it, "0f8fad5b-d9cb-469f-a165-70867728950e")
        },
        Case("start dismiss", "guild start dismiss") { onDismissGettingStarted(it) },
        Case("help", "guild help") { onHelp(it, null) },
        Case("help", "guild help war guide") { onHelp(it, "war guide") },
        Case("ally", "guild ally Iron Order") { onAlly(it, "Iron Order") },
        Case("enemy", "guild enemy Iron Order") { onEnemy(it, "Iron Order") },
        Case("truce", "guild truce Iron") { onTruce(it, "Iron", null) },
        Case("truce", "guild truce Iron 7") { onTruce(it, "Iron", 7) },
        Case("neutral", "guild neutral Iron Order") { onNeutral(it, "Iron Order") },
        Case("balance", "guild balance") { onBalance(it, null) },
        Case("bal", "guild bal Iron Order") { onBalance(it, "Iron Order") },
        Case("baltop", "guild baltop") { onBaltop(it) },
    )

    @Test
    fun `cases cover all 71 alias-expanded guild routes`() {
        assertEquals(71, cases.map { it.path }.toSet().size)
    }

    @Test
    fun `every route dispatches through the pinned Brigadier tree to the shared handler`() {
        val sender = player(all = true)
        val src = source(sender)
        cases.forEach { case ->
            clearMocks(handlers, answers = false)
            assertEquals(1, guild.dispatcher.execute(case.command, src), "/${case.command} result")
            verify(exactly = 1) { case.expect(handlers, sender) }
        }
    }

    @Test
    fun `every adapter bean is stateless and shares the single handler instance holding the cooldown`() {
        guild.beans.forEach { bean ->
            val fields = generateSequence<Class<*>>(bean.javaClass) { it.superclass }
                .takeWhile { it != Any::class.java }
                .flatMap { it.declaredFields.asSequence() }
                .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .toList()
            assertTrue(fields.isNotEmpty(), "${bean.javaClass.simpleName} has no handler reference")
            fields.forEach { field ->
                field.isAccessible = true
                assertSame(handlers, field.get(bean), "${bean.javaClass.simpleName}.${field.name} must be the shared handlers")
            }
        }
        val cooldown = GuildCommandHandlers::class.java.declaredFields.map { it.name }
        assertTrue("lastHomeTeleport" in cooldown, "home cooldown state must live in GuildCommandHandlers")
    }

    @Test
    fun `missing route permission hides the literal and never invokes the handler`() {
        val src = source(player("lumaguilds.guild.home"))
        clearMocks(handlers, answers = false)
        assertThrows<CommandSyntaxException> { guild.dispatcher.execute("guild rename My New Guild", src) }
        assertThrows<CommandSyntaxException> { guild.dispatcher.execute("guild disband", src) }
        assertThrows<CommandSyntaxException> { guild.dispatcher.execute("guild strikes punish Iron", src) }
        verify(exactly = 0) { handlers.onRename(any(), any()) }
        verify(exactly = 0) { handlers.onDisband(any()) }
        verify(exactly = 0) { handlers.onStrikesPunish(any(), any()) }
    }

    @Test
    fun `bare root without menu permission does not open the guild menu`() {
        val sender = player("lumaguilds.guild.home")
        clearMocks(handlers, answers = false)
        assertEquals(0, guild.dispatcher.execute("guild", source(sender)))
        verify(exactly = 0) { handlers.onDefault(any()) }
    }

    @Test
    fun `routes without an ACF permission stay available to plain members`() {
        val sender = player()
        val src = source(sender)
        clearMocks(handlers, answers = false)
        guild.dispatcher.execute("guild notify toggle", src)
        guild.dispatcher.execute("guild info Iron Order", src)
        guild.dispatcher.execute("guild strikes", src)
        verify(exactly = 1) { handlers.onNotifications(sender, "toggle") }
        verify(exactly = 1) { handlers.onInfo(sender, "Iron Order") }
        verify(exactly = 1) { handlers.onStrikes(sender, null) }
    }

    @Test
    fun `console is rejected on every player-only guild route`() {
        val console = mockk<CommandSender>(relaxed = true).also { every { it.hasPermission(any<String>()) } returns true }
        val src = source(console)
        clearMocks(handlers, answers = false)
        cases.forEach { case ->
            runCatching { guild.dispatcher.execute(case.command, src) }
        }
        verify(exactly = 0) { handlers.onRename(any(), any()) }
        verify(exactly = 0) { handlers.onDefault(any()) }
        verify(exactly = 0) { handlers.onNotifications(any(), any()) }
        verify(exactly = 0) { handlers.onHelp(any(), any()) }
    }

    @Test
    fun `integer truce duration keeps ACF type checking`() {
        val src = source(player(all = true))
        clearMocks(handlers, answers = false)
        assertThrows<CommandSyntaxException> { guild.dispatcher.execute("guild truce Iron seven", src) }
        verify(exactly = 0) { handlers.onTruce(any(), any(), any()) }
    }

    /**
     * Literal completions reach clients through Paper's synced command tree, which only contains
     * nodes whose `requires` passes for that sender (Brigadier's server-side
     * `getCompletionSuggestions` does not re-check `requires`), so visibility is asserted via `canUse`.
     */
    private fun visibleChildren(src: CommandSourceStack, vararg path: String): Set<String> {
        var node: com.mojang.brigadier.tree.CommandNode<CommandSourceStack> = guild.dispatcher.root.getChild("guild")
        path.forEach { node = node.getChild(it) }
        return node.children.filter { it.canUse(src) }.map { it.name }.toSet()
    }

    @Test
    fun `completions are permission filtered`() {
        val member = source(player("lumaguilds.guild.home"))
        val root = visibleChildren(member)
        assertTrue(root.containsAll(listOf("home", "homes", "notify", "notifications", "info", "strikes")), "$root")
        assertFalse("rename" in root || "disband" in root || "sethome" in root, "$root")
        assertEquals(setOf("homeName", "activate"), visibleChildren(member, "home"), "home unlock needs sethome")
        assertFalse("punish" in visibleChildren(member, "strikes"))
        assertTrue("punish" in visibleChildren(source(player("lumaguilds.admin.strikes")), "strikes"))
        val console = mockk<CommandSender>(relaxed = true).also { every { it.hasPermission(any<String>()) } returns true }
        assertEquals(emptySet(), visibleChildren(source(console)), "every /guild route is player-only")
    }

    @Test
    fun `argument completions use the guild suggestion providers`() {
        val src = source(player(all = true))
        mapOf(
            "guild kick " to "guild.guildmembers",
            "guild transfer " to "guild.guildmembers",
            "guild home " to "guild.guildaccessiblehomes",
            "guild home activate " to "guild.guildhomes",
            "guild removehome " to "guild.guildhomes",
            "guild allyhome " to "guild.allyguilds",
            "guild truce " to "guild.guilds",
            "guild notify " to "guild.onofftoggle",
            "guild ranks chat " to "guild.onoff",
            "guild mode " to "guild.modes",
            "guild invite " to "guild.allplayers",
            "guild decline " to "guild.pendinginvites",
            "guild info " to "guild.guildsorplayers",
            "guild emoji " to "guild.unlockedemojis",
            "guild history " to "guild.players",
        ).forEach { (input, provider) ->
            assertTrue(provider in suggestions(input, src), "/$input -> ${suggestions(input, src)}")
        }
    }

    private fun suggestions(input: String, src: CommandSourceStack): List<String> =
        guild.dispatcher.getCompletionSuggestions(guild.dispatcher.parse(input, src)).get().list.map { it.text }
}
