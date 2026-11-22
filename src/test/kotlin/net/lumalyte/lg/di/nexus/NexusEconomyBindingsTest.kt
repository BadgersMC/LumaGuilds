package net.lumalyte.lg.di.nexus

import io.mockk.confirmVerified
import io.mockk.mockk
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.application.persistence.WarBannerRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PhysicalGoldPort
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.BukkitPhysicalGoldAdapter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NexusEconomyBindingsTest {

    private val expectedNames = setOf(
        // Banner purchases
        "bannerPurchaseRepository", "bannerPurchaseService",
        // Repositories
        "bankRepository", "bankSettingsRepository", "guildGoldRepositorySQL",
        // Guild gold and physical gold
        "guildGoldService", "bukkitPhysicalGoldAdapter", "warBannerRawGold",
        // War banners
        "warBannerRepository", "warBannerService", "warBannerServiceBukkit", "warBannerListener",
        // Costs
        "vaultGuildCreationPaymentAdapter", "guildCostService", "guildHomeActivationService",
        // Bank
        "bankService", "bankAutomationService", "physicalCurrencyService",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    @Test
    fun `registers the economy module by contract name`() = withContext { context ->
        val before = context.getBeanNames().toSet()
        context.registerEconomyBindings()
        val added = context.getBeanNames().toSet() - before

        // Koin had 19 definitions; the GuildGoldRepository alias collapses into the concrete bean.
        assertEquals(18, expectedNames.size)
        assertEquals(expectedNames, added)
    }

    @Test
    fun `collapses the gold repository alias and keeps the raw gold adapter distinctly named`() =
        withContext { context ->
            context.registerEconomyBindings()
            val names = context.getBeanNames()

            assertFalse("guildGoldRepository" in names)
            assertTrue("bukkitPhysicalGoldAdapter" in names)
            assertTrue("warBannerRawGold" in names)
            // Deferred tranches must not be shadowed here.
            assertFalse("warService" in names)
            assertFalse("vaultInventoryManager" in names)
        }

    @Test
    fun `war banner raw gold holder is never indexed as a physical gold adapter or port`() {
        // Unqualified PhysicalGoldPort / BukkitPhysicalGoldAdapter lookups must stay unambiguous
        // and can never return the RAW_GOLD adapter in place of the configured bank currency.
        assertTrue(WarBannerRawGold::class.java.interfaces.isEmpty())
        assertFalse(PhysicalGoldPort::class.java.isAssignableFrom(WarBannerRawGold::class.java))
        assertFalse(BukkitPhysicalGoldAdapter::class.java.isAssignableFrom(WarBannerRawGold::class.java))
    }

    @Test
    fun `economy names do not collide with guild core, social or progression tranches`() =
        withContext { context ->
            context.registerGuildCoreBindings(claimsEnabled = false)
            context.registerSocialBindings()
            context.registerProgressionBindings()
            val before = context.getBeanNames().toSet()

            context.registerEconomyBindings()
            val after = context.getBeanNames().toSet()

            assertTrue(before.intersect(expectedNames).isEmpty())
            assertEquals(before.size + expectedNames.size, after.size)
        }

    @Test
    fun `registration is lazy and touches neither storage nor configuration`() = withContext { context ->
        val storage = mockk<Storage<*>>()
        val config = mockk<ConfigService>()
        context.registerBean("storage", Storage::class, storage)
        context.registerBean("configService", ConfigService::class, config)

        context.registerEconomyBindings()

        // Strict mocks: any schema init, journal DDL or config snapshot would be recorded.
        confirmVerified(storage, config)
    }

    @Test
    fun `war banner repository is a lazy singleton that issues no SQL on construction`() =
        withContext { context ->
            val storage = mockk<Storage<*>>()
            context.registerBean("storage", Storage::class, storage)
            context.registerEconomyBindings()

            val repository = context.getBean<WarBannerRepository>()
            assertSame(repository, context.getBean<WarBannerRepository>())
            confirmVerified(storage)
        }
}
