package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.GuildStallInfo
import net.lumalyte.lg.application.services.GuildStallMemberInfo
import net.lumalyte.lg.application.services.GuildStallPermission
import net.lumalyte.lg.application.services.StallReadResult
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.ServicePriority
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.File
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** Optional integration profile consumes the actual companion JAR in a separate classloader. */
internal class GuildStallRuntimeContractTest {
    @BeforeEach
    fun setup() {
        MockBukkit.mock()
    }

    @AfterEach
    fun cleanup() {
        MockBukkit.unmock()
    }

    /** Public records and service calls must decode across the plugin classloader boundary. */
    @Test
    fun decodesCompanionRuntime() {
        withCompanion { loader ->
            val guildId = UUID.randomUUID()
            val memberId = UUID.randomUUID()
            val row = row(loader, memberId)
            assertSame(loader, row.javaClass.classLoader)
            val calls = mutableListOf<List<Any?>>()
            val provider = provider(loader, calls) { CompletableFuture.completedFuture(listOf(row)) }
            val result = GuildStallReadClient { provider }.read(guildId, memberId).join()
            val stall = assertIs<StallReadResult.Available>(result).stalls.single()
            assertEquals(expectedStall(memberId), stall)
            assertEquals<List<List<Any?>>>(listOf(listOf(guildId, memberId)), calls)
        }
    }

    @Test
    fun discoversCompanionAndRemovesService() {
        withCompanion { loader ->
            val plugin = MockBukkit.createMockPlugin()
            val guildId = UUID.randomUUID()
            val memberId = UUID.randomUUID()
            val calls = mutableListOf<List<Any?>>()
            val provider = provider(loader, calls) {
                CompletableFuture.completedFuture(listOf(row(loader, memberId, nullableMetadata = true)))
            }
            val client = GuildStallReadClient()
            assertEquals(StallReadResult.Unavailable, client.read(guildId, memberId).join())

            register(loader.loadClass(API_NAME), provider, plugin)
            val available = assertIs<StallReadResult.Available>(client.read(guildId, memberId).join())
            assertEquals(
                expectedStall(memberId).copy(nextRentAt = null, graceEndsAt = null, coordinates = null),
                available.stalls.single(),
            )

            plugin.server.servicesManager.unregisterAll(plugin)
            assertEquals(StallReadResult.Unavailable, client.read(guildId, memberId).join())
            assertEquals<List<List<Any?>>>(listOf(listOf(guildId, memberId)), calls)
        }
    }

    @Test
    fun preservesEmptyAndUnavailableResponses() {
        withCompanion { loader ->
            val guildId = UUID.randomUUID()
            val memberId = UUID.randomUUID()
            var response = CompletableFuture.completedFuture(emptyList<Any>())
            val calls = mutableListOf<List<Any?>>()
            val provider = provider(loader, calls) { response }
            val client = GuildStallReadClient { provider }
            assertEquals(StallReadResult.Available(emptyList()), client.read(guildId, memberId).join())

            response = CompletableFuture.failedFuture(IllegalStateException("Companion unavailable"))
            assertEquals(StallReadResult.Unavailable, client.read(guildId, memberId).join())
            assertEquals<List<List<Any?>>>(listOf(listOf(guildId, memberId), listOf(guildId, memberId)), calls)
        }
    }

    @Test
    fun rejectsUnknownCompanionCapabilities() {
        withCompanion { loader ->
            val guildId = UUID.randomUUID()
            val memberId = UUID.randomUUID()
            val calls = mutableListOf<List<Any?>>()
            val provider = provider(loader, calls) {
                CompletableFuture.completedFuture(
                    listOf(row(loader, memberId), row(loader, memberId, permissions = setOf("FUTURE_CAPABILITY"))),
                )
            }
            assertEquals(StallReadResult.Unavailable, GuildStallReadClient { provider }.read(guildId, memberId).join())
            assertEquals<List<List<Any?>>>(listOf(listOf(guildId, memberId)), calls)
        }
    }

    private fun withCompanion(assertions: (ClassLoader) -> Unit) {
        val artifact = System.getenv("MARKET_API_JAR")?.takeIf(String::isNotBlank)?.let(::File)
        assumeTrue(artifact != null, "Set MARKET_API_JAR to the built companion artifact")
        val file = checkNotNull(artifact)
        require(file.isFile) { "Configured MARKET_API_JAR does not exist: $file" }
        URLClassLoader(arrayOf(file.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            assertSame(loader, loader.loadClass(API_NAME).classLoader)
            assertions(loader)
        }
    }

    private fun row(
        loader: ClassLoader,
        memberId: UUID,
        nullableMetadata: Boolean = false,
        permissions: Set<String> = PERMISSIONS,
    ): Any {
        val memberClass = loader.loadClass("net.enthusia.market.api.guild.GuildStallMember")
        val member = memberClass.getConstructor(UUID::class.java, Set::class.java).newInstance(memberId, permissions)
        val rowClass = loader.loadClass("net.enthusia.market.api.guild.GuildStallSnapshot")
        val constructor =
            rowClass.getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java,
                java.lang.Long.TYPE, java.lang.Long.TYPE, Instant::class.java, Instant::class.java,
                String::class.java, List::class.java,
            )
        return constructor.newInstance(
            "stall1",
            "region1",
            "world",
            "OWNED",
            RENT,
            INTERVAL,
            NEXT_RENT.takeUnless { nullableMetadata },
            GRACE_END.takeUnless { nullableMetadata },
            "1, 2, 3".takeUnless { nullableMetadata },
            listOf(member),
        )
    }

    private fun provider(
        loader: ClassLoader,
        calls: MutableList<List<Any?>>,
        response: () -> CompletableFuture<List<Any>>,
    ): Any {
        val api = loader.loadClass(API_NAME)
        return Proxy.newProxyInstance(loader, arrayOf(api)) { proxy, method, args ->
            when (method.name) {
                "apiVersion" -> 1
                "guildStalls" -> {
                    calls.add(checkNotNull(args).toList())
                    response()
                }
                "equals" -> proxy === args?.single()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Market companion contract fixture"
                else -> error("Unexpected API method ${method.name}")
            }
        }
    }

    private fun <T : Any> register(type: Class<T>, provider: Any, plugin: Plugin) {
        plugin.server.servicesManager.register(type, type.cast(provider), plugin, ServicePriority.Normal)
    }

    private fun expectedStall(memberId: UUID) =
        GuildStallInfo(
            "stall1", "region1", "world", "OWNED", RENT, INTERVAL, NEXT_RENT, GRACE_END, "1, 2, 3",
            listOf(GuildStallMemberInfo(memberId, PERMISSIONS.map { GuildStallPermission.valueOf(it) }.toSet())),
        )

    private companion object {
        const val API_NAME = "net.enthusia.market.api.guild.GuildStallReadApi"
        const val RENT = 100L
        const val INTERVAL = 86_400L
        val NEXT_RENT: Instant = Instant.parse("2026-10-21T12:00:00Z")
        val GRACE_END: Instant = Instant.parse("2026-10-22T12:00:00Z")
        val PERMISSIONS = setOf("MANAGE_SHOPS", "ACCESS_SHOP_CHESTS", "EDIT_SHOP_STOCK", "MODIFY_SHOP_PRICES")
    }
}
