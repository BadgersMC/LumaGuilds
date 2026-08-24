package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarObjective
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import java.time.Duration as JavaDuration
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.util.*

class GuildWarDeclarationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var targetGuild: Guild? = null
) : Menu, KoinComponent, ChatInputHandler {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val guildRepository: GuildRepository by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val configService: ConfigService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()
    
    // War configuration state
    private var selectedDuration: Duration = Duration.ofDays(7) // Default 7 days
    private var selectedObjectives: MutableSet<WarObjective> = mutableSetOf()
    private var warTerms: String? = null
    private var inputMode: String? = null // Track what input mode we're in ("war_terms")
    private var wagerAmount: Int = 0 // War pot amount

    override fun open() {
        // Check permissions first
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("menu.war_declaration.feedback.no_permission"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Check if guild is in peaceful mode
        if (guild.mode == GuildMode.PEACEFUL) {
            player.sendMessage(lang.msg("menu.war_declaration.feedback.peaceful"))
            player.sendMessage(lang.msg("menu.war_declaration.feedback.switch_hostile"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(
            guild.guiTheme,
            6,
            lang.legacy("menu.war_declaration.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        if (targetGuild == null) {
            // Show guild selection
            addGuildSelectionSection(pane)
        } else {
            // Show war configuration
            addWarConfigurationSection(pane)
        }

        // Navigation
        addBackButton(pane, 8, 5)

        gui.show(player)
    }

    private fun addGuildSelectionSection(pane: StaticPane) {
        // Title
        val titleItem = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.legacy("menu.war_declaration.item.select_target.name"))
            .lore(lang.legacy("menu.war_declaration.item.select_target.lore.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.select_target.lore.warning"))
        pane.addItem(GuiItem(titleItem), 4, 0)

        // Get all guilds except own guild
        val availableGuilds = guildRepository.getAll()
            .filter { it.id != guild.id }
            .filter { warService.getCurrentWarBetweenGuilds(guild.id, it.id)?.isActive != true }
            .sortedBy { it.name }

        // Display guilds (first 7 slots in row 2)
        availableGuilds.take(7).forEachIndexed { index, targetGuild ->
            val guildItem = createGuildSelectionItem(targetGuild)
            val guiItem = GuiItem(guildItem) {
                selectTargetGuild(targetGuild)
            }
            pane.addItem(guiItem, index + 1, 2)
        }

        // Show "More Guilds" if there are more than 7
        if (availableGuilds.size > 7) {
            val moreItem = ItemStack.of(Material.BOOK)
                .name(lang.legacy("menu.war_declaration.item.more_guilds.name", "count" to availableGuilds.size - 7))
                .lore(lang.legacy("menu.war_declaration.item.more_guilds.lore"))
            val guiItem = GuiItem(moreItem) {
                openGuildListMenu(availableGuilds)
            }
            pane.addItem(guiItem, 8, 2)
        }

        // Info section
        val infoItem = ItemStack.of(Material.KNOWLEDGE_BOOK)
            .name(lang.legacy("menu.war_declaration.item.info.name"))
            .lore(lang.legacy("menu.war_declaration.item.info.lore.duration"))
            .lore(lang.legacy("menu.war_declaration.item.info.lore.objectives"))
            .lore(lang.legacy("menu.war_declaration.item.info.lore.winners"))
            .lore(lang.legacy("menu.war_declaration.item.info.lore.losers"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.info.lore.warning"))
        pane.addItem(GuiItem(infoItem), 4, 4)
    }

    private fun createGuildSelectionItem(targetGuild: Guild): ItemStack {
        val memberCount = memberService.getGuildMembers(targetGuild.id).size
        val warHistory = warService.getWarHistory(targetGuild.id, 5)
        val winLossRatio = warService.getWinLossRatio(targetGuild.id)

        // Try to use guild banner, fallback to mode-appropriate material
        val defaultBanner = if (targetGuild.mode == GuildMode.HOSTILE) Material.RED_BANNER else Material.WHITE_BANNER
        val bannerItem = targetGuild.banner?.let { banner ->
            try {
                val deserialized = banner.deserializeToItemStack()
                deserialized?.clone() ?: ItemStack.of(defaultBanner)
            } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                // Fallback on any deserialization error
                ItemStack.of(defaultBanner)
            }
        } ?: ItemStack.of(defaultBanner)

        // Add guild mode indicator
        return bannerItem
            .name(if (targetGuild.mode == GuildMode.HOSTILE) {
                lang.legacy("menu.war_declaration.item.guild.name.hostile", "guild" to targetGuild.name)
            } else {
                lang.legacy("menu.war_declaration.item.guild.name.peaceful", "guild" to targetGuild.name)
            })
            .lore(lang.legacy("menu.war_declaration.item.guild.lore.members", "count" to memberCount))
            .lore(lang.legacy("menu.war_declaration.item.guild.lore.ratio", "ratio" to String.format("%.2f", winLossRatio)))
            .lore(lang.legacy("menu.war_declaration.item.guild.lore.recent", "count" to warHistory.size))
            .lore(lang.legacy("menu.common.blank"))
            .lore(if (targetGuild.mode == GuildMode.HOSTILE) {
                lang.legacy("menu.war_declaration.item.guild.lore.mode.hostile")
            } else {
                lang.legacy("menu.war_declaration.item.guild.lore.mode.peaceful")
            })
            .lore(lang.legacy("menu.war_declaration.item.guild.lore.level", "level" to targetGuild.level))
            .lore(lang.legacy("menu.common.blank"))
            .lore(if (targetGuild.mode == GuildMode.HOSTILE) {
                lang.legacy("menu.war_declaration.item.guild.lore.declare")
            } else {
                lang.legacy("menu.war_declaration.item.guild.lore.send")
            })
    }

    private fun addWarConfigurationSection(pane: StaticPane) {
        val target = targetGuild ?: return // Should never be null when this method is called
        
        // Target guild display
        val targetItem = ItemStack.of(Material.TARGET)
            .name(lang.legacy("menu.war_declaration.item.target.name", "guild" to target.name))
            .lore(lang.legacy("menu.war_declaration.item.target.lore.description"))
            .lore(lang.legacy("menu.war_declaration.item.target.lore.members", "count" to memberService.getGuildMembers(target.id).size))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.target.lore.action"))
        val targetGuiItem = GuiItem(targetItem) {
            targetGuild = null
            open() // Return to guild selection
        }
        pane.addItem(targetGuiItem, 1, 0)

        // Duration selection
        addDurationSelection(pane)
        
        // War wager selection
        addWarWagerSection(pane)

        // Objectives selection
        addObjectivesSelection(pane)

        // War terms
        addWarTermsSection(pane)

        // Declare war button
        addDeclareWarButton(pane)
    }

    private fun addDurationSelection(pane: StaticPane) {
        val durationItem = ItemStack.of(Material.CLOCK)
            .name(lang.legacy("menu.war_declaration.item.duration.name"))
            .lore(lang.legacy("menu.war_declaration.item.duration.lore.current", "days" to selectedDuration.toDays()))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.duration.lore.available"))
            .lore(lang.legacy("menu.war_declaration.item.duration.lore.quick"))
            .lore(lang.legacy("menu.war_declaration.item.duration.lore.standard"))
            .lore(lang.legacy("menu.war_declaration.item.duration.lore.extended"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.duration.lore.action"))

        val guiItem = GuiItem(durationItem) {
            cycleDuration()
            open() // Refresh menu
        }
        pane.addItem(guiItem, 3, 1)
    }

    private fun addWarWagerSection(pane: StaticPane) {
        val guildBalance = bankService.getBalance(guild.id)
        val maxWager = guildBalance // No limits - high stakes gambling!

        // Main wager display
        val wagerItem = ItemStack.of(Material.GOLD_INGOT)
            .name(lang.legacy("menu.war_declaration.item.wager.name"))
            .lore(lang.legacy("menu.war_declaration.item.wager.lore.current", "amount" to wagerAmount))
            .lore(lang.legacy("menu.war_declaration.item.wager.lore.bank", "amount" to guildBalance))
            .lore(lang.legacy("menu.war_declaration.item.wager.lore.maximum", "amount" to maxWager))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.wager.lore.match"))
            .lore(lang.legacy("menu.war_declaration.item.wager.lore.winner"))
            .lore(lang.legacy("menu.common.blank"))
            if (wagerAmount > 0) {
                wagerItem.lore(lang.legacy("menu.war_declaration.item.wager.lore.pot", "amount" to wagerAmount * 2))
            } else {
                wagerItem.lore(lang.legacy("menu.war_declaration.item.wager.lore.none"))
            }
            wagerItem.lore(lang.legacy("menu.common.blank"))
            wagerItem.lore(lang.legacy("menu.war_declaration.item.wager.lore.action"))

        val guiItem = GuiItem(wagerItem) {
            cycleWagerAmount(maxWager)
            open() // Refresh menu
        }
        pane.addItem(guiItem, 5, 1)

        // Add wager buttons if there's available balance
        if (guildBalance > 0) {
            // Add 10% button
            val add10Percent = ItemStack.of(Material.GREEN_CONCRETE)
                .name(lang.legacy("menu.war_declaration.item.wager_add.ten.name"))
                .lore(lang.legacy("menu.war_declaration.item.wager_add.ten.lore"))
                .lore(lang.legacy("menu.war_declaration.item.wager_add.amount", "amount" to guildBalance / 10))

            val add10GuiItem = GuiItem(add10Percent) {
                val amountToAdd = guildBalance / 10
                if (amountToAdd > 0 && wagerAmount + amountToAdd <= guildBalance) {
                    wagerAmount += amountToAdd
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.wager_added", "amount" to amountToAdd))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.insufficient_funds"))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(add10GuiItem, 5, 2)

            // Add 25% button
            val add25Percent = ItemStack.of(Material.BLUE_CONCRETE)
                .name(lang.legacy("menu.war_declaration.item.wager_add.twenty_five.name"))
                .lore(lang.legacy("menu.war_declaration.item.wager_add.twenty_five.lore"))
                .lore(lang.legacy("menu.war_declaration.item.wager_add.amount", "amount" to guildBalance / 4))

            val add25GuiItem = GuiItem(add25Percent) {
                val amountToAdd = guildBalance / 4
                if (amountToAdd > 0 && wagerAmount + amountToAdd <= guildBalance) {
                    wagerAmount += amountToAdd
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.wager_added", "amount" to amountToAdd))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.insufficient_funds"))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(add25GuiItem, 6, 2)

            // Wager All button
            val wagerAllItem = ItemStack.of(Material.RED_CONCRETE)
                .name(lang.legacy("menu.war_declaration.item.wager_all.name"))
                .lore(lang.legacy("menu.war_declaration.item.wager_all.lore.description"))
                .lore(lang.legacy("menu.war_declaration.item.wager_add.amount", "amount" to guildBalance))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.war_declaration.item.wager_all.lore.warning"))

            val wagerAllGuiItem = GuiItem(wagerAllItem) {
                if (guildBalance > 0) {
                    wagerAmount = guildBalance
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.all_in", "amount" to guildBalance))
                    player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.no_funds"))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(wagerAllGuiItem, 7, 2)

            // Wager Enemy Bank button (if enemy guild has funds)
            val enemyBalance = targetGuild?.let { bankService.getBalance(it.id) } ?: 0
            if (enemyBalance > 0) {
                val wagerEnemyItem = ItemStack.of(Material.PURPLE_CONCRETE)
                    .name(lang.legacy("menu.war_declaration.item.match_enemy.name"))
                    .lore(lang.legacy("menu.war_declaration.item.match_enemy.lore.description"))
                    .lore(lang.legacy("menu.war_declaration.item.match_enemy.lore.bank", "amount" to enemyBalance))
                    .lore(lang.legacy("menu.war_declaration.item.match_enemy.lore.wager", "amount" to enemyBalance))
                    .lore(lang.legacy("menu.common.blank"))
                    .lore(lang.legacy("menu.war_declaration.item.match_enemy.lore.warning"))

                val wagerEnemyGuiItem = GuiItem(wagerEnemyItem) {
                    if (enemyBalance > 0 && guildBalance >= enemyBalance) {
                        wagerAmount = enemyBalance
                        player.sendMessage(lang.msg("menu.war_declaration.feedback.matching_enemy", "amount" to enemyBalance))
                        player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                        open() // Refresh menu
                    } else if (enemyBalance > 0 && guildBalance < enemyBalance) {
                        player.sendMessage(lang.msg("menu.war_declaration.feedback.match_insufficient"))
                        player.sendMessage(lang.msg("menu.war_declaration.feedback.need_have", "need" to enemyBalance, "have" to guildBalance))
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    } else {
                        player.sendMessage(lang.msg("menu.war_declaration.feedback.enemy_no_funds"))
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    }
                }
                pane.addItem(wagerEnemyGuiItem, 7, 3)
            }

            // Remove wager button
            if (wagerAmount > 0) {
                val removeWager = ItemStack.of(Material.GRAY_CONCRETE)
                    .name(lang.legacy("menu.war_declaration.item.remove_wager.name"))
                    .lore(lang.legacy("menu.war_declaration.item.remove_wager.lore.description"))
                    .lore(lang.legacy("menu.war_declaration.item.remove_wager.lore.current", "amount" to wagerAmount))

                val removeGuiItem = GuiItem(removeWager) {
                    val removedAmount = wagerAmount
                    wagerAmount = 0
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.wager_removed", "amount" to removedAmount))
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                    open() // Refresh menu
                }
                pane.addItem(removeGuiItem, 8, 2)
            }
        }
    }

    private fun addObjectivesSelection(pane: StaticPane) {
        // Default to kill-based objective if none selected
        if (selectedObjectives.isEmpty()) {
            selectedObjectives.add(WarObjective(
                type = ObjectiveType.KILLS,
                targetValue = 10,
                description = lang.raw("menu.war_declaration.objective.default_description")
            ))
        }

        val objectivesItem = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.legacy("menu.war_declaration.item.objectives.name", "count" to selectedObjectives.size))
            .lore(lang.legacy("menu.common.blank"))

        // List all selected objectives
        selectedObjectives.forEach { obj ->
            val icon = when (obj.type) {
                ObjectiveType.KILLS -> lang.raw("menu.war_declaration.objective.icon.kills")
                ObjectiveType.TIME_SURVIVAL -> lang.raw("menu.war_declaration.objective.icon.time")
                ObjectiveType.CLAIMS_CAPTURED -> lang.raw("menu.war_declaration.objective.icon.claims")
                else -> lang.raw("menu.war_declaration.objective.icon.other")
            }
            val typeName = when (obj.type) {
                ObjectiveType.KILLS -> lang.raw("menu.war_declaration.objective.type.kills")
                ObjectiveType.TIME_SURVIVAL -> lang.raw("menu.war_declaration.objective.type.time")
                ObjectiveType.CLAIMS_CAPTURED -> lang.raw("menu.war_declaration.objective.type.claims")
                else -> lang.raw("menu.war_declaration.objective.type.other")
            }
            objectivesItem.lore(lang.legacy("menu.war_declaration.item.objectives.lore.row", "icon" to icon, "type" to typeName, "target" to obj.targetValue))
        }

        objectivesItem
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.objectives.lore.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.objectives.lore.action"))

        val guiItem = GuiItem(objectivesItem) {
            openObjectivesMenu()
        }
        pane.addItem(guiItem, 5, 1)
    }

    private fun addWarTermsSection(pane: StaticPane) {
        val termsItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.war_declaration.item.terms.name"))
            .lore(if (warTerms != null) {
                lang.legacy("menu.war_declaration.item.terms.lore.current", "terms" to warTerms)
            } else {
                lang.legacy("menu.war_declaration.item.terms.lore.none")
            })
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.terms.lore.description"))
            .lore(lang.legacy("menu.war_declaration.item.terms.lore.reason"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.war_declaration.item.terms.lore.action"))

        val guiItem = GuiItem(termsItem) {
            player.sendMessage(lang.msg("menu.war_declaration.feedback.terms_prompt"))
            inputMode = "war_terms"
            chatInputListener.startInputMode(player, this@GuildWarDeclarationMenu)
            player.closeInventory()
        }
        pane.addItem(guiItem, 7, 1)
    }

    private fun addDeclareWarButton(pane: StaticPane) {
        val target = targetGuild ?: return // Should never be null when this method is called
        
        // Check if war can be declared
        val canDeclare = warService.canGuildDeclareWar(guild.id)
        val hasActiveWar = warService.getCurrentWarBetweenGuilds(guild.id, target.id) != null

        val declareItem = if (canDeclare && !hasActiveWar) {
            ItemStack.of(Material.DIAMOND_SWORD)
                .name(lang.legacy("menu.war_declaration.item.declare.name"))
                .lore(lang.legacy("menu.war_declaration.item.declare.lore.target", "guild" to target.name))
                .lore(lang.legacy("menu.war_declaration.item.declare.lore.duration", "days" to selectedDuration.toDays()))
                .lore(lang.legacy("menu.war_declaration.item.declare.lore.objectives", "count" to selectedObjectives.size))
                .lore(if (wagerAmount > 0) {
                    lang.legacy("menu.war_declaration.item.declare.lore.wager", "amount" to wagerAmount)
                } else {
                    lang.legacy("menu.war_declaration.item.declare.lore.no_wager")
                })
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.war_declaration.item.declare.lore.notify_1"))
                .lore(lang.legacy("menu.war_declaration.item.declare.lore.notify_2"))
                .also { item ->
                    if (wagerAmount > 0) {
                        item.lore(lang.legacy("menu.war_declaration.item.declare.lore.escrow"))
                    }
                }
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.war_declaration.item.declare.lore.action"))
        } else {
            ItemStack.of(Material.BARRIER)
                .name(lang.legacy("menu.war_declaration.item.cannot_declare.name"))
                .lore(when {
                    !canDeclare -> lang.legacy("menu.war_declaration.item.cannot_declare.lore.guild_restricted")
                    hasActiveWar -> lang.legacy("menu.war_declaration.item.cannot_declare.lore.active_war")
                    else -> lang.legacy("menu.war_declaration.item.cannot_declare.lore.unknown")
                })
        }

        val guiItem = GuiItem(declareItem) {
            if (canDeclare && !hasActiveWar) {
                declareWar()
            } else {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(guiItem, 4, 4)
    }

    private fun declareWar() {
        val target = targetGuild ?: return // Should never be null when this method is called

        try {
            // DUPLICATE PROTECTION: Check if war or declaration already exists
            val existingWar = warService.getCurrentWarBetweenGuilds(guild.id, target.id)
            if (existingWar != null) {
                player.sendMessage(lang.msg("menu.war_declaration.feedback.active_war", "guild" to target.name))
                player.sendMessage(lang.msg("menu.war_declaration.feedback.end_current_war"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return
            }

            // Check for pending declarations
            val existingDeclarations = warService.getPendingDeclarationsForGuild(target.id)
                .filter { it.declaringGuildId == guild.id }
            if (existingDeclarations.isNotEmpty()) {
                player.sendMessage(lang.msg("menu.war_declaration.feedback.pending", "guild" to target.name))
                player.sendMessage(lang.msg("menu.war_declaration.feedback.await_decision"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return
            }

            // REQ-024: no auto-accept — every declaration goes through the
            // accept/decline flow. REQ-039: escrow is handled by the war service
            // (createWager deducts both guilds on acceptance); the menu no longer
            // moves bank funds itself.
            val declaration = warService.createWarDeclaration(
                declaringGuildId = guild.id,
                defendingGuildId = target.id,
                duration = selectedDuration,
                objectives = selectedObjectives,
                wagerAmount = wagerAmount,
                terms = warTerms,
                actorId = player.uniqueId
            )

            if (declaration != null) {
                player.sendMessage(lang.msg("menu.war_declaration.feedback.sent", "guild" to target.name))
                player.sendMessage(lang.msg("menu.war_declaration.feedback.duration", "days" to selectedDuration.toDays()))
                if (selectedObjectives.isNotEmpty()) {
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.objectives", "count" to selectedObjectives.size))
                }
                if (wagerAmount > 0) {
                    player.sendMessage(lang.msg("menu.war_declaration.feedback.wager_escrow", "amount" to wagerAmount))
                }
                player.sendMessage(lang.msg("menu.war_declaration.feedback.must_accept"))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)

                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))

                // Notify defending guild of the declaration
                notifyGuildOfWarDeclaration(declaration)
                return
            } else {
                player.sendMessage(lang.msg("menu.war_declaration.feedback.send_failed"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.war_declaration.feedback.error", "error" to (e.message ?: lang.raw("general.unknown"))))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    private fun selectTargetGuild(target: Guild) {
        targetGuild = target
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
        open() // Refresh to show configuration
    }

    private fun cycleDuration() {
        selectedDuration = when (selectedDuration.toDays()) {
            3L -> Duration.ofDays(7)
            7L -> Duration.ofDays(14)
            else -> Duration.ofDays(3)
        }
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
    }

    private fun cycleWagerAmount(maxWager: Int) {
        // Cycle through wager amounts: 0, 10%, 25%, 50%, 75%, 100% of max - HIGH STAKES!
        val wagerOptions = listOf(0, maxWager / 10, maxWager / 4, maxWager / 2, (maxWager * 3) / 4, maxWager)
        val currentIndex = wagerOptions.indexOf(wagerAmount)
        val nextIndex = if (currentIndex == -1 || currentIndex >= wagerOptions.size - 1) 0 else currentIndex + 1
        wagerAmount = wagerOptions[nextIndex]
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
    }

    private fun cycleKillTarget() {
        val killTargets = listOf(5, 10, 25, 50)
        val currentKills = selectedObjectives.firstOrNull()?.targetValue ?: 10
        val currentIndex = killTargets.indexOf(currentKills)
        val nextIndex = if (currentIndex == -1 || currentIndex >= killTargets.size - 1) 0 else currentIndex + 1
        val newTarget = killTargets[nextIndex]
        
        // Update the objective
        selectedObjectives.clear()
        selectedObjectives.add(WarObjective(
            type = ObjectiveType.KILLS,
            targetValue = newTarget,
            description = "Kill $newTarget enemy players"
        ))
        
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
    }

    private fun openObjectivesMenu() {
        // Open the objectives selection menu
        val objectivesMenu = WarObjectivesSelectionMenu(
            menuNavigator,
            player,
            selectedObjectives
        ) { updatedObjectives ->
            // Callback: update the selected objectives
            selectedObjectives.clear()
            selectedObjectives.addAll(updatedObjectives)
        }
        menuNavigator.openMenu(objectivesMenu)
    }

    private fun openGuildListMenu(guilds: List<Guild>) {
        // Open the guild selection menu
        val guildListMenu = WarGuildSelectionMenu(
            menuNavigator = menuNavigator,
            player = player,
            availableGuilds = guilds,
            callback = { selectedGuild ->
                // Callback: set the selected guild as target
                targetGuild = selectedGuild
            }
        )
        menuNavigator.openMenu(guildListMenu)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.war_declaration.item.back.name"))
            .lore(lang.legacy("menu.war_declaration.item.back.lore"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun notifyGuildOfWarDeclaration(declaration: WarDeclaration) {
        try {
            val declaringGuild = guildService.getGuild(declaration.declaringGuildId)
            val defendingGuild = guildService.getGuild(declaration.defendingGuildId)

            if (declaringGuild == null || defendingGuild == null) return

            // Notify defending guild members
            val defendingMembers = memberService.getGuildMembers(declaration.defendingGuildId)

            for (member in defendingMembers) {
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    // Send title
                    onlinePlayer.showTitle(net.kyori.adventure.title.Title.title(
                        lang.msg("menu.war_declaration.notification.title"),
                        lang.msg("menu.war_declaration.notification.subtitle", "guild" to declaringGuild.name),
                        net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(500),
                            java.time.Duration.ofSeconds(4),
                            java.time.Duration.ofSeconds(1)
                        )
                    ))

                    // Send chat messages
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.divider"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.received"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.from", "guild" to declaringGuild.name))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.duration", "days" to declaration.proposedDuration.toDays()))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.objectives", "count" to declaration.objectives.size))
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.wager", "amount" to wagerAmount))
                        onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.match"))
                    }
                    if (declaration.terms != null) {
                        onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.terms", "terms" to declaration.terms))
                    }
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.expires", "hours" to declaration.remainingTime.toHours()))
                    onlinePlayer.sendMessage(lang.msg("menu.common.blank"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.respond"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.divider"))

                    // Play alert sound
                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.BLOCK_BELL_USE, 1.0f, 0.8f)
                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.0f)
                }
            }

            // Also notify declaring guild that declaration was sent
            val declaringMembers = memberService.getGuildMembers(declaration.declaringGuildId)
            for (member in declaringMembers) {
                if (member.playerId == player.uniqueId) continue // Skip the sender

                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.ally_sent", "guild" to defendingGuild.name))
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.duration", "days" to declaration.proposedDuration.toDays()))
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.ally_wager", "amount" to wagerAmount))
                    }
                    onlinePlayer.sendMessage(lang.msg("menu.war_declaration.notification.awaiting"))
                }
            }

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.war_declaration.feedback.notify_failed"))
            println("Error notifying guild of war declaration: ${e.message}")
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }

    // ChatInputHandler implementation
    override fun onChatInput(player: Player, input: String) {
        if (inputMode == "war_terms") {
            if (input.lowercase() == "cancel") {
                player.sendMessage(lang.msg("menu.war_declaration.feedback.terms_cancelled"))
                inputMode = null
                open() // Reopen menu
                return
            }

            // Validate terms length
            if (input.length > 200) {
                player.sendMessage(lang.msg("menu.war_declaration.feedback.terms_too_long", "maximum" to 200))
                player.sendMessage(lang.msg("menu.war_declaration.feedback.terms_retry"))
                return
            }

            warTerms = input
            inputMode = null
            player.sendMessage(lang.msg("menu.war_declaration.feedback.terms_set", "terms" to input))
            open() // Reopen menu with updated terms
        }
    }

    override fun onCancel(player: Player) {
        if (inputMode == "war_terms") {
            player.sendMessage(lang.msg("menu.war_declaration.feedback.terms_cancelled"))
            inputMode = null
            open() // Reopen menu
        }
    }
}

