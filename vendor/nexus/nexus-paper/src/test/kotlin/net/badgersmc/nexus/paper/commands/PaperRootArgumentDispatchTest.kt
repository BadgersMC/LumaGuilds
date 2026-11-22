package net.badgersmc.nexus.paper.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Records every invocation so dispatcher tests can assert routing and parsed values. */
class RootCallLog {
    val calls = mutableListOf<String>()
}

// /gc <message...> — LumaGuilds guild chat: player-only root route with a greedy message.
@Suppress("UnusedParameter")
@Command(name = "gc")
class RootGuildChatFixture(private val log: RootCallLog) {
    @Subcommand("")
    @PlayerOnly
    fun chat(@Context sender: CommandSender, @Arg("message") @Greedy message: String) {
        log.calls += "gc:$message"
    }
}

// Second class sharing the /gc root: subcommands must coexist with root arguments.
@Command(name = "gc")
class RootGuildChatToggleFixture(private val log: RootCallLog) {
    @Subcommand("toggle")
    fun toggle() {
        log.calls += "gc toggle"
    }
}

// /claimlist [page] — bare optional root argument.
@Command(name = "claimlist")
class RootClaimListFixture(private val log: RootCallLog) {
    @Subcommand("")
    fun list(@Arg("page", required = false) page: Int = 1) {
        log.calls += "claimlist:$page"
    }
}

// /ga <message...> — permission-guarded greedy root argument, plus an unguarded subcommand.
@Command(name = "ga")
class RootGuildAnnounceFixture(private val log: RootCallLog) {
    @Subcommand("")
    @Permission("guild.admin.announce")
    fun announce(@Arg("message") @Greedy message: String) {
        log.calls += "ga:$message"
    }

    @Subcommand("status")
    fun status() {
        log.calls += "ga status"
    }
}

// /rootmix <target> [count] — required word followed by an optional trailing argument.
@Command(name = "rootmix")
class RootMixedArgsFixture(private val log: RootCallLog) {
    @Subcommand("")
    fun run(@Arg("target") target: String, @Arg("count", required = false) count: Int = 5) {
        log.calls += "rootmix:$target:$count"
    }
}

class PaperRootArgumentDispatchTest {
    private val scanner = PaperCommandScanner()
    private val log = RootCallLog()
    private val builder = PaperCommandTreeBuilder(CoroutineScope(Dispatchers.Unconfined))
    private val dispatcher = CommandDispatcher<CommandSourceStack>().also { dispatcher ->
        val definitions = listOf(
            RootGuildChatFixture::class,
            RootGuildChatToggleFixture::class,
            RootClaimListFixture::class,
            RootGuildAnnounceFixture::class,
            RootMixedArgsFixture::class
        ).map { scanner.processClass(it) }
        PaperCommandPlanner.plan(definitions).forEach { plan ->
            val tree = builder.build(plan) { definition ->
                definition.commandClass.constructors.single().call(log)
            }
            dispatcher.root.addChild(tree)
        }
    }

    private val console = sender(CommandSender::class.java, permissions = emptySet())
    private val admin = sender(CommandSender::class.java, permissions = setOf("guild.admin.announce"))
    private val player = sender(Player::class.java, permissions = emptySet())

    @Test
    fun `required greedy root argument parses whole message`() {
        assertEquals(1, dispatcher.execute("gc hello guild members", source(player)))
        assertEquals(listOf("gc:hello guild members"), log.calls)
    }

    @Test
    fun `root subcommand coexists with root arguments in a merged root`() {
        assertEquals(1, dispatcher.execute("gc toggle", source(player)))
        assertEquals(listOf("gc toggle"), log.calls)
    }

    @Test
    fun `bare root rejects missing required argument`() {
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("gc", source(player)) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("ga", source(admin)) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("rootmix", source(console)) }
        assertTrue(log.calls.isEmpty())
    }

    @Test
    fun `player-only root argument route rejects console`() {
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("gc hello", source(console)) }
        assertTrue(log.calls.isEmpty())
    }

    @Test
    fun `optional root argument parses value and allows bare root`() {
        assertEquals(1, dispatcher.execute("claimlist 3", source(console)))
        assertEquals(1, dispatcher.execute("claimlist", source(console)))
        assertEquals(listOf("claimlist:3", "claimlist:1"), log.calls)
    }

    @Test
    fun `optional root argument still type checks`() {
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("claimlist abc", source(console)) }
        assertTrue(log.calls.isEmpty())
    }

    @Test
    fun `permitted sender runs guarded greedy root route`() {
        assertEquals(1, dispatcher.execute("ga server restart in five", source(admin)))
        assertEquals(listOf("ga:server restart in five"), log.calls)
    }

    @Test
    fun `denied permission hides root argument but not unguarded subcommand`() {
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("ga hello", source(console)) }
        assertEquals(1, dispatcher.execute("ga status", source(console)))
        assertEquals(listOf("ga status"), log.calls)
    }

    @Test
    fun `required then optional root arguments`() {
        assertEquals(1, dispatcher.execute("rootmix bob", source(console)))
        assertEquals(1, dispatcher.execute("rootmix bob 2", source(console)))
        assertEquals(listOf("rootmix:bob:5", "rootmix:bob:2"), log.calls)
    }

    private fun source(sender: CommandSender): CommandSourceStack =
        proxy(CommandSourceStack::class.java) { method, _ ->
            if (method.name == "getSender") sender else null
        }

    private fun <T : CommandSender> sender(type: Class<T>, permissions: Set<String>): T =
        proxy(type) { method, args ->
            when (method.name) {
                "hasPermission" -> (args?.firstOrNull() as? String)?.let { it in permissions } ?: false
                "isOp" -> false
                else -> null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, answer: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "${type.simpleName}Proxy"
                else -> answer(method, args) ?: defaultFor(method.returnType)
            }
        } as T

    private fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        else -> null
    }
}
