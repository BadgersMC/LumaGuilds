package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
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

class GuildControlPanelMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val partyService: PartyService by inject()
    private val warService: WarService by inject()
    private val relationService: RelationService by inject()
    private val bankService: BankService by inject()
    private val killService: KillService by inject()

    override fun open() {
        val playerId = player.uniqueId
        val gui = ChestGui(6, "§6Guild Control Panel - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Row 1: Core Settings
        addGuildSettingsButton(pane, 0, 0)
        addEmojiSettingsButton(pane, 1, 0)
        addBannerSettingsButton(pane, 2, 0)
        addModeSettingsButton(pane, 3, 0)
        addHomeSettingsButton(pane, 4, 0)

        // Row 2: Management
        addRankManagementButton(pane, 0, 1)
        addMemberManagementButton(pane, 1, 1)
        addPartyManagementButton(pane, 2, 1)
        addWarManagementButton(pane, 3, 1)
        addRelationManagementButton(pane, 4, 1)

        // Row 3: Economy & Stats
        addBankManagementButton(pane, 0, 2)
        addStatisticsButton(pane, 1, 2)
        addProgressionInfoButton(pane, 2, 2)

        // Row 4: Quick Actions
        addInvitePlayerButton(pane, 0, 3)
        addKickPlayerButton(pane, 1, 3)
        addPromotePlayerButton(pane, 2, 3)

        // Row 5: Information
        addGuildInfoButton(pane, 0, 4)
        addMemberListButton(pane, 1, 4)
        addRankListButton(pane, 2, 4)

        // Row 6: Danger Zone
        addDisbandGuildButton(pane, 0, 5)
        addLeaveGuildButton(pane, 8, 5)

        gui.show(player)
    }

    private fun addGuildSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val settingsItem = ItemStack(Material.COMMAND_BLOCK)
            .name("§eGuild Settings")
            .lore("§7Manage basic guild information")
            .lore("§7Name, description, and general settings")
        val guiItem = GuiItem(settingsItem) {
            menuNavigator.openMenu(GuildSettingsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addEmojiSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val emoji = guildService.getEmoji(guild.id)
        val emojiItem = ItemStack(Material.NAME_TAG)
            .name("§dGuild Emoji")
            .lore("§7Current: ${emoji ?: "§cNot set"}")
            .lore("§7Set your guild's emoji for chat")
        val guiItem = GuiItem(emojiItem) {
            menuNavigator.openMenu(GuildEmojiMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBannerSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val bannerItem = if (guild.banner != null) {
            // Try to deserialize the banner
            val bannerData = guild.banner!!
            val deserializedBanner = bannerData.deserializeToItemStack()
            if (deserializedBanner != null) {
                deserializedBanner.clone()
                    .name("§bGuild Banner")
                    .lore("§7Current: ${deserializedBanner.type.name.lowercase().replace("_", " ")}")
                    .lore("§7Choose your guild's banner")
            } else {
                // Fallback if deserialization fails
                ItemStack(Material.WHITE_BANNER)
                    .name("§bGuild Banner")
                    .lore("§7Current: §cError loading banner")
                    .lore("§7Choose your guild's banner")
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
                .name("§bGuild Banner")
                .lore("§7Current: §cNot set")
                .lore("§7Choose your guild's banner")
        }
        val guiItem = GuiItem(bannerItem) {
            menuNavigator.openMenu(GuildBannerMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addModeSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val modeItem = when (guild.mode) {
            GuildMode.PEACEFUL -> ItemStack(Material.GREEN_WOOL)
            GuildMode.HOSTILE -> ItemStack(Material.RED_WOOL)
        }
            .name("§cGuild Mode")
            .lore("§7Current: §f${guild.mode}")
            .lore("§7Peaceful = Cannot be attacked")
            .lore("§7Hostile = Can engage in wars")
        val guiItem = GuiItem(modeItem) {
            menuNavigator.openMenu(GuildModeMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addHomeSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val home = guildService.getHome(guild.id)
        val homeItem = ItemStack(Material.COMPASS)
            .name("§aGuild Home")
            .lore(if (home != null) "§7Set at: ${home.position.x}, ${home.position.y}, ${home.position.z}" else "§cNot set")
            .lore("§7Teleport point for /guild home")
        val guiItem = GuiItem(homeItem) {
            menuNavigator.openMenu(GuildHomeMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRankManagementButton(pane: StaticPane, x: Int, y: Int) {
        val rankCount = rankService.listRanks(guild.id).size
        val rankItem = ItemStack(Material.IRON_SWORD)
            .name("§6Rank Management")
            .lore("§7Manage guild ranks and permissions")
            .lore("§7Current ranks: §f$rankCount")
        val guiItem = GuiItem(rankItem) {
            menuNavigator.openMenu(GuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addMemberManagementButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val memberItem = ItemStack(Material.PLAYER_HEAD)
            .name("§bMember Management")
            .lore("§7Manage guild members and ranks")
            .lore("§7Current members: §f$memberCount")
        val guiItem = GuiItem(memberItem) {
            menuNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPartyManagementButton(pane: StaticPane, x: Int, y: Int) {
        val partyItem = ItemStack(Material.FIREWORK_ROCKET)
            .name("§dParty Management")
            .lore("§7Start parties and coordinate with allies")
            .lore("§7Invite other guilds to events")
        val guiItem = GuiItem(partyItem) {
            menuNavigator.openMenu(GuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addWarManagementButton(pane: StaticPane, x: Int, y: Int) {
        val warItem = ItemStack(Material.DIAMOND_SWORD)
            .name("§4War Management")
            .lore("§7Declare wars and manage conflicts")
            .lore("§7Propose truces and alliances")
        val guiItem = GuiItem(warItem) {
            menuNavigator.openMenu(GuildWarManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRelationManagementButton(pane: StaticPane, x: Int, y: Int) {
        val relationItem = ItemStack(Material.BOOK)
            .name("§eRelations")
            .lore("§7View alliances and rivalries")
            .lore("§7Manage diplomatic relations")
        val guiItem = GuiItem(relationItem) {
            menuNavigator.openMenu(GuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBankManagementButton(pane: StaticPane, x: Int, y: Int) {
        val bankItem = ItemStack(Material.GOLD_BLOCK)
            .name("§6Guild Bank")
            .lore("§7Manage guild treasury")
            .lore("§7View transactions and balance")
        val guiItem = GuiItem(bankItem) {
            menuNavigator.openMenu(GuildBankMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addStatisticsButton(pane: StaticPane, x: Int, y: Int) {
        val statsItem = ItemStack(Material.BOOKSHELF)
            .name("§aStatistics")
            .lore("§7View guild performance metrics")
            .lore("§7Kills, deaths, wins, losses")
        val guiItem = GuiItem(statsItem) {
            menuNavigator.openMenu(GuildStatisticsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addProgressionInfoButton(pane: StaticPane, x: Int, y: Int) {
        // Create progression info item with safe error handling
        val progressionItem = try {
            createProgressionInfoItem()
        } catch (e: Exception) {
            // Fallback if progression system isn't available
            ItemStack(Material.EXPERIENCE_BOTTLE)
                .name("§b⭐ GUILD PROGRESSION")
                .lore("§7Level: §e1 §7(Starting Level)")
                .lore("§7XP Progress: §e0§7/§e800 §7(§a0%§7)")
                .lore("§7")
                .lore("§c⚠️ Progression system loading...")
                .lore("§7Try again in a moment")
        }
        
        val guiItem = GuiItem(progressionItem) {
            // Just refresh the menu for now - could add detailed progression menu later
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addInvitePlayerButton(pane: StaticPane, x: Int, y: Int) {
        val inviteItem = ItemStack(Material.PAPER)
            .name("§aInvite Player")
            .lore("§7Send guild invitation to a player")
        val guiItem = GuiItem(inviteItem) {
            menuNavigator.openMenu(GuildInviteMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addKickPlayerButton(pane: StaticPane, x: Int, y: Int) {
        val kickItem = ItemStack(Material.BARRIER)
            .name("§cKick Player")
            .lore("§7Remove a player from the guild")
        val guiItem = GuiItem(kickItem) {
            menuNavigator.openMenu(GuildKickMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPromotePlayerButton(pane: StaticPane, x: Int, y: Int) {
        val promoteItem = ItemStack(Material.ANVIL)
            .name("§6Promote/Demote")
            .lore("§7Change member ranks")
        val guiItem = GuiItem(promoteItem) {
            menuNavigator.openMenu(GuildPromotionMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addGuildInfoButton(pane: StaticPane, x: Int, y: Int) {
        val infoItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .name("§9Guild Info")
            .lore("§7Detailed information about your guild")
        val guiItem = GuiItem(infoItem) {
            menuNavigator.openMenu(GuildInfoMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addMemberListButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val listItem = ItemStack(Material.BOOK)
            .name("§bMember List")
            .lore("§7View all guild members")
            .lore("§7Total: §f$memberCount members")
        val guiItem = GuiItem(listItem) {
            menuNavigator.openMenu(GuildMemberListMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRankListButton(pane: StaticPane, x: Int, y: Int) {
        val rankCount = rankService.listRanks(guild.id).size
        val listItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§6Rank List")
            .lore("§7View all guild ranks")
            .lore("§7Total: §f$rankCount ranks")
        val guiItem = GuiItem(listItem) {
            menuNavigator.openMenu(GuildRankListMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addDisbandGuildButton(pane: StaticPane, x: Int, y: Int) {
        val disbandItem = ItemStack(Material.TNT)
            .name("§4§lDISBAND GUILD")
            .lore("§c§lPERMANENT ACTION")
            .lore("§7This will delete the guild forever")
            .lore("§7All members will be removed")
        val guiItem = GuiItem(disbandItem) {
            menuNavigator.openMenu(GuildDisbandConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addLeaveGuildButton(pane: StaticPane, x: Int, y: Int) {
        val leaveItem = ItemStack(Material.DARK_OAK_DOOR)
            .name("§eLeave Guild")
            .lore("§7Leave the guild")
            .lore("§7You can rejoin later if invited")
        val guiItem = GuiItem(leaveItem) {
            menuNavigator.openMenu(GuildLeaveConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun createProgressionInfoItem(): ItemStack {
        val levelingItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .name("§b⭐ GUILD PROGRESSION")

        // Check if claims are enabled in config
        val configService = getKoin().get<ConfigService>()
        val claimsEnabled = configService.loadConfig().claimsEnabled

        // Get actual progression data from ProgressionService with safe error handling
        try {
            val progressionService = getKoin().get<ProgressionService>()
            val progressionRepository = getKoin().get<ProgressionRepository>()
            
            val progression = progressionRepository.getGuildProgression(guild.id)
            if (progression != null) {
                val (experienceThisLevel, experienceForNextLevel) = progressionService.getLevelProgress(progression.totalExperience)
                val progressPercent = if (experienceForNextLevel > 0) {
                    ((experienceThisLevel.toDouble() / experienceForNextLevel.toDouble()) * 100).toInt()
                } else 100
                
                levelingItem.lore("§7Level: §e${progression.currentLevel}")
                levelingItem.lore("§7XP Progress: §e$experienceThisLevel§7/§e$experienceForNextLevel §7(§a$progressPercent%§7)")
                
                // Show unlocked perks count
                val unlockedPerks = progressionService.getUnlockedPerks(guild.id)
                levelingItem.lore("§7Unlocked Perks: §a${unlockedPerks.size}")
            } else {
                levelingItem.lore("§7Level: §e1 §7(Starting Level)")
                levelingItem.lore("§7XP Progress: §e0§7/§e800 §7(§a0%§7)")
                levelingItem.lore("§7Unlocked Perks: §a2 §7(Basic perks)")
            }
        } catch (e: Exception) {
            // Fallback if progression system has issues
            levelingItem.lore("§7Level: §e1 §7(Starting Level)")
            levelingItem.lore("§7XP Progress: §e0§7/§e800 §7(§a0%§7)")
            levelingItem.lore("§7Unlocked Perks: §a2 §7(Basic perks)")
            levelingItem.lore("§7")
            levelingItem.lore("§c⚠️ Progression system unavailable")
        }
        
        levelingItem.lore("§7")
        levelingItem.lore("§6📈 Earn Experience Points:")

        // Guild activities
        levelingItem.lore("§7• §f💰 Bank deposits")
        levelingItem.lore("§7• §f👥 Guild member joins")
        levelingItem.lore("§7• §f⚔️ War victories")
        
        // Player activities
        levelingItem.lore("§7• §f🗡️ Player & mob kills")
        levelingItem.lore("§7• §f🌾 Farming & fishing")
        levelingItem.lore("§7• §f⛏️ Mining & building")
        levelingItem.lore("§7• §f🔨 Crafting & smelting")
        levelingItem.lore("§7• §f✨ Enchanting")

        // Only show claim-related XP if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore("§7• §f🏞️ Claiming land")
        }
        levelingItem.lore("§7")
        levelingItem.lore("§a🎁 Level Up Rewards:")

        // Bank rewards
        levelingItem.lore("§7• §e💰 Higher bank balance limits")
        levelingItem.lore("§7• §e💸 Better interest rates")
        levelingItem.lore("§7• §e💳 Reduced withdrawal fees")
        
        // Home rewards
        levelingItem.lore("§7• §e🏠 Additional home locations")
        levelingItem.lore("§7• §e⚡ Faster teleport cooldowns")
        
        // Audio/Visual rewards
        levelingItem.lore("§7• §e✨ Special particle effects")
        levelingItem.lore("§7• §e🔊 Sound effects & announcements")

        // Only show claim-related rewards if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore("§7• §e📦 More claim blocks")
            levelingItem.lore("§7• §e⚡ Faster claim regeneration")
        }
        levelingItem.lore("§7")
        levelingItem.lore("§7Higher levels = §aBetter perks!")
        levelingItem.lore("§7")
        levelingItem.lore("§eClick to refresh progression data")

        return levelingItem
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
