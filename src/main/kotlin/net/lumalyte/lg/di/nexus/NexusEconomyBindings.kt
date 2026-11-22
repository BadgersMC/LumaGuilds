package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.persistence.BannerPurchaseRepository
import net.lumalyte.lg.application.persistence.GuildHomeAccessRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.RewardPurchaseRepository
import net.lumalyte.lg.application.persistence.WarBannerRepository
import net.lumalyte.lg.application.services.BankAutomationService
import net.lumalyte.lg.application.services.BankDepositXpDeliveryService
import net.lumalyte.lg.application.services.BankDepositXpQuotes
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BannerPurchaseService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildCostService
import net.lumalyte.lg.application.services.GuildGoldAuthorizationPort
import net.lumalyte.lg.application.services.GuildGoldService
import net.lumalyte.lg.application.services.GuildHomeActivationService
import net.lumalyte.lg.application.services.GuildRewardPurchaseAccess
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.WarBannerService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.RewardPurchaseRejection
import net.lumalyte.lg.domain.rewards.RewardPurchaseRequest
import net.lumalyte.lg.infrastructure.listeners.WarBannerListener
import net.lumalyte.lg.infrastructure.persistence.guilds.BankRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.BankSettingsRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.BannerPurchaseRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardPurchaseRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.WarBannerRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit
import net.lumalyte.lg.infrastructure.services.BukkitPhysicalGoldAdapter
import net.lumalyte.lg.infrastructure.services.ConfiguredGuildGoldSettings
import net.lumalyte.lg.infrastructure.services.GuildCreationVaultJournal
import net.lumalyte.lg.infrastructure.services.PhysicalCurrencyServiceBukkit
import net.lumalyte.lg.infrastructure.services.PhysicalGoldJournal
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.TeleportationService
import net.lumalyte.lg.infrastructure.services.VaultGuildCreationPaymentAdapter
import net.lumalyte.lg.infrastructure.services.VaultPersonalEconomyAdapter
import net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.infrastructure.services.currentVaultEconomy
import net.lumalyte.lg.application.services.PersonalEconomyPort
import org.bukkit.Bukkit
import org.bukkit.Material
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Distinct Nexus bean for the legacy Koin `single(named("WarBannerRawGold"))` adapter.
 *
 * The raw-gold adapter is a second BukkitPhysicalGoldAdapter (RAW_GOLD / RAW_GOLD_BLOCK x9).
 * Registering it under `BukkitPhysicalGoldAdapter::class` would make the unqualified
 * `getBean<BukkitPhysicalGoldAdapter>()` used by GuildGoldService ambiguous, and Nexus
 * interface indexing would expose it as a second `PhysicalGoldPort`. A dedicated holder
 * type with no interfaces keeps it a separately named, separately typed singleton that
 * only WarBannerService consumes, so the currency adapters can never be swapped.
 */
internal class WarBannerRawGold(val adapter: BukkitPhysicalGoldAdapter)

/**
 * Lazy Nexus replacements for the legacy Koin `economyModule` (Modules.kt).
 *
 * Every definition is a SINGLETON whose factory runs only on first lookup: no SQL schema
 * init (GuildGoldRepositorySQL, PhysicalGoldJournal, BankRepositorySQLite preload, ...),
 * no config snapshot, no Bukkit API and no scheduler task (WarBannerServiceBukkit) runs at
 * registration time.
 *
 * Financial wiring is a 1:1 transcription of the Koin graph:
 *  - GuildGoldService: same authorization rules (DEPOSIT_TO_BANK / WITHDRAW_FROM_BANK rank
 *    permission; any membership for physical deposits), same additional bank-freeze check,
 *    same UTC-midnight withdrawal period, same reward-purchase gate and deposit XP quotes.
 *  - Vault Economy is fetched from Bukkit's ServicesManager on every call (never cached),
 *    exactly as before, so a late-registering economy plugin is still observed.
 *  - Each physical adapter gets its own PhysicalGoldJournal instance, as in Koin.
 *  - GuildCostService / GuildHomeActivationService / WarBannerService config providers read
 *    ConfigService per call where Koin did; one-shot snapshots stay one-shot.
 *
 * Concrete/interface alias: Koin registered GuildGoldRepositorySQL concretely plus a
 * `single<GuildGoldRepository>` alias delegating to it. Nexus indexes the concrete class
 * under its direct interface, so ONE registration serves both lookups with one instance
 * (one guild lock map; a second instance would split the per-guild ReentrantLocks and
 * break debit/credit atomicity).
 *
 * Constructor cycles preserved as lazy lookups:
 *  - GuildHomeActivationService.maySetHome / paidOutpostForNewHome -> GuildService
 *    (GuildServiceBukkit takes GuildHomeActivationService in its constructor).
 *  - GuildGoldService.rewardPurchases -> RewardPurchaseRepositorySQL per purchase
 *    (RewardPurchaseRepositorySQL depends on GuildGoldRepositorySQL).
 *  - GuildGoldService.depositXpQuote -> BankDepositXpQuotes per deposit.
 *  - BankDepositXpDeliveryService (progression tranche) already resolves ProgressionService
 *    lazily, so BankServiceBukkit may take it eagerly as in Koin.
 *
 * Unresolvable until later tranches register them (lookup fails at first use, not at
 * registration): VaultInventoryManager (vault module) for BankService, and WarService
 * (war tranche) for WarBannerService.
 */
internal fun NexusContext.registerEconomyBindings() {
    // --- Banner purchases ---
    economySingleton<BannerPurchaseRepository> { BannerPurchaseRepositorySQL(economyStorage()) }
    economySingleton<BannerPurchaseService> {
        BannerPurchaseService(getBean<BannerPurchaseRepository>(), getBean<GuildGoldService>())
    }

    // --- Repositories ---
    economySingleton<BankRepository> { BankRepositorySQLite(economyStorage()) }
    economySingleton<BankSettingsRepository> { BankSettingsRepositorySQLite(economyStorage()) }
    // Also serves getBean<GuildGoldRepository>() via Nexus interface indexing (single instance).
    economySingleton<GuildGoldRepositorySQL> { GuildGoldRepositorySQL(economyStorage()) }

    // --- Guild gold ---
    economySingleton<GuildGoldService> {
        val config = getBean<ConfigService>()
        val progression = getBean<ProgressionRepository>()
        val rewards = getBean<ProgressionConfigService>()
        val members = getBean<MemberRepository>()
        val ranks = getBean<RankRepository>()
        val guilds = getBean<GuildRepository>()
        GuildGoldService(
            repository = getBean<GuildGoldRepositorySQL>(),
            settingsProvider = ConfiguredGuildGoldSettings(
                config, progression, rewards, getBean<net.lumalyte.lg.application.services.GuildRewardService>(),
            ),
            authorization = object : GuildGoldAuthorizationPort {
                private fun allowed(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
                    val member = members.getByPlayerAndGuild(playerId, guildId) ?: return false
                    return ranks.getById(member.rankId)?.permissions?.contains(permission) == true
                }
                override fun canDeposit(playerId: UUID, guildId: UUID) =
                    allowed(playerId, guildId, RankPermission.DEPOSIT_TO_BANK)
                override fun canDepositPhysical(playerId: UUID, guildId: UUID) =
                    members.getByPlayerAndGuild(playerId, guildId) != null
                override fun canWithdraw(playerId: UUID, guildId: UUID) =
                    allowed(playerId, guildId, RankPermission.WITHDRAW_FROM_BANK)
            },
            personalEconomy = vaultPersonalEconomy(),
            physicalGold = getBean<BukkitPhysicalGoldAdapter>(),
            additionalFrozen = { guildId -> guilds.getById(guildId)?.bankFrozen == true },
            rewardPurchases = object : RewardPurchaseRepository {
                override fun purchase(request: RewardPurchaseRequest, guard: () -> RewardPurchaseRejection?) =
                    getBean<RewardPurchaseRepositorySQL>().purchase(request, guard)
            },
            rewardPurchaseAuthorization = getBean<GuildRewardPurchaseAccess>()::allowed,
            rewardPurchasesEnabled = { config.loadConfig().chapterTwoRewardsEnabled },
            depositXpQuote = { actorId -> getBean<BankDepositXpQuotes>().capture(actorId) },
            periodStartProvider = {
                LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
    }

    // --- Physical gold (two distinct adapters, two distinct journals, as in Koin) ---
    // Also serves getBean<PhysicalGoldPort>() via interface indexing; the war-banner adapter does not.
    economySingleton<BukkitPhysicalGoldAdapter> {
        BukkitPhysicalGoldAdapter.fromConfig(
            { Bukkit.getPlayer(it) },
            getBean<ConfigService>().loadConfig().vault,
            PhysicalGoldJournal(economyStorage()),
        )
    }
    economySingleton<WarBannerRawGold> {
        WarBannerRawGold(
            BukkitPhysicalGoldAdapter(
                { Bukkit.getPlayer(it) },
                Material.RAW_GOLD,
                Material.RAW_GOLD_BLOCK,
                9L,
                PhysicalGoldJournal(economyStorage()),
            )
        )
    }

    // --- War banners ---
    economySingleton<WarBannerRepository> { WarBannerRepositorySQL(economyStorage()) }
    economySingleton<WarBannerService> {
        val members = getBean<MemberRepository>()
        val ranks = getBean<RankRepository>()
        val wars = getBean<WarService>()
        val config = getBean<ConfigService>()
        WarBannerService(
            repository = getBean<WarBannerRepository>(),
            physicalGold = getBean<WarBannerRawGold>().adapter,
            config = { config.loadConfig().warBanner },
            isMember = { playerId, guildId ->
                members.getByPlayerAndGuild(playerId, guildId) != null
            },
            canPlace = { playerId, guildId ->
                val member = members.getByPlayerAndGuild(playerId, guildId)
                member != null && ranks.getById(member.rankId)?.permissions
                    ?.contains(RankPermission.PLACE_WAR_BANNER) == true
            },
            hasActiveWar = { guildId ->
                wars.getWarsForGuild(guildId).any { it.isActive }
            },
        )
    }
    economySingleton<WarBannerServiceBukkit> {
        WarBannerServiceBukkit(
            getBean<LumaGuilds>(),
            getBean<WarBannerService>(),
            getBean<ConfigService>(),
            getBean<TeleportationService>(),
            getBean<LangService>(),
        )
    }
    economySingleton<WarBannerListener> {
        WarBannerListener(
            getBean<WarBannerService>(),
            getBean<WarBannerServiceBukkit>(),
            getBean<GuildService>(),
            getBean<LangService>(),
        )
    }

    // --- Guild creation / home costs ---
    // Consumers resolve the concrete type, as in Koin. Do NOT look up GuildCreationPaymentPort by
    // interface: PhysicalGoldPort extends it, so BukkitPhysicalGoldAdapter is also a candidate if
    // Nexus indexes super-interfaces transitively, and the wrong payment rail could be chosen.
    economySingleton<VaultGuildCreationPaymentAdapter> {
        VaultGuildCreationPaymentAdapter(
            vaultPersonalEconomy(),
            GuildCreationVaultJournal(economyStorage()),
        )
    }
    economySingleton<GuildCostService> {
        GuildCostService(
            { getBean<ConfigService>().loadConfig() },
            getBean<VaultGuildCreationPaymentAdapter>(),
            getBean<GuildGoldService>(),
            reportCreationFailure = { transactionId, error ->
                org.slf4j.LoggerFactory.getLogger("GuildCreationPayments")
                    .error("Guild setup/payment requires review; transaction={}", transactionId, error)
            },
        )
    }
    economySingleton<GuildHomeActivationService> {
        GuildHomeActivationService(
            repository = getBean(),
            costs = getBean<GuildCostService>(),
            maySetHome = { guildId, homeName, actorId, existedBefore, _ ->
                // Resolve lazily to avoid a constructor cycle; never debit when the actor
                // lost MANAGE_HOME or a "new" name appeared after the UI was opened.
                val guilds = getBean<GuildService>()
                if (!guilds.hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
                    false
                } else {
                    val saved = guilds.getHomes(guildId)
                    val access = getBean<GuildHomeAccessRepository>()
                    val nameAllowed = if (existedBefore) {
                        saved.homes.containsKey(homeName) && !guilds.isHomeLocked(guildId, homeName)
                    } else {
                        if (saved.size == 0) access.prepareEmptyGuild(guildId)
                        val noCollision = saved.homeNames.none { it.equals(homeName, ignoreCase = true) }
                        val freeSlot = if (access.isManaged(guildId)) {
                            saved.size < 16 && access.canGrantNewHome(guildId, guilds.getAvailableHomeSlots(guildId))
                        } else saved.size < guilds.getAvailableHomeSlots(guildId)
                        noCollision && freeSlot
                    }
                    // Homes can be anywhere. Never consult claim storage for home locations.
                    nameAllowed
                }
            },
            config = { getBean<ConfigService>().loadConfig() },
            homeAccessState = { guildId, homeName ->
                getBean<GuildHomeAccessRepository>().state(guildId, homeName)
            },
            paidOutpostForNewHome = { guildId, _ ->
                val guilds = getBean<GuildService>()
                getBean<GuildHomeAccessRepository>()
                    .willUsePaidOutpostForNewHome(guildId, guilds.getAvailableHomeSlots(guildId))
            },
        )
    }

    // --- Bank ---
    economySingleton<BankService> {
        BankServiceBukkit(
            getBean<BankRepository>(),
            getBean<MemberRepository>(),
            getBean<RankRepository>(),
            getBean<ProgressionRepository>(),
            getBean<ProgressionConfigService>(),
            getBean<ConfigService>(),
            getBean<GuildRepository>(),
            getBean<GuildService>(),
            getBean<VaultInventoryManager>(),
            getBean<BankDepositXpDeliveryService>(),
            getBean<GuildGoldService>(),
        )
    }
    economySingleton<BankAutomationService> {
        BankAutomationService(
            getBean<BankRepository>(),
            getBean<BankSettingsRepository>(),
            getBean<GuildRepository>(),
            getBean<BankService>(),
            getBean<ConfigService>(),
        )
    }
    economySingleton<PhysicalCurrencyService> {
        PhysicalCurrencyServiceBukkit(getBean<ConfigService>(), getBean<BankService>())
    }
}

/**
 * Vault personal economy; the provider and player lookups run on every call (never at
 * construction), so availability tracks Bukkit's ServicesManager exactly as in Koin.
 */
private fun vaultPersonalEconomy(): PersonalEconomyPort {
    // Creating the provider method reference links Vault's Economy return type.
    if (Bukkit.getPluginManager().getPlugin("Vault") == null) return PersonalEconomyPort.Unavailable
    return VaultPersonalEconomyAdapter(::currentVaultEconomy) { Bukkit.getOfflinePlayer(it) }
}

@Suppress("UNCHECKED_CAST")
private fun NexusContext.economyStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.economySingleton(noinline factory: () -> T) {
    registerBean(economyBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun economyBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
