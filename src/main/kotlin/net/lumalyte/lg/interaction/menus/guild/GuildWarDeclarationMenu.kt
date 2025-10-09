package net.lumalyte.lg.interaction.menus.guild

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
import net.lumalyte.lg.domain.entities.WarObjectiveType
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarObjective
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildWarDeclarationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var targetGuild: Guild? = null,
    private val messageService: MessageService
) : Menu, KoinComponent, ChatInputHandler {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val guildRepository: GuildRepository by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val configService: ConfigService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    
    // War configuration state
    private var selectedDuration: Duration = Duration.ofDays(7) // Default 7 days
    private var selectedObjectives: MutableSet<WarObjective> = mutableSetOf()
    private var warTerms: String? = null
    private var inputMode: String? = null // Track what input mode we're in ("war_terms")
    private var wagerAmount: Int = 0 // War pot amount

    override fun open() {
        // Check permissions first
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to declare war for your guild!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Check if guild is in peaceful mode
        if (guild.mode == GuildMode.PEACEFUL) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Peaceful guilds cannot declare war!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Switch to Hostile mode first in guild settings.")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>⚔ Declare War - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
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
        val titleItem = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<dark_red>⚔ SELECT TARGET GUILD")
            .addAdventureLore(player, messageService, "<gray>Choose which guild to declare war against")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>Warning: This action cannot be undone!")
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
            val moreItem = ItemStack(Material.BOOK)
                .setAdventureName(player, messageService, "<yellow>📖 More Guilds (${availableGuilds.size - 7})")
                .addAdventureLore(player, messageService, "<gray>Click to see all available guilds")
            val guiItem = GuiItem(moreItem) {
                openGuildListMenu(availableGuilds)
            }
            pane.addItem(guiItem, 8, 2)
        }

        // Info section
        val infoItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .setAdventureName(player, messageService, "<gold>ℹ️ War Declaration Info")
            .addAdventureLore(player, messageService, "<gray>• Wars last 1-14 days")
            .addAdventureLore(player, messageService, "<gray>• Both guilds can set objectives")
            .addAdventureLore(player, messageService, "<gray>• Winners gain progression XP")
            .addAdventureLore(player, messageService, "<gray>• Losers may lose resources")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Choose your target wisely!")
        pane.addItem(GuiItem(infoItem), 4, 4)
    }

    private fun createGuildSelectionItem(targetGuild: Guild): ItemStack {
        val memberCount = memberService.getGuildMembers(targetGuild.id).size
        val warHistory = warService.getWarHistory(targetGuild.id, 5)
        val winLossRatio = warService.getWinLossRatio(targetGuild.id)

        // Try to use guild banner, fallback to mode-appropriate material
        val bannerItem = if (targetGuild.banner != null) {
            try {
                val deserialized = targetGuild.banner!!.deserializeToItemStack()
                if (deserialized != null) {
                    deserialized.clone()
                } else {
                    // Fallback based on guild mode - deserialization failed
                    ItemStack(if (targetGuild.mode == GuildMode.HOSTILE) Material.RED_BANNER else Material.WHITE_BANNER)
                }
            } catch (e: Exception) {
                // Fallback on any deserialization error
                ItemStack(if (targetGuild.mode == GuildMode.HOSTILE) Material.RED_BANNER else Material.WHITE_BANNER)
            }
        } else {
            // Default banner based on mode - no banner configured
            ItemStack(if (targetGuild.mode == GuildMode.HOSTILE) Material.RED_BANNER else Material.WHITE_BANNER)
        }

        // Add guild mode indicator
        val modeColor = if (targetGuild.mode == GuildMode.HOSTILE) "<red>" else "<green>"
        val modeIcon = if (targetGuild.mode == GuildMode.HOSTILE) "⚔" else "☮"

        return bannerItem
            .setAdventureName(player, messageService, "$modeColor$modeIcon ${targetGuild.name}")
            .addAdventureLore(player, messageService, "<gray>Members: <white>$memberCount")
            .lore("<gray>Win/Loss Ratio: <white>${String.format("%.2f", winLossRatio)}")
            .addAdventureLore(player, messageService, "<gray>Recent Wars: <white>${warHistory.size}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Mode: $modeColor${targetGuild.mode}")
            .addAdventureLore(player, messageService, "<gray>Level: <white>${targetGuild.level}")
            .addAdventureLore(player, messageService, "<gray>")
            .lore(if (targetGuild.mode == GuildMode.HOSTILE) {
                "<yellow>Click to declare war!"
            } else {
                "<yellow>Click to send war declaration!"
            })
    }

    private fun addWarConfigurationSection(pane: StaticPane) {
        val target = targetGuild!!
        
        // Target guild display
        val targetItem = ItemStack(Material.TARGET)
            .setAdventureName(player, messageService, "<red>🎯 Target: ${target.name}")
            .addAdventureLore(player, messageService, "<gray>Declaring war against this guild")
            .addAdventureLore(player, messageService, "<gray>Members: <white>${memberService.getGuildMembers(target.id).size}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to change target")
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
        val durationItem = ItemStack(Material.CLOCK)
            .setAdventureName(player, messageService, "<gold>◷ War Duration")
            .addAdventureLore(player, messageService, "<gray>Current: <white>${selectedDuration.toDays()} days")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Available durations:")
            .addAdventureLore(player, messageService, "<gray>• <white>3 days <gray>(Quick skirmish)")
            .addAdventureLore(player, messageService, "<gray>• <white>7 days <gray>(Standard war)")
            .addAdventureLore(player, messageService, "<gray>• <white>14 days <gray>(Extended campaign)")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to change duration")

        val guiItem = GuiItem(durationItem) {
            cycleDuration()
            open() // Refresh menu
        }
        pane.addItem(guiItem, 3, 1)
    }

    private fun addWarWagerSection(pane: StaticPane) {
        val guildBalance = guild.bankBalance
        val maxWager = guildBalance // No limits - high stakes gambling!

        // Main wager display
        val wagerItem = ItemStack(Material.GOLD_INGOT)
            .setAdventureName(player, messageService, "<gold>$ War Wager")
            .addAdventureLore(player, messageService, "<gray>Current Wager: <gold>$wagerAmount coins")
            .addAdventureLore(player, messageService, "<gray>Guild Bank: <gold>$guildBalance coins")
            .addAdventureLore(player, messageService, "<gray>Max Wager: <gold>$maxWager coins <red>(ALL IN!)")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>The enemy guild must match your wager")
            .addAdventureLore(player, messageService, "<gray>Winner takes the entire pot!")
            .addAdventureLore(player, messageService, "<gray>")
            if (wagerAmount > 0) {
                wagerItem.addAdventureLore(player, messageService, "<green>✓ Pot: <gold>${wagerAmount * 2} coins <gray>(if matched)")
            } else {
                wagerItem.addAdventureLore(player, messageService, "<gray>No wager set - war for honor only")
            }
            wagerItem.addAdventureLore(player, messageService, "<gray>")
            wagerItem.addAdventureLore(player, messageService, "<yellow>Click to adjust wager amount")

        val guiItem = GuiItem(wagerItem) {
            cycleWagerAmount(maxWager)
            open() // Refresh menu
        }
        pane.addItem(guiItem, 5, 1)

        // Add wager buttons if there's available balance
        if (guildBalance > 0) {
            // Add 10% button
            val add10Percent = ItemStack(Material.GREEN_CONCRETE)
                .setAdventureName(player, messageService, "<green>➕ Add 10%")
                .addAdventureLore(player, messageService, "<gray>Add 10% of guild bank")
                .addAdventureLore(player, messageService, "<gray>Amount: <gold>${guildBalance / 10} coins")

            val add10GuiItem = GuiItem(add10Percent) {
                val amountToAdd = guildBalance / 10
                if (amountToAdd > 0 && wagerAmount + amountToAdd <= guildBalance) {
                    wagerAmount += amountToAdd
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Added <gold>$amountToAdd coins <green>to wager")
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Insufficient funds or would exceed bank balance!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(add10GuiItem, 5, 2)

            // Add 25% button
            val add25Percent = ItemStack(Material.BLUE_CONCRETE)
                .setAdventureName(player, messageService, "<blue>➕ Add 25%")
                .addAdventureLore(player, messageService, "<gray>Add 25% of guild bank")
                .addAdventureLore(player, messageService, "<gray>Amount: <gold>${guildBalance / 4} coins")

            val add25GuiItem = GuiItem(add25Percent) {
                val amountToAdd = guildBalance / 4
                if (amountToAdd > 0 && wagerAmount + amountToAdd <= guildBalance) {
                    wagerAmount += amountToAdd
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Added <gold>$amountToAdd coins <green>to wager")
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Insufficient funds or would exceed bank balance!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(add25GuiItem, 6, 2)

            // Wager All button
            val wagerAllItem = ItemStack(Material.RED_CONCRETE)
                .setAdventureName(player, messageService, "<red>$ WAGER ALL")
                .addAdventureLore(player, messageService, "<gray>Wager entire guild bank!")
                .addAdventureLore(player, messageService, "<gray>Amount: <gold>$guildBalance coins")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<red>⚠︎ HIGH RISK!")

            val wagerAllGuiItem = GuiItem(wagerAllItem) {
                if (guildBalance > 0) {
                    wagerAmount = guildBalance
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>$ ALL IN! <gold>$guildBalance coins <red>wagered!")
                    player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No funds available to wager!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(wagerAllGuiItem, 7, 2)

            // Wager Enemy Bank button (if enemy guild has funds)
            val enemyBalance = targetGuild?.bankBalance ?: 0
            if (enemyBalance > 0) {
                val wagerEnemyItem = ItemStack(Material.PURPLE_CONCRETE)
                    .setAdventureName(player, messageService, "<dark_purple>∩ MATCH ENEMY")
                    .addAdventureLore(player, messageService, "<gray>Wager to match enemy bank!")
                    .addAdventureLore(player, messageService, "<gray>Enemy Bank: <gold>$enemyBalance coins")
                    .addAdventureLore(player, messageService, "<gray>You would wager: <gold>$enemyBalance coins")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<red>⚠︎ Must have sufficient funds!")

                val wagerEnemyGuiItem = GuiItem(wagerEnemyItem) {
                    if (enemyBalance > 0 && guildBalance >= enemyBalance) {
                        wagerAmount = enemyBalance
                        AdventureMenuHelper.sendMessage(player, messageService, "<dark_purple>∩ MATCHING ENEMY! <gold>$enemyBalance coins <dark_purple>wagered!")
                        player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                        open() // Refresh menu
                    } else if (enemyBalance > 0 && guildBalance < enemyBalance) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Insufficient funds to match enemy wager!")
                        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Need: <gold>$enemyBalance coins, Have: <gold>$guildBalance coins")
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Enemy guild has no funds to match!")
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    }
                }
                pane.addItem(wagerEnemyGuiItem, 7, 3)
            }

            // Remove wager button
            if (wagerAmount > 0) {
                val removeWager = ItemStack(Material.GRAY_CONCRETE)
                    .setAdventureName(player, messageService, "<gray>➖ Remove All")
                    .addAdventureLore(player, messageService, "<gray>Remove entire wager")
                    .addAdventureLore(player, messageService, "<gray>Current: <gold>$wagerAmount coins")

                val removeGuiItem = GuiItem(removeWager) {
                    val removedAmount = wagerAmount
                    wagerAmount = 0
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>🗑️ Removed <gold>$removedAmount coins <gray>from wager")
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
                type = WarObjectiveType.KILL_PLAYERS,
                targetValue = 10,
                description = "Kill 10 enemy players"
            ))
        }

        val killObjective = selectedObjectives.first()
        val objectivesItem = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<red>⚔ War Objective: KILLS")
            .addAdventureLore(player, messageService, "<gray>Target: <white>${killObjective.targetValue} enemy kills")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>The first guild to reach the kill target wins!")
            .addAdventureLore(player, messageService, "<gray>Only kills against enemy guild members count.")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Available targets:")
            .addAdventureLore(player, messageService, "<gray>• <white>5 kills <gray>(Quick skirmish)")
            .addAdventureLore(player, messageService, "<gray>• <white>10 kills <gray>(Standard battle)")
            .addAdventureLore(player, messageService, "<gray>• <white>25 kills <gray>(Extended war)")
            .addAdventureLore(player, messageService, "<gray>• <white>50 kills <gray>(Epic campaign)")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to change kill target")

        val guiItem = GuiItem(objectivesItem) {
            cycleKillTarget()
            open() // Refresh menu
        }
        pane.addItem(guiItem, 5, 1)
    }

    private fun addWarTermsSection(pane: StaticPane) {
        val termsItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<yellow>§ War Terms")
            .lore(if (warTerms != null) "<gray>Terms: <white>$warTerms" else "<gray>No terms set")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Optional message to the defending guild")
            .addAdventureLore(player, messageService, "<gray>explaining your reasons for war")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to set terms")

        val guiItem = GuiItem(termsItem) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>💬 Type your war terms in chat (or 'cancel' to skip):")
            inputMode = "war_terms"
            chatInputListener.startInputMode(player, this@GuildWarDeclarationMenu)
            player.closeInventory()
        }
        pane.addItem(guiItem, 7, 1)
    }

    private fun addDeclareWarButton(pane: StaticPane) {
        val target = targetGuild!!
        
        // Check if war can be declared
        val canDeclare = warService.canGuildDeclareWar(guild.id)
        val hasActiveWar = warService.getCurrentWarBetweenGuilds(guild.id, target.id) != null

        val declareItem = if (canDeclare && !hasActiveWar) {
            ItemStack(Material.DIAMOND_SWORD)
                .setAdventureName(player, messageService, "<dark_red>⚔ DECLARE WAR!")
                .addAdventureLore(player, messageService, "<gray>Target: <white>${target.name}")
                .addAdventureLore(player, messageService, "<gray>Duration: <white>${selectedDuration.toDays()} days")
                .addAdventureLore(player, messageService, "<gray>Objectives: <white>${selectedObjectives.size}")
                .lore(if (wagerAmount > 0) {
                    "<gray>Wager: <gold>$wagerAmount coins"
                } else {
                    "<gray>Wager: <gray>None (honor only)"
                })
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<red>⚠︎ This will notify all members")
                .addAdventureLore(player, messageService, "<red>⚠︎ of both guilds!")
                .also { item ->
                    if (wagerAmount > 0) {
                        item.addAdventureLore(player, messageService, "<red>⚠︎ Funds will be held in escrow!")
                    }
                }
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<yellow>Click to declare war!")
        } else {
            ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>❌ Cannot Declare War")
                .lore(when {
                    !canDeclare -> "<red>Your guild cannot declare war right now"
                    hasActiveWar -> "<red>Already at war with this guild"
                    else -> "<red>Unknown restriction"
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
        val target = targetGuild!!
        
        try {
            // Handle wager escrow if there's a wager
            if (wagerAmount > 0) {
                // Check if guild has sufficient funds
                if (guild.bankBalance < wagerAmount) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Insufficient guild bank funds for wager!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Need: <gold>$wagerAmount coins")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Have: <gold>${guild.bankBalance} coins")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Check withdrawal permissions
                if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to withdraw from guild bank for wagers!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Withdraw funds for escrow (this will be implemented in the war service)
                val withdrawal = bankService.withdraw(
                    guildId = guild.id,
                    playerId = player.uniqueId,
                    amount = wagerAmount,
                    description = "War wager escrow vs ${target.name}"
                )

                if (withdrawal == null) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to secure wager funds!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
            }

            // Check if this should be auto-accepted (hostile guild with no wager)
            val shouldAutoAccept = target.mode == GuildMode.HOSTILE && wagerAmount == 0

            if (shouldAutoAccept) {
                // Auto-accept for hostile guilds with no wager
                val war = warService.declareWar(
                    declaringGuildId = guild.id,
                    defendingGuildId = target.id,
                    duration = selectedDuration,
                    objectives = selectedObjectives,
                    actorId = player.uniqueId
                )

                if (war != null) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>⚔ WAR STARTED against ${target.name}!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Hostile guild auto-accepted - battle begins now!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Duration: <white>${selectedDuration.toDays()} days")
                    if (selectedObjectives.isNotEmpty()) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Objectives: <white>${selectedObjectives.size} set")
                    }
                    player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)

                    // Close menu and return to war management
                    player.closeInventory()
                    menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))

                    // Notify both guilds
                    notifyGuildsOfWarDeclaration(war)
                    return
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to declare war!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
            } else {
                // Create war declaration that requires acceptance (peaceful guilds or wagers)
                // Cast to access the internal method (this should be added to the interface)
                val warServiceBukkit = warService as? net.lumalyte.lg.infrastructure.services.WarServiceBukkit
                val declaration = warServiceBukkit?.createWarDeclaration(
                    declaringGuildId = guild.id,
                    defendingGuildId = target.id,
                    duration = selectedDuration,
                    objectives = selectedObjectives,
                    wagerAmount = wagerAmount,
                    terms = warTerms,
                    actorId = player.uniqueId
                )

                if (declaration != null) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<gold>⚔ WAR DECLARATION SENT to ${target.name}!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Duration: <white>${selectedDuration.toDays()} days")
                    if (selectedObjectives.isNotEmpty()) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Objectives: <white>${selectedObjectives.size} set")
                    }
                    if (wagerAmount > 0) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Wager: <gold>$wagerAmount coins <gray>(awaiting their match)")
                    }
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>They must accept your declaration for war to begin.")
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)

                    // Close menu and return to war management
                    player.closeInventory()
                    menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))

                    // Notify defending guild of the declaration
                    notifyGuildOfWarDeclaration(declaration)
                    return
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to send war declaration!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error declaring war: ${e.message}")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)

            // Refund wager on error
            if (wagerAmount > 0) {
                try {
                    bankService.deposit(guild.id, player.uniqueId, wagerAmount, "War wager refund (error)")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Wager funds have been refunded.")
                } catch (refundError: Exception) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to refund wager! Contact an administrator.")
                }
            }
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
            type = WarObjectiveType.KILL_PLAYERS,
            targetValue = newTarget,
            description = "Kill $newTarget enemy players"
        ))
        
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
    }

    private fun openObjectivesMenu() {
        val claimsEnabled = configService.loadConfig().claimsEnabled

        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Objectives menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This will allow you to set custom war objectives like:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>• Kill X enemy players")

        // Only show claim-related objectives if claims are enabled
        if (claimsEnabled) {
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>• Capture X claims")
        }

        AdventureMenuHelper.sendMessage(player, messageService, "<gray>• Survive for X hours")
        // TODO: Implement objectives menu
    }

    private fun openGuildListMenu(guilds: List<Guild>) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Guild list menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This will show all ${guilds.size} available guilds in a paginated menu.")
        // TODO: Implement paginated guild list
    }

    private fun notifyGuildsOfWarDeclaration(war: War) {
        try {
            val declaringGuild = guildService.getGuild(war.declaringGuildId)
            val defendingGuild = guildService.getGuild(war.defendingGuildId)
            
            if (declaringGuild == null || defendingGuild == null) {
                return
            }

            // Get all online members of both guilds
            val declaringMembers = memberService.getGuildMembers(war.declaringGuildId)
            val defendingMembers = memberService.getGuildMembers(war.defendingGuildId)

            // Notify declaring guild members
            declaringMembers.forEach { member ->
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    // Title and subtitle
                    onlinePlayer.showTitle(Title.title(
                        Component.text("<dark_red>⚔ WAR DECLARED! ⚔"),
                        Component.text("<gray>Against <red>${defendingGuild.name}"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Chat messages
                    onlinePlayer.sendMessage("<dark_gray><bold>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    onlinePlayer.sendMessage("<dark_red>⚔ <bold>WAR DECLARED! <dark_red>⚔")
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("<gray>Your guild <white>${declaringGuild.name} <gray>has declared war on <red>${defendingGuild.name}<gray>!")
                    onlinePlayer.sendMessage("<gray>Duration: <white>${war.duration.toDays()} days")
                    if (war.objectives.isNotEmpty()) {
                        onlinePlayer.sendMessage("<gray>Objectives: <white>${war.objectives.size} war goals set")
                    }
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("<gray>Wager: <gold>$wagerAmount coins <gray>(escrowed)")
                    }
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("<yellow>⚡ Prepare for battle! Victory brings honor and rewards!")
                    onlinePlayer.sendMessage("<dark_gray><bold>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    
                    // Dramatic sound
                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.2f)
                }
            }

            // Notify defending guild members
            defendingMembers.forEach { member ->
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    // Title and subtitle
                    onlinePlayer.showTitle(Title.title(
                        Component.text("<red>⚠︎ UNDER ATTACK! ⚠︎"),
                        Component.text("<gray>War declared by <dark_red>${declaringGuild.name}"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Chat messages
                    onlinePlayer.sendMessage("<dark_gray><bold>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    onlinePlayer.sendMessage("<red>⚠︎ <bold>WAR DECLARED AGAINST YOU! <red>⚠︎")
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("<red>${declaringGuild.name} <gray>has declared war on your guild <white>${defendingGuild.name}<gray>!")
                    onlinePlayer.sendMessage("<gray>Duration: <white>${war.duration.toDays()} days")
                    if (war.objectives.isNotEmpty()) {
                        onlinePlayer.sendMessage("<gray>Enemy Objectives: <white>${war.objectives.size} goals")
                    }
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("<gray>Enemy Wager: <gold>$wagerAmount coins")
                        onlinePlayer.sendMessage("<gray>You must match this wager to accept!")
                    }
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("<yellow>▊ Defend your guild! Rally your members and fight back!")
                    onlinePlayer.sendMessage("<gray>Use <white>/guild war <gray>to manage the conflict")
                    onlinePlayer.sendMessage("<dark_gray><bold>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    
                    // Alert sounds
                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.BLOCK_BELL_USE, 1.0f, 0.8f)
                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.0f)
                }
            }

            // Log to console
            org.bukkit.Bukkit.getLogger().info("WAR DECLARED: ${declaringGuild.name} vs ${defendingGuild.name} (${war.duration.toDays()} days)")
            
        } catch (e: Exception) {
            org.bukkit.Bukkit.getLogger().warning("Failed to notify guilds of war declaration: ${e.message}")
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>⬅️ Back")
            .addAdventureLore(player, messageService, "<gray>Return to war management")

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
                        net.kyori.adventure.text.Component.text("<red>⚠︎ WAR DECLARATION! ⚠︎"),
                        net.kyori.adventure.text.Component.text("<gray>${declaringGuild.name} challenges you!"),
                        net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(500),
                            java.time.Duration.ofSeconds(4),
                            java.time.Duration.ofSeconds(1)
                        )
                    ))

                    // Send chat messages
                    onlinePlayer.sendMessage("<red>═══════════════════════════════════")
                    onlinePlayer.sendMessage("<red>⚠︎ WAR DECLARATION RECEIVED! ⚠︎")
                    onlinePlayer.sendMessage("<gray>From: <white>${declaringGuild.name}")
                    onlinePlayer.sendMessage("<gray>Duration: <white>${declaration.proposedDuration.toDays()} days")
                    onlinePlayer.sendMessage("<gray>Objectives: <white>${declaration.objectives.size}")
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("<gray>Their Wager: <gold>$wagerAmount coins")
                        onlinePlayer.sendMessage("<gray>You must match to accept!")
                    }
                    if (declaration.terms != null) {
                        onlinePlayer.sendMessage("<gray>Terms: <white>${declaration.terms}")
                    }
                    onlinePlayer.sendMessage("<gray>Expires: <white>${declaration.remainingTime.toHours()}h")
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("<yellow>Use <white>/guild war <yellow>to respond!")
                    onlinePlayer.sendMessage("<red>═══════════════════════════════════")

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
                    onlinePlayer.sendMessage("<gold>⚔ War declaration sent to ${defendingGuild.name}!")
                    onlinePlayer.sendMessage("<gray>Duration: <white>${declaration.proposedDuration.toDays()} days")
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("<gray>Wager: <gold>$wagerAmount coins")
                    }
                    onlinePlayer.sendMessage("<gray>Awaiting their response...")
                }
            }

        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to notify defending guild of war declaration!")
            println("Error notifying guild of war declaration: ${e.message}")
        }
    }// ChatInputHandler implementation
    override fun onChatInput(player: Player, input: String) {
        if (inputMode == "war_terms") {
            if (input.lowercase() == "cancel") {
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>War terms input cancelled")
                inputMode = null
                open() // Reopen menu
                return
            }

            // Validate terms length
            if (input.length > 200) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ War terms too long! Maximum 200 characters.")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Please try again or type 'cancel' to skip:")
                return
            }

            warTerms = input
            inputMode = null
            player.sendMessage("<green>✅ War terms set: <white>\"$input\"")
            open() // Reopen menu with updated terms
        }
    }

    override fun onCancel(player: Player) {
        if (inputMode == "war_terms") {
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>War terms input cancelled")
            inputMode = null
            open() // Reopen menu
        }
    }
}

