package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.*
import org.bukkit.configuration.file.FileConfiguration

class ConfigServiceBukkit(private val config: FileConfiguration): ConfigService {
    override fun loadConfig(): MainConfig {
        return MainConfig(
            claimsEnabled = config.getBoolean("claims_enabled", true),
            partiesEnabled = config.getBoolean("parties_enabled", true),
            claimLimit = config.getInt("claim_limit"),
            claimBlockLimit = config.getInt("claim_block_limit"),
            initialClaimSize = config.getInt("initial_claim_size"),
            minimumPartitionSize = config.getInt("minimum_partition_size"),
            distanceBetweenClaims = config.getInt("distance_between_claims"),
            visualiserHideDelayPeriod = config.getDouble("visualiser_hide_delay_period"),
            visualiserRefreshPeriod = config.getDouble("visualiser_refresh_period"),
            rightClickHarvest = config.getBoolean("right_click_harvest"),
            pluginLanguage = config.getString("plugin_language") ?: "EN",
            customClaimToolModelId = config.getInt("custom_claim_tool_model_id"),
            customMoveToolModelId = config.getInt("custom_move_tool_model_id"),
            guild = loadGuildConfig(),
            teamRolePermissions = loadTeamRolePermissions(),
            bank = loadBankConfig(),
            combat = loadCombatConfig(),
            chat = loadChatConfig(),
            progression = loadProgressionConfig(),
            ui = loadUIConfig(),
            discord = loadDiscordConfig(),
            party = loadPartyConfig(),
            itemBanking = loadItemBankingConfig(),
            bedrock = loadBedrockConfig()
        )
    }
    
    private fun loadGuildConfig(): GuildConfig {
        return GuildConfig(
            maxNameLength = config.getInt("guild.max_name_length", 32),
            minNameLength = config.getInt("guild.min_name_length", 1),
            maxGuildCount = config.getInt("guild.max_guild_count", 1000),
            createGuildCost = config.getInt("guild.create_guild_cost", 0),
            disbandRefundPercent = config.getDouble("guild.disband_refund_percent", 0.5),
            peacefulModeEnabled = config.getBoolean("guild.peaceful_mode_enabled", true),
            modeSwitchCooldownDays = config.getInt("guild.mode_switch_cooldown_days", 7),
            hostileModeMinimumDays = config.getInt("guild.hostile_mode_minimum_days", 7),
            peacefulModeClaimPvpDisabled = config.getBoolean("guild.peaceful_mode_claim_pvp_disabled", true),
            peacefulModePreventWars = config.getBoolean("guild.peaceful_mode_prevent_wars", true),
            maxCustomRanks = config.getInt("guild.max_custom_ranks", 10),
            maxRankNameLength = config.getInt("guild.max_rank_name_length", 16),
            maxMembersPerGuild = config.getInt("guild.max_members_per_guild", 50),
            homeTeleportCooldownSeconds = config.getInt("guild.home_teleport_cooldown_seconds", 5),
            homeSetCooldownMinutes = config.getInt("guild.home_set_cooldown_minutes", 10),
            homeTeleportWarmupSeconds = config.getInt("guild.home_teleport_warmup_seconds", 3),
            homeTeleportSafetyCheck = config.getBoolean("guild.home_teleport_safety_check", true),
            bannerCopyEnabled = config.getBoolean("guild.banner_copy_enabled", true),
            bannerCopyCost = config.getInt("guild.banner_copy_cost", 100),
            bannerCopyChargeGuildBank = config.getBoolean("guild.banner_copy_charge_guild_bank", true),
            bannerCopyFree = config.getBoolean("guild.banner_copy_free", false),
            bannerCopyUseItemCost = config.getBoolean("guild.banner_copy_use_item_cost", false),
            bannerCopyItemMaterial = config.getString("guild.banner_copy_item_material", "DIAMOND") ?: "DIAMOND",
            bannerCopyItemAmount = config.getInt("guild.banner_copy_item_amount", 1),
            bannerCopyItemCustomModelData = if (config.contains("guild.banner_copy_item_custom_model_data")) {
                config.getInt("guild.banner_copy_item_custom_model_data")
            } else null,

            // War & Combat settings
            peaceAgreementSystemEnabled = config.getBoolean("guild.peace_agreement_system_enabled", false),
            dailyWarExpCost = config.getInt("guild.daily_war_exp_cost", 10),
            dailyWarMoneyCost = config.getInt("guild.daily_war_money_cost", 100),
            warFarmingCooldownHours = config.getInt("guild.war_farming_cooldown_hours", 24)
        )
    }
    
    private fun loadBankConfig(): BankConfig {
        return BankConfig(
            minDepositAmount = config.getInt("bank.min_deposit_amount", 1),
            maxDepositAmount = config.getInt("bank.max_deposit_amount", 100000),
            maxWithdrawalPercent = config.getDouble("bank.max_withdrawal_percent", 0.5),
            dailyWithdrawalLimit = config.getInt("bank.daily_withdrawal_limit", 50000),
            depositFeePercent = config.getDouble("bank.deposit_fee_percent", 0.01),
            withdrawalFeePercent = config.getDouble("bank.withdrawal_fee_percent", 0.02),
            maxDepositFee = config.getInt("bank.max_deposit_fee", 1000),
            maxWithdrawalFee = config.getInt("bank.max_withdrawal_fee", 2000),
            interestRatePercent = config.getDouble("bank.interest_rate_percent", 0.005),
            interestCompoundPeriodHours = config.getInt("bank.interest_compound_period_hours", 24),
            maxBankBalance = config.getInt("bank.max_bank_balance", 1000000),
            auditLogRetentionDays = config.getInt("bank.audit_log_retention_days", 30),
            suspiciousTransactionThreshold = config.getInt("bank.suspicious_transaction_threshold", 50000),
            autoLockSuspiciousAccounts = config.getBoolean("bank.auto_lock_suspicious_accounts", false)
        )
    }
    
    private fun loadCombatConfig(): CombatConfig {
        return CombatConfig(
            killCooldownMinutes = config.getInt("combat.kill_cooldown_minutes", 5),
            samePlayerKillLimit = config.getInt("combat.same_player_kill_limit", 3),
            antiGriefingEnabled = config.getBoolean("combat.anti_griefing_enabled", true),
            warDeclarationCooldownHours = config.getInt("combat.war_declaration_cooldown_hours", 24),
            warDurationHours = config.getInt("combat.war_duration_hours", 168),
            warEndGracePeriodMinutes = config.getInt("combat.war_end_grace_period_minutes", 30),
            maxSimultaneousWars = config.getInt("combat.max_simultaneous_wars", 3),
            killExperience = config.getInt("combat.kill_experience", 10),
            warWinExperience = config.getInt("combat.war_win_experience", 500),
            warLoseExperience = config.getInt("combat.war_lose_experience", 100)
        )
    }
    
    private fun loadChatConfig(): ChatConfig {
        return ChatConfig(
            announceCooldownMinutes = config.getInt("chat.announce_cooldown_minutes", 30),
            pingCooldownMinutes = config.getInt("chat.ping_cooldown_minutes", 5),
            maxMessageLength = config.getInt("chat.max_message_length", 256),
            defaultChannelVisibility = config.getBoolean("chat.default_channel_visibility", true),
            allyChatEnabled = config.getBoolean("chat.ally_chat_enabled", true),
            partyChatEnabled = config.getBoolean("chat.party_chat_enabled", true),
            guildChatEnabled = config.getBoolean("chat.guild_chat_enabled", true),
            enableEmojis = config.getBoolean("chat.enable_emojis", true),
            emojiPermissionPrefix = config.getString("chat.emoji_permission_prefix") ?: "lumalyte.emoji",
            maxEmojisPerMessage = config.getInt("chat.max_emojis_per_message", 5),
            coloredChatEnabled = config.getBoolean("chat.colored_chat_enabled", true)
        )
    }
    
    private fun loadProgressionConfig(): ProgressionConfig {
        return ProgressionConfig(
            // Experience values for different activities
            bankDepositXpPer100 = config.getInt("progression.bank_deposit_xp_per_100", 1),
            memberJoinedXp = config.getInt("progression.member_joined_xp", 50),
            playerKillXp = config.getInt("progression.player_kill_xp", 25),
            mobKillXp = config.getInt("progression.mob_kill_xp", 2),
            cropBreakXp = config.getInt("progression.crop_break_xp", 1),
            blockBreakXp = config.getInt("progression.block_break_xp", 1),
            blockPlaceXp = config.getInt("progression.block_place_xp", 1),
            craftingXp = config.getInt("progression.crafting_xp", 2),
            smeltingXp = config.getInt("progression.smelting_xp", 2),
            fishingXp = config.getInt("progression.fishing_xp", 3),
            enchantingXp = config.getInt("progression.enchanting_xp", 10),
            claimCreatedXp = config.getInt("progression.claim_created_xp", 100),
            warWonXp = config.getInt("progression.war_won_xp", 500),
            
            // Rate limiting settings
            xpCooldownMs = config.getLong("progression.xp_cooldown_ms", 5000L),
            maxXpPerBatch = config.getInt("progression.max_xp_per_batch", 50),
            
            // Leveling curve settings
            baseXp = config.getDouble("progression.base_xp", 800.0),
            levelExponent = config.getDouble("progression.level_exponent", 1.3),
            linearBonusPerLevel = config.getInt("progression.linear_bonus_per_level", 200)
        )
    }
    
    private fun loadUIConfig(): UIConfig {
        return UIConfig(
            guildMenuItem = loadMenuItemConfig("ui.guild_menu_item", "BANNER", "Guild", 732100),
            bankMenuItem = loadMenuItemConfig("ui.bank_menu_item", "GOLD_INGOT", "Bank", 732101),
            rankMenuItem = loadMenuItemConfig("ui.rank_menu_item", "GOLDEN_HELMET", "Ranks", 732102),
            relationMenuItem = loadMenuItemConfig("ui.relation_menu_item", "COMPASS", "Relations", 732103),
            warMenuItem = loadMenuItemConfig("ui.war_menu_item", "DIAMOND_SWORD", "Wars", 732104),
            modeMenuItem = loadMenuItemConfig("ui.mode_menu_item", "SHIELD", "Mode", 732105),
            homeMenuItem = loadMenuItemConfig("ui.home_menu_item", "BED", "Home", 732106),
            leaderboardMenuItem = loadMenuItemConfig("ui.leaderboard_menu_item", "ITEM_FRAME", "Leaderboards", 732107),
            chatMenuItem = loadMenuItemConfig("ui.chat_menu_item", "WRITABLE_BOOK", "Chat", 732108),
            partyMenuItem = loadMenuItemConfig("ui.party_menu_item", "CAKE", "Party", 732109),
            backButton = loadMenuItemConfig("ui.back_button", "ARROW", "Back", 732200),
            nextButton = loadMenuItemConfig("ui.next_button", "ARROW", "Next", 732201),
            confirmButton = loadMenuItemConfig("ui.confirm_button", "EMERALD", "Confirm", 732202),
            cancelButton = loadMenuItemConfig("ui.cancel_button", "REDSTONE", "Cancel", 732203),
            closeButton = loadMenuItemConfig("ui.close_button", "BARRIER", "Close", 732204),
            onlineIndicator = loadMenuItemConfig("ui.online_indicator", "LIME_DYE", "Online", 732300),
            offlineIndicator = loadMenuItemConfig("ui.offline_indicator", "GRAY_DYE", "Offline", 732301),
            peacefulModeIndicator = loadMenuItemConfig("ui.peaceful_mode_indicator", "WHITE_BANNER", "Peaceful", 732302),
            hostileModeIndicator = loadMenuItemConfig("ui.hostile_mode_indicator", "RED_BANNER", "Hostile", 732303),
            ownerIcon = loadMenuItemConfig("ui.owner_icon", "GOLDEN_CROWN", "Owner", 732400),
            coOwnerIcon = loadMenuItemConfig("ui.co_owner_icon", "GOLDEN_HELMET", "Co-Owner", 732401),
            adminIcon = loadMenuItemConfig("ui.admin_icon", "IRON_HELMET", "Admin", 732402),
            modIcon = loadMenuItemConfig("ui.mod_icon", "LEATHER_HELMET", "Mod", 732403),
            memberIcon = loadMenuItemConfig("ui.member_icon", "PLAYER_HEAD", "Member", 732404),
            menuSize = config.getInt("ui.menu_size", 54),
            enableMenuAnimations = config.getBoolean("ui.enable_menu_animations", true),
            menuUpdateIntervalTicks = config.getInt("ui.menu_update_interval_ticks", 20)
        )
    }
    
    private fun loadMenuItemConfig(path: String, defaultMaterial: String, defaultName: String, defaultModelData: Int): MenuItemConfig {
        return MenuItemConfig(
            material = config.getString("$path.material") ?: defaultMaterial,
            name = config.getString("$path.name") ?: defaultName,
            customModelData = if (config.contains("$path.custom_model_data")) config.getInt("$path.custom_model_data") else defaultModelData,
            enchanted = config.getBoolean("$path.enchanted", false)
        )
    }
    
    private fun loadTeamRolePermissions(): TeamRolePermissions {
        val teamSection = config.getConfigurationSection("team_role_permissions")
        if (teamSection == null) {
            return TeamRolePermissions()
        }
        
        val roleMappings = mutableMapOf<String, Set<String>>()
        val rolesSection = teamSection.getConfigurationSection("roles")
        if (rolesSection != null) {
            for (roleName in rolesSection.getKeys(false)) {
                val permissions = rolesSection.getStringList(roleName).toSet()
                roleMappings[roleName] = permissions
            }
        }
        
        val defaultPermissions = teamSection.getStringList("default_permissions").toSet()
        val cacheDelay = teamSection.getInt("cache_invalidation_delay_seconds", 5)
        
        return TeamRolePermissions(
            roleMappings = roleMappings.ifEmpty { TeamRolePermissions.defaultRoleMappings() },
            defaultPermissions = defaultPermissions.ifEmpty { setOf("VIEW") },
            cacheInvalidationDelaySeconds = cacheDelay
        )
    }

    private fun loadDiscordConfig(): DiscordConfig {
        return DiscordConfig(
            webhookUrl = config.getString("discord_webhook_url") ?: "",
            csvDeliveryEnabled = config.getBoolean("discord_csv_delivery", false)
        )
    }

    private fun loadPartyConfig(): PartyConfig {
        return PartyConfig(
            maxPartyNameLength = config.getInt("party.max_party_name_length", 32),
            minPartyNameLength = config.getInt("party.min_party_name_length", 1),
            defaultPartyDurationHours = config.getInt("party.default_party_duration_hours", 24),
            maxSimultaneousPartiesPerGuild = config.getInt("party.max_simultaneous_parties_per_guild", 3),
            allowPrivateParties = config.getBoolean("party.allow_private_parties", true),
            partyChatEnabled = config.getBoolean("party.party_chat_enabled", true),
            partyChatPriority = config.getInt("party.party_chat_priority", 100),
            partyChatFormat = config.getString("party.party_chat_format") ?: "[%bellclaims_party_name%] %bellclaims_rel_<player>_status% %bellclaims_guild_emoji%%bellclaims_guild_tag% %luckperms-suffix% %player_name% ⋙ <message>",
            partyChatPrefix = config.getString("party.party_chat_prefix") ?: "[PARTY]",
            partyChatSuffix = config.getString("party.party_chat_suffix") ?: "",
            chatInputListenerPriority = config.getString("party.chat_input_listener_priority") ?: "HIGHEST",
            allowRoleRestrictions = config.getBoolean("party.allow_role_restrictions", true),
            defaultToAllMembers = config.getBoolean("party.default_to_all_members", true)
        )
    }

    private fun loadItemBankingConfig(): ItemBankingConfig {
        return ItemBankingConfig(
            enabled = config.getBoolean("item_banking.enabled", true),
            allowDualBanking = config.getBoolean("item_banking.allow_dual_banking", true),
            defaultChestSize = config.getInt("item_banking.default_chest_size", 54),
            maxChestSize = config.getInt("item_banking.max_chest_size", 270),
            chestBreakConfirmation = config.getBoolean("item_banking.chest_break_confirmation", true),
            chestBreakMessage = config.getString("item_banking.chest_break_message") ?: "§cWarning: Breaking this guild chest will drop all items! Break again to confirm.",
            worldGuardIntegration = config.getBoolean("item_banking.worldguard_integration", true),
            worldGuardDenyRegions = config.getStringList("item_banking.worldguard_deny_regions").toSet().takeIf { it.isNotEmpty() } ?: setOf("spawn", "pvp", "shop"),
            unlockLevels = loadUnlockLevels(),
            defaultCurrencyItem = config.getString("item_banking.default_currency_item") ?: "DIAMOND",
            currencyItems = loadCurrencyItems(),
            autoEnemyOnChestBreak = config.getBoolean("item_banking.auto_enemy_on_chest_break", true),
            autoEnemyDurationHours = config.getInt("item_banking.auto_enemy_duration_hours", 24),
            autoEnemyReason = config.getString("item_banking.auto_enemy_reason") ?: "Guild chest was destroyed by member of this guild",
            requireClaimsForChestProtection = config.getBoolean("item_banking.require_claims_for_chest_protection", false),
            chestExplosionRequired = config.getBoolean("item_banking.chest_explosion_required", true),
            logChestBreakAttempts = config.getBoolean("item_banking.log_chest_break_attempts", true),
            allowPhysicalAccess = config.getBoolean("item_banking.allow_physical_access", true),
            allowGuiAccess = config.getBoolean("item_banking.allow_gui_access", true),
            requirePermissionForAccess = config.getBoolean("item_banking.require_permission_for_access", false),
            accessPermission = config.getString("item_banking.access_permission") ?: "lumaguilds.chest.access"
        )
    }

    private fun loadBedrockConfig(): BedrockConfig {
        return BedrockConfig(
            bedrockMenusEnabled = config.getBoolean("bedrock.bedrock_menus_enabled", true),
            forceBedrockMenus = config.getBoolean("bedrock.force_bedrock_menus", false),
            fallbackToJavaMenus = config.getBoolean("bedrock.fallback_to_java_menus", true),
            fallbackOnFloodgateUnavailable = config.getBoolean("bedrock.fallback_on_floodgate_unavailable", true),
            fallbackOnCumulusUnavailable = config.getBoolean("bedrock.fallback_on_cumulus_unavailable", true),
            formCacheEnabled = config.getBoolean("bedrock.form_cache_enabled", true),
            formCacheSize = config.getInt("bedrock.form_cache_size", 100),
            formCacheExpirationMinutes = config.getInt("bedrock.form_cache_expiration_minutes", 30),
            maxFormButtons = config.getInt("bedrock.max_form_buttons", 8),
            formTimeoutSeconds = config.getInt("bedrock.form_timeout_seconds", 300),
            enableBedrockConfirmations = config.getBoolean("bedrock.enable_bedrock_confirmations", true),
            enableBedrockSelections = config.getBoolean("bedrock.enable_bedrock_selections", true),
            enableBedrockCustomForms = config.getBoolean("bedrock.enable_bedrock_custom_forms", true),
            imageSource = ImageSource.valueOf(config.getString("bedrock.image_source") ?: "URL"),
            defaultButtonImageUrl = config.getString("bedrock.default_button_image_url") ?: "https://via.placeholder.com/64x64/4CAF50/FFFFFF?text=ICON",
            defaultButtonImagePath = config.getString("bedrock.default_button_image_path") ?: "textures/ui/icon.png",
            guildMembersIconUrl = config.getString("bedrock.guild_members_icon_url") ?: "https://via.placeholder.com/64x64/2196F3/FFFFFF?text=MEMBERS",
            guildMembersIconPath = config.getString("bedrock.guild_members_icon_path") ?: "textures/ui/members.png",
            guildSettingsIconUrl = config.getString("bedrock.guild_settings_icon_url") ?: "https://via.placeholder.com/64x64/FF9800/FFFFFF?text=SETTINGS",
            guildSettingsIconPath = config.getString("bedrock.guild_settings_icon_path") ?: "textures/ui/settings.png",
            guildBankIconUrl = config.getString("bedrock.guild_bank_icon_url") ?: "https://via.placeholder.com/64x64/FFC107/FFFFFF?text=BANK",
            guildBankIconPath = config.getString("bedrock.guild_bank_icon_path") ?: "textures/ui/bank.png",
            guildWarsIconUrl = config.getString("bedrock.guild_wars_icon_url") ?: "https://via.placeholder.com/64x64/F44336/FFFFFF?text=WARS",
            guildWarsIconPath = config.getString("bedrock.guild_wars_icon_path") ?: "textures/ui/wars.png",
            guildHomeIconUrl = config.getString("bedrock.guild_home_icon_url") ?: "https://via.placeholder.com/64x64/9C27B0/FFFFFF?text=HOME",
            guildHomeIconPath = config.getString("bedrock.guild_home_icon_path") ?: "textures/ui/home.png",
            guildTagIconUrl = config.getString("bedrock.guild_tag_icon_url") ?: "https://via.placeholder.com/64x64/607D8B/FFFFFF?text=TAG",
            guildTagIconPath = config.getString("bedrock.guild_tag_icon_path") ?: "textures/ui/tag.png",
            confirmIconUrl = config.getString("bedrock.confirm_icon_url") ?: "https://via.placeholder.com/64x64/4CAF50/FFFFFF?text=CONFIRM",
            confirmIconPath = config.getString("bedrock.confirm_icon_path") ?: "textures/ui/confirm.png",
            cancelIconUrl = config.getString("bedrock.cancel_icon_url") ?: "https://via.placeholder.com/64x64/F44336/FFFFFF?text=CANCEL",
            cancelIconPath = config.getString("bedrock.cancel_icon_path") ?: "textures/ui/cancel.png",
            backIconUrl = config.getString("bedrock.back_icon_url") ?: "https://via.placeholder.com/64x64/757575/FFFFFF?text=BACK",
            backIconPath = config.getString("bedrock.back_icon_path") ?: "textures/ui/back.png",
            closeIconUrl = config.getString("bedrock.close_icon_url") ?: "https://via.placeholder.com/64x64/000000/FFFFFF?text=CLOSE",
            closeIconPath = config.getString("bedrock.close_icon_path") ?: "textures/ui/close.png",
            editIconUrl = config.getString("bedrock.edit_icon_url") ?: "https://via.placeholder.com/64x64/FF9800/FFFFFF?text=EDIT",
            editIconPath = config.getString("bedrock.edit_icon_path") ?: "textures/ui/edit.png",
            deleteIconUrl = config.getString("bedrock.delete_icon_url") ?: "https://via.placeholder.com/64x64/F44336/FFFFFF?text=DELETE",
            deleteIconPath = config.getString("bedrock.delete_icon_path") ?: "textures/ui/delete.png",
            debugBedrockMenus = config.getBoolean("bedrock.debug_bedrock_menus", false),
            logFormInteractions = config.getBoolean("bedrock.log_form_interactions", false)
        )
    }

    private fun loadUnlockLevels(): Map<Int, Int> {
        val levelsSection = config.getConfigurationSection("item_banking.unlock_levels")
        if (levelsSection == null) {
            return mapOf(
                5 to 9, 10 to 18, 15 to 27, 20 to 36, 25 to 45, 30 to 54
            )
        }
        return levelsSection.getKeys(false).associate { key ->
            key.toIntOrNull() to levelsSection.getInt(key)
        }.filterKeys { it != null } as Map<Int, Int>
    }

    private fun loadCurrencyItems(): Map<String, Double> {
        val currencySection = config.getConfigurationSection("item_banking.currency_items")
        if (currencySection == null) {
            return mapOf(
                "DIAMOND" to 1.0,
                "EMERALD" to 0.5,
                "GOLD_INGOT" to 0.25,
                "IRON_INGOT" to 0.1,
                "COAL" to 0.05
            )
        }
        return currencySection.getKeys(false).associateWith { key ->
            currencySection.getDouble(key)
        }
    }

    private fun loadChestUpgradeCosts(): Map<String, Int> {
        val upgradeSection = config.getConfigurationSection("item_banking.chest_upgrade_costs")
        if (upgradeSection == null) {
            return mapOf(
                "SIZE_UPGRADE" to 1000,
                "SECURITY_UPGRADE" to 2000,
                "CAPACITY_UPGRADE" to 1500,
                "PROTECTION_UPGRADE" to 3000
            )
        }
        return upgradeSection.getKeys(false).associateWith { key ->
            upgradeSection.getInt(key)
        }
    }
}
