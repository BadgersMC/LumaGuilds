package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.UUID
import net.lumalyte.lg.api.GuildShopXpApi
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun providerIsDiscoverableAndRemovedWithOwningPlugin() {
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
    fun installedMarketGatewayDiscoversRealProviderAndRoutesLifecycleCalls() {
        val marketJar = System.getenv("MARKET_RUNTIME_JAR")
        assumeTrue(marketJar != null, "Set MARKET_RUNTIME_JAR for actual Market artifact contract")
        val plugin = MockBukkit.createMockPlugin()
        registerGuildShopXpApi(plugin, repository, config, progression)
        every { repository.prepare(any(), any()) } returns "PREPARED"
        every { repository.complete(sale) } returnsMany listOf(
            GuildShopXpCompletion("AWARDED:5", guild, 2, awardedNow = true),
            GuildShopXpCompletion("AWARDED:5", guild, 2, awardedNow = false),
        )
        val failedSale = UUID.randomUUID()
        every { repository.abort(failedSale) } returns GuildShopXpCompletion("ABORTED", guild)
        URLClassLoader(arrayOf(Path.of(checkNotNull(marketJar)).toUri().toURL()), javaClass.classLoader).use { loader ->
            val type = loader.loadClass("net.badgersmc.em.infrastructure.guild.BukkitGuildShopXpGateway")
            val gateway = type.getConstructor().newInstance()
            val prepare = type.getMethod(
                "prepare", UUID::class.java, UUID::class.java, UUID::class.java, java.lang.Long.TYPE,
            )
            assertEquals("PREPARED", prepare.invoke(gateway, sale, guild, buyer, 1L))
            val complete = type.getMethod("complete", UUID::class.java)
            assertEquals("AWARDED:5", complete.invoke(gateway, sale))
            assertEquals("AWARDED:5", complete.invoke(gateway, sale))
            assertEquals("ABORTED", type.getMethod("abort", UUID::class.java).invoke(gateway, failedSale))
        }
        verify(exactly = 1) { repository.prepare(match { it.id == sale && it.guild == guild && it.buyer == buyer }, any()) }
        verify(exactly = 1) { repository.abort(failedSale) }
        verify(exactly = 1) { progression.onCommittedExperience(guild, 2) }
    }
}
