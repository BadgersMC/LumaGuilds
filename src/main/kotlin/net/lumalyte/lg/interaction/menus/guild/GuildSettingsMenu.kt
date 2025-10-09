package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.AdventureMenuHelper
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildSettingsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                       private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    override fun open() {
        // Create 6x9 double chest GUI with Adventure Components
        val title = AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>Guild Settings - ${guild.name}")
        val gui = ChestGui(6, title)
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0-1: Guild Information Section
        addGuildInfoSection(pane)

        // Row 2-3: Appearance Section
        addAppearanceSection(pane)

        // Row 4-5: Location & Mode Section
        addLocationModeSection(pane)

        gui.show(player)
    }

    private fun addGuildInfoSection(pane: StaticPane) {
        // Guild name editing
        val hasNamePermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_NAME)

        val nameItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<white>📖 GUILD NAME")
            .addAdventureLore(player, messageService, "<gray>Current: <white>${guild.name}")
            .addAdventureLore(player, messageService, "")

        if (hasNamePermission) {
            nameItem.addAdventureLore(player, messageService, "<yellow>Click to rename guild")
        } else {
            nameItem.addAdventureLore(player, messageService, "<red>Requires MANAGE_GUILD_NAME permission")
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (hasNamePermission) {
                openGuildNameEditor()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to rename the guild")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>You need the MANAGE_GUILD_NAME permission")
            }
        }
        pane.addItem(nameGuiItem, 0, 0)

        // Guild description
        val hasDescriptionPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)
        val currentDescription = guild.description

        val descItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<white>📝 DESCRIPTION")

        if (currentDescription != null) {
            descItem.addAdventureLore(player, messageService, "<gray>Status: <green>Set")
            descItem.addAdventureLore(player, messageService, "<gray>Current: <white>\"${parseMiniMessageForDisplay(currentDescription)}\"")
        } else {
            descItem.addAdventureLore(player, messageService, "<gray>Status: <red>Not set")
        }

        descItem.addAdventureLore(player, messageService, "")

        if (hasDescriptionPermission) {
            descItem.addAdventureLore(player, messageService, "<yellow>Click to edit description")
        } else {
            descItem.addAdventureLore(player, messageService, "<red>Requires MANAGE_DESCRIPTION permission")
        }

        val guiItem = GuiItem(descItem) {
            if (hasDescriptionPermission) {
                menuNavigator.openMenu(menuFactory.createDescriptionEditorMenu(menuNavigator, player, guild))
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage guild description")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>You need the MANAGE_DESCRIPTION permission")
            }
        }

        pane.addItem(guiItem, 1, 0)

        // Guild creation date
        val localDateTime = guild.createdAt.atZone(ZoneId.systemDefault())
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        val createdItem = ItemStack(Material.CLOCK)
            .setAdventureName(player, messageService, "<white>◷ CREATED")
            .addAdventureLore(player, messageService, "<gray>Date: <white>${localDateTime.format(dateFormatter)}")
            .addAdventureLore(player, messageService, "<gray>Time: <white>${localDateTime.format(timeFormatter)}")

        pane.addItem(GuiItem(createdItem), 2, 0)

        // Guild leveling information
        val levelingItem = createLevelingInfoItem()
        pane.addItem(GuiItem(levelingItem), 3, 0)
    }

    private fun createLevelingInfoItem(): ItemStack {
        val levelingItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .setAdventureName(player, messageService, "<aqua>☆ GUILD PROGRESSION")

        // Check if claims are enabled in config
        val configService = getKoin().get<ConfigService>()
        val claimsEnabled = configService.loadConfig().claimsEnabled

        // Get actual progression data from ProgressionService
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
        levelingItem.addAdventureLore(player, messageService, "<gray>")
        levelingItem.addAdventureLore(player, messageService, "<gold>📈 Earn Experience Points:")

        // Guild activities
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>💰 Bank deposits")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>👥 Guild member joins")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>⚔️ War victories")
        
        // Player activities
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>⚔ Player & mob kills")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>♣ Farming & fishing")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>⛏ Mining & building")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>⚒ Crafting & smelting")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <white>✦ Enchanting")

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
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>⌂ Additional home locations")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>⚡ Faster teleport cooldowns")

        // Audio/Visual rewards
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>✦ Special particle effects")
        levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>♪ Sound effects & announcements")
        
        // No system rewards currently

        // Only show claim-related rewards if claims are enabled
        if (claimsEnabled) {
            levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>📦 More claim blocks")
            levelingItem.addAdventureLore(player, messageService, "<gray>• <yellow>⚡ Faster claim regeneration")
        }
        levelingItem.addAdventureLore(player, messageService, "<gray>")
        levelingItem.addAdventureLore(player, messageService, "<gray>Higher levels = <green>Better perks!")

        return levelingItem
    }

    private fun addAppearanceSection(pane: StaticPane) {
        // Guild Banner
        val bannerItem = if (guild.banner != null) {
            // Try to deserialize and display current banner
            val bannerData = guild.banner!!
            val bannerStack = bannerData.deserializeToItemStack()
            if (bannerStack != null) {
                bannerStack.clone()
                    .setAdventureName(player, messageService, "<white>🏴 BANNER")
                    .addAdventureLore(player, messageService, "<gray>Status: <green>Set")
                    .addAdventureLore(player, messageService, "<gray>Type: <white>${bannerStack.type.name.lowercase().replace("_", " ")}")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<gray>Click to manage banner")
            } else {
                ItemStack(Material.WHITE_BANNER)
                    .setAdventureName(player, messageService, "<white>🏴 BANNER")
                    .addAdventureLore(player, messageService, "<gray>Status: <red>Error loading banner")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<gray>Click to manage banner")
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
                .setAdventureName(player, messageService, "<white>🏴 BANNER")
                .addAdventureLore(player, messageService, "<gray>Status: <red>Not set")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<gray>Click to manage banner")
        }

        val bannerGuiItem = GuiItem(bannerItem) {
            menuNavigator.openMenu(menuFactory.createGuildBannerMenu(menuNavigator, player, guild))
        }
        pane.addItem(bannerGuiItem, 0, 2)

        // Guild Emoji
        val emojiItem = ItemStack(Material.FIREWORK_STAR)
            .setAdventureName(player, messageService, "<white>☆ EMOJI")
            .addAdventureLore(player, messageService, "<gray>Current: <white>${guild.emoji ?: "<red>Not set"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Click to manage guild emoji")

        val emojiGuiItem = GuiItem(emojiItem) {
            menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
        }
        pane.addItem(emojiGuiItem, 1, 2)

        // Guild Tag - NEW FEATURE
        val tagItem = ItemStack(Material.NAME_TAG)
            .setAdventureName(player, messageService, "<white>🏷️ GUILD TAG")
            .addAdventureLore(player, messageService, "<gray>Current: <white>${guild.tag ?: "<red>Not set"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Click to edit guild tag")
            .addAdventureLore(player, messageService, "<gray>Supports MiniMessage formatting")

        val tagGuiItem = GuiItem(tagItem) {
            val menuFactory = MenuFactory()
            menuNavigator.openMenu(menuFactory.createTagEditorMenu(menuNavigator, player, guild))
        }
        pane.addItem(tagGuiItem, 2, 2)

        // Preview section
        val currentTag = guild.tag ?: guild.name
        val previewItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<green>🔍 PREVIEW")
            .addAdventureLore(player, messageService, "<gray>Chat appearance:")
            .addAdventureLore(player, messageService, "<gray>[${player.name}] $currentTag <gray>Hello!")

        pane.addItem(GuiItem(previewItem), 4, 2)
    }

    private fun addLocationModeSection(pane: StaticPane) {
        // Guild Home
        val homeItem = ItemStack(Material.COMPASS)
                .setAdventureName(player, messageService, "<white>🏠 HOME MANAGEMENT")

        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        homeItem.addAdventureLore(player, messageService, "<gray>Homes Set: <white>${allHomes.size}<gray>/${availableSlots}")
        homeItem.addAdventureLore(player, messageService, "<gray>")

        if (allHomes.hasHomes()) {
            allHomes.homes.entries.take(3).forEach { entry ->
                val name = entry.key
                val marker = if (name == "main") "<yellow>[MAIN]" else ""
                homeItem.addAdventureLore(player, messageService, "<gray>• <white>$name $marker")
            }
            if (allHomes.size > 3) {
                homeItem.addAdventureLore(player, messageService, "<gray>• <white>... and ${allHomes.size - 3} more")
            }
            homeItem.addAdventureLore(player, messageService, "<gray>")
            homeItem.addAdventureLore(player, messageService, "<yellow>Click to manage homes")
        } else {
            homeItem.addAdventureLore(player, messageService, "<gray>No homes set yet")
            homeItem.addAdventureLore(player, messageService, "<gray>")
            homeItem.addAdventureLore(player, messageService, "<yellow>Click to set first home")
        }

        if (allHomes.size < availableSlots) {
            homeItem.addAdventureLore(player, messageService, "<green>Available slots: <white>${availableSlots - allHomes.size}")
        }

        val homeGuiItem = GuiItem(homeItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }
        pane.addItem(homeGuiItem, 0, 4)

        // Guild Members
        val membersItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<white>👥 MANAGE MEMBERS")
            .addAdventureLore(player, messageService, "<gray>Invite and kick guild members")
            .addAdventureLore(player, messageService, "<gray>View member list with pagination")

        val membersGuiItem = GuiItem(membersItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(membersGuiItem, 2, 4)

        // Guild Mode
        val config = getKoin().get<ConfigService>().loadConfig()
        if (config.guild.peacefulModeEnabled) {
            val modeItem = ItemStack(
                if (guild.mode == GuildMode.PEACEFUL)
                    Material.GREEN_WOOL else Material.RED_WOOL
            )
                .setAdventureName(player, messageService, "<white>⚔ GUILD MODE")
                .addAdventureLore(player, messageService, "<gray>Current: <white>${guild.mode.name}")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<gray>Peaceful: No PvP, safe trading")
                .addAdventureLore(player, messageService, "<gray>Hostile: PvP enabled, competitive")

            // Add cooldown information
            val modeChangedAt = guild.modeChangedAt
            if (modeChangedAt != null) {
                if (guild.mode == GuildMode.PEACEFUL) {
                    // Show hostile switch cooldown
                    val hostileCooldownEnd = modeChangedAt.plus(Duration.ofDays(config.guild.hostileModeMinimumDays.toLong()))
                    if (Instant.now().isBefore(hostileCooldownEnd)) {
                        val remaining = Duration.between(Instant.now(), hostileCooldownEnd)
                        val days = remaining.toDays()
                        val hours = remaining.toHours() % 24
                        modeItem.addAdventureLore(player, messageService, "<gray>")
                                .addAdventureLore(player, messageService, "<red>◷ Cannot switch to Hostile: ${days}d ${hours}h remaining")
                    }
                } else {
                    // Show peaceful switch cooldown
                    val peacefulCooldownEnd = modeChangedAt.plus(Duration.ofDays(config.guild.modeSwitchCooldownDays.toLong()))
                    if (Instant.now().isBefore(peacefulCooldownEnd)) {
                        val remaining = Duration.between(Instant.now(), peacefulCooldownEnd)
                        val days = remaining.toDays()
                        val hours = remaining.toHours() % 24
                        modeItem.addAdventureLore(player, messageService, "<gray>")
                                .addAdventureLore(player, messageService, "<red>◷ Cannot switch to Peaceful: ${days}d ${hours}h remaining")
                    }
                }
            }

            modeItem.addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<yellow>Click to change mode")

            val modeGuiItem = GuiItem(modeItem) {
                menuNavigator.openMenu(menuFactory.createGuildModeMenu(menuNavigator, player, guild))
            }
            pane.addItem(modeGuiItem, 1, 4)
        } else {
            // Show disabled mode indicator
            val modeItem = ItemStack(Material.GRAY_WOOL)
                .setAdventureName(player, messageService, "<white>⚔ GUILD MODE")
                .addAdventureLore(player, messageService, "<gray>Current: <white>HOSTILE")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<red>Mode switching disabled")
                .addAdventureLore(player, messageService, "<gray>All guilds are hostile by default")

            pane.addItem(GuiItem(modeItem), 1, 4)
        }

        // Back button
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>⬅️ BACK")
            .addAdventureLore(player, messageService, "<gray>Return to control panel")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 4, 5)
    }

    private fun parseMiniMessageForDisplay(description: String?): String? {
        if (description == null) return null
        return try {
            val miniMessage = MiniMessage.miniMessage()
            val component = miniMessage.deserialize(description)
            val plainText = PlainTextComponentSerializer.plainText().serialize(component)
            plainText
        } catch (e: Exception) {
            description // Fallback to raw text if parsing fails
        }
    }

    /**
     * Open guild name editor menu
     */
    private fun openGuildNameEditor() {
        val confirmationMenu = object : Menu {
            override fun open() {
                val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Rename Guild"))
                AntiDupeUtil.protect(gui)

                val pane = StaticPane(0, 0, 9, 4)
                gui.addPane(pane)

                // Current name display
                val currentNameItem = ItemStack(Material.BOOK)
                    .setAdventureName(player, messageService, "<gold>Current Guild Name")
                    .addAdventureLore(player, messageService, "<gray>${guild.name}")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<gray>Type the new name in chat")
                    .addAdventureLore(player, messageService, "<gray>Requirements:")
                    .addAdventureLore(player, messageService, "<gray>• 1-32 characters")
                    .addAdventureLore(player, messageService, "<gray>• No inappropriate content")
                    .addAdventureLore(player, messageService, "<gray>• Cannot copy other guild names")

                pane.addItem(GuiItem(currentNameItem), 4, 0)

                // Warning about name copying
                val warningItem = ItemStack(Material.REDSTONE)
                    .setAdventureName(player, messageService, "<red>⚠️ Name Tag Protection")
                    .addAdventureLore(player, messageService, "<gray>Names cannot be copied from other guilds")
                    .addAdventureLore(player, messageService, "<gray>Even with different colors/formatting")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<gray>Examples of blocked names:")
                    .addAdventureLore(player, messageService, "<gray>• <red>Dawgz<white> (if <green>Dawgz<white> exists)")
                    .addAdventureLore(player, messageService, "<gray>• <gold><bold>ELITE<white> (if <green>Elite<white> exists)")

                pane.addItem(GuiItem(warningItem), 4, 1)

                // Confirm button (placeholder for now)
                val confirmItem = ItemStack(Material.GREEN_WOOL)
                    .setAdventureName(player, messageService, "<green>Enter New Name")
                    .addAdventureLore(player, messageService, "<gray>Type the new guild name in chat")
                    .addAdventureLore(player, messageService, "<gray>Then click this button to confirm")

                val confirmGuiItem = GuiItem(confirmItem) { event ->
                    event.isCancelled = true
                    AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Type the new guild name in chat...")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Requirements: 1-32 characters, no copying other guild names")
                    // This would trigger chat input handling
                }
                pane.addItem(confirmGuiItem, 3, 3)

                // Cancel button
                val cancelItem = ItemStack(Material.RED_WOOL)
                    .setAdventureName(player, messageService, "<red>Cancel")
                    .addAdventureLore(player, messageService, "<gray>Cancel name change")

                val cancelGuiItem = GuiItem(cancelItem) { event ->
                    event.isCancelled = true
                    menuNavigator.openMenu(this@GuildSettingsMenu)
                }
                pane.addItem(cancelGuiItem, 5, 3)

                gui.show(player)
            }

            override fun passData(data: Any?) = Unit
        }

        menuNavigator.openMenu(confirmationMenu)
    }}

