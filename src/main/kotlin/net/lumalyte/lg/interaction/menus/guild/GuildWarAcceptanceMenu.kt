package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

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
    private val lang: LangService by inject()

    override fun open() {
        // Check permissions first
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("menu.war_acceptance.feedback.no_permission"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Check if declaration is still valid
        if (!warDeclaration.isValid) {
            player.sendMessage(lang.msg("menu.war_acceptance.feedback.expired"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val gui = ChestGui(5, MenuTitleBuilder.build(guild.guiTheme, 5, lang.guiTitle("menu.war_acceptance.title", "guild" to guild.name)))
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
            player.sendMessage(lang.msg("menu.war_acceptance.feedback.declaring_guild_missing"))
            return
        }

        // Declaring guild display with banner
        val declaringGuildItem = createGuildDisplayItem(declaringGuild, lang.gui("menu.war_acceptance.guild.declaring"))
        pane.addItem(GuiItem(declaringGuildItem), 1, 1)

        // VS indicator
        val vsItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.war_acceptance.vs.name"))
            .lore(lang.gui("menu.war_acceptance.vs.description"))
        pane.addItem(GuiItem(vsItem), 4, 1)

        // Your guild display
        val yourGuildItem = createGuildDisplayItem(guild, lang.gui("menu.war_acceptance.guild.yours"))
        pane.addItem(GuiItem(yourGuildItem), 7, 1)

        // War details
        val detailsItem = ItemStack.of(Material.WRITTEN_BOOK)
            .name(lang.gui("menu.war_acceptance.details.name"))
            .lore(lang.gui("menu.war_acceptance.details.duration", "days" to warDeclaration.proposedDuration.toDays()))
            .lore(lang.gui("menu.war_acceptance.details.objectives", "count" to warDeclaration.objectives.size))
            if (warDeclaration.objectives.isNotEmpty()) {
                warDeclaration.objectives.forEach { objective ->
                    detailsItem.lore(lang.gui("menu.war_acceptance.details.objective", "objective" to objective.description))
                }
            }
            detailsItem.lore(lang.gui("menu.common.blank"))
            if (warDeclaration.wagerAmount > 0) {
                detailsItem.lore(lang.gui("menu.war_acceptance.details.wager", "amount" to warDeclaration.wagerAmount))
                detailsItem.lore(lang.gui("menu.war_acceptance.details.match", "amount" to warDeclaration.wagerAmount))
                detailsItem.lore(lang.gui("menu.war_acceptance.details.pot", "amount" to warDeclaration.wagerAmount * 2))
                detailsItem.lore(lang.gui("menu.war_acceptance.details.winner"))
                detailsItem.lore(lang.gui("menu.common.blank"))
            }
            if (warDeclaration.terms != null) {
                detailsItem.lore(lang.gui("menu.war_acceptance.details.terms", "terms" to warDeclaration.terms!!))
                detailsItem.lore(lang.gui("menu.common.blank"))
            }
            detailsItem.lore(lang.gui("menu.war_acceptance.details.expires", "hours" to warDeclaration.remainingTime.toHours()))

        pane.addItem(GuiItem(detailsItem), 4, 0)
    }

    private fun createGuildDisplayItem(targetGuild: Guild, title: Component): ItemStack {
        val memberCount = memberService.getGuildMembers(targetGuild.id).size

        // Try to use guild banner, fallback to mode-appropriate material
        val bannerItem = targetGuild.banner?.let { banner ->
            try {
                val deserialized = banner.deserializeToItemStack()
                deserialized?.clone() ?: ItemStack.of(Material.WHITE_BANNER)
            } catch (e: Exception) {
                // Menu operation - log and fall back to white banner to prevent UI failure
                org.slf4j.LoggerFactory.getLogger(GuildWarAcceptanceMenu::class.java)
                    .warn("Failed to deserialize guild banner for ${targetGuild.name}: ${e.message}")
                ItemStack.of(Material.WHITE_BANNER)
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)

        return bannerItem
            .name(title)
            .lore(lang.gui("menu.war_acceptance.guild.name", "guild" to targetGuild.name))
            .lore(lang.gui("menu.war_acceptance.guild.members", "count" to memberCount))
            .lore(lang.gui("menu.war_acceptance.guild.level", "level" to targetGuild.level))
            .lore(lang.gui("menu.war_acceptance.guild.mode", "mode" to targetGuild.mode))
    }

    private fun addResponseOptions(pane: StaticPane) {
        // Accept button
        val acceptItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name(lang.gui("menu.war_acceptance.accept.name"))
            .lore(lang.gui("menu.war_acceptance.accept.description"))
            .lore(lang.gui("menu.war_acceptance.accept.begin"))
            .lore(lang.gui("menu.common.blank"))
            if (warDeclaration.wagerAmount > 0) {
                acceptItem.lore(lang.gui("menu.war_acceptance.accept.withdraw"))
                acceptItem.lore(lang.gui("menu.war_acceptance.accept.amount", "amount" to warDeclaration.wagerAmount))
                acceptItem.lore(lang.gui("menu.common.blank"))
            }
            acceptItem.lore(lang.gui("menu.war_acceptance.accept.immediate"))
            acceptItem.lore(lang.gui("menu.war_acceptance.accept.target"))

        val acceptGuiItem = GuiItem(acceptItem) {
            acceptWarDeclaration()
        }
        pane.addItem(acceptGuiItem, 2, 3)

        // Reject button
        val rejectItem = ItemStack.of(Material.REDSTONE_BLOCK)
            .name(lang.gui("menu.war_acceptance.reject.name"))
            .lore(lang.gui("menu.war_acceptance.reject.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.war_acceptance.reject.warning"))
            .lore(lang.gui("menu.war_acceptance.reject.no_conflict"))

        val rejectGuiItem = GuiItem(rejectItem) {
            rejectWarDeclaration()
        }
        pane.addItem(rejectGuiItem, 6, 3)
    }

    private fun acceptWarDeclaration() {
        try {
            // REQ-039: escrow happens in the war service (acceptWarDeclaration →
            // createWager deducts both guilds). The menu only pre-checks funds for
            // a friendly error; it must NOT withdraw or create the wager itself.
            if (warDeclaration.wagerAmount > 0) {
                // Refresh guild data to get current bank balance
                guild = guildService.getGuild(guild.id) ?: run {
                    player.sendMessage(lang.msg("menu.war_acceptance.feedback.guild_load_failed"))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Check if guild has sufficient funds to match wager
                val currentBalance = bankService.getBalance(guild.id)
                if (currentBalance < warDeclaration.wagerAmount) {
                    player.sendMessage(lang.msg("menu.war_acceptance.feedback.insufficient_funds"))
                    player.sendMessage(lang.msg("menu.war_acceptance.feedback.need", "amount" to warDeclaration.wagerAmount))
                    player.sendMessage(lang.msg("menu.war_acceptance.feedback.have", "amount" to currentBalance))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }

                // Check withdraw permissions
                if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
                    player.sendMessage(lang.msg("menu.war_acceptance.feedback.no_withdraw_permission"))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    return
                }
            }

            val war = warService.acceptWarDeclaration(warDeclaration.id, player.uniqueId)
            if (war != null) {
                player.sendMessage(lang.msg("menu.war_acceptance.feedback.accepted"))
                if (warDeclaration.wagerAmount > 0) {
                    val wager = warService.getWager(war.id)
                    if (wager != null) {
                        player.sendMessage(lang.msg("menu.war_acceptance.feedback.pot", "amount" to wager.totalPot))
                    } else {
                        player.sendMessage(lang.msg("menu.war_acceptance.feedback.escrow_failed"))
                    }
                }

                player.sendMessage(lang.msg("menu.war_acceptance.feedback.duration", "days" to war.duration.toDays()))
                if (war.objectives.isNotEmpty()) {
                    player.sendMessage(lang.msg("menu.war_acceptance.feedback.objectives", "count" to war.objectives.size))
                }
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)
                
                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                
                // Notify both guilds of war acceptance
                notifyGuildsOfWarAcceptance(war)
                
            } else {
                player.sendMessage(lang.msg("menu.war_acceptance.feedback.accept_failed"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.war_acceptance.feedback.accept_error", "error" to (e.message ?: lang.raw("general.unknown"))))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    private fun rejectWarDeclaration() {
        try {
            val success = warService.rejectWarDeclaration(warDeclaration.id, player.uniqueId)
            if (success) {
                player.sendMessage(lang.msg("menu.war_acceptance.feedback.rejected"))
                player.sendMessage(lang.msg("menu.war_acceptance.feedback.declined"))
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f)
                
                // Close menu and return to war management
                player.closeInventory()
                menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                
                // Notify declaring guild of rejection
                notifyGuildOfWarRejection(warDeclaration.declaringGuildId)
                
            } else {
                player.sendMessage(lang.msg("menu.war_acceptance.feedback.reject_failed"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.war_acceptance.feedback.reject_error", "error" to (e.message ?: lang.raw("general.unknown"))))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.war_declaration.item.back.name"))
            .lore(lang.gui("menu.war_declaration.item.back.lore"))

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
                        lang.msg("menu.war_acceptance.notification.accepted.title"),
                        lang.msg("menu.war_acceptance.notification.accepted.subtitle", "guild" to defendingGuild.name),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(4), JavaDuration.ofSeconds(1))
                    ))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.accepted.border"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.accepted.header"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.enemy", "guild" to defendingGuild.name))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.duration", "days" to war.duration.toDays()))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.target", "objective" to (war.objectives.firstOrNull()?.description ?: lang.raw("menu.war_acceptance.notification.no_objectives"))))
                    onlinePlayer.sendMessage(lang.msg("menu.common.blank"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.accepted.begun"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.accepted.border"))
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
                        lang.msg("menu.war_acceptance.notification.defending.title"),
                        lang.msg("menu.war_acceptance.notification.defending.subtitle", "guild" to declaringGuild.name),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(4), JavaDuration.ofSeconds(1))
                    ))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.defending.border"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.defending.header"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.enemy", "guild" to declaringGuild.name))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.duration", "days" to war.duration.toDays()))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.target", "objective" to (war.objectives.firstOrNull()?.description ?: lang.raw("menu.war_acceptance.notification.no_objectives"))))
                    onlinePlayer.sendMessage(lang.msg("menu.common.blank"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.defending.challenge"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.defending.border"))
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
                        lang.msg("menu.war_acceptance.notification.rejected.title"),
                        lang.msg("menu.war_acceptance.notification.rejected.subtitle", "guild" to guild.name),
                        Title.Times.times(JavaDuration.ofMillis(500), JavaDuration.ofSeconds(3), JavaDuration.ofSeconds(1))
                    ))
                    
                    // Send chat messages
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.border"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.header"))
                    onlinePlayer.sendMessage(lang.msg("menu.common.blank"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.guild", "guild" to guild.name))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.response"))
                    onlinePlayer.sendMessage(lang.msg("menu.common.blank"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.decision"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.diplomacy"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.opponents"))
                    onlinePlayer.sendMessage(lang.msg("menu.war_acceptance.notification.rejected.border"))
                    
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

