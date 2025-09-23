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
    
    // War configuration state
    private var selectedDuration: Duration = Duration.ofDays(7) // Default 7 days
    private var selectedObjectives: MutableSet<WarObjective> = mutableSetOf()
    private var warTerms: String? = null
    private var inputMode: String? = null // Track what input mode we're in ("war_terms")
    private var wagerAmount: Int = 0 // War pot amount

    override fun open() {
        // Check permissions first
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage("§c❌ You don't have permission to declare war for your guild!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Check if guild is in peaceful mode
        if (guild.mode == GuildMode.PEACEFUL) {
            player.sendMessage("§c❌ Peaceful guilds cannot declare war!")
            player.sendMessage("§7Switch to Hostile mode first in guild settings.")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val gui = ChestGui(6, "§4⚔ Declare War - ${guild.name}")
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
        val titleItem = ItemStack(Material.DIAMOND_SWORD)
            .name("§4⚔ SELECT TARGET GUILD")
            .lore("§7Choose which guild to declare war against")
            .lore("§7")
            .lore("§cWarning: This action cannot be undone!")
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
                .name("§e📖 More Guilds (${availableGuilds.size - 7})")
                .lore("§7Click to see all available guilds")
            val guiItem = GuiItem(moreItem) {
                openGuildListMenu(availableGuilds)
            }
            pane.addItem(guiItem, 8, 2)
        }

        // Info section
        val infoItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .name("§6ℹ️ War Declaration Info")
            .lore("§7• Wars last 1-14 days")
            .lore("§7• Both guilds can set objectives")
            .lore("§7• Winners gain progression XP")
            .lore("§7• Losers may lose resources")
            .lore("§7")
            .lore("§eChoose your target wisely!")
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
        val modeColor = if (targetGuild.mode == GuildMode.HOSTILE) "§c" else "§a"
        val modeIcon = if (targetGuild.mode == GuildMode.HOSTILE) "⚔" else "☮"

        return bannerItem
            .name("$modeColor$modeIcon ${targetGuild.name}")
            .lore("§7Members: §f$memberCount")
            .lore("§7Win/Loss Ratio: §f${String.format("%.2f", winLossRatio)}")
            .lore("§7Recent Wars: §f${warHistory.size}")
            .lore("§7")
            .lore("§7Mode: $modeColor${targetGuild.mode}")
            .lore("§7Level: §f${targetGuild.level}")
            .lore("§7")
            .lore(if (targetGuild.mode == GuildMode.HOSTILE) {
                "§eClick to declare war!"
            } else {
                "§eClick to send war declaration!"
            })
    }

    private fun addWarConfigurationSection(pane: StaticPane) {
        val target = targetGuild!!
        
        // Target guild display
        val targetItem = ItemStack(Material.TARGET)
            .name("§c🎯 Target: ${target.name}")
            .lore("§7Declaring war against this guild")
            .lore("§7Members: §f${memberService.getGuildMembers(target.id).size}")
            .lore("§7")
            .lore("§eClick to change target")
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
            .name("§6◷ War Duration")
            .lore("§7Current: §f${selectedDuration.toDays()} days")
            .lore("§7")
            .lore("§7Available durations:")
            .lore("§7• §f3 days §7(Quick skirmish)")
            .lore("§7• §f7 days §7(Standard war)")
            .lore("§7• §f14 days §7(Extended campaign)")
            .lore("§7")
            .lore("§eClick to change duration")

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
            .name("§6$ War Wager")
            .lore("§7Current Wager: §6$wagerAmount coins")
            .lore("§7Guild Bank: §6$guildBalance coins")
            .lore("§7Max Wager: §6$maxWager coins §c(ALL IN!)")
            .lore("§7")
            .lore("§7The enemy guild must match your wager")
            .lore("§7Winner takes the entire pot!")
            .lore("§7")
            if (wagerAmount > 0) {
                wagerItem.lore("§a✓ Pot: §6${wagerAmount * 2} coins §7(if matched)")
            } else {
                wagerItem.lore("§7No wager set - war for honor only")
            }
            wagerItem.lore("§7")
            wagerItem.lore("§eClick to adjust wager amount")

        val guiItem = GuiItem(wagerItem) {
            cycleWagerAmount(maxWager)
            open() // Refresh menu
        }
        pane.addItem(guiItem, 5, 1)

        // Add wager buttons if there's available balance
        if (guildBalance > 0) {
            // Add 10% button
            val add10Percent = ItemStack(Material.GREEN_CONCRETE)
                .name("§a➕ Add 10%")
                .lore("§7Add 10% of guild bank")
                .lore("§7Amount: §6${guildBalance / 10} coins")

            val add10GuiItem = GuiItem(add10Percent) {
                val amountToAdd = guildBalance / 10
                if (amountToAdd > 0 && wagerAmount + amountToAdd <= guildBalance) {
                    wagerAmount += amountToAdd
                    player.sendMessage("§a✅ Added §6$amountToAdd coins §ato wager")
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    player.sendMessage("§c❌ Insufficient funds or would exceed bank balance!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(add10GuiItem, 5, 2)

            // Add 25% button
            val add25Percent = ItemStack(Material.BLUE_CONCRETE)
                .name("§9➕ Add 25%")
                .lore("§7Add 25% of guild bank")
                .lore("§7Amount: §6${guildBalance / 4} coins")

            val add25GuiItem = GuiItem(add25Percent) {
                val amountToAdd = guildBalance / 4
                if (amountToAdd > 0 && wagerAmount + amountToAdd <= guildBalance) {
                    wagerAmount += amountToAdd
                    player.sendMessage("§a✅ Added §6$amountToAdd coins §ato wager")
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    player.sendMessage("§c❌ Insufficient funds or would exceed bank balance!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(add25GuiItem, 6, 2)

            // Wager All button
            val wagerAllItem = ItemStack(Material.RED_CONCRETE)
                .name("§c$ WAGER ALL")
                .lore("§7Wager entire guild bank!")
                .lore("§7Amount: §6$guildBalance coins")
                .lore("§7")
                .lore("§c⚠︎ HIGH RISK!")

            val wagerAllGuiItem = GuiItem(wagerAllItem) {
                if (guildBalance > 0) {
                    wagerAmount = guildBalance
                    player.sendMessage("§c$ ALL IN! §6$guildBalance coins §cwagered!")
                    player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                    open() // Refresh menu
                } else {
                    player.sendMessage("§c❌ No funds available to wager!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
            pane.addItem(wagerAllGuiItem, 7, 2)

            // Wager Enemy Bank button (if enemy guild has funds)
            val enemyBalance = targetGuild?.bankBalance ?: 0
            if (enemyBalance > 0) {
                val wagerEnemyItem = ItemStack(Material.PURPLE_CONCRETE)
                    .name("§5∩ MATCH ENEMY")
                    .lore("§7Wager to match enemy bank!")
                    .lore("§7Enemy Bank: §6$enemyBalance coins")
                    .lore("§7You would wager: §6$enemyBalance coins")
                    .lore("§7")
                    .lore("§c⚠︎ Must have sufficient funds!")

                val wagerEnemyGuiItem = GuiItem(wagerEnemyItem) {
                    if (enemyBalance > 0 && guildBalance >= enemyBalance) {
                        wagerAmount = enemyBalance
                        player.sendMessage("§5∩ MATCHING ENEMY! §6$enemyBalance coins §5wagered!")
                        player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                        open() // Refresh menu
                    } else if (enemyBalance > 0 && guildBalance < enemyBalance) {
                        player.sendMessage("§c❌ Insufficient funds to match enemy wager!")
                        player.sendMessage("§7Need: §6$enemyBalance coins, Have: §6$guildBalance coins")
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    } else {
                        player.sendMessage("§c❌ Enemy guild has no funds to match!")
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    }
                }
                pane.addItem(wagerEnemyGuiItem, 7, 3)
            }

            // Remove wager button
            if (wagerAmount > 0) {
                val removeWager = ItemStack(Material.GRAY_CONCRETE)
                    .name("§7➖ Remove All")
                    .lore("§7Remove entire wager")
                    .lore("§7Current: §6$wagerAmount coins")

                val removeGuiItem = GuiItem(removeWager) {
                    val removedAmount = wagerAmount
                    wagerAmount = 0
                    player.sendMessage("§7🗑️ Removed §6$removedAmount coins §7from wager")
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
                description = "Kill 10 enemy players"
            ))
        }

        val killObjective = selectedObjectives.first()
        val objectivesItem = ItemStack(Material.DIAMOND_SWORD)
            .name("§c⚔ War Objective: KILLS")
            .lore("§7Target: §f${killObjective.targetValue} enemy kills")
            .lore("§7")
            .lore("§7The first guild to reach the kill target wins!")
            .lore("§7Only kills against enemy guild members count.")
            .lore("§7")
            .lore("§7Available targets:")
            .lore("§7• §f5 kills §7(Quick skirmish)")
            .lore("§7• §f10 kills §7(Standard battle)")
            .lore("§7• §f25 kills §7(Extended war)")
            .lore("§7• §f50 kills §7(Epic campaign)")
            .lore("§7")
            .lore("§eClick to change kill target")

        val guiItem = GuiItem(objectivesItem) {
            cycleKillTarget()
            open() // Refresh menu
        }
        pane.addItem(guiItem, 5, 1)
    }

    private fun addWarTermsSection(pane: StaticPane) {
        val termsItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§e§ War Terms")
            .lore(if (warTerms != null) "§7Terms: §f$warTerms" else "§7No terms set")
            .lore("§7")
            .lore("§7Optional message to the defending guild")
            .lore("§7explaining your reasons for war")
            .lore("§7")
            .lore("§eClick to set terms")

        val guiItem = GuiItem(termsItem) {
            player.sendMessage("§e💬 Type your war terms in chat (or 'cancel' to skip):")
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
                .name("§4⚔ DECLARE WAR!")
                .lore("§7Target: §f${target.name}")
                .lore("§7Duration: §f${selectedDuration.toDays()} days")
                .lore("§7Objectives: §f${selectedObjectives.size}")
                .lore(if (wagerAmount > 0) {
                    "§7Wager: §6$wagerAmount coins"
                } else {
                    "§7Wager: §7None (honor only)"
                })
                .lore("§7")
                .lore("§c⚠︎ This will notify all members")
                .lore("§c⚠︎ of both guilds!")
                .also { item ->
                    if (wagerAmount > 0) {
                        item.lore("§c⚠︎ Funds will be held in escrow!")
                    }
                }
                .lore("§7")
                .lore("§eClick to declare war!")
        } else {
            ItemStack(Material.BARRIER)
                .name("§c❌ Cannot Declare War")
                .lore(when {
                    !canDeclare -> "§cYour guild cannot declare war right now"
                    hasActiveWar -> "§cAlready at war with this guild"
                    else -> "§cUnknown restriction"
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
                    player.sendMessage("§c❌ Insufficient guild bank funds for wager!")
                    player.sendMessage("§7Need: §6$wagerAmount coins")
                    player.sendMessage("§7Have: §6${guild.bankBalance} coins")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Check withdrawal permissions
                if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
                    player.sendMessage("§c❌ You don't have permission to withdraw from guild bank for wagers!")
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
                    player.sendMessage("§c❌ Failed to secure wager funds!")
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
                    player.sendMessage("§a⚔ WAR STARTED against ${target.name}!")
                    player.sendMessage("§7Hostile guild auto-accepted - battle begins now!")
                    player.sendMessage("§7Duration: §f${selectedDuration.toDays()} days")
                    if (selectedObjectives.isNotEmpty()) {
                        player.sendMessage("§7Objectives: §f${selectedObjectives.size} set")
                    }
                    player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)

                    // Close menu and return to war management
                    player.closeInventory()
                    menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))

                    // Notify both guilds
                    notifyGuildsOfWarDeclaration(war)
                    return
                } else {
                    player.sendMessage("§c❌ Failed to declare war!")
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
                    player.sendMessage("§6⚔ WAR DECLARATION SENT to ${target.name}!")
                    player.sendMessage("§7Duration: §f${selectedDuration.toDays()} days")
                    if (selectedObjectives.isNotEmpty()) {
                        player.sendMessage("§7Objectives: §f${selectedObjectives.size} set")
                    }
                    if (wagerAmount > 0) {
                        player.sendMessage("§7Wager: §6$wagerAmount coins §7(awaiting their match)")
                    }
                    player.sendMessage("§7They must accept your declaration for war to begin.")
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)

                    // Close menu and return to war management
                    player.closeInventory()
                    menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))

                    // Notify defending guild of the declaration
                    notifyGuildOfWarDeclaration(declaration)
                    return
                } else {
                    player.sendMessage("§c❌ Failed to send war declaration!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
            }
        } catch (e: Exception) {
            player.sendMessage("§c❌ Error declaring war: ${e.message}")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)

            // Refund wager on error
            if (wagerAmount > 0) {
                try {
                    bankService.deposit(guild.id, player.uniqueId, wagerAmount, "War wager refund (error)")
                    player.sendMessage("§7Wager funds have been refunded.")
                } catch (refundError: Exception) {
                    player.sendMessage("§c❌ Failed to refund wager! Contact an administrator.")
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
            type = ObjectiveType.KILLS,
            targetValue = newTarget,
            description = "Kill $newTarget enemy players"
        ))
        
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
    }

    private fun openObjectivesMenu() {
        val claimsEnabled = configService.loadConfig().claimsEnabled

        player.sendMessage("§eObjectives menu coming soon!")
        player.sendMessage("§7This will allow you to set custom war objectives like:")
        player.sendMessage("§7• Kill X enemy players")

        // Only show claim-related objectives if claims are enabled
        if (claimsEnabled) {
            player.sendMessage("§7• Capture X claims")
        }

        player.sendMessage("§7• Survive for X hours")
        // TODO: Implement objectives menu
    }

    private fun openGuildListMenu(guilds: List<Guild>) {
        player.sendMessage("§eGuild list menu coming soon!")
        player.sendMessage("§7This will show all ${guilds.size} available guilds in a paginated menu.")
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
                        Component.text("§4⚔ WAR DECLARED! ⚔"),
                        Component.text("§7Against §c${defendingGuild.name}"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Chat messages
                    onlinePlayer.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    onlinePlayer.sendMessage("§4⚔ §lWAR DECLARED! §4⚔")
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("§7Your guild §f${declaringGuild.name} §7has declared war on §c${defendingGuild.name}§7!")
                    onlinePlayer.sendMessage("§7Duration: §f${war.duration.toDays()} days")
                    if (war.objectives.isNotEmpty()) {
                        onlinePlayer.sendMessage("§7Objectives: §f${war.objectives.size} war goals set")
                    }
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("§7Wager: §6$wagerAmount coins §7(escrowed)")
                    }
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("§e⚡ Prepare for battle! Victory brings honor and rewards!")
                    onlinePlayer.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    
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
                        Component.text("§c⚠︎ UNDER ATTACK! ⚠︎"),
                        Component.text("§7War declared by §4${declaringGuild.name}"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Chat messages
                    onlinePlayer.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    onlinePlayer.sendMessage("§c⚠︎ §lWAR DECLARED AGAINST YOU! §c⚠︎")
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("§c${declaringGuild.name} §7has declared war on your guild §f${defendingGuild.name}§7!")
                    onlinePlayer.sendMessage("§7Duration: §f${war.duration.toDays()} days")
                    if (war.objectives.isNotEmpty()) {
                        onlinePlayer.sendMessage("§7Enemy Objectives: §f${war.objectives.size} goals")
                    }
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("§7Enemy Wager: §6$wagerAmount coins")
                        onlinePlayer.sendMessage("§7You must match this wager to accept!")
                    }
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("§e▊ Defend your guild! Rally your members and fight back!")
                    onlinePlayer.sendMessage("§7Use §f/guild war §7to manage the conflict")
                    onlinePlayer.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    
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
            .name("§c⬅️ Back")
            .lore("§7Return to war management")

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
                        net.kyori.adventure.text.Component.text("§c⚠︎ WAR DECLARATION! ⚠︎"),
                        net.kyori.adventure.text.Component.text("§7${declaringGuild.name} challenges you!"),
                        net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(500),
                            java.time.Duration.ofSeconds(4),
                            java.time.Duration.ofSeconds(1)
                        )
                    ))

                    // Send chat messages
                    onlinePlayer.sendMessage("§c═══════════════════════════════════")
                    onlinePlayer.sendMessage("§c⚠︎ WAR DECLARATION RECEIVED! ⚠︎")
                    onlinePlayer.sendMessage("§7From: §f${declaringGuild.name}")
                    onlinePlayer.sendMessage("§7Duration: §f${declaration.proposedDuration.toDays()} days")
                    onlinePlayer.sendMessage("§7Objectives: §f${declaration.objectives.size}")
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("§7Their Wager: §6$wagerAmount coins")
                        onlinePlayer.sendMessage("§7You must match to accept!")
                    }
                    if (declaration.terms != null) {
                        onlinePlayer.sendMessage("§7Terms: §f${declaration.terms}")
                    }
                    onlinePlayer.sendMessage("§7Expires: §f${declaration.remainingTime.toHours()}h")
                    onlinePlayer.sendMessage("")
                    onlinePlayer.sendMessage("§eUse §f/guild war §eto respond!")
                    onlinePlayer.sendMessage("§c═══════════════════════════════════")

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
                    onlinePlayer.sendMessage("§6⚔ War declaration sent to ${defendingGuild.name}!")
                    onlinePlayer.sendMessage("§7Duration: §f${declaration.proposedDuration.toDays()} days")
                    if (wagerAmount > 0) {
                        onlinePlayer.sendMessage("§7Wager: §6$wagerAmount coins")
                    }
                    onlinePlayer.sendMessage("§7Awaiting their response...")
                }
            }

        } catch (e: Exception) {
            player.sendMessage("§c❌ Failed to notify defending guild of war declaration!")
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
                player.sendMessage("§7War terms input cancelled")
                inputMode = null
                open() // Reopen menu
                return
            }

            // Validate terms length
            if (input.length > 200) {
                player.sendMessage("§c❌ War terms too long! Maximum 200 characters.")
                player.sendMessage("§7Please try again or type 'cancel' to skip:")
                return
            }

            warTerms = input
            inputMode = null
            player.sendMessage("§a✅ War terms set: §f\"$input\"")
            open() // Reopen menu with updated terms
        }
    }

    override fun onCancel(player: Player) {
        if (inputMode == "war_terms") {
            player.sendMessage("§7War terms input cancelled")
            inputMode = null
            open() // Reopen menu
        }
    }
}

