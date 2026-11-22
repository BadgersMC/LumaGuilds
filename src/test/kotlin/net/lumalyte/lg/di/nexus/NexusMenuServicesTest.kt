package net.lumalyte.lg.di.nexus

import io.mockk.mockk
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** REQ-NEXUS-DI-3: the inert Nexus menu bundle is lazy and keeps optional lookups optional. */
class NexusMenuServicesTest {
    @Test
    fun `partial context resolves nothing eagerly and absent optional beans are null`() {
        val context = NexusContext.create()
        try {
            val factory = mockk<MenuFactory>()
            val services = context.nexusMenuServices(factory)
            assertFalse(services.guildService.isInitialized())
            assertFalse(services.getClaimFlags.isInitialized(), "claim beans are never requested at bundle creation")
            assertSame(factory, services.menuFactory.value)
            assertNull(services.adminOverrideServiceOrNull.value)
            assertNull(services.guildCosmeticUnlockServiceOrNull.value, "holiday themes stay locked without the ledger")
            assertNull(services.guildServiceOrNull.value, "Chronicle keeps its snapshot theme fallback")
        } finally {
            context.close()
        }
    }

    @Test
    fun `registered optional bean is returned by its guild binding name`() {
        val context = NexusContext.create()
        try {
            val admin = mockk<AdminOverrideService>()
            context.registerBean("adminOverrideService", AdminOverrideService::class, admin)
            assertSame(admin, context.nexusMenuServices(mockk()).adminOverrideServiceOrNull.value)
        } finally {
            context.close()
        }
    }
}
