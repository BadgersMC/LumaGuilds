package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.api.GuildInviteBlocks
import net.lumalyte.lg.api.GuildInviteBlocksV2
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import java.util.concurrent.CompletionStage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Companion discovery must preserve v1 while withholding backend-local claims from shared MariaDB storage. */
internal class GuildInviteBlocksRegistrationTest {
    private val legacy = mockk<InviteBlockService>()
    private val durable = mockk<DurableInviteBlockService>()

    @BeforeEach
    fun setup() {
        MockBukkit.mock()
    }

    @AfterEach
    fun cleanup() {
        MockBukkit.unmock()
    }

    @Test
    fun sqliteRegistersSeparateVersionedProviderWithJdkOnlyVoidContract() {
        val plugin = MockBukkit.createMockPlugin()
        registerGuildInviteBlocks(plugin, SqlDialect.SQLITE, legacy, durable)
        val services = plugin.server.servicesManager
        assertEquals(1, assertNotNull(services.load(GuildInviteBlocks::class.java)).apiVersion())
        val registration = assertNotNull(services.getRegistration(GuildInviteBlocksV2::class.java))
        assertSame(plugin, registration.plugin)
        assertEquals(2, registration.provider.apiVersion())
        assertEquals("BACKEND_LOCAL_SQLITE", registration.provider.storageScopeKind())
        val method = GuildInviteBlocksV2::class.java.getMethod(
            "setBlockedDurable", UUID::class.java, UUID::class.java, java.lang.Boolean.TYPE,
        )
        assertEquals(CompletionStage::class.java, method.returnType)
        assertEquals("java.util.concurrent.CompletionStage<java.lang.Void>", method.genericReturnType.typeName)
        services.unregisterAll(plugin)
        assertNull(services.load(GuildInviteBlocksV2::class.java))
    }

    @Test
    fun mariaDbRetainsV1WithoutAdvertisingV2ScopeOrCoherence() {
        val plugin = MockBukkit.createMockPlugin()
        registerGuildInviteBlocks(plugin, SqlDialect.MARIADB, legacy, durable)
        assertNotNull(plugin.server.servicesManager.load(GuildInviteBlocks::class.java))
        assertNull(plugin.server.servicesManager.load(GuildInviteBlocksV2::class.java))
    }
}
