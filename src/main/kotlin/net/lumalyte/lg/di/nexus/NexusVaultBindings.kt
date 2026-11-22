package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
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
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildVaultRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.GuildVaultServiceBukkit
import net.lumalyte.lg.infrastructure.services.VaultBackupServiceBukkit
import net.lumalyte.lg.infrastructure.services.VaultHologramService
import net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.listeners.VaultInventoryListener
import kotlin.reflect.KClass

/** Bean name NexusClaimsBindings gives GetClaimAtPosition; present only when claims are enabled. */
private const val CLAIM_LOOKUP_BEAN = "getClaimAtPosition"

/**
 * Nexus replacement for the legacy Koin `vaultModule()` (Modules.kt).
 *
 * All 8 legacy definitions are mapped 1:1, under the same contract types, as lazy
 * singletons. Nothing is constructed at registration time, so:
 *  - no vault SQL runs before storage migrations (vault_slots / vault_gold /
 *    vault_transaction_log are still created by the existing migration chain, not here);
 *  - VaultAutoSaveService, VaultBackupServiceBukkit and VaultHologramService schedule no
 *    Bukkit tasks; their start()/stop() remain owned by the runtime lifecycle;
 *  - VaultInventoryListener is constructed only, never registered with the PluginManager.
 *
 * Config snapshots: exactly like the legacy module, VaultInventoryManager, VaultAutoSaveService
 * and VaultInventoryListener each call ConfigService.loadConfig() once, at their own first
 * construction, and keep that snapshot (`vault` section / `transactionLogRetentionDays`).
 * The shared "mainConfig" bean is deliberately not used, to keep per-bean snapshot timing.
 *
 * Plugin instance: every JavaPlugin/Plugin parameter resolves the single "plugin"
 * LumaGuilds bean, the same instance the legacy `get<LumaGuilds>()` returned.
 *
 * Optional claims: GuildVaultServiceBukkit accepts a nullable GetClaimAtPosition. The claims
 * graph is only registered when claims are enabled, so it is looked up with containsBean and
 * resolves to null (never an exception) when absent, mirroring the legacy `getOrNull()`. The
 * lookup happens at GuildVaultService's first construction, not at registration, so the
 * relative registration order of claims and vault bindings does not matter.
 *
 * Listener collaborators are explicit and lazy; Bukkit registration remains runtime-owned.
 */
internal fun NexusContext.registerVaultBindings() {
    // --- Repositories ---
    vaultSingleton<GuildVaultRepository> { GuildVaultRepositorySQLite(vaultStorage()) }
    vaultSingleton<VaultTransactionLogger> { VaultTransactionLogger(vaultStorage()) }

    // --- Services ---
    vaultSingleton<VaultInventoryManager> {
        val config = getBean<ConfigService>().loadConfig()
        VaultInventoryManager(getBean<GuildVaultRepository>(), getBean<VaultTransactionLogger>(), config.vault)
    }
    vaultSingleton<GuildVaultService> {
        GuildVaultServiceBukkit(
            getBean<LumaGuilds>(),
            getBean<GuildRepository>(),
            getBean<GuildVaultRepository>(),
            getBean<MemberRepository>(),
            getBean<ConfigService>(),
            getBean<VaultInventoryManager>(),
            getBean<VaultHologramService>(),
            getBean<RankService>(),
            optionalVaultClaimLookup(),
            getBean<LangService>(),
        )
    }
    vaultSingleton<VaultAutoSaveService> {
        val config = getBean<ConfigService>().loadConfig()
        VaultAutoSaveService(
            getBean<LumaGuilds>(),
            getBean<VaultInventoryManager>(),
            getBean<VaultTransactionLogger>(),
            config.vault.transactionLogRetentionDays,
        )
    }
    vaultSingleton<VaultBackupService> {
        VaultBackupServiceBukkit(
            getBean<LumaGuilds>(),
            getBean<GuildVaultRepository>(),
            getBean<VaultInventoryManager>(),
        )
    }
    vaultSingleton<VaultHologramService> {
        VaultHologramService(getBean<LumaGuilds>(), getBean<GuildRepository>(), getBean<LangService>())
    }

    // --- Listeners (constructed lazily; registration with Bukkit stays with the runtime) ---
    vaultSingleton<VaultInventoryListener> {
        val config = getBean<ConfigService>().loadConfig()
        VaultInventoryListener(
            getBean<LumaGuilds>(),
            getBean<VaultInventoryManager>(),
            getBean<VaultTransactionLogger>(),
            config.vault,
            getBean<GuildRepository>(),
            getBean<MemberService>(),
            getBean<PlatformDetectionService>(),
            getBean<ProgressionRepository>(),
            bankService = lazy { getBean<net.lumalyte.lg.application.services.BankService>() },
            lang = lazy { getBean<net.badgersmc.nexus.i18n.LangService>() },
            menuServices = { getBean<net.lumalyte.lg.interaction.menus.MenuFactory>().menuServices },
        )
    }
}

/** Claims are optional: null when the claims graph was not registered (claims disabled). */
internal fun NexusContext.optionalVaultClaimLookup(): GetClaimAtPosition? =
    if (containsBean(CLAIM_LOOKUP_BEAN)) getBean(CLAIM_LOOKUP_BEAN) as GetClaimAtPosition else null

@Suppress("UNCHECKED_CAST")
private fun NexusContext.vaultStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.vaultSingleton(noinline factory: () -> T) {
    registerBean(vaultBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun vaultBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
