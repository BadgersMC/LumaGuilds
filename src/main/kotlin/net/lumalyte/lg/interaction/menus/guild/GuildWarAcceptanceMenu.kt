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
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildWarAcceptanceMenu(
    private val menuNavigator: MenuNavigator, 
    private val player: Player,
    private var guild: Guild,
    private val warDeclaration: WarDeclaration
, private val messageService: MessageService) : Menu, KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        // Check permissions first
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to respond to war declarations!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Check if declaration is still valid
        if (!warDeclaration.isValid) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ This war declaration has expired or is no longer valid!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>⚔️ War Declaration - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 5)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
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
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error: Declaring guild not found!")
            return
        }

        // Declaring guild display with banner
        val declaringGuildItem = createGuildDisplayItem(declaringGuild, "<red>⚔️ DECLARING WAR")
        pane.addItem(GuiItem(declaringGuildItem), 1, 1)

        // VS indicator
        val vsItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<dark_red>⚡ VS ⚡")
            .addAdventureLore(player, messageService, "<gray>War Declaration")
        pane.addItem(GuiItem(vsItem), 4, 1)

        // Your guild display
        val yourGuildItem = createGuildDisplayItem(guild, "<green>🛡️ YOUR GUILD")
        pane.addItem(GuiItem(yourGuildItem), 7, 1)

        // War details
        val detailsItem = ItemStack(Material.WRITTEN_BOOK)
            .setAdventureName(player, messageService, "<yellow>📋 War Details")
            .addAdventureLore(player, messageService, "<gray>Duration: <white>${warDeclaration.proposedDuration.toDays()} days")
            .addAdventureLore(player, messageService, "<gray>Objectives: <white>${warDeclaration.objectives.size}")
            if (warDeclaration.objectives.isNotEmpty()) {
                warDeclaration.objectives.forEach { objective ->
                    detailsItem.addAdventureLore(player, messageService, "<gray>• <white>${objective.description}")
                }
            }
            detailsItem.addAdventureLore(player, messageService, "<gray>")
            if (warDeclaration.terms != null) {
                detailsItem.addAdventureLore(player, messageService, "<gray>Terms: <white>${warDeclaration.terms}")
                detailsItem.addAdventureLore(player, messageService, "<gray>")
            }
            detailsItem.addAdventureLore(player, messageService, "<gray>Expires: <white>${warDeclaration.remainingTime.toHours()}h remaining")

        pane.addItem(GuiItem(detailsItem), 4, 0)
    }

    private fun createGuildDisplayItem(targetGuild: Guild, title: String): ItemStack {
        val memberCount = memberService.getGuildMembers(targetGuild.id).size

        // Try to use guild banner, fallback to mode-appropriate material
        val bannerItem = if (targetGuild.banner != null) {
            try {
                val deserialized = targetGuild.banner!!.deserializeToItemStack()
                if (deserialized != null) {
                    deserialized.clone()
                } else {
                    ItemStack(Material.WHITE_BANNER)
                }
            } catch (e: Exception) {
                ItemStack(Material.WHITE_BANNER)
            }
        } else {
            ItemStack(Material.WHITE_BANNER)
        }

        return bannerItem
            .name(title)
            .addAdventureLore(player, messageService, "<gray>Guild: <white>${targetGuild.name}")
            .addAdventureLore(player, messageService, "<gray>Members: <white>$memberCount")
            .addAdventureLore(player, messageService, "<gray>Level: <white>${targetGuild.level}")
            .addAdventureLore(player, messageService, "<gray>Mode: <white>${targetGuild.mode}")
    }

    private fun addResponseOptions(pane: StaticPane) {
        // Accept button
        val acceptItem = ItemStack(Material.EMERALD_BLOCK)
            .setAdventureName(player, messageService, "<green>✅ ACCEPT WAR")
            .addAdventureLore(player, messageService, "<gray>Accept this war declaration")
            .addAdventureLore(player, messageService, "<gray>and begin the conflict!")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<green>⚔️ Battle begins immediately")
            .addAdventureLore(player, messageService, "<green>First to reach kill target wins!")

        val acceptGuiItem = GuiItem(acceptItem) {
            acceptWarDeclaration()
        }
        pane.addItem(acceptGuiItem, 2, 3)

        // Reject button
        val rejectItem = ItemStack(Material.REDSTONE_BLOCK)
            .setAdventureName(player, messageService, "<red>❌ REJECT WAR")
            .addAdventureLore(player, messageService, "<gray>Reject this war declaration")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>⚠️ This will decline the war")
            .addAdventureLore(player, messageService, "<red>No conflict will occur")

        val rejectGuiItem = GuiItem(rejectItem) {
            rejectWarDeclaration()
        }
        pane.addItem(rejectGuiItem, 6, 3)
    }

    private fun acceptWarDeclaration() {
        try {
            // TODO: Handle wager matching if there's a wager
            
            val war = warService.acceptWarDeclaration(warDeclaration.id, player.uniqueId)
            if (war != null) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>⚔️ War accepted! Battle begins now!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Duration: <white>${war.duration.toDays()} days")
                if (war.objectives.isNotEmpty()) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Objectives: <white>${war.objectives.size}")
                }
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                
                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                
                // Notify both guilds of war acceptance
                notifyGuildsOfWarAcceptance(war)
                
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to accept war declaration!")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error accepting war: ${e.message}")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    private fun rejectWarDeclaration() {
        try {
            val success = warService.rejectWarDeclaration(warDeclaration.id, player.uniqueId)
            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ War declaration rejected!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>The conflict has been declined.")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f)
                
                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                
                // Notify declaring guild of rejection
                notifyGuildOfWarRejection(warDeclaration.declaringGuildId)
                
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to reject war declaration!")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error rejecting war: ${e.message}")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
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
                        Component.text("<green>⚔️ WAR ACCEPTED! ⚔️"),
                        Component.text("<gray>${defendingGuild.name} accepted - BATTLE BEGINS!"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(4), JavaDuration.ofSeconds(1))
                    ))
                    onlinePlayer.sendMessage("<green>═══════════════════════════════════")
                    onlinePlayer.sendMessage("<green>⚔️ WAR DECLARATION ACCEPTED!")
                    onlinePlayer.sendMessage("<gray>Enemy Guild: <white>${defendingGuild.name}")
                    onlinePlayer.sendMessage("<gray>Duration: <white>${war.duration.toDays()} days")
                    onlinePlayer.sendMessage("<gray>Target: <white>${war.objectives.firstOrNull()?.description ?: "No objectives"}")
                    onlinePlayer.sendMessage("<gray>")
                    onlinePlayer.sendMessage("<red>⚔️ THE BATTLE HAS BEGUN! ⚔️")
                    onlinePlayer.sendMessage("<green>═══════════════════════════════════")
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
                        Component.text("<red>⚔️ WAR BEGINS! ⚔️"),
                        Component.text("<gray>War against ${declaringGuild.name} - FIGHT!"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(4), JavaDuration.ofSeconds(1))
                    ))
                    onlinePlayer.sendMessage("<red>═══════════════════════════════════")
                    onlinePlayer.sendMessage("<red>⚔️ WAR HAS BEEN DECLARED!")
                    onlinePlayer.sendMessage("<gray>Enemy Guild: <white>${declaringGuild.name}")
                    onlinePlayer.sendMessage("<gray>Duration: <white>${war.duration.toDays()} days")
                    onlinePlayer.sendMessage("<gray>Target: <white>${war.objectives.firstOrNull()?.description ?: "No objectives"}")
                    onlinePlayer.sendMessage("<gray>")
                    onlinePlayer.sendMessage("<green>⚔️ YOUR GUILD ACCEPTED THE CHALLENGE!")
                    onlinePlayer.sendMessage("<red>═══════════════════════════════════")
                    onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                }
            }
        } catch (e: Exception) {
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
                        Component.text("<red>⚔️ WAR REJECTED ⚔️"),
                        Component.text("<gray>${guild.name} declined your declaration"),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Send chat messages
                    onlinePlayer.sendMessage("<red>═══════════════════════════════════")
                    onlinePlayer.sendMessage("<red>⚔️ WAR DECLARATION REJECTED!")
                    onlinePlayer.sendMessage("<gray>")
                    onlinePlayer.sendMessage("<gray>Guild: <white>${guild.name}")
                    onlinePlayer.sendMessage("<gray>Response: <red>DECLINED")
                    onlinePlayer.sendMessage("<gray>")
                    onlinePlayer.sendMessage("<gray>They chose not to engage in battle.")
                    onlinePlayer.sendMessage("<gray>Consider diplomatic solutions or")
                    onlinePlayer.sendMessage("<gray>find other opponents willing to fight!")
                    onlinePlayer.sendMessage("<red>═══════════════════════════════════")
                    
                    // Play sound
                    onlinePlayer.playSound(onlinePlayer.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
                    onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
        } catch (e: Exception) {
            // Log error but don't break the rejection process
            println("Error notifying guild of war rejection: ${e.message}")
        }
    }}

