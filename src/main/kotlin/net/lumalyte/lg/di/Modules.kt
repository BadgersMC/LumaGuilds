package net.lumalyte.lg.di

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.GuildBannerRepository
import net.lumalyte.lg.application.persistence.AuditRepository
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository

import net.lumalyte.lg.infrastructure.persistence.guilds.GuildRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RankRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.MemberRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RelationRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PartyRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildInvitationRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PartyRequestRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PlayerPartyPreferenceRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.ChatSettingsRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.BankRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildBannerRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.AuditRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.KillRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimFlagRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimPermissionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.PlayerAccessRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.partitions.PartitionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.players.PlayerStateRepositoryMemory
import net.lumalyte.lg.infrastructure.persistence.storage.Storage

import co.aikar.idb.Database
import org.koin.dsl.module

/**
 * Core platform-agnostic module - Database storage and repositories
 * This module contains ONLY platform-agnostic dependencies
 */
fun coreModule(storage: Storage<*>) = module {
    // Storage
    @Suppress("UNCHECKED_CAST")
    single<Storage<Database>> { storage as Storage<Database> }
}

/**
 * Guild system module - Core guild repositories
 */
fun guildsModule() = module {
    // Repositories (100% platform-agnostic)
    single<GuildRepository> { GuildRepositorySQLite(get()) }
    single<RankRepository> { RankRepositorySQLite(get()) }
    single<MemberRepository> { MemberRepositorySQLite(get()) }
    single<RelationRepository> { RelationRepositorySQLite(get()) }
    single<GuildInvitationRepository> { GuildInvitationRepositorySQLite(get()) }
    single<GuildBannerRepository> { GuildBannerRepositorySQLite(get()) }
    single<AuditRepository> { AuditRepositorySQLite(get()) }
}

/**
 * Social module - Party and chat repositories
 */
fun socialModule() = module {
    // Repositories (100% platform-agnostic)
    single<PartyRepository> { PartyRepositorySQLite(get()) }
    single<PlayerPartyPreferenceRepository> { PlayerPartyPreferenceRepositorySQLite(get()) }
    single<PartyRequestRepository> { PartyRequestRepositorySQLite(get()) }
    single<ChatSettingsRepository> { ChatSettingsRepositorySQLite(get()) }
}

/**
 * Progression module - Combat, kills, wars repositories
 */
fun progressionModule() = module {
    // Repositories (100% platform-agnostic)
    single<KillRepository> { KillRepositorySQLite(get()) }
    single<ProgressionRepository> { ProgressionRepositorySQLite(get()) }
}

/**
 * Economy module - Bank repositories
 */
fun economyModule() = module {
    // Repositories (100% platform-agnostic)
    single<BankRepository> { BankRepositorySQLite(get()) }
}

/**
 * Claims module - Claim system repositories
 */
fun claimsModule() = module {
    // Repositories (100% platform-agnostic)
    single<ClaimFlagRepository> { ClaimFlagRepositorySQLite(get()) }
    single<ClaimPermissionRepository> { ClaimPermissionRepositorySQLite(get()) }
    single<ClaimRepository> { ClaimRepositorySQLite(get()) }
    single<PartitionRepository> { PartitionRepositorySQLite(get()) }
    single<PlayerAccessRepository> { PlayerAccessRepositorySQLite(get()) }
    single<PlayerStateRepository> { PlayerStateRepositoryMemory() }
}

/**
 * Main application module that combines all repository modules
 * This is the entry point for Koin dependency injection (platform-agnostic part)
 *
 * Platform-specific services should be added in platform modules (e.g., Hytale module)
 */
fun appModule(storage: Storage<*>, claimsEnabled: Boolean = true) =
    listOf(
        coreModule(storage),
        guildsModule(),
        socialModule(),
        progressionModule(),
        economyModule()
    ) + if (claimsEnabled) {
        listOf(claimsModule())
    } else {
        emptyList()
    }
