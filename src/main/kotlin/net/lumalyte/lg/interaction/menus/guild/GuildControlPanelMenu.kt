package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildControlPanelMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val rankService: RankService,
    private val memberService: MemberService,
    private val vaultService: GuildVaultService,
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory,
    private val configService: ConfigService,
    private val progressionService: ProgressionService,
    private val progressionRepository: ProgressionRepository
): Menu, KoinComponent {

    private val lang: LangService by inject()

    override fun open() {
        val playerId = player.uniqueId

        // Security check: Only guild members can access the control panel
        if (memberService.getMember(playerId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.control_panel.feedback.not_member"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(
            guild.guiTheme,
            6,
            lang.guiTitle("menu.control_panel.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Row 1: Core Settings
        addGuildSettingsButton(pane, 2, 0)
        addEmojiSettingsButton(pane, 3, 0)
        addBannerSettingsButton(pane, 4, 0)
        addModeSettingsButton(pane, 5, 0)
        addHomeSettingsButton(pane, 6, 0)

        // Row 2: Management
        addRankManagementButton(pane, 2, 1)
        addMemberManagementButton(pane, 3, 1)
        addPartyManagementButton(pane, 4, 1)
        addWarManagementButton(pane, 5, 1)
        addRelationManagementButton(pane, 6, 1)

        // Row 3: Economy & Stats
        addBankManagementButton(pane, 2, 2)
        addVaultButton(pane, 3, 2)
        addStatisticsButton(pane, 4, 2)
        addProgressionInfoButton(pane, 5, 2)

        // Row 4: Quick Actions
        addInvitePlayerButton(pane, 3, 3)
        addKickPlayerButton(pane, 4, 3)
        addPromotePlayerButton(pane, 5, 3)

        // Row 5: Information
        addGuildInfoButton(pane, 3, 4)
        addMemberListButton(pane, 4, 4)
        addRankListButton(pane, 5, 4)

        // Row 6: Danger Zone
        addDisbandGuildButton(pane, 4, 5)
        addLeaveGuildButton(pane, 8, 5)

        gui.show(player)
    }

    private fun addGuildSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val settingsItem = ItemStack.of(Material.COMMAND_BLOCK)
            .name(lang.gui("menu.control_panel.item.settings.name"))
            .lore(lang.gui("menu.control_panel.item.settings.lore.description"))
            .lore(lang.gui("menu.control_panel.item.settings.lore.details"))
        val guiItem = GuiItem(settingsItem) {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addEmojiSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val emoji = guildService.getEmoji(guild.id)
        val emojiItem = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.control_panel.item.emoji.name"))
            .lore(lang.gui(
                "menu.control_panel.item.emoji.lore.current",
                "emoji" to (emoji ?: lang.gui("menu.control_panel.state.not_set")),
            ))
            .lore(lang.gui("menu.control_panel.item.emoji.lore.description"))
        val guiItem = GuiItem(emojiItem) {
            menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBannerSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val bannerItem = guild.banner?.let { bannerData ->
            // Try to deserialize the banner
            val deserializedBanner = bannerData.deserializeToItemStack()
            if (deserializedBanner != null) {
                deserializedBanner.clone()
                    .name(lang.gui("menu.control_panel.item.banner.name"))
                    .lore(lang.gui(
                        "menu.control_panel.item.banner.lore.current",
                        "banner" to deserializedBanner.type.name.lowercase().replace("_", " "),
                    ))
                    .lore(lang.gui("menu.control_panel.item.banner.lore.description"))
            } else {
                // Fallback if deserialization fails
                ItemStack.of(Material.WHITE_BANNER)
                    .name(lang.gui("menu.control_panel.item.banner.name"))
                    .lore(lang.gui("menu.control_panel.item.banner.lore.error"))
                    .lore(lang.gui("menu.control_panel.item.banner.lore.description"))
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)
            .name(lang.gui("menu.control_panel.item.banner.name"))
            .lore(lang.gui("menu.control_panel.item.banner.lore.not_set"))
            .lore(lang.gui("menu.control_panel.item.banner.lore.description"))

        val guiItem = GuiItem(bannerItem) {
            menuNavigator.openMenu(menuFactory.createGuildBannerMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addModeSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val modeItem = when (guild.mode) {
            GuildMode.PEACEFUL -> ItemStack.of(Material.GREEN_WOOL)
            GuildMode.HOSTILE -> ItemStack.of(Material.RED_WOOL)
        }
            .name(lang.gui("menu.control_panel.item.mode.name"))
            .lore(lang.gui(
                "menu.control_panel.item.mode.lore.current",
                "mode" to if (guild.mode == GuildMode.PEACEFUL) {
                    lang.gui("menu.control_panel.state.peaceful")
                } else {
                    lang.gui("menu.control_panel.state.hostile")
                },
            ))
            .lore(lang.gui("menu.control_panel.item.mode.lore.peaceful"))
            .lore(lang.gui("menu.control_panel.item.mode.lore.hostile"))
        val guiItem = GuiItem(modeItem) {
            menuNavigator.openMenu(menuFactory.createGuildModeMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addHomeSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val home = guildService.getHome(guild.id)
        val homeItem = ItemStack.of(Material.COMPASS)
            .name(lang.gui("menu.control_panel.item.home.name"))
            .lore(if (home != null) {
                lang.gui("menu.control_panel.item.home.lore.set")
            } else {
                lang.gui("menu.control_panel.item.home.lore.not_set")
            })
            .lore(lang.gui("menu.control_panel.item.home.lore.description"))
        val guiItem = GuiItem(homeItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRankManagementButton(pane: StaticPane, x: Int, y: Int) {
        val rankCount = rankService.listRanks(guild.id).size
        val hasPermission = rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)

        val rankItem = ItemStack.of(Material.IRON_SWORD)
            .name(lang.gui("menu.control_panel.item.rank_management.name"))
            .lore(lang.gui("menu.control_panel.item.rank_management.lore.description"))
            .lore(lang.gui("menu.control_panel.item.rank_management.lore.count", "count" to rankCount))

        if (!hasPermission) {
            rankItem.lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.control_panel.item.rank_management.lore.locked"))
        }

        val guiItem = GuiItem(rankItem) {
            if (!hasPermission) {
                player.sendMessage(lang.msg("menu.rank_management.feedback.no_permission"))
                player.sendMessage(lang.msg("menu.rank_management.feedback.required_permission"))
                return@GuiItem
            }
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addMemberManagementButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val memberItem = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.gui("menu.control_panel.item.member_management.name"))
            .lore(lang.gui("menu.control_panel.item.member_management.lore.description"))
            .lore(lang.gui("menu.control_panel.item.member_management.lore.count", "count" to memberCount))
        val guiItem = GuiItem(memberItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPartyManagementButton(pane: StaticPane, x: Int, y: Int) {
        val partyItem = ItemStack.of(Material.FIREWORK_ROCKET)
            .name(lang.gui("menu.control_panel.item.party.name"))
            .lore(lang.gui("menu.control_panel.item.party.lore.description"))
            .lore(lang.gui("menu.control_panel.item.party.lore.details"))
        val guiItem = GuiItem(partyItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addWarManagementButton(pane: StaticPane, x: Int, y: Int) {
        val warItem = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.gui("menu.control_panel.item.war.name"))
            .lore(lang.gui("menu.control_panel.item.war.lore.description"))
            .lore(lang.gui("menu.control_panel.item.war.lore.details"))
        val guiItem = GuiItem(warItem) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRelationManagementButton(pane: StaticPane, x: Int, y: Int) {
        val relationItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.control_panel.item.relations.name"))
            .lore(lang.gui("menu.control_panel.item.relations.lore.description"))
            .lore(lang.gui("menu.control_panel.item.relations.lore.details"))
        val guiItem = GuiItem(relationItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBankManagementButton(pane: StaticPane, x: Int, y: Int) {
        val bankItem = ItemStack.of(Material.GOLD_BLOCK)
            .name(lang.gui("menu.control_panel.item.bank.name"))
            .lore(lang.gui("menu.control_panel.item.bank.lore.description"))
            .lore(lang.gui("menu.control_panel.item.bank.lore.details"))
        val guiItem = GuiItem(bankItem) {
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addVaultButton(pane: StaticPane, x: Int, y: Int) {
        val vaultItem = when (guild.vaultStatus) {
            net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE -> {
                ItemStack.of(Material.CHEST)
                    .name(lang.gui("menu.control_panel.item.vault.name"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.available"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.open"))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.storage_line_1"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.storage_line_2"))
            }
            net.lumalyte.lg.domain.entities.VaultStatus.UNAVAILABLE -> {
                ItemStack.of(Material.BARRIER)
                    .name(lang.gui("menu.control_panel.item.vault.name"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.not_placed"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.obtain"))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_1"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_2"))
            }
            net.lumalyte.lg.domain.entities.VaultStatus.NEVER_PLACED -> {
                ItemStack.of(Material.BARRIER)
                    .name(lang.gui("menu.control_panel.item.vault.name"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.never_placed"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.obtain"))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_1"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_2"))
            }
        }

        val guiItem = GuiItem(vaultItem) {
            // Re-fetch guild to get current vault status — the cached guild object may be stale
            // if another player broke the vault after this menu was opened
            val currentGuild = guildService.getGuild(guild.id)
            if (currentGuild == null) {
                player.sendMessage(lang.msg("menu.control_panel.feedback.guild_missing"))
                return@GuiItem
            }
            if (currentGuild.vaultStatus != net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
                player.sendMessage(lang.msg("menu.control_panel.feedback.vault_unavailable"))
                return@GuiItem
            }
            // Only close the menu once we've confirmed the vault is currently available
            player.closeInventory()
            val result = vaultService.openVaultInventory(player, currentGuild)
            when (result) {
                is VaultResult.Success -> {
                    // Vault opened successfully
                }
                is VaultResult.Failure -> {
                    player.sendMessage(lang.msg("menu.control_panel.feedback.vault_failure", "error" to result.message))
                }
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addStatisticsButton(pane: StaticPane, x: Int, y: Int) {
        val statsItem = ItemStack.of(Material.BOOKSHELF)
            .name(lang.gui("menu.control_panel.item.statistics.name"))
            .lore(lang.gui("menu.control_panel.item.statistics.lore.description"))
            .lore(lang.gui("menu.control_panel.item.statistics.lore.details"))
        val guiItem = GuiItem(statsItem) {
            menuNavigator.openMenu(menuFactory.createGuildStatisticsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addProgressionInfoButton(pane: StaticPane, x: Int, y: Int) {
        // Create progression info item with safe error handling
        val progressionItem = try {
            createProgressionInfoItem()
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback if progression system isn't available
            ItemStack.of(Material.EXPERIENCE_BOTTLE)
                .name(lang.gui("menu.control_panel.item.progression.name"))
                .lore(lang.gui("menu.control_panel.item.progression.lore.starting_level"))
                .lore(lang.gui("menu.control_panel.item.progression.lore.starting_progress"))
                .lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.control_panel.item.progression.lore.loading"))
                .lore(lang.gui("menu.control_panel.item.progression.lore.retry"))
        }
        
        val guiItem = GuiItem(progressionItem) {
            // Just refresh the menu for now - could add detailed progression menu later
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addInvitePlayerButton(pane: StaticPane, x: Int, y: Int) {
        val inviteItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.control_panel.item.invite.name"))
            .lore(lang.gui("menu.control_panel.item.invite.lore"))
        val guiItem = GuiItem(inviteItem) {
            menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addKickPlayerButton(pane: StaticPane, x: Int, y: Int) {
        val kickItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.control_panel.item.kick.name"))
            .lore(lang.gui("menu.control_panel.item.kick.lore"))
        val guiItem = GuiItem(kickItem) {
            menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPromotePlayerButton(pane: StaticPane, x: Int, y: Int) {
        val promoteItem = ItemStack.of(Material.ANVIL)
            .name(lang.gui("menu.control_panel.item.promote.name"))
            .lore(lang.gui("menu.control_panel.item.promote.lore"))
        val guiItem = GuiItem(promoteItem) {
            menuNavigator.openMenu(menuFactory.createGuildPromotionMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addGuildInfoButton(pane: StaticPane, x: Int, y: Int) {
        val infoItem = ItemStack.of(Material.KNOWLEDGE_BOOK)
            .name(lang.gui("menu.control_panel.item.info.name"))
            .lore(lang.gui("menu.control_panel.item.info.lore"))
        val guiItem = GuiItem(infoItem) {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addMemberListButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val listItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.control_panel.item.member_list.name"))
            .lore(lang.gui("menu.control_panel.item.member_list.lore.description"))
            .lore(lang.gui("menu.control_panel.item.member_list.lore.count", "count" to memberCount))
        val guiItem = GuiItem(listItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberListMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRankListButton(pane: StaticPane, x: Int, y: Int) {
        val rankCount = rankService.listRanks(guild.id).size
        val listItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.gui("menu.control_panel.item.rank_list.name"))
            .lore(lang.gui("menu.control_panel.item.rank_list.lore.description"))
            .lore(lang.gui("menu.control_panel.item.rank_list.lore.count", "count" to rankCount))
        val guiItem = GuiItem(listItem) {
            menuNavigator.openMenu(menuFactory.createGuildRankListMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addDisbandGuildButton(pane: StaticPane, x: Int, y: Int) {
        val disbandItem = ItemStack.of(Material.TNT)
            .name(lang.gui("menu.control_panel.item.disband.name"))
            .lore(lang.gui("menu.control_panel.item.disband.lore.warning"))
            .lore(lang.gui("menu.control_panel.item.disband.lore.description"))
            .lore(lang.gui("menu.control_panel.item.disband.lore.members"))
        val guiItem = GuiItem(disbandItem) {
            menuNavigator.openMenu(menuFactory.createGuildDisbandConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addLeaveGuildButton(pane: StaticPane, x: Int, y: Int) {
        val leaveItem = ItemStack.of(Material.DARK_OAK_DOOR)
            .name(lang.gui("menu.control_panel.item.leave.name"))
            .lore(lang.gui("menu.control_panel.item.leave.lore.description"))
            .lore(lang.gui("menu.control_panel.item.leave.lore.rejoin"))
        val guiItem = GuiItem(leaveItem) {
            menuNavigator.openMenu(menuFactory.createGuildLeaveConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun createProgressionInfoItem(): ItemStack {
        val levelingItem = ItemStack.of(Material.EXPERIENCE_BOTTLE)
            .name(lang.gui("menu.control_panel.item.progression.name"))

        // Check if claims are enabled in config
        val claimsEnabled = configService.loadConfig().claimsEnabled

        // Get actual progression data from ProgressionService with safe error handling
        try {
            
            val progression = progressionRepository.getGuildProgression(guild.id)
            if (progression != null) {
                val (experienceThisLevel, experienceForNextLevel) = progressionService.getLevelProgress(progression.totalExperience)
                val progressPercent = if (experienceForNextLevel > 0) {
                    ((experienceThisLevel.toDouble() / experienceForNextLevel.toDouble()) * 100).toInt()
                } else 100
                
                levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.level", "level" to progression.currentLevel))
                levelingItem.lore(lang.gui(
                    "menu.control_panel.item.progression.lore.progress",
                    "current" to experienceThisLevel,
                    "required" to experienceForNextLevel,
                    "percent" to progressPercent,
                ))
                
                // Show unlocked perks count
                val unlockedPerks = progressionService.getUnlockedPerks(guild.id)
                levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.unlocked", "count" to unlockedPerks.size))
            } else {
                addStartingProgressionLore(levelingItem)
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback if progression system has issues
            addStartingProgressionLore(levelingItem)
            levelingItem.lore(lang.gui("menu.common.blank"))
            levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.unavailable"))
        }
        
        levelingItem.lore(lang.gui("menu.common.blank"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn_header"))

        // Guild activities
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.bank"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.members"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.wars"))
        
        // Player activities
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.kills"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.farming"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.mining"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.crafting"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.brewing"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.enchanting"))

        // Only show claim-related XP if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.earn.claims"))
        }
        levelingItem.lore(lang.gui("menu.common.blank"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards_header"))

        // Bank rewards
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.balance"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.interest"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.fees"))
        
        // Home rewards
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.homes"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.teleport"))
        
        // Audio/Visual rewards
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.particles"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.sounds"))

        // Only show claim-related rewards if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.claim_blocks"))
            levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.rewards.claim_regeneration"))
        }
        levelingItem.lore(lang.gui("menu.common.blank"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.better_perks"))
        levelingItem.lore(lang.gui("menu.common.blank"))
        levelingItem.lore(lang.gui("menu.control_panel.item.progression.lore.refresh"))

        return levelingItem
    }

    private fun addStartingProgressionLore(item: ItemStack) {
        item.lore(lang.gui("menu.control_panel.item.progression.lore.starting_level"))
        item.lore(lang.gui("menu.control_panel.item.progression.lore.starting_progress"))
        item.lore(lang.gui("menu.control_panel.item.progression.lore.starting_perks"))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}


