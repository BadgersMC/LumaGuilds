package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildSettingsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val menuItemBuilder: MenuItemBuilder,
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory,
    private val configService: ConfigService,
    private val progressionService: ProgressionService,
    private val progressionRepository: ProgressionRepository
): Menu, KoinComponent {

    private val lang: LangService by inject()

    override fun open() {
        // Refresh guild data from database to ensure we have latest changes
        guild = guildService.getGuild(guild.id) ?: guild

        // Create 6x9 double chest GUI
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.guild_settings.title", "guild" to guild.name)))
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
        val nameItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.guild_settings.item.name.name"))
            .lore(lang.legacy("menu.guild_settings.item.name.lore.current", "guild" to guild.name))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.name.lore.tip"))
            .lore(lang.legacy("menu.guild_settings.item.name.lore.chat"))

        pane.addItem(GuiItem(nameItem), 0, 0)

        // Guild description
        val hasDescriptionPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)
        val currentDescription = guild.description

        val descItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.guild_settings.item.description.name"))

        if (currentDescription != null) {
            descItem.lore(lang.legacy("menu.guild_settings.item.description.lore.set"))
                .lore(lang.legacy("menu.guild_settings.item.description.lore.current", "description" to parseMiniMessageForDisplay(currentDescription)))
        } else {
            descItem.lore(lang.legacy("menu.guild_settings.item.description.lore.not_set"))
        }

        descItem.lore(lang.legacy("menu.common.blank"))

        if (hasDescriptionPermission) {
            descItem.lore(lang.legacy("menu.guild_settings.item.description.lore.action"))
        } else {
            descItem.lore(lang.legacy("menu.guild_settings.item.description.lore.locked"))
        }

        val guiItem = GuiItem(descItem) {
            if (hasDescriptionPermission) {
                menuNavigator.openMenu(menuFactory.createDescriptionEditorMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_description_permission"))
                player.sendMessage(lang.msg("menu.guild_settings.feedback.description_requirement"))
            }
        }

        pane.addItem(guiItem, 1, 0)

        // Guild creation date
        val localDateTime = guild.createdAt.atZone(ZoneId.systemDefault())
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        val createdItem = ItemStack.of(Material.CLOCK)
            .name(lang.legacy("menu.guild_settings.item.created.name"))
            .lore(lang.legacy("menu.guild_settings.item.created.lore.date", "date" to localDateTime.format(dateFormatter)))
            .lore(lang.legacy("menu.guild_settings.item.created.lore.time", "time" to localDateTime.format(timeFormatter)))

        pane.addItem(GuiItem(createdItem), 2, 0)

        // Guild leveling information
        val levelingItem = createLevelingInfoItem()
        pane.addItem(GuiItem(levelingItem), 3, 0)
    }

    private fun createLevelingInfoItem(): ItemStack {
        val levelingItem = ItemStack.of(Material.EXPERIENCE_BOTTLE)
            .name(lang.legacy("menu.control_panel.item.progression.name"))

        // Check if claims are enabled in config
        val claimsEnabled = configService.loadConfig().claimsEnabled

        // Get actual progression data from ProgressionService
        
        val progression = progressionRepository.getGuildProgression(guild.id)
        if (progression != null) {
            val (experienceThisLevel, experienceForNextLevel) = progressionService.getLevelProgress(progression.totalExperience)
            val progressPercent = if (experienceForNextLevel > 0) {
                ((experienceThisLevel.toDouble() / experienceForNextLevel.toDouble()) * 100).toInt()
            } else 100
            
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.level", "level" to progression.currentLevel))
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.progress", "current" to experienceThisLevel, "required" to experienceForNextLevel, "percent" to progressPercent))
            
            // Show unlocked perks count
            val unlockedPerks = progressionService.getUnlockedPerks(guild.id)
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.unlocked", "count" to unlockedPerks.size))
        } else {
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.starting_level"))
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.starting_progress"))
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.starting_perks"))
        }
        levelingItem.lore(lang.legacy("menu.common.blank"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn_header"))

        // Guild activities
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.bank"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.members"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.wars"))
        
        // Player activities
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.kills"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.farming"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.mining"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.crafting"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.enchanting"))

        // Only show claim-related XP if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.earn.claims"))
        }
        levelingItem.lore(lang.legacy("menu.common.blank"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards_header"))

        // Bank rewards
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.balance"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.interest"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.fees"))
        
        // Home rewards
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.homes"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.teleport"))

        // Audio/Visual rewards
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.particles"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.sounds"))
        
        // No system rewards currently

        // Only show claim-related rewards if claims are enabled
        if (claimsEnabled) {
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.claim_blocks"))
            levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.rewards.claim_regeneration"))
        }
        levelingItem.lore(lang.legacy("menu.common.blank"))
        levelingItem.lore(lang.legacy("menu.control_panel.item.progression.lore.better_perks"))

        return levelingItem
    }

    private fun addAppearanceSection(pane: StaticPane) {
        // Guild Banner
        val bannerItem = guild.banner?.let { bannerData ->
            // Try to deserialize and display current banner
            val bannerStack = bannerData.deserializeToItemStack()
            if (bannerStack != null) {
                bannerStack.clone()
                    .name(lang.legacy("menu.guild_settings.item.banner.name"))
                    .lore(lang.legacy("menu.guild_settings.item.banner.lore.set"))
                    .lore(lang.legacy("menu.guild_settings.item.banner.lore.type", "type" to bannerStack.type.name.lowercase().replace("_", " ")))
                    .lore(lang.legacy("menu.common.blank"))
                    .lore(lang.legacy("menu.guild_settings.item.banner.lore.action"))
            } else {
                ItemStack.of(Material.WHITE_BANNER)
                    .name(lang.legacy("menu.guild_settings.item.banner.name"))
                    .lore(lang.legacy("menu.guild_settings.item.banner.lore.error"))
                    .lore(lang.legacy("menu.common.blank"))
                    .lore(lang.legacy("menu.guild_settings.item.banner.lore.action"))
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)
            .name(lang.legacy("menu.guild_settings.item.banner.name"))
            .lore(lang.legacy("menu.guild_settings.item.banner.lore.not_set"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.banner.lore.action"))

        val bannerGuiItem = GuiItem(bannerItem) {
            menuNavigator.openMenu(menuFactory.createGuildBannerMenu(menuNavigator, player, guild))
        }
        pane.addItem(bannerGuiItem, 0, 2)

        // Guild Emoji
        val emojiItem = ItemStack.of(Material.FIREWORK_STAR)
            .name(lang.legacy("menu.guild_settings.item.emoji.name"))
            .lore(lang.legacy("menu.guild_settings.item.emoji.lore.current", "emoji" to (guild.emoji ?: lang.raw("menu.control_panel.state.not_set"))))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.emoji.lore.action"))

        val emojiGuiItem = GuiItem(emojiItem) {
            menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
        }
        pane.addItem(emojiGuiItem, 1, 2)

        // Guild Tag - NEW FEATURE
        val tagItem = ItemStack.of(Material.NAME_TAG)
            .name(lang.legacy("menu.guild_settings.item.tag.name"))
            .lore(lang.legacy("menu.guild_settings.item.tag.lore.current", "tag" to (guild.tag ?: lang.raw("menu.control_panel.state.not_set"))))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.tag.lore.action"))
            .lore(lang.legacy("menu.guild_settings.item.tag.lore.formatting"))

        val tagGuiItem = GuiItem(tagItem) {
            menuNavigator.openMenu(menuFactory.createTagEditorMenu(menuNavigator, player, guild))
        }
        pane.addItem(tagGuiItem, 2, 2)

        // Preview section
        val currentTag = guild.tag ?: guild.name
        val previewItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.guild_settings.item.preview.name"))
            .lore(lang.legacy("menu.guild_settings.item.preview.lore.description"))
            .lore(lang.legacy("menu.guild_settings.item.preview.lore.example", "player" to player.name, "tag" to currentTag))

        pane.addItem(GuiItem(previewItem), 4, 2)

        // GUI Theme Selector
        val themeItem = ItemStack.of(Material.PAINTING)
            .name(lang.legacy("menu.guild_settings.item.theme.name"))
            .lore(lang.legacy("menu.guild_settings.item.theme.lore.current", "theme" to guild.guiTheme.displayName))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.theme.lore.description"))
            .lore(lang.legacy("menu.guild_settings.item.theme.lore.details"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.theme.lore.action"))

        val themeGuiItem = GuiItem(themeItem) {
            if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)) {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_settings_permission"))
                return@GuiItem
            }
            openThemeSelector()
        }
        pane.addItem(themeGuiItem, 5, 2)
    }

    private fun addLocationModeSection(pane: StaticPane) {
        // Guild Home
        val homeItem = ItemStack.of(Material.COMPASS)
                .name(lang.legacy("menu.guild_settings.item.homes.name"))

        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        homeItem.lore(lang.legacy("menu.guild_settings.item.homes.lore.count", "count" to allHomes.size, "available" to availableSlots))
        homeItem.lore(lang.legacy("menu.common.blank"))

        if (allHomes.hasHomes()) {
            allHomes.homes.entries.take(3).forEach { entry ->
                val name = entry.key
                homeItem.lore(if (name == "main") {
                    lang.legacy("menu.guild_settings.item.homes.lore.home_main", "home" to name)
                } else {
                    lang.legacy("menu.guild_settings.item.homes.lore.home", "home" to name)
                })
            }
            if (allHomes.size > 3) {
                homeItem.lore(lang.legacy("menu.guild_settings.item.homes.lore.more", "count" to allHomes.size - 3))
            }
            homeItem.lore(lang.legacy("menu.common.blank"))
            homeItem.lore(lang.legacy("menu.guild_settings.item.homes.lore.manage"))
        } else {
            homeItem.lore(lang.legacy("menu.guild_settings.item.homes.lore.none"))
            homeItem.lore(lang.legacy("menu.common.blank"))
            homeItem.lore(lang.legacy("menu.guild_settings.item.homes.lore.first"))
        }

        if (allHomes.size < availableSlots) {
            homeItem.lore(lang.legacy("menu.guild_settings.item.homes.lore.slots", "count" to availableSlots - allHomes.size))
        }

        val homeGuiItem = GuiItem(homeItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }
        pane.addItem(homeGuiItem, 0, 4)

        // Guild Open/Closed Toggle
        val openClosedItem = ItemStack.of(
            if (guild.isOpen) Material.LIME_DYE else Material.GRAY_DYE
        )
            .name(lang.legacy("menu.guild_settings.item.access.name"))
            .lore(if (guild.isOpen) lang.legacy("menu.guild_settings.item.access.lore.current.open") else lang.legacy("menu.guild_settings.item.access.lore.current.closed"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.access.lore.open"))
            .lore(lang.legacy("menu.guild_settings.item.access.lore.closed"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.access.lore.action"))

        val hasPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)
        val openClosedGuiItem = GuiItem(openClosedItem) {
            if (!hasPermission) {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_access_permission"))
                player.sendMessage(lang.msg("menu.guild_settings.feedback.settings_requirement"))
                return@GuiItem
            }

            // Toggle the isOpen status
            val newIsOpen = !guild.isOpen
            val success = guildService.setOpen(guild.id, newIsOpen, player.uniqueId)

            if (success) {
                guild = guild.copy(isOpen = newIsOpen)
                player.sendMessage(if (newIsOpen) lang.msg("menu.guild_settings.feedback.access_open") else lang.msg("menu.guild_settings.feedback.access_closed"))
                player.sendMessage(if (newIsOpen) lang.msg("menu.guild_settings.feedback.access_open_description") else lang.msg("menu.guild_settings.feedback.access_closed_description"))

                // Reopen the menu to show updated status
                open()
            } else {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.access_failure"))
            }
        }
        pane.addItem(openClosedGuiItem, 1, 4)

        // Lunar Tracking Toggle
        val trackingItem = ItemStack.of(
            if (guild.trackingEnabled) Material.RECOVERY_COMPASS else Material.COMPASS
        )
            .name(lang.legacy("menu.guild_settings.item.tracking.name"))
            .lore(if (guild.trackingEnabled) lang.legacy("menu.guild_settings.item.tracking.lore.current.enabled") else lang.legacy("menu.guild_settings.item.tracking.lore.current.disabled"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.tracking.lore.enabled"))
            .lore(lang.legacy("menu.guild_settings.item.tracking.lore.hud"))
            .lore(lang.legacy("menu.guild_settings.item.tracking.lore.disabled"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_settings.item.tracking.lore.action"))

        val trackingGuiItem = GuiItem(trackingItem) {
            if (!hasPermission) {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_settings_permission"))
                player.sendMessage(lang.msg("menu.guild_settings.feedback.settings_requirement"))
                return@GuiItem
            }

            val newTracking = !guild.trackingEnabled
            val success = guildService.setTrackingEnabled(guild.id, newTracking, player.uniqueId)

            if (success) {
                guild = guild.copy(trackingEnabled = newTracking)
                player.sendMessage(if (newTracking) lang.msg("menu.guild_settings.feedback.tracking_enabled") else lang.msg("menu.guild_settings.feedback.tracking_disabled"))
                open()
            } else {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.tracking_failure"))
            }
        }
        pane.addItem(trackingGuiItem, 4, 4)

        // Guild Members
        val membersItem = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.legacy("menu.guild_settings.item.members.name"))
            .lore(lang.legacy("menu.guild_settings.item.members.lore.description"))
            .lore(lang.legacy("menu.guild_settings.item.members.lore.details"))

        val membersGuiItem = GuiItem(membersItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(membersGuiItem, 2, 4)

        // Guild Mode
        val config = configService.loadConfig()
        if (config.guild.peacefulModeEnabled) {
            val modeItem = ItemStack.of(
                if (guild.mode == GuildMode.PEACEFUL)
                    Material.GREEN_WOOL else Material.RED_WOOL
            )
                .name(lang.legacy("menu.guild_settings.item.mode.name"))
                .lore(if (guild.mode == GuildMode.PEACEFUL) lang.legacy("menu.guild_settings.item.mode.lore.current.peaceful") else lang.legacy("menu.guild_settings.item.mode.lore.current.hostile"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.guild_settings.item.mode.lore.peaceful"))
                .lore(lang.legacy("menu.guild_settings.item.mode.lore.hostile"))

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
                        modeItem.lore(lang.legacy("menu.common.blank"))
                                .lore(lang.legacy("menu.guild_settings.item.mode.lore.hostile_cooldown", "days" to days, "hours" to hours))
                    }
                } else {
                    // Show peaceful switch cooldown
                    val peacefulCooldownEnd = modeChangedAt.plus(Duration.ofDays(config.guild.modeSwitchCooldownDays.toLong()))
                    if (Instant.now().isBefore(peacefulCooldownEnd)) {
                        val remaining = Duration.between(Instant.now(), peacefulCooldownEnd)
                        val days = remaining.toDays()
                        val hours = remaining.toHours() % 24
                        modeItem.lore(lang.legacy("menu.common.blank"))
                                .lore(lang.legacy("menu.guild_settings.item.mode.lore.peaceful_cooldown", "days" to days, "hours" to hours))
                    }
                }
            }

            modeItem.lore(lang.legacy("menu.common.blank"))
                    .lore(lang.legacy("menu.guild_settings.item.mode.lore.action"))

            val modeGuiItem = GuiItem(modeItem) {
                menuNavigator.openMenu(menuFactory.createGuildModeMenu(menuNavigator, player, guild))
            }
            pane.addItem(modeGuiItem, 3, 4)
        } else {
            // Show disabled mode indicator
            val modeItem = ItemStack.of(Material.GRAY_WOOL)
                .name(lang.legacy("menu.guild_settings.item.mode.name"))
                .lore(lang.legacy("menu.guild_settings.item.mode.lore.current.hostile"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.guild_settings.item.mode.lore.disabled"))
                .lore(lang.legacy("menu.guild_settings.item.mode.lore.default_hostile"))

            pane.addItem(GuiItem(modeItem), 3, 4)
        }

        // Back button
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.guild_settings.item.back.name"))
            .lore(lang.legacy("menu.guild_settings.item.back.lore"))

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
            // Convert to legacy formatting for menu display
            val legacyText = LegacyComponentSerializer.legacySection().serialize(component)
            legacyText
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            description // Fallback to raw text if parsing fails
        }
    }

    /**
     * Opens a small sub-menu showing all available GUI themes.
     * The player clicks one to apply it; the settings menu then reopens
     * with the new theme applied.
     */
    private fun openThemeSelector() {
        val gui = ChestGui(1, MenuTitleBuilder.build(guild.guiTheme, 1, lang.legacy("menu.guild_settings.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 1)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.addPane(pane)

        val themes = net.lumalyte.lg.utils.GuiTheme.entries
        // Place up to 6 themes in a single row; each takes 1 slot
        // with a gap between them for visual clarity.
        themes.forEachIndexed { index, theme ->
            val isCurrent = theme == guild.guiTheme
            val slot = index * 1 + index  // 0, 2, 4, 6, 8, 10 — but max 6 in 9 slots
            // Recalculate: 9 slots, 6 themes, spread evenly
            val pos = if (themes.size <= 9) index else index * 9 / themes.size

            val item = ItemStack.of(
                when {
                    isCurrent -> Material.GREEN_STAINED_GLASS_PANE
                    else -> Material.GRAY_STAINED_GLASS_PANE
                }
            )
                .name(if (isCurrent) lang.legacy("menu.guild_settings.item.theme_option.name.current", "theme" to theme.displayName) else lang.legacy("menu.guild_settings.item.theme_option.name.available", "theme" to theme.displayName))
                .lore(if (isCurrent) lang.legacy("menu.guild_settings.item.theme_option.lore.current") else lang.legacy("menu.guild_settings.item.theme_option.lore.apply"))

            pane.addItem(GuiItem(item) {
                if (!isCurrent) {
                    guildService.setGuiTheme(guild.id, theme, player.uniqueId)
                    guild = guild.copy(guiTheme = theme)
                    player.sendMessage(lang.msg("menu.guild_settings.feedback.theme_changed", "theme" to theme.displayName))
                    open()
                }
            }, pos, 0)
        }

        // Back button at the last slot
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.guild_settings.item.back.name"))
        pane.addItem(GuiItem(backItem) { open() }, 8, 0)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

