package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
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
import java.util.*

class GuildWarAcceptanceMenu(
    private val menuNavigator: MenuNavigator, 
    private val player: Player,
    private var guild: Guild,
    private val warDeclaration: WarDeclaration
) : Menu, KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        // Check permissions first
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage("§c❌ You don't have permission to respond to war declarations!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Check if declaration is still valid
        if (!warDeclaration.isValid) {
            player.sendMessage("§c❌ This war declaration has expired or is no longer valid!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val gui = ChestGui(5, "§4⚔ War Declaration - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 5)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Display war declaration details
        addWarDeclarationInfo(pane)
        
        // Response options
        addResponseOptions(pane)

        // Navigation
        addBackButton(pane, 8, 4)

        gui.show(player)
    }

    private fun addWarDeclarationInfo(pane: StaticPane) {
        val declaringGuild = guildService.getGuild(warDeclaration.declaringGuildId)
        if (declaringGuild == null) {
            player.sendMessage("§c❌ Error: Declaring guild not found!")
            return
        }

        // Declaring guild display with banner
        val declaringGuildItem = createGuildDisplayItem(declaringGuild, "§c⚔ DECLARING WAR")
        pane.addItem(GuiItem(declaringGuildItem), 1, 1)

        // VS indicator
        val vsItem = ItemStack.of(Material.BARRIER)
            .name("§4⚡ VS ⚡")
            .lore("§7War Declaration")
        pane.addItem(GuiItem(vsItem), 4, 1)

        // Your guild display
        val yourGuildItem = createGuildDisplayItem(guild, "§a🛡 YOUR GUILD")
        pane.addItem(GuiItem(yourGuildItem), 7, 1)

        // War details
        val detailsItem = ItemStack.of(Material.WRITTEN_BOOK)
            .name("§e📋 War Details")
            .lore("§7Duration: §f${warDeclaration.proposedDuration.toDays()} days")
            .lore("§7Objectives: §f${warDeclaration.objectives.size}")
            if (warDeclaration.objectives.isNotEmpty()) {
                warDeclaration.objectives.forEach { objective ->
                    detailsItem.lore("§7• §f${objective.description}")
                }
            }
            detailsItem.lore("§7")
            if (warDeclaration.wagerAmount > 0) {
                detailsItem.lore("§6💰 Wager: §f${warDeclaration.wagerAmount} gold")
                detailsItem.lore("§7  • You must match: §6${warDeclaration.wagerAmount} gold")
                detailsItem.lore("§7  • Total pot: §6${warDeclaration.wagerAmount * 2} gold")
                detailsItem.lore("§7  • Winner takes all!")
                detailsItem.lore("§7")
            }
            if (warDeclaration.terms != null) {
                detailsItem.lore("§7Terms: §f${warDeclaration.terms}")
                detailsItem.lore("§7")
            }
            detailsItem.lore("§7Expires: §f${warDeclaration.remainingTime.toHours()}h remaining")

        pane.addItem(GuiItem(detailsItem), 4, 0)
    }

    private fun createGuildDisplayItem(targetGuild: Guild, title: String): ItemStack {
        val memberCount = memberService.getGuildMembers(targetGuild.id).size

        // Try to use guild banner, fallback to mode-appropriate material
        val bannerItem = targetGuild.banner?.let { banner ->
            try {
                val deserialized = banner.deserializeToItemStack()
                deserialized?.clone() ?: ItemStack.of(Material.WHITE_BANNER)
            } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                ItemStack.of(Material.WHITE_BANNER)
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)

        return bannerItem
            .name(title)
            .lore("§7Guild: §f${targetGuild.name}")
            .lore("§7Members: §f$memberCount")
            .lore("§7Level: §f${targetGuild.level}")
            .lore("§7Mode: §f${targetGuild.mode}")
    }

    private fun addResponseOptions(pane: StaticPane) {
        // Accept button
        val acceptItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name("§a✅ ACCEPT WAR")
            .lore("§7Accept this war declaration")
            .lore("§7and begin the conflict!")
            .lore("§7")
            if (warDeclaration.wagerAmount > 0) {
                acceptItem.lore("§6💰 This will withdraw:")
                acceptItem.lore("§6  ${warDeclaration.wagerAmount} gold from guild bank")
                acceptItem.lore("§7")
            }
            acceptItem.lore("§a⚔ Battle begins immediately")
            acceptItem.lore("§aFirst to reach kill target wins!")

        val acceptGuiItem = GuiItem(acceptItem) {
            acceptWarDeclaration()
        }
        pane.addItem(acceptGuiItem, 2, 3)

        // Reject button
        val rejectItem = ItemStack.of(Material.REDSTONE_BLOCK)
            .name("§c❌ REJECT WAR")
            .lore("§7Reject this war declaration")
            .lore("§7")
            .lore("§c⚠ This will decline the war")
            .lore("§cNo conflict will occur")

        val rejectGuiItem = GuiItem(rejectItem) {
            rejectWarDeclaration()
        }
        pane.addItem(rejectGuiItem, 6, 3)
    }

    private fun acceptWarDeclaration() {
        try {
            // Handle wager matching if there's a wager
            if (warDeclaration.wagerAmount > 0) {
                // Refresh guild data to get current bank balance
                guild = guildService.getGuild(guild.id) ?: run {
                    player.sendMessage("§c❌ Error: Could not load guild data!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Check if guild has sufficient funds to match wager
                if (guild.bankBalance < warDeclaration.wagerAmount) {
                    player.sendMessage("§c❌ Insufficient guild bank funds to match wager!")
                    player.sendMessage("§7Need: §6${warDeclaration.wagerAmount} gold")
                    player.sendMessage("§7Have: §6${guild.bankBalance} gold")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Check withdraw permissions
                if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
                    player.sendMessage("§c❌ You don't have permission to withdraw from guild bank for wagers!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Get declaring guild info for description
                val declaringGuild = guildService.getGuild(warDeclaration.declaringGuildId)
                val declaringGuildName = declaringGuild?.name ?: "Unknown"

                // Withdraw matching wager amount from defending guild's bank
                val withdrawal = bankService.withdraw(
                    guildId = guild.id,
                    playerId = player.uniqueId,
                    amount = warDeclaration.wagerAmount,
                    description = "War wager match vs $declaringGuildName"
                )

                if (withdrawal == null) {
                    player.sendMessage("§c❌ Failed to withdraw wager funds from guild bank!")
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
            }

            val war = warService.acceptWarDeclaration(warDeclaration.id, player.uniqueId)
            if (war != null) {
                // Create wager if both guilds put up funds
                if (warDeclaration.wagerAmount > 0) {
                    val wager = warService.createWager(
                        warId = war.id,
                        declaringGuildWager = warDeclaration.wagerAmount,
                        defendingGuildWager = warDeclaration.wagerAmount
                    )

                    if (wager != null) {
                        player.sendMessage("§a⚔ War accepted! Battle begins now!")
                        player.sendMessage("§6💰 War pot: ${wager.totalPot} gold (winner takes all!)")
                    } else {
                        player.sendMessage("§a⚔ War accepted! Battle begins now!")
                        player.sendMessage("§e⚠ Warning: Failed to create wager escrow")
                    }
                } else {
                    player.sendMessage("§a⚔ War accepted! Battle begins now!")
                }

                player.sendMessage("§7Duration: §f${war.duration.toDays()} days")
                if (war.objectives.isNotEmpty()) {
                    player.sendMessage("§7Objectives: §f${war.objectives.size}")
                }
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                
                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                
                // Notify both guilds of war acceptance
                notifyGuildsOfWarAcceptance(war)
                
            } else {
                player.sendMessage("§c❌ Failed to accept war declaration!")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage("§c❌ Error accepting war: ${e.message}")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    private fun rejectWarDeclaration() {
        try {
            val success = warService.rejectWarDeclaration(warDeclaration.id, player.uniqueId)
            if (success) {
                player.sendMessage("§c❌ War declaration rejected!")
                player.sendMessage("§7The conflict has been declined.")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f)
                
                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                
                // Notify declaring guild of rejection
                notifyGuildOfWarRejection(warDeclaration.declaringGuildId)
                
            } else {
                player.sendMessage("§c❌ Failed to reject war declaration!")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage("§c❌ Error rejecting war: ${e.message}")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name("§c⬅ Back")
            .lore("§7Return to war management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun notifyGuildsOfWarAcceptance(war: War) {
        try {
            val declaringGuild = guildService.getGuild(war.declaringGuildId)
            val defendingGuild = guildService.getGuild(war.defendingGuildId)
            
            if (declaringGuild == null || defendingGuild == null) return
            
            // Notify declaring guild
            val declaringMembers = memberService.getGuildMembers(war.declaringGuildId)
            for (member in declaringMembers) {
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    onlinePlayer.showTitle(Title.title(
                        Component.text("§a⚔ WAR ACCEPTED! ⚔"),
                        Component.text("§7${defendingGuild.name} accepted - BATTLE BEGINS!"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(4), JavaDuration.ofSeconds(1))
                    ))
                    onlinePlayer.sendMessage("§a═══════════════════════════════════")
                    onlinePlayer.sendMessage("§a⚔ WAR DECLARATION ACCEPTED!")
                    onlinePlayer.sendMessage("§7Enemy Guild: §f${defendingGuild.name}")
                    onlinePlayer.sendMessage("§7Duration: §f${war.duration.toDays()} days")
                    onlinePlayer.sendMessage("§7Target: §f${war.objectives.firstOrNull()?.description ?: "No objectives"}")
                    onlinePlayer.sendMessage("§7")
                    onlinePlayer.sendMessage("§c⚔ THE BATTLE HAS BEGUN! ⚔")
                    onlinePlayer.sendMessage("§a═══════════════════════════════════")
                    onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                }
            }
            
            // Notify defending guild (excluding the player who accepted)
            val defendingMembers = memberService.getGuildMembers(war.defendingGuildId)
            for (member in defendingMembers) {
                if (member.playerId == player.uniqueId) continue // Skip the accepting player
                
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    onlinePlayer.showTitle(Title.title(
                        Component.text("§c⚔ WAR BEGINS! ⚔"),
                        Component.text("§7War against ${declaringGuild.name} - FIGHT!"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(4), JavaDuration.ofSeconds(1))
                    ))
                    onlinePlayer.sendMessage("§c═══════════════════════════════════")
                    onlinePlayer.sendMessage("§c⚔ WAR HAS BEEN DECLARED!")
                    onlinePlayer.sendMessage("§7Enemy Guild: §f${declaringGuild.name}")
                    onlinePlayer.sendMessage("§7Duration: §f${war.duration.toDays()} days")
                    onlinePlayer.sendMessage("§7Target: §f${war.objectives.firstOrNull()?.description ?: "No objectives"}")
                    onlinePlayer.sendMessage("§7")
                    onlinePlayer.sendMessage("§a⚔ YOUR GUILD ACCEPTED THE CHALLENGE!")
                    onlinePlayer.sendMessage("§c═══════════════════════════════════")
                    onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                }
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            println("Error notifying guilds of war acceptance: ${e.message}")
        }
    }

    private fun notifyGuildOfWarRejection(declaringGuildId: UUID) {
        try {
            val declaringGuild = guildService.getGuild(declaringGuildId) ?: return
            val declaringMembers = memberService.getGuildMembers(declaringGuildId)
            
            for (member in declaringMembers) {
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    // Send title
                    onlinePlayer.showTitle(Title.title(
                        Component.text("§c⚔ WAR REJECTED ⚔"),
                        Component.text("§7${guild.name} declined your declaration"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Send chat messages
                    onlinePlayer.sendMessage("§c═══════════════════════════════════")
                    onlinePlayer.sendMessage("§c⚔ WAR DECLARATION REJECTED!")
                    onlinePlayer.sendMessage("§7")
                    onlinePlayer.sendMessage("§7Guild: §f${guild.name}")
                    onlinePlayer.sendMessage("§7Response: §cDECLINED")
                    onlinePlayer.sendMessage("§7")
                    onlinePlayer.sendMessage("§7They chose not to engage in battle.")
                    onlinePlayer.sendMessage("§7Consider diplomatic solutions or")
                    onlinePlayer.sendMessage("§7find other opponents willing to fight!")
                    onlinePlayer.sendMessage("§c═══════════════════════════════════")
                    
                    // Play sound
                    onlinePlayer.playSound(onlinePlayer.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
                    onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Log error but don't break the rejection process
            println("Error notifying guild of war rejection: ${e.message}")
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

