package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.GuildStallPermission
import net.lumalyte.lg.application.services.StallReadResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Optional integration profile consumes the actual companion JAR in a separate classloader. */
internal class GuildStallRuntimeContractTest {
    /** Public records and service calls must decode across the plugin classloader boundary. */
    @Test
    fun decodesCompanionRuntime() {
        val artifact = System.getenv("MARKET_API_JAR")?.let(::File)
        assumeTrue(artifact?.isFile == true, "Set MARKET_API_JAR to the built companion artifact")
        URLClassLoader(arrayOf(artifact!!.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            val memberId = UUID.randomUUID()
            val provider = provider(loader, row(loader, memberId))
            val result = GuildStallReadClient { provider }.read(UUID.randomUUID(), memberId).join()
            val stall = assertIs<StallReadResult.Available>(result).stalls.single()
            assertEquals(RENT, stall.rent)
            assertEquals("1, 2, 3", stall.coordinates)
            assertEquals(memberId, stall.members.single().playerId)
            assertEquals(setOf(GuildStallPermission.ACCESS_SHOP_CHESTS), stall.members.single().permissions)
        }
    }

    private fun row(loader: ClassLoader, memberId: UUID): Any {
        val memberClass = loader.loadClass("net.enthusia.market.api.guild.GuildStallMember")
        val member = memberClass.constructors.single().newInstance(memberId, setOf("ACCESS_SHOP_CHESTS"))
        val rowClass = loader.loadClass("net.enthusia.market.api.guild.GuildStallSnapshot")
        return rowClass.constructors.single().newInstance(
            "stall1",
            "stall1",
            "world",
            "OWNED",
            RENT,
            INTERVAL,
            Instant.EPOCH,
            null,
            "1, 2, 3",
            listOf(member),
        )
    }

    private fun provider(loader: ClassLoader, row: Any): Any {
        val api = loader.loadClass("net.enthusia.market.api.guild.GuildStallReadApi")
        return Proxy.newProxyInstance(loader, arrayOf(api)) { _, method, _ ->
            when (method.name) {
                "apiVersion" -> 1
                "guildStalls" -> CompletableFuture.completedFuture(listOf(row))
                else -> error("Unexpected API method ${method.name}")
            }
        }
    }

    private companion object {
        const val RENT = 100L
        const val INTERVAL = 86_400L
    }
}
