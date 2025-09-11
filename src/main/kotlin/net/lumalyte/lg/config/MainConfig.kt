package net.lumalyte.lg.config

data class MainConfig(
    // Core Claims Configuration
    var claimsEnabled: Boolean = true,
    var partiesEnabled: Boolean = true,
    var claimLimit: Int = 0,
    var claimBlockLimit: Int = 0,
    var initialClaimSize: Int = 0,
    var minimumPartitionSize: Int = 0,
    var distanceBetweenClaims: Int = 0,
    var visualiserHideDelayPeriod: Double = 0.0,
    var visualiserRefreshPeriod: Double = 0.0,
    var rightClickHarvest: Boolean = true,
    
    // Localization and UI
    var pluginLanguage: String = "EN",
    var customClaimToolModelId: Int = 732000,
    var customMoveToolModelId: Int = 732001,
    
    // Guild System Configuration
    var guild: GuildConfig = GuildConfig(),
    var teamRolePermissions: TeamRolePermissions = TeamRolePermissions(),
    var bank: BankConfig = BankConfig(),
    var combat: CombatConfig = CombatConfig(),
    var chat: ChatConfig = ChatConfig(),
    var progression: ProgressionConfig = ProgressionConfig(),
    var ui: UIConfig = UIConfig(),
    var discord: DiscordConfig = DiscordConfig(),
    var party: PartyConfig = PartyConfig()
)

data class GuildConfig(
    // Creation and Management
    var maxNameLength: Int = 32,
    var minNameLength: Int = 1,
    var maxGuildCount: Int = 1000,
    var createGuildCost: Int = 0,
    var disbandRefundPercent: Double = 0.5,
    
    // Mode Switching
    var peacefulModeEnabled: Boolean = true,
    var modeSwitchingEnabled: Boolean = true, // If false, guilds cannot switch between peaceful/hostile modes
    var modeSwitchCooldownDays: Int = 7,
    var hostileModeMinimumDays: Int = 7,
    var peacefulModeClaimPvpDisabled: Boolean = true,
    var peacefulModePreventWars: Boolean = true,
    var peacefulGuildPvpOptIn: Boolean = false, // If true, peaceful guilds can opt-in to PvP; if false, PvP is forced off

    // Ranks and Members
    var maxCustomRanks: Int = 10,
    var maxRankNameLength: Int = 16,
    var maxMembersPerGuild: Int = 50,
    
    // Home System
    var homeTeleportCooldownSeconds: Int = 5,
    var homeSetCooldownMinutes: Int = 10,
    var homeTeleportWarmupSeconds: Int = 3,
    var homeTeleportSafetyCheck: Boolean = true,

    // Banner System
    var bannerCopyEnabled: Boolean = true,
    var bannerCopyCost: Int = 100,
    var bannerCopyChargeGuildBank: Boolean = true,
    // If true, banner copies are free regardless of cost setting
    var bannerCopyFree: Boolean = false,

    // Item-based cost system for banner copies
    var bannerCopyUseItemCost: Boolean = false, // If true, use item cost instead of coin cost
    var bannerCopyItemMaterial: String = "DIAMOND", // Material name for item cost
    var bannerCopyItemAmount: Int = 1, // Amount of items required
    var bannerCopyItemCustomModelData: Int? = null, // Custom model data for the item

    // War & Combat
    var peaceAgreementSystemEnabled: Boolean = false, // If true, replaces default war ending with peace agreements
    var dailyWarExpCost: Int = 10, // EXP lost per day during war
    var dailyWarMoneyCost: Int = 100, // Money lost per day during war
    var warFarmingCooldownHours: Int = 24 // Hours before guild can earn EXP after war ends
)

data class BankConfig(
    // Transaction Limits
    var minDepositAmount: Int = 1,
    var maxDepositAmount: Int = 100000,
    var maxWithdrawalPercent: Double = 0.5,
    var dailyWithdrawalLimit: Int = 50000,
    
    // Fees and Taxes
    var depositFeePercent: Double = 0.01,
    var withdrawalFeePercent: Double = 0.02,
    var maxDepositFee: Int = 1000,
    var maxWithdrawalFee: Int = 2000,
    
    // Interest and Growth
    var interestRatePercent: Double = 0.005,
    var interestCompoundPeriodHours: Int = 24,
    var maxBankBalance: Int = 1000000,
    
    // Audit and Security
    var auditLogRetentionDays: Int = 30,
    var suspiciousTransactionThreshold: Int = 50000,
    var autoLockSuspiciousAccounts: Boolean = false
)

data class CombatConfig(
    // Anti-farming
    var killCooldownMinutes: Int = 5,
    var samePlayerKillLimit: Int = 3,
    var antiGriefingEnabled: Boolean = true,
    
    // War System
    var warDeclarationCooldownHours: Int = 24,
    var warDurationHours: Int = 168, // 1 week
    var warEndGracePeriodMinutes: Int = 30,
    var maxSimultaneousWars: Int = 3,
    
    // Experience and Rewards
    var killExperience: Int = 10,
    var warWinExperience: Int = 500,
    var warLoseExperience: Int = 100
)

data class ChatConfig(
    // Rate Limiting
    var announceCooldownMinutes: Int = 30,
    var pingCooldownMinutes: Int = 5,
    var maxMessageLength: Int = 256,
    
    // Channels
    var defaultChannelVisibility: Boolean = true,
    var allyChatEnabled: Boolean = true,
    var partyChatEnabled: Boolean = true,
    var guildChatEnabled: Boolean = true,
    
    // Emojis and Formatting
    var enableEmojis: Boolean = true,
    var emojiPermissionPrefix: String = "lumalyte.emoji",
    var maxEmojisPerMessage: Int = 5,
    var coloredChatEnabled: Boolean = true
)


data class UIConfig(
    // Menu Items with Custom Model Data
    var guildMenuItem: MenuItemConfig = MenuItemConfig("BANNER", "Guild", 732100),
    var bankMenuItem: MenuItemConfig = MenuItemConfig("GOLD_INGOT", "Bank", 732101),
    var rankMenuItem: MenuItemConfig = MenuItemConfig("GOLDEN_HELMET", "Ranks", 732102),
    var relationMenuItem: MenuItemConfig = MenuItemConfig("COMPASS", "Relations", 732103),
    var warMenuItem: MenuItemConfig = MenuItemConfig("DIAMOND_SWORD", "Wars", 732104),
    var modeMenuItem: MenuItemConfig = MenuItemConfig("SHIELD", "Mode", 732105),
    var homeMenuItem: MenuItemConfig = MenuItemConfig("BED", "Home", 732106),
    var leaderboardMenuItem: MenuItemConfig = MenuItemConfig("ITEM_FRAME", "Leaderboards", 732107),
    var chatMenuItem: MenuItemConfig = MenuItemConfig("WRITABLE_BOOK", "Chat", 732108),
    var partyMenuItem: MenuItemConfig = MenuItemConfig("CAKE", "Party", 732109),
    
    // Navigation Items
    var backButton: MenuItemConfig = MenuItemConfig("ARROW", "Back", 732200),
    var nextButton: MenuItemConfig = MenuItemConfig("ARROW", "Next", 732201),
    var confirmButton: MenuItemConfig = MenuItemConfig("EMERALD", "Confirm", 732202),
    var cancelButton: MenuItemConfig = MenuItemConfig("REDSTONE", "Cancel", 732203),
    var closeButton: MenuItemConfig = MenuItemConfig("BARRIER", "Close", 732204),
    
    // Status Indicators
    var onlineIndicator: MenuItemConfig = MenuItemConfig("LIME_DYE", "Online", 732300),
    var offlineIndicator: MenuItemConfig = MenuItemConfig("GRAY_DYE", "Offline", 732301),
    var peacefulModeIndicator: MenuItemConfig = MenuItemConfig("WHITE_BANNER", "Peaceful", 732302),
    var hostileModeIndicator: MenuItemConfig = MenuItemConfig("RED_BANNER", "Hostile", 732303),
    
    // Rank Indicators
    var ownerIcon: MenuItemConfig = MenuItemConfig("GOLDEN_CROWN", "Owner", 732400),
    var coOwnerIcon: MenuItemConfig = MenuItemConfig("GOLDEN_HELMET", "Co-Owner", 732401),
    var adminIcon: MenuItemConfig = MenuItemConfig("IRON_HELMET", "Admin", 732402),
    var modIcon: MenuItemConfig = MenuItemConfig("LEATHER_HELMET", "Mod", 732403),
    var memberIcon: MenuItemConfig = MenuItemConfig("PLAYER_HEAD", "Member", 732404),
    
    // Menu Settings
    var menuSize: Int = 54,
    var enableMenuAnimations: Boolean = true,
    var menuUpdateIntervalTicks: Int = 20
)

data class MenuItemConfig(
    val material: String,
    val name: String,
    val customModelData: Int? = null,
    val enchanted: Boolean = false
)

data class TeamRolePermissions(
    var roleMappings: Map<String, Set<String>> = defaultRoleMappings(),
    var defaultPermissions: Set<String> = setOf("VIEW"),
    var cacheInvalidationDelaySeconds: Int = 5
) {
    companion object {
        fun defaultRoleMappings(): Map<String, Set<String>> = mapOf(
            "Owner" to setOf("BUILD", "HARVEST", "CONTAINER", "DISPLAY", "VEHICLE", "SIGN", "REDSTONE", "DOOR", "TRADE", "HUSBANDRY", "DETONATE", "EVENT", "SLEEP", "VIEW"),
            "Co-Owner" to setOf("BUILD", "HARVEST", "CONTAINER", "DISPLAY", "VEHICLE", "SIGN", "REDSTONE", "DOOR", "TRADE", "HUSBANDRY", "DETONATE", "EVENT", "SLEEP", "VIEW"),
            "Admin" to setOf("BUILD", "HARVEST", "CONTAINER", "DISPLAY", "VEHICLE", "SIGN", "REDSTONE", "DOOR", "TRADE", "HUSBANDRY", "VIEW"),
            "Mod" to setOf("BUILD", "HARVEST", "CONTAINER", "DISPLAY", "VEHICLE", "SIGN", "VIEW"),
            "Member" to setOf("HARVEST", "CONTAINER", "VIEW")
        )
    }
}

data class DiscordConfig(
    var webhookUrl: String = "",
    var csvDeliveryEnabled: Boolean = false
)

data class PartyConfig(
    // Party Creation
    var maxPartyNameLength: Int = 32,
    var minPartyNameLength: Int = 1,
    var defaultPartyDurationHours: Int = 24,
    var maxSimultaneousPartiesPerGuild: Int = 3,
    var allowPrivateParties: Boolean = true, // Allow creation of private guild-only parties

    // Party Chat
    var partyChatEnabled: Boolean = true,
    var partyChatPriority: Int = 100,
    var partyChatFormat: String = "[%bellclaims_party_name%] %bellclaims_rel_<player>_status% %bellclaims_guild_emoji%%bellclaims_guild_tag% %luckperms-suffix% %player_name% ⋙ <message>",
    var partyChatPrefix: String = "[PARTY]",
    var partyChatSuffix: String = "",
    var chatInputListenerPriority: String = "HIGHEST", // HIGHEST, HIGH, NORMAL, LOW, LOWEST

    // Role Restrictions
    var allowRoleRestrictions: Boolean = true,
    var defaultToAllMembers: Boolean = true
)

data class ProgressionConfig(
    // Experience values for different activities
    var bankDepositXpPer100: Int = 1,
    var memberJoinedXp: Int = 50,
    var playerKillXp: Int = 25,
    var mobKillXp: Int = 2,
    var cropBreakXp: Int = 1,
    var blockBreakXp: Int = 1,
    var blockPlaceXp: Int = 1,
    var craftingXp: Int = 2,
    var smeltingXp: Int = 2,
    var fishingXp: Int = 3,
    var enchantingXp: Int = 10,
    var claimCreatedXp: Int = 100,
    var warWonXp: Int = 500,
    
    // Rate limiting settings
    var xpCooldownMs: Long = 5000L,
    var maxXpPerBatch: Int = 50,
    
    // Leveling curve settings
    var baseXp: Double = 800.0,
    var levelExponent: Double = 1.3,
    var linearBonusPerLevel: Int = 200
)
