package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.api.GuildShopXpApi
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import org.bukkit.plugin.ServicePriority
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class GuildShopXpRegistrationTest {
    private val repository = mockk<GuildShopXpRepository>()
    private val progression = mockk<ProgressionService>(relaxed = true)
    private val config = mockk<ConfigService>(relaxed = true)
    private val sale = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val buyer = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        MockBukkit.mock()
    }

    @AfterEach
    fun cleanup() {
        MockBukkit.unmock()
    }

    @Test
    fun discoveryAndCleanup() {
        val plugin = MockBukkit.createMockPlugin()
        registerGuildShopXpApi(plugin, repository, config, progression)
        val manager = plugin.server.servicesManager
        val registration = assertNotNull(manager.getRegistration(GuildShopXpApi::class.java))
        assertEquals(plugin, registration.plugin)
        assertEquals(2, registration.provider.apiVersion())
        manager.unregisterAll(plugin)
        assertNull(manager.load(GuildShopXpApi::class.java))
    }

    @Test
    fun marketLifecycle() {
        val plugin = MockBukkit.createMockPlugin()
        registerGuildShopXpApi(plugin, repository, config, progression)
        val failedSale = UUID.randomUUID()
        prepareRepository(failedSale)
        withMarketGateway { type, gateway ->
            assertMarketCalls(type, gateway, failedSale)
        }
        verify(exactly = 1) {
            repository.prepare(
                match { it.id == sale && it.guild == guild && it.buyer == buyer },
                any(),
            )
        }
        verify(exactly = 1) { repository.abort(failedSale) }
        verify(exactly = 1) { progression.onCommittedExperience(guild, 2) }
        verify(exactly = 1) { progression.onCommittedExperience(guild, null) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["OWN_GUILD", "DISABLED", "COOLDOWN", "CAPPED", "STALE_RUN", "ABORTED", "CHAPTER_FROZEN"])
    fun marketRetainsNonAwardingOutcomes(status: String) {
        withMarketGateway { type, gateway ->
            registerGuildShopXpApi(MockBukkit.createMockPlugin(), repository, config, progression)
            every { repository.prepare(any(), any()) } returns status
            every { repository.complete(sale) } returns GuildShopXpCompletion(status, guild)
            every { repository.abort(sale) } returns GuildShopXpCompletion(status, guild)
            val prepare = prepareMethod(type)

            assertEquals(status, prepare.invoke(gateway, sale, guild, buyer, OCCURRED_AT))
            assertEquals(status, type.getMethod("complete", UUID::class.java).invoke(gateway, sale))
            assertEquals(status, type.getMethod("abort", UUID::class.java).invoke(gateway, sale))

            verify(exactly = 1) {
                repository.prepare(
                    match {
                        it.id == sale && it.guild == guild && it.buyer == buyer &&
                            it.occurredAt == OCCURRED_AT
                    },
                    any(),
                )
            }
            verify(exactly = 1) { repository.complete(sale) }
            verify(exactly = 1) { repository.abort(sale) }
            verify(exactly = 0) { progression.onCommittedExperience(any(), any()) }
        }
    }

    @Test
    fun marketFailureRemainsRetryable() {
        withMarketGateway { type, gateway ->
            registerGuildShopXpApi(MockBukkit.createMockPlugin(), repository, config, progression)
            every { repository.complete(sale) } throws IllegalStateException("SQL unavailable") andThen
                GuildShopXpCompletion(AWARDED, guild, 2, awardedNow = true)
            val complete = type.getMethod("complete", UUID::class.java)

            assertFailsWith<InvocationTargetException> { complete.invoke(gateway, sale) }
            verify(exactly = 0) { progression.onCommittedExperience(any(), any()) }
            assertEquals(AWARDED, complete.invoke(gateway, sale))

            verify(exactly = 2) { repository.complete(sale) }
            verify(exactly = 1) { progression.onCommittedExperience(guild, 2) }
            verify(exactly = 0) { repository.abort(any()) }
        }
    }

    @Test
    fun marketResolvesReplacementAfterUnregistration() {
        withMarketGateway { type, gateway ->
            val plugin = MockBukkit.createMockPlugin()
            val manager = plugin.server.servicesManager
            val complete = type.getMethod("complete", UUID::class.java)
            assertFailsWith<InvocationTargetException> { complete.invoke(gateway, sale) }

            registerGuildShopXpApi(plugin, repository, config, progression)
            every { repository.complete(sale) } returns GuildShopXpCompletion(AWARDED, guild, 2, awardedNow = true)
            assertEquals(AWARDED, complete.invoke(gateway, sale))

            manager.unregisterAll(plugin)
            assertFailsWith<InvocationTargetException> { complete.invoke(gateway, sale) }

            val replacement = mockk<GuildShopXpRepository>()
            val replacementProgression = mockk<ProgressionService>(relaxed = true)
            every { replacement.complete(sale) } returns GuildShopXpCompletion("CHAPTER_FROZEN", guild)
            registerGuildShopXpApi(MockBukkit.createMockPlugin("GuildsReplacement"), replacement, config, replacementProgression)
            assertEquals("CHAPTER_FROZEN", complete.invoke(gateway, sale))

            verify(exactly = 1) { repository.complete(sale) }
            verify(exactly = 1) { replacement.complete(sale) }
            verify(exactly = 1) { progression.onCommittedExperience(guild, 2) }
            verify(exactly = 0) { replacementProgression.onCommittedExperience(any(), any()) }
        }
    }

    @Test
    fun marketRejectsIncompatibleProvider() {
        withMarketGateway { type, gateway ->
            val plugin = MockBukkit.createMockPlugin()
            val incompatible = mockk<GuildShopXpApi>()
            every { incompatible.apiVersion() } returns 1
            plugin.server.servicesManager.register(GuildShopXpApi::class.java, incompatible, plugin, ServicePriority.Normal)

            assertFailsWith<InvocationTargetException> {
                type.getMethod("complete", UUID::class.java).invoke(gateway, sale)
            }
            verify(exactly = 1) { incompatible.apiVersion() }
            verify(exactly = 0) { incompatible.complete(any()) }
        }
    }

    private fun withMarketGateway(assertions: (Class<*>, Any) -> Unit) {
        val marketJar = System.getenv("MARKET_RUNTIME_JAR")?.takeIf(String::isNotBlank)?.let(::File)
        assumeTrue(marketJar != null, "Set MARKET_RUNTIME_JAR for actual Market artifact contract")
        val artifact = checkNotNull(marketJar)
        require(artifact.isFile) { "Configured MARKET_RUNTIME_JAR does not exist: $artifact" }
        URLClassLoader(arrayOf(artifact.toURI().toURL()), javaClass.classLoader).use { loader ->
            val type = loader.loadClass("net.badgersmc.em.infrastructure.guild.BukkitGuildShopXpGateway")
            assertSame(loader, type.classLoader, "Gateway must come from the selected Market artifact")
            assertions(type, type.getConstructor().newInstance())
        }
    }

    private fun prepareRepository(failedSale: UUID) {
        every { repository.prepare(any(), any()) } returns "PREPARED"
        every { repository.complete(sale) } returnsMany
            listOf(
                GuildShopXpCompletion(AWARDED, guild, 2, awardedNow = true),
                GuildShopXpCompletion(AWARDED, guild, 2, awardedNow = false),
            )
        every { repository.abort(failedSale) } returns GuildShopXpCompletion("ABORTED", guild)
    }

    private fun assertMarketCalls(type: Class<*>, gateway: Any, failedSale: UUID) {
        val prepare = prepareMethod(type)
        assertEquals("PREPARED", prepare.invoke(gateway, sale, guild, buyer, 1L))
        val complete = type.getMethod("complete", UUID::class.java)
        assertEquals(AWARDED, complete.invoke(gateway, sale))
        assertEquals(AWARDED, complete.invoke(gateway, sale))
        assertEquals("ABORTED", type.getMethod("abort", UUID::class.java).invoke(gateway, failedSale))
    }

    private fun prepareMethod(type: Class<*>) =
        type.getMethod("prepare", UUID::class.java, UUID::class.java, UUID::class.java, java.lang.Long.TYPE)

    private companion object {
        const val AWARDED = "AWARDED:5"
        const val OCCURRED_AT = 1_792_519_200_000L
    }
}
