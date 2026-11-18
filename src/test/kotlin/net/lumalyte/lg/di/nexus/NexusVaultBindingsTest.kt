package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.VaultBackupService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.VaultHologramService
import net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.listeners.VaultInventoryListener
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class NexusVaultBindingsTest {
    @TempDir
    lateinit var tempDir: File

    /** Exact legacy vaultModule() definitions (Modules.kt), keyed by registered contract. */
    private val expectedNames = setOf(
        "guildVaultRepository",
        "vaultTransactionLogger",
        "vaultInventoryManager",
        "guildVaultService",
        "vaultAutoSaveService",
        "vaultBackupService",
        "vaultHologramService",
        "vaultInventoryListener",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    /** Storage double: vault repositories never touch it at construction time. */
    private fun stubStorage(): Storage<Database> {
        val storage = mockk<Storage<Database>>()
        every { storage.connection } returns mockk<Database>(relaxed = true)
        every { storage.dialect } returns SqlDialect.SQLITE
        return storage
    }

    /** Registers mocks for every non-vault dependency of the vault graph (claims absent). */
    private fun registerVaultDependencies(context: NexusContext, config: ConfigService) {
        val plugin = mockk<LumaGuilds>()
        every { plugin.name } returns "LumaGuilds"
        every { plugin.dataFolder } returns tempDir
        context.registerBean("plugin", LumaGuilds::class, plugin)
        context.registerBean("storage", Storage::class, stubStorage())
        context.registerBean("configService", ConfigService::class, config)
        context.registerBean("langService", LangService::class, mockk<LangService>(relaxed = true))
        context.registerBean("guildRepository", GuildRepository::class, mockk<GuildRepository>())
        context.registerBean("memberRepository", MemberRepository::class, mockk<MemberRepository>())
        context.registerBean("rankService", RankService::class, mockk<RankService>())
        context.registerBean("memberService", MemberService::class, mockk<MemberService>())
        context.registerBean(
            "platformDetectionService", PlatformDetectionService::class, mockk<PlatformDetectionService>(),
        )
        context.registerBean("progressionRepository", ProgressionRepository::class, mockk<ProgressionRepository>())
    }

    private fun configReturning(main: MainConfig): ConfigService {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns main
        return config
    }

    @Test
    fun `registers exactly the legacy vault module definitions`() = withContext { context ->
        val before = context.getBeanNames().toSet()
        context.registerVaultBindings()
        val added = context.getBeanNames().toSet() - before

        assertEquals(8, expectedNames.size)
        assertEquals(expectedNames, added)
        // Claims graph is optional and owned by NexusClaimsBindings; never registered here.
        assertFalse("getClaimAtPosition" in added)
    }

    @Test
    fun `registration is lazy and touches neither storage, configuration nor plugin`() = withContext { context ->
        val plugin = mockk<LumaGuilds>()
        val storage = mockk<Storage<*>>()
        val config = mockk<ConfigService>()
        context.registerBean("plugin", LumaGuilds::class, plugin)
        context.registerBean("storage", Storage::class, storage)
        context.registerBean("configService", ConfigService::class, config)

        context.registerVaultBindings()

        // Strict mocks: any DB access, config snapshot, task start or listener wiring would be recorded.
        confirmVerified(plugin, storage, config)
    }

    @Test
    fun `optional claim lookup yields null when the claims graph is absent`() = withContext { context ->
        context.registerVaultBindings()

        assertNull(context.optionalVaultClaimLookup())
    }

    @Test
    fun `optional claim lookup yields the claims graph instance when present`() = withContext { context ->
        val claimLookup = mockk<GetClaimAtPosition>()
        context.registerBean("getClaimAtPosition", GetClaimAtPosition::class, claimLookup)
        context.registerVaultBindings()

        assertSame(claimLookup, context.optionalVaultClaimLookup())
    }

    @Test
    fun `vault graph builds as singletons with claims disabled`() = withContext { context ->
        registerVaultDependencies(context, configReturning(MainConfig()))
        context.registerVaultBindings()

        assertSame(context.getBean<GuildVaultRepository>(), context.getBean<GuildVaultRepository>())
        assertSame(context.getBean<VaultTransactionLogger>(), context.getBean<VaultTransactionLogger>())
        assertSame(context.getBean<VaultInventoryManager>(), context.getBean<VaultInventoryManager>())
        assertSame(context.getBean<VaultHologramService>(), context.getBean<VaultHologramService>())
        // GetClaimAtPosition is not registered: must resolve to null, not throw.
        assertSame(context.getBean<GuildVaultService>(), context.getBean<GuildVaultService>())
        assertSame(context.getBean<VaultAutoSaveService>(), context.getBean<VaultAutoSaveService>())
        assertSame(context.getBean<VaultBackupService>(), context.getBean<VaultBackupService>())
        assertSame(context.getBean<VaultInventoryListener>(), context.getBean<VaultInventoryListener>())
    }

    @Test
    fun `each config-consuming bean takes one config snapshot like the legacy module`() = withContext { context ->
        val config = configReturning(MainConfig())
        registerVaultDependencies(context, config)
        context.registerVaultBindings()

        context.getBean<VaultInventoryManager>()
        context.getBean<VaultInventoryManager>()
        verify(exactly = 1) { config.loadConfig() }

        context.getBean<VaultAutoSaveService>()
        context.getBean<VaultAutoSaveService>()
        verify(exactly = 2) { config.loadConfig() }

        context.getBean<VaultInventoryListener>()
        context.getBean<VaultInventoryListener>()
        verify(exactly = 3) { config.loadConfig() }
    }
}
