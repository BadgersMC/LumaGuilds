package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GuildSettingsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                       private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val menuItemBuilder: MenuItemBuilder by inject()

    override fun open() {
        // Create 6x9 double chest GUI
        val gui = ChestGui(6, "§6Guild Settings - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
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
        // Guild name display (placeholder for now)
        val nameItem = ItemStack(Material.BOOK)
            .name("§f📖 GUILD NAME")
            .lore("§7Current: §f${guild.name}")
            .lore("§7")
            .lore("§7Name editing coming soon")
            .lore("§7Contact admin to change name")

        pane.addItem(GuiItem(nameItem), 0, 0)

        // Guild description (placeholder for now)
        val descItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§f📝 DESCRIPTION")
            .lore("§7Status: §cNot set")
            .lore("§7")
            .lore("§7Description editing coming soon")

        pane.addItem(GuiItem(descItem), 1, 0)

        // Guild creation date
        val localDateTime = guild.createdAt.atZone(ZoneId.systemDefault())
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        val createdItem = ItemStack(Material.CLOCK)
            .name("§f⏰ CREATED")
            .lore("§7Date: §f${localDateTime.format(dateFormatter)}")
            .lore("§7Time: §f${localDateTime.format(timeFormatter)}")

        pane.addItem(GuiItem(createdItem), 2, 0)

        // Guild leveling information
        val levelingItem = createLevelingInfoItem()
        pane.addItem(GuiItem(levelingItem), 3, 0)
    }

    private fun createLevelingInfoItem(): ItemStack {
        val levelingItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .name("§b⭐ GUILD PROGRESSION")

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
        
        // No system rewards currently

        // Only show claim-related rewards if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore("§7• §e📦 More claim blocks")
            levelingItem.lore("§7• §e⚡ Faster claim regeneration")
        }
        levelingItem.lore("§7")
        levelingItem.lore("§7Higher levels = §aBetter perks!")

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
                    .name("§f🏴 BANNER")
                    .lore("§7Status: §aSet")
                    .lore("§7Type: §f${bannerStack.type.name.lowercase().replace("_", " ")}")
                    .lore("§7")
                    .lore("§7Click to manage banner")
            } else {
                ItemStack(Material.WHITE_BANNER)
                    .name("§f🏴 BANNER")
                    .lore("§7Status: §cError loading banner")
                    .lore("§7")
                    .lore("§7Click to manage banner")
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
                .name("§f🏴 BANNER")
                .lore("§7Status: §cNot set")
                .lore("§7")
                .lore("§7Click to manage banner")
        }

        val bannerGuiItem = GuiItem(bannerItem) {
            menuNavigator.openMenu(GuildBannerMenu(menuNavigator, player, guild))
        }
        pane.addItem(bannerGuiItem, 0, 2)

        // Guild Emoji
        val emojiItem = ItemStack(Material.FIREWORK_STAR)
            .name("§f⭐ EMOJI")
            .lore("§7Current: §f${guild.emoji ?: "§cNot set"}")
            .lore("§7")
            .lore("§7Click to manage guild emoji")

        val emojiGuiItem = GuiItem(emojiItem) {
            menuNavigator.openMenu(GuildEmojiMenu(menuNavigator, player, guild))
        }
        pane.addItem(emojiGuiItem, 1, 2)

        // Guild Tag - NEW FEATURE
        val tagItem = ItemStack(Material.NAME_TAG)
            .name("§f🏷️ GUILD TAG")
            .lore("§7Current: §f${guild.tag ?: "§cNot set"}")
            .lore("§7")
            .lore("§7Click to edit guild tag")
            .lore("§7Supports MiniMessage formatting")

        val tagGuiItem = GuiItem(tagItem) {
            menuNavigator.openMenu(TagEditorMenu(menuNavigator, player, guild))
        }
        pane.addItem(tagGuiItem, 2, 2)

        // Preview section
        val currentTag = guild.tag ?: guild.name
        val previewItem = ItemStack(Material.PAPER)
            .name("§a🔍 PREVIEW")
            .lore("§7Chat appearance:")
            .lore("§7[${player.name}] $currentTag §7Hello!")

        pane.addItem(GuiItem(previewItem), 4, 2)
    }

    private fun addLocationModeSection(pane: StaticPane) {
        // Guild Home
        val homeItem = ItemStack(Material.COMPASS)
            .name("§f🏠 HOME LOCATION")
            .lore("§7Status: §f${if (guild.home != null) "Set" else "§cNot set"}")

        if (guild.home != null) {
            homeItem.lore("§7World: §f${guild.home!!.worldId}")
            homeItem.lore("§7Position: §f${guild.home!!.position.x.toInt()}, ${guild.home!!.position.y.toInt()}, ${guild.home!!.position.z.toInt()}")
            homeItem.lore("§7")
            homeItem.lore("§7Click to manage home")
        } else {
            homeItem.lore("§7Click to set home location")
        }

        val homeGuiItem = GuiItem(homeItem) {
            menuNavigator.openMenu(GuildHomeMenu(menuNavigator, player, guild))
        }
        pane.addItem(homeGuiItem, 0, 4)

        // Guild Members
        val membersItem = ItemStack(Material.PLAYER_HEAD)
            .name("§f👥 MANAGE MEMBERS")
            .lore("§7Invite and kick guild members")
            .lore("§7View member list with pagination")

        val membersGuiItem = GuiItem(membersItem) {
            menuNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(membersGuiItem, 2, 4)

        // Guild Mode
        val config = getKoin().get<ConfigService>().loadConfig()
        if (config.guild.peacefulModeEnabled) {
            val modeItem = ItemStack(
                if (guild.mode == GuildMode.PEACEFUL)
                    Material.GREEN_WOOL else Material.RED_WOOL
            )
                .name("§f⚔️ GUILD MODE")
                .lore("§7Current: §f${guild.mode.name}")
                .lore("§7")
                .lore("§7Peaceful: No PvP, safe trading")
                .lore("§7Hostile: PvP enabled, competitive")

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
                        modeItem.lore("§7")
                                .lore("§c⏰ Cannot switch to Hostile: ${days}d ${hours}h remaining")
                    }
                } else {
                    // Show peaceful switch cooldown
                    val peacefulCooldownEnd = modeChangedAt.plus(Duration.ofDays(config.guild.modeSwitchCooldownDays.toLong()))
                    if (Instant.now().isBefore(peacefulCooldownEnd)) {
                        val remaining = Duration.between(Instant.now(), peacefulCooldownEnd)
                        val days = remaining.toDays()
                        val hours = remaining.toHours() % 24
                        modeItem.lore("§7")
                                .lore("§c⏰ Cannot switch to Peaceful: ${days}d ${hours}h remaining")
                    }
                }
            }

            modeItem.lore("§7")
                    .lore("§eClick to change mode")

            val modeGuiItem = GuiItem(modeItem) {
                menuNavigator.openMenu(GuildModeMenu(menuNavigator, player, guild))
            }
            pane.addItem(modeGuiItem, 1, 4)
        } else {
            // Show disabled mode indicator
            val modeItem = ItemStack(Material.GRAY_WOOL)
                .name("§f⚔️ GUILD MODE")
                .lore("§7Current: §fHOSTILE")
                .lore("§7")
                .lore("§cMode switching disabled")
                .lore("§7All guilds are hostile by default")

            pane.addItem(GuiItem(modeItem), 1, 4)
        }

        // Back button
        val backItem = ItemStack(Material.BARRIER)
            .name("§c⬅️ BACK")
            .lore("§7Return to control panel")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 4, 5)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
