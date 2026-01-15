package net.lumalyte.lg.di

import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.services.scheduling.SchedulerService
import net.lumalyte.lg.infrastructure.hytale.services.*
import org.koin.dsl.module

/**
 * Hytale-specific services module.
 *
 * This module provides Hytale implementations of all platform-specific services.
 * These services integrate with Hytale's native APIs for player management, combat,
 * chat, visualization, and world manipulation.
 *
 * Services included:
 * - PlayerService: Player operations (messages, positions, permissions)
 * - PlayerLocaleService: Player language/locale management
 * - ConfigService: Configuration loading and management (JSON-based)
 * - PlayerMetadataService: Player metadata storage
 * - SchedulerService: Task scheduling (sync/async)
 * - ToolItemService: Claim tool item management
 * - WorldManipulationService: World/block operations
 * - VisualisationService: Particle-based claim boundary visualization
 * - CombatService: PvP/combat validation and guild mode enforcement
 * - ChatService: Guild chat, announcements, and messaging
 */
fun hytaleServicesModule(dataDirectory: java.nio.file.Path) = module {
    // Core player service (no dependencies)
    single<PlayerService> { HytalePlayerService() }

    // Locale service (no dependencies)
    single<PlayerLocaleService> { HytalePlayerLocaleService() }

    // Config service (depends on data directory)
    single<ConfigService> { HytaleConfigService(dataDirectory) }

    // Player metadata service (depends on PlayerService, ConfigService)
    single<PlayerMetadataService> { HytalePlayerMetadataService(get(), get()) }

    // Scheduler service (no dependencies)
    single<SchedulerService> { HytaleSchedulerService() }

    // World manipulation service (no dependencies)
    single<WorldManipulationService> { HytaleWorldManipulationService() }

    // Visualization service (no dependencies)
    single<VisualisationService> { HytaleVisualisationService() }

    // Tool item service (depends on PlayerService)
    single<ToolItemService> { HytaleToolItemService(get()) }

    // Combat service (depends on GuildRepository, MemberRepository)
    single<CombatService> { HytaleCombatService(get(), get()) }

    // Chat service (depends on GuildRepository, MemberRepository, PlayerService)
    single<ChatService> { HytaleChatService(get(), get(), get()) }
}

/**
 * Complete Hytale application module that includes all repositories and services.
 *
 * This function combines the platform-agnostic repository modules with Hytale-specific
 * service implementations to create a complete dependency injection configuration.
 *
 * Usage in plugin:
 * ```kotlin
 * startKoin {
 *     modules(hytaleAppModule(storage, dataDirectory, claimsEnabled = true))
 * }
 * ```
 *
 * @param storage The database storage instance
 * @param dataDirectory The plugin data directory for config files
 * @param claimsEnabled Whether to enable the claims system (default: true)
 * @return List of Koin modules for Hytale
 */
fun hytaleAppModule(
    storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<*>,
    dataDirectory: java.nio.file.Path,
    claimsEnabled: Boolean = true
) = appModule(storage, claimsEnabled) + hytaleServicesModule(dataDirectory)
