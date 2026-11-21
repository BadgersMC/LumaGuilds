package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.di.legacyMenuServices
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** REQ-NEXUS-DI-1/3: the shared menu bundle is explicit, lazy and never falls back to global Koin. */
class MenuServicesFoundationTest {
    private fun factory(provider: ((MenuFactory) -> MenuServices)? = null): MenuFactory {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig()
        return MenuFactory(
            mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), mockk<LangService>(),
            menuServicesProvider = provider,
        )
    }

    /** Builds the legacy bundle inside an isolated (non-global) Koin application, as `coreModule()` does. */
    private fun legacyServices(owner: MenuFactory, extra: org.koin.core.module.Module = module { }): MenuServices =
        koinApplication { modules(extra, module { single { legacyMenuServices(owner) } }) }.koin.get()

    @Test
    fun unboundMenuServicesFailClosedWithoutGlobalKoin() {
        assertNull(GlobalContext.getOrNull(), "test must not rely on a started global Koin")
        val error = assertFailsWith<IllegalStateException> { factory().menuServices }
        assertEquals("Menu services were not bound", error.message)
    }

    @Test
    fun providerRunsOnceOnFirstUseAndReceivesOwningFactory() {
        var calls = 0
        lateinit var owner: MenuFactory
        owner = factory { received ->
            calls++
            assertSame(owner, received)
            legacyServices(received)
        }
        assertEquals(0, calls, "constructing MenuFactory must not build menu services")
        val first = owner.menuServices
        assertSame(first, owner.menuServices)
        assertEquals(1, calls)
        assertSame(owner, first.menuFactory.value, "menu factory is bound directly, not resolved from Koin")
    }

    @Test
    fun legacyBundleResolvesNothingUntilReadAndKeepsOptionalLookupsOptional() {
        val lang = mockk<LangService>()
        val services = legacyServices(factory(), module { single { lang } })

        assertFalse(services.guildService.isInitialized(), "services stay lazy (claims-disabled/cycle safety)")
        assertFalse(services.getClaimFlags.isInitialized())
        assertSame(lang, services.langService.value)
        assertTrue(services.langService.isInitialized())
        assertNull(services.adminOverrideServiceOrNull.value, "absent optional service resolves to null")
        assertNull(services.guildCosmeticUnlockServiceOrNull.value)
        assertNull(services.guildServiceOrNull.value, "Chronicle snapshot fallback stays reachable")
        assertFailsWith<Exception> { services.guildService.value }
    }
}
