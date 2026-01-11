package net.lumalyte.lg.infrastructure.services.apollo

import com.lunarclient.apollo.Apollo
import com.lunarclient.apollo.module.notification.Notification
import com.lunarclient.apollo.module.notification.NotificationModule
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * Manages Apollo notification integration for guild events.
 * Shows rich notifications for invites, joins, promotions, war declarations, KOTH captures, etc.
 */
class GuildNotificationService(
    private val plugin: Plugin,
    private val lunarClientService: LunarClientService,
    private val guildService: GuildService,
    private val memberService: MemberService
) {
    private val logger = LoggerFactory.getLogger(GuildNotificationService::class.java)

    // Apollo notification module
    private val notificationModule: NotificationModule by lazy {
        Apollo.getModuleManager().getModule(NotificationModule::class.java)
    }

    // Notification icons - loaded from config with fallbacks
    private val iconWelcome: String by lazy {
        plugin.config.getString("apollo.notifications.icons.welcome", "minecraft:textures/item/banner.png") ?: "minecraft:textures/item/banner.png"
    }
    private val iconGuild: String by lazy {
        plugin.config.getString("apollo.notifications.icons.guild", "minecraft:textures/item/banner.png") ?: "minecraft:textures/item/banner.png"
    }
    private val iconInvite: String by lazy {
        plugin.config.getString("apollo.notifications.icons.invite", "minecraft:textures/item/writable_book.png") ?: "minecraft:textures/item/writable_book.png"
    }
    private val iconJoin: String by lazy {
        plugin.config.getString("apollo.notifications.icons.join", "minecraft:textures/item/green_banner.png") ?: "minecraft:textures/item/green_banner.png"
    }
    private val iconLeave: String by lazy {
        plugin.config.getString("apollo.notifications.icons.leave", "minecraft:textures/item/red_banner.png") ?: "minecraft:textures/item/red_banner.png"
    }
    private val iconPromotion: String by lazy {
        plugin.config.getString("apollo.notifications.icons.promotion", "minecraft:textures/item/experience_bottle.png") ?: "minecraft:textures/item/experience_bottle.png"
    }
    private val iconWar: String by lazy {
        plugin.config.getString("apollo.notifications.icons.war", "minecraft:textures/item/netherite_sword.png") ?: "minecraft:textures/item/netherite_sword.png"
    }
    private val iconPeace: String by lazy {
        plugin.config.getString("apollo.notifications.icons.peace", "minecraft:textures/item/golden_apple.png") ?: "minecraft:textures/item/golden_apple.png"
    }
    private val iconNeutral: String by lazy {
        plugin.config.getString("apollo.notifications.icons.neutral", "minecraft:textures/item/apple.png") ?: "minecraft:textures/item/apple.png"
    }
    private val iconKoth: String by lazy {
        plugin.config.getString("apollo.notifications.icons.koth", "minecraft:textures/item/golden_helmet.png") ?: "minecraft:textures/item/golden_helmet.png"
    }

    /**
     * Send notification to a specific player.
     */
    private fun sendNotification(
        player: Player,
        title: Component,
        description: Component,
        iconPath: String,
        durationSeconds: Int = 5
    ) {
        if (!isNotificationsEnabled()) {
            logger.info("Notifications disabled in config")
            return
        }
        if (!lunarClientService.isLunarClient(player)) {
            logger.info("${player.name} is not a Lunar Client user")
            return
        }

        try {
            val apolloPlayer = lunarClientService.getApolloPlayer(player)
            if (apolloPlayer == null) {
                logger.warn("Could not get ApolloPlayer for ${player.name}")
                return
            }

            logger.info("Building notification for ${player.name} with icon: $iconPath")
            val notification = Notification.builder()
                .titleComponent(title)
                .descriptionComponent(description)
                .resourceLocation(iconPath)
                .displayTime(Duration.ofSeconds(durationSeconds.toLong()))
                .build()

            logger.info("Displaying notification to ${player.name}")
            notificationModule.displayNotification(apolloPlayer, notification)
            logger.info("Successfully sent notification to ${player.name}")
        } catch (e: Exception) {
            logger.error("Failed to send notification to ${player.name}: ${e.message}", e)
        }
    }

    /**
     * Send notification to all online members of a guild.
     */
    private fun sendGuildNotification(
        guildId: UUID,
        title: Component,
        description: Component,
        iconPath: String,
        durationSeconds: Int = 5,
        excludePlayer: UUID? = null
    ) {
        try {
            val members = memberService.getGuildMembers(guildId)
            val onlineMembers = members
                .filter { it.playerId != excludePlayer }
                .mapNotNull { Bukkit.getPlayer(it.playerId) }
                .filter { lunarClientService.isLunarClient(it) }

            onlineMembers.forEach { player ->
                sendNotification(player, title, description, iconPath, durationSeconds)
            }
        } catch (e: Exception) {
            logger.debug("Failed to send guild notification: ${e.message}")
        }
    }

    // ========================================
    // Welcome Notification
    // ========================================

    /**
     * Send welcome notification to Lunar Client users on join.
     */
    fun sendWelcomeNotification(player: Player) {
        logger.info("sendWelcomeNotification called for ${player.name}")

        if (!plugin.config.getBoolean("apollo.notifications.welcome_notification", true)) {
            logger.info("Welcome notification disabled in config")
            return
        }
        if (!lunarClientService.isLunarClient(player)) {
            logger.info("${player.name} is not using Lunar Client")
            return
        }

        logger.info("Sending welcome notification to ${player.name}")
        val title = Component.text("Welcome to the Server!", NamedTextColor.GOLD, TextDecoration.BOLD)
        val description = Component.text("You're using Lunar Client!", NamedTextColor.GRAY)
            .append(Component.text("\n"))
            .append(Component.text("Bonus: ", NamedTextColor.GREEN))
            .append(Component.text("2x Guild Progression XP", NamedTextColor.YELLOW))

        sendNotification(player, title, description, iconWelcome, 8)
        logger.info("Welcome notification sent to ${player.name}")
    }

    // ========================================
    // Invite Notifications
    // ========================================

    /**
     * Notify player they've been invited to a guild.
     */
    fun notifyGuildInvite(playerId: UUID, guildName: String, inviterName: String) {
        if (!plugin.config.getBoolean("apollo.notifications.guild_invites", true)) return

        val player = Bukkit.getPlayer(playerId) ?: return

        val title = Component.text("Guild Invite", NamedTextColor.GOLD, TextDecoration.BOLD)
        val description = Component.text("$inviterName invited you to ", NamedTextColor.GRAY)
            .append(Component.text(guildName, NamedTextColor.YELLOW))

        sendNotification(player, title, description, iconInvite, 7)
    }

    /**
     * Notify guild that an invite was accepted.
     */
    fun notifyInviteAccepted(guildId: UUID, playerName: String) {
        if (!plugin.config.getBoolean("apollo.notifications.member_events", true)) return

        val guild = guildService.getGuild(guildId) ?: return

        val title = Component.text("Invite Accepted", NamedTextColor.GREEN, TextDecoration.BOLD)
        val description = Component.text("$playerName accepted invitation to ", NamedTextColor.GRAY)
            .append(Component.text(guild.name, NamedTextColor.YELLOW))

        sendGuildNotification(guildId, title, description, iconJoin, 5)
    }

    // ========================================
    // Member Join/Leave Notifications
    // ========================================

    /**
     * Notify guild that a member joined.
     */
    fun notifyMemberJoin(guildId: UUID, playerName: String, playerId: UUID) {
        if (!plugin.config.getBoolean("apollo.notifications.member_events", true)) return

        val guild = guildService.getGuild(guildId) ?: return

        val title = Component.text("Member Joined", NamedTextColor.GREEN, TextDecoration.BOLD)
        val description = Component.text("$playerName joined ", NamedTextColor.GRAY)
            .append(Component.text(guild.name, NamedTextColor.YELLOW))

        sendGuildNotification(guildId, title, description, iconJoin, 4, excludePlayer = playerId)
    }

    /**
     * Notify guild that a member left.
     */
    fun notifyMemberLeave(guildId: UUID, playerName: String, wasKicked: Boolean = false) {
        if (!plugin.config.getBoolean("apollo.notifications.member_events", true)) return

        val guild = guildService.getGuild(guildId) ?: return

        val title = if (wasKicked) {
            Component.text("Member Kicked", NamedTextColor.RED, TextDecoration.BOLD)
        } else {
            Component.text("Member Left", NamedTextColor.YELLOW, TextDecoration.BOLD)
        }

        val action = if (wasKicked) "was kicked from" else "left"
        val description = Component.text("$playerName $action ", NamedTextColor.GRAY)
            .append(Component.text(guild.name, NamedTextColor.YELLOW))

        sendGuildNotification(guildId, title, description, iconLeave, 4)
    }

    // ========================================
    // Rank Change Notifications
    // ========================================

    /**
     * Notify player and guild about promotion.
     */
    fun notifyPromotion(playerId: UUID, guildId: UUID, newRankName: String, promoterName: String) {
        if (!plugin.config.getBoolean("apollo.notifications.member_events", true)) return

        val player = Bukkit.getPlayer(playerId)
        val guild = guildService.getGuild(guildId) ?: return

        // Notify the promoted player
        if (player != null) {
            val title = Component.text("Promoted!", NamedTextColor.GREEN, TextDecoration.BOLD)
            val description = Component.text("You were promoted to ", NamedTextColor.GRAY)
                .append(Component.text(newRankName, NamedTextColor.GOLD))
                .append(Component.text(" by $promoterName", NamedTextColor.GRAY))

            sendNotification(player, title, description, iconPromotion, 6)
        }

        // Notify the guild
        val guildTitle = Component.text("Member Promoted", NamedTextColor.GREEN, TextDecoration.BOLD)
        val guildDescription = Component.text("${player?.name ?: "Player"} promoted to ", NamedTextColor.GRAY)
            .append(Component.text(newRankName, NamedTextColor.GOLD))

        sendGuildNotification(guildId, guildTitle, guildDescription, iconPromotion, 4, excludePlayer = playerId)
    }

    /**
     * Notify player and guild about demotion.
     */
    fun notifyDemotion(playerId: UUID, guildId: UUID, newRankName: String, demoterName: String) {
        if (!plugin.config.getBoolean("apollo.notifications.member_events", true)) return

        val player = Bukkit.getPlayer(playerId)
        val guild = guildService.getGuild(guildId) ?: return

        // Notify the demoted player
        if (player != null) {
            val title = Component.text("Demoted", NamedTextColor.RED, TextDecoration.BOLD)
            val description = Component.text("You were demoted to ", NamedTextColor.GRAY)
                .append(Component.text(newRankName, NamedTextColor.YELLOW))
                .append(Component.text(" by $demoterName", NamedTextColor.GRAY))

            sendNotification(player, title, description, iconPromotion, 6)
        }

        // Notify the guild
        val guildTitle = Component.text("Member Demoted", NamedTextColor.YELLOW, TextDecoration.BOLD)
        val guildDescription = Component.text("${player?.name ?: "Player"} demoted to ", NamedTextColor.GRAY)
            .append(Component.text(newRankName, NamedTextColor.YELLOW))

        sendGuildNotification(guildId, guildTitle, guildDescription, iconPromotion, 4, excludePlayer = playerId)
    }

    // ========================================
    // War/Peace/Neutral Notifications
    // ========================================

    /**
     * Notify guilds about relation change (war/peace/neutral).
     */
    fun notifyRelationChange(
        guild1Id: UUID,
        guild2Id: UUID,
        relationType: RelationType,
        initiatorGuildId: UUID? = null
    ) {
        if (!plugin.config.getBoolean("apollo.notifications.war_events", true)) return

        val guild1 = guildService.getGuild(guild1Id) ?: return
        val guild2 = guildService.getGuild(guild2Id) ?: return

        val (title, icon, color) = when (relationType) {
            RelationType.ENEMY -> Triple(
                Component.text("War Declared!", NamedTextColor.RED, TextDecoration.BOLD),
                iconWar,
                NamedTextColor.RED
            )
            RelationType.TRUCE -> Triple(
                Component.text("Peace Treaty", NamedTextColor.GREEN, TextDecoration.BOLD),
                iconPeace,
                NamedTextColor.GREEN
            )
            RelationType.NEUTRAL -> Triple(
                Component.text("Now Neutral", NamedTextColor.YELLOW, TextDecoration.BOLD),
                iconNeutral,
                NamedTextColor.YELLOW
            )
            RelationType.ALLY -> Triple(
                Component.text("Alliance Formed!", NamedTextColor.AQUA, TextDecoration.BOLD),
                iconPeace,
                NamedTextColor.AQUA
            )
        }

        // Notify guild 1
        val desc1 = when (relationType) {
            RelationType.ENEMY -> Component.text("You are now at war with ", NamedTextColor.GRAY)
                .append(Component.text(guild2.name, color))
            RelationType.TRUCE -> Component.text("You are now at peace with ", NamedTextColor.GRAY)
                .append(Component.text(guild2.name, color))
            RelationType.NEUTRAL -> Component.text("You are now neutral with ", NamedTextColor.GRAY)
                .append(Component.text(guild2.name, color))
            RelationType.ALLY -> Component.text("You are now allied with ", NamedTextColor.GRAY)
                .append(Component.text(guild2.name, color))
        }
        sendGuildNotification(guild1Id, title, desc1, icon, 7)

        // Notify guild 2
        val desc2 = when (relationType) {
            RelationType.ENEMY -> Component.text("You are now at war with ", NamedTextColor.GRAY)
                .append(Component.text(guild1.name, color))
            RelationType.TRUCE -> Component.text("You are now at peace with ", NamedTextColor.GRAY)
                .append(Component.text(guild1.name, color))
            RelationType.NEUTRAL -> Component.text("You are now neutral with ", NamedTextColor.GRAY)
                .append(Component.text(guild1.name, color))
            RelationType.ALLY -> Component.text("You are now allied with ", NamedTextColor.GRAY)
                .append(Component.text(guild1.name, color))
        }
        sendGuildNotification(guild2Id, title, desc2, icon, 7)
    }

    // ========================================
    // KOTH Capture Notifications
    // ========================================

    /**
     * Notify all online players that a guild captured KOTH.
     */
    fun notifyKothCapture(guildId: UUID, kothName: String = "KOTH") {
        val guild = guildService.getGuild(guildId) ?: return

        val title = Component.text("KOTH Captured!", NamedTextColor.GOLD, TextDecoration.BOLD)
        val description = Component.text(guild.name, NamedTextColor.YELLOW)
            .append(Component.text(" has captured ", NamedTextColor.GRAY))
            .append(Component.text(kothName, NamedTextColor.GOLD))

        // Send to all online Lunar Client users
        Bukkit.getOnlinePlayers()
            .filter { lunarClientService.isLunarClient(it) }
            .forEach { player ->
                sendNotification(player, title, description, iconKoth, 8)
            }
    }

    /**
     * Check if notifications module is enabled in config.
     */
    private fun isNotificationsEnabled(): Boolean {
        return plugin.config.getBoolean("apollo.enabled", true) &&
               plugin.config.getBoolean("apollo.notifications.enabled", true) &&
               lunarClientService.isApolloAvailable()
    }

    /**
     * Get statistics about notifications.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "notifications_enabled" to isNotificationsEnabled(),
            "lunar_client_users" to lunarClientService.getLunarClientCount()
        )
    }
}
