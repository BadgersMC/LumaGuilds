package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.interaction.menus.MenuFactory

/** Lazy collaborators for the shared guild command handler. */
internal data class GuildCommandDependencies(
    val lang: Lazy<LangService>,
    val guildService: Lazy<GuildService>,
    val lfgService: Lazy<net.lumalyte.lg.application.services.LfgService>,
    val guildRepository: Lazy<net.lumalyte.lg.application.persistence.GuildRepository>,
    val rankService: Lazy<RankService>,
    val chatRankSettings: Lazy<net.lumalyte.lg.application.services.GuildChatRankSettingsService>,
    val memberService: Lazy<MemberService>,
    val inviteBlocks: Lazy<net.lumalyte.lg.application.services.InviteBlockService>,
    val vaultService: Lazy<net.lumalyte.lg.application.services.GuildVaultService>,
    val warService: Lazy<net.lumalyte.lg.application.services.WarService>,
    val configService: Lazy<ConfigService>,
    val menuFactory: Lazy<MenuFactory>,
    val progressionService: Lazy<net.lumalyte.lg.application.services.ProgressionService>,
    val historyRepository: Lazy<MembershipHistoryRepository>,
    val guildChatListener: Lazy<net.lumalyte.lg.interaction.listeners.GuildChatListener>,
    val adminOverrideService: Lazy<net.lumalyte.lg.application.services.AdminOverrideService>,
    val teleportationService: Lazy<net.lumalyte.lg.infrastructure.services.TeleportationService>,
    val bannermanListeners: Lazy<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners>,
    val strikeService: Lazy<net.lumalyte.lg.application.services.StrikeService>,
    val bankService: Lazy<net.lumalyte.lg.application.services.BankService>,
    val guildCostService: Lazy<net.lumalyte.lg.application.services.GuildCostService>,
    val guildHomeActivationService: Lazy<net.lumalyte.lg.application.services.GuildHomeActivationService>,
    val warBannerService: Lazy<net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit>,
    val guildLoginNotificationService: Lazy<net.lumalyte.lg.application.services.GuildLoginNotificationService>,
    /** Used by ally homes and the relation subcommands; resolved on first use. */
    val relationService: Lazy<net.lumalyte.lg.application.services.RelationService>,
    /** Used only by `/g emoji`; resolved on first use. */
    val nexoEmojiService: Lazy<net.lumalyte.lg.infrastructure.services.NexoEmojiService>,
    /** Used only by `/g join` open-recruitment admission; resolved on first use. */
    val guildRecruitmentService: Lazy<net.lumalyte.lg.application.services.GuildRecruitmentService>,
    /** Used only by `/g start dismiss`; resolved on first use. */
    val guildOnboardingRepository: Lazy<net.lumalyte.lg.application.persistence.GuildOnboardingRepository>,
)
