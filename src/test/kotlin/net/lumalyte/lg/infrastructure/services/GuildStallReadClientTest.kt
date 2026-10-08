package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.StallReadResult
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Optional integration must preserve unavailable versus empty and stale UI safety. */
internal class GuildStallReadClientTest {
    private val guild = UUID.randomUUID()
    private val viewer = UUID.randomUUID()

    /** Missing or incompatible companions are unavailable, not no-stall results. */
    @Test
    fun rejectsMissingProvider() {
        assertEquals(StallReadResult.Unavailable, GuildStallReadClient { null }.read(guild, viewer).join())
        assertEquals(StallReadResult.Unavailable, GuildStallReadClient { Provider(2) }.read(guild, viewer).join())
    }

    /** A successful empty response is a distinct state. */
    @Test
    fun preservesEmptyResponse() {
        assertEquals(
            StallReadResult.Available(emptyList()),
            GuildStallReadClient { Provider(1) }.read(guild, viewer).join(),
        )
    }

    /** Exceptional or malformed results cannot claim usable data. */
    @Test
    fun rejectsFailedResponse() {
        val provider = Provider(1)
        provider.response.completeExceptionally(IllegalStateException("offline"))
        assertEquals(StallReadResult.Unavailable, GuildStallReadClient { provider }.read(guild, viewer).join())
    }

    /** Nonstandard stages that cannot expose a CompletableFuture fail closed. */
    @Test
    fun rejectsUnconvertibleStage() {
        val provider = UnconvertibleProvider()
        assertEquals(StallReadResult.Unavailable, GuildStallReadClient { provider }.read(guild, viewer).join())
    }

    /** Navigation, disconnect and membership loss all discard late results. */
    @Test
    fun staleResultIsDiscarded() {
        assertTrue(GuildStallLoadGuard.accepts(1L, 1L, true, true))
        assertFalse(GuildStallLoadGuard.accepts(1L, 2L, true, true))
        assertFalse(GuildStallLoadGuard.accepts(1L, 1L, false, true))
        assertFalse(GuildStallLoadGuard.accepts(1L, 1L, true, false))
    }

    /** Reflective shape fixture; actual API compatibility is verified separately. */
    class Provider(private val version: Int) {
        /** Response controlled by failure-path tests. */
        val response = CompletableFuture<List<Any>>()

        /** Supported read contract version. */
        fun apiVersion(): Int = version

        /** Test-only service method preserving the public companion call signature. */
        fun guildStalls(guild: UUID, viewer: UUID): CompletableFuture<List<Any>> {
            require(guild != viewer) { "Fixture expects distinct guild and viewer identities" }
            return if (response.isDone) response else CompletableFuture.completedFuture(emptyList())
        }
    }

    /** Fixture whose stage deliberately rejects CompletableFuture conversion. */
    class UnconvertibleProvider(private val version: Int = 1) {
        /** Supported read contract version. */
        fun apiVersion(): Int = version

        /** Returns a stage implementation that throws during conversion. */
        fun guildStalls(guild: UUID, viewer: UUID): CompletableFuture<List<Any>> {
            require(guild != viewer) { "Fixture expects distinct guild and viewer identities" }
            return object : CompletableFuture<List<Any>>() {
                override fun toCompletableFuture(): CompletableFuture<List<Any>> =
                    throw UnsupportedOperationException("conversion unavailable")
            }
        }
    }
}
