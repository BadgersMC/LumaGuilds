package net.lumalyte.lg.interaction.menus.guild

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
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildControlPanelMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val partyService: PartyService by inject()
    private val warService: WarService by inject()
    private val relationService: RelationService by inject()
    private val bankService: BankService by inject()
    private val killService: KillService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val playerId = player.uniqueId
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Guild Control Panel - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
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
            .setAdventureName(player, messageService, "<yellow>Guild Settings")
            .addAdventureLore(player, messageService, "<gray>Manage basic guild information")
            .addAdventureLore(player, messageService, "<gray>Name, description, and general settings")
        val guiItem = GuiItem(settingsItem) {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addEmojiSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val emoji = guildService.getEmoji(guild.id)
        val emojiItem = ItemStack(Material.NAME_TAG)
            .setAdventureName(player, messageService, "<light_purple>Guild Emoji")
            .lore("<gray>Current: ${emoji ?: "§cNot set"}")
            .addAdventureLore(player, messageService, "<gray>Set your guild's emoji for chat")
        val guiItem = GuiItem(emojiItem) {
            menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
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
                    .setAdventureName(player, messageService, "<aqua>Guild Banner")
                    .lore("<gray>Current: ${deserializedBanner.type.name.lowercase().replace("_", " ")}")
                    .addAdventureLore(player, messageService, "<gray>Choose your guild's banner")
            } else {
                // Fallback if deserialization fails
                ItemStack(Material.WHITE_BANNER)
                    .setAdventureName(player, messageService, "<aqua>Guild Banner")
                    .addAdventureLore(player, messageService, "<gray>Current: <red>Error loading banner")
                    .addAdventureLore(player, messageService, "<gray>Choose your guild's banner")
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
                .setAdventureName(player, messageService, "<aqua>Guild Banner")
                .addAdventureLore(player, messageService, "<gray>Current: <red>Not set")
                .addAdventureLore(player, messageService, "<gray>Choose your guild's banner")
        }
        val guiItem = GuiItem(bannerItem) {
            menuNavigator.openMenu(menuFactory.createGuildBannerMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addModeSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val modeItem = when (guild.mode) {
            GuildMode.PEACEFUL -> ItemStack(Material.GREEN_WOOL)
            GuildMode.HOSTILE -> ItemStack(Material.RED_WOOL)
        }
            .setAdventureName(player, messageService, "<red>Guild Mode")
            .addAdventureLore(player, messageService, "<gray>Current: <white>${guild.mode}")
            .addAdventureLore(player, messageService, "<gray>Peaceful = Cannot be attacked")
            .addAdventureLore(player, messageService, "<gray>Hostile = Can engage in wars")
        val guiItem = GuiItem(modeItem) {
            menuNavigator.openMenu(menuFactory.createGuildModeMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addHomeSettingsButton(pane: StaticPane, x: Int, y: Int) {
        val home = guildService.getHome(guild.id)
        val homeItem = ItemStack(Material.COMPASS)
            .setAdventureName(player, messageService, "<green>Guild Home")
            .lore(if (home != null) "<gray>Set at: ${home.position.x}, ${home.position.y}, ${home.position.z}" else "<red>Not set")
            .addAdventureLore(player, messageService, "<gray>Teleport point for /guild home")
        val guiItem = GuiItem(homeItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRankManagementButton(pane: StaticPane, x: Int, y: Int) {
        val rankCount = rankService.listRanks(guild.id).size
        val rankItem = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<gold>Rank Management")
            .addAdventureLore(player, messageService, "<gray>Manage guild ranks and permissions")
            .addAdventureLore(player, messageService, "<gray>Current ranks: <white>$rankCount")
        val guiItem = GuiItem(rankItem) {
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addMemberManagementButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val memberItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<aqua>Member Management")
            .addAdventureLore(player, messageService, "<gray>Manage guild members and ranks")
            .addAdventureLore(player, messageService, "<gray>Current members: <white>$memberCount")
        val guiItem = GuiItem(memberItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPartyManagementButton(pane: StaticPane, x: Int, y: Int) {
        val partyItem = ItemStack(Material.FIREWORK_ROCKET)
            .setAdventureName(player, messageService, "<light_purple>Party Management")
            .addAdventureLore(player, messageService, "<gray>Start parties and coordinate with allies")
            .addAdventureLore(player, messageService, "<gray>Invite other guilds to events")
        val guiItem = GuiItem(partyItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addWarManagementButton(pane: StaticPane, x: Int, y: Int) {
        val warItem = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<dark_red>War Management")
            .addAdventureLore(player, messageService, "<gray>Declare wars and manage conflicts")
            .addAdventureLore(player, messageService, "<gray>Propose truces and alliances")
        val guiItem = GuiItem(warItem) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRelationManagementButton(pane: StaticPane, x: Int, y: Int) {
        val relationItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<yellow>Relations")
            .addAdventureLore(player, messageService, "<gray>View alliances and rivalries")
            .addAdventureLore(player, messageService, "<gray>Manage diplomatic relations")
        val guiItem = GuiItem(relationItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBankManagementButton(pane: StaticPane, x: Int, y: Int) {
        val bankItem = ItemStack(Material.GOLD_BLOCK)
            .setAdventureName(player, messageService, "<gold>Guild Bank")
            .addAdventureLore(player, messageService, "<gray>Manage guild treasury")
            .addAdventureLore(player, messageService, "<gray>View transactions and balance")
        val guiItem = GuiItem(bankItem) {
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addStatisticsButton(pane: StaticPane, x: Int, y: Int) {
        val statsItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<green>Statistics")
            .addAdventureLore(player, messageService, "<gray>View guild performance metrics")
            .addAdventureLore(player, messageService, "<gray>Kills, deaths, wins, losses")
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
            // Fallback if progression system isn't available
            ItemStack(Material.EXPERIENCE_BOTTLE)
                .setAdventureName(player, messageService, "<aqua>⭐ GUILD PROGRESSION")
                .addAdventureLore(player, messageService, "<gray>Level: <yellow>1 <gray>(Starting Level)")
                .addAdventureLore(player, messageService, "<gray>XP Progress: <yellow>0<gray>/<yellow>800 <gray>(<green>0%<gray>)")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<red>⚠️ Progression system loading...")
                .addAdventureLore(player, messageService, "<gray>Try again in a moment")
        }
        
        val guiItem = GuiItem(progressionItem) {
            // Just refresh the menu for now - could add detailed progression menu later
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addInvitePlayerButton(pane: StaticPane, x: Int, y: Int) {
        val inviteItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<green>Invite Player")
            .addAdventureLore(player, messageService, "<gray>Send guild invitation to a player")
        val guiItem = GuiItem(inviteItem) {
            menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addKickPlayerButton(pane: StaticPane, x: Int, y: Int) {
        val kickItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>Kick Player")
            .addAdventureLore(player, messageService, "<gray>Remove a player from the guild")
        val guiItem = GuiItem(kickItem) {
            menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPromotePlayerButton(pane: StaticPane, x: Int, y: Int) {
        val promoteItem = ItemStack(Material.ANVIL)
            .setAdventureName(player, messageService, "<gold>Promote/Demote")
            .addAdventureLore(player, messageService, "<gray>Change member ranks")
        val guiItem = GuiItem(promoteItem) {
            menuNavigator.openMenu(menuFactory.createGuildPromotionMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addGuildInfoButton(pane: StaticPane, x: Int, y: Int) {
        val infoItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .setAdventureName(player, messageService, "<blue>Guild Info")
            .addAdventureLore(player, messageService, "<gray>Detailed information about your guild")
        val guiItem = GuiItem(infoItem) {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addMemberListButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val listItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Member List")
            .addAdventureLore(player, messageService, "<gray>View all guild members")
            .addAdventureLore(player, messageService, "<gray>Total: <white>$memberCount members")
        val guiItem = GuiItem(listItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberListMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRankListButton(pane: StaticPane, x: Int, y: Int) {
        val rankCount = rankService.listRanks(guild.id).size
        val listItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<gold>Rank List")
            .addAdventureLore(player, messageService, "<gray>View all guild ranks")
            .addAdventureLore(player, messageService, "<gray>Total: <white>$rankCount ranks")
        val guiItem = GuiItem(listItem) {
            menuNavigator.openMenu(menuFactory.createGuildRankListMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addDisbandGuildButton(pane: StaticPane, x: Int, y: Int) {
        val disbandItem = ItemStack(Material.TNT)
            .setAdventureName(player, messageService, "<dark_red><bold>DISBAND GUILD")
            .addAdventureLore(player, messageService, "<red><bold>PERMANENT ACTION")
            .addAdventureLore(player, messageService, "<gray>This will delete the guild forever")
            .addAdventureLore(player, messageService, "<gray>All members will be removed")
        val guiItem = GuiItem(disbandItem) {
            val menuFactory = MenuFactory()
            menuNavigator.openMenu(menuFactory.createGuildDisbandConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addLeaveGuildButton(pane: StaticPane, x: Int, y: Int) {
        val leaveItem = ItemStack(Material.DARK_OAK_DOOR)
            .setAdventureName(player, messageService, "<yellow>Leave Guild")
            .addAdventureLore(player, messageService, "<gray>Leave the guild")
            .addAdventureLore(player, messageService, "<gray>You can rejoin later if invited")
        val guiItem = GuiItem(leaveItem) {
            val menuFactory = MenuFactory()
            menuNavigator.openMenu(menuFactory.createGuildLeaveConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun createProgressionInfoItem(): ItemStack {
        val levelingItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .setAdventureName(player, messageService, "<aqua>⭐ GUILD PROGRESSION")

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
                
                levelingItem.addAdventureLore(player, messageService, "<gray>Level: <yellow>${progression.currentLevel}")
                levelingItem.addAdventureLore(player, messageService, "<gray>XP Progress: <yellow>$experienceThisLevel<gray>/<yellow>$experienceForNextLevel <gray>(<green>$progressPercent%<gray>)")
                
                // Show unlocked perks count
                val unlockedPerks = progressionService.getUnlockedPerks(guild.id)
                levelingItem.addAdventureLore(player, messageService, "<gray>Unlocked Perks: <green>${unlockedPerks.size}")
            } else {
                levelingItem.addAdventureLore(player, messageService, "<gray>Level: <yellow>1 <gray>(Starting Level)")
                levelingItem.addAdventureLore(player, messageService, "<gray>XP Progress: <yellow>0<gray>/<yellow>800 <gray>(<green>0%<gray>)")
                levelingItem.addAdventureLore(player, messageService, "<gray>Unlocked Perks: <green>2 <gray>(Basic perks)")
            }
        } catch (e: Exception) {
            // Fallback if progression system has issues
            levelingItem.addAdventureLore(player, messageService, "<gray>Level: <yellow>1 <gray>(Starting Level)")
            levelingItem.addAdventureLore(player, messageService, "<gray>XP Progress: <yellow>0<gray>/<yellow>800 <gray>(<green>0%<gray>)")
            levelingItem.addAdventureLore(player, messageService, "<gray>Unlocked Perks: <green>2 <gray>(Basic perks)")
            levelingItem.addAdventureLore(player, messageService, "<gray>")
            levelingItem.addAdventureLore(player, messageService, "<red>⚠️ Progression system unavailable")
        }
        
        levelingItem.addAdventureLore(player, messageService, "<gray>")
        levelingItem.addAdventureLore(player, messageService, "<gold>📈 Earn Experience Points:")

        // Guild activities
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>💰 Bank deposits")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>👥 Guild member joins")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>⚔️ War victories")
        
        // Player activities
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>🗡️ Player & mob kills")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>🌾 Farming & fishing")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>⛏️ Mining & building")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>🔨 Crafting & smelting")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>✨ Enchanting")

        // Only show claim-related XP if claims are enabled
        if (claimsEnabled) {
            levelingItem.addAdventureLore(player, messageService, "<gray>• <white>🏞️ Claiming land")
        }
        levelingItem.addAdventureLore(player, messageService, "<gray>")
        levelingItem.addAdventureLore(player, messageService, "<green>🎁 Level Up Rewards:")

        // Bank rewards
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>💰 Higher bank balance limits")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>💸 Better interest rates")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>💳 Reduced withdrawal fees")
        
        // Home rewards
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>🏠 Additional home locations")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>⚡ Faster teleport cooldowns")
        
        // Audio/Visual rewards
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>✨ Special particle effects")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>🔊 Sound effects & announcements")

        // Only show claim-related rewards if claims are enabled
        if (claimsEnabled) {
            levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>📦 More claim blocks")
            levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>⚡ Faster claim regeneration")
        }
        levelingItem.addAdventureLore(player, messageService, "<gray>")
        levelingItem.addAdventureLore(player, messageService, "<gray>Higher levels = <green>Better perks!")
        levelingItem.addAdventureLore(player, messageService, "<gray>")
        levelingItem.addAdventureLore(player, messageService, "<yellow>Click to refresh progression data")

        return levelingItem
    }}

