package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildRecruitmentService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.util.UUID

private const val TICKS_PER_SECOND = 20L

/** Non-interrupting discovery and private digest notifications with durable replay protection. */
internal class GuildRecruitmentListener(
    private val plugin: Plugin,
    private val recruitment: GuildRecruitmentService,
    private val config: ConfigService,
    private val notifications: GuildRecruitmentNotifications,
    private val discovery: GuildRecruitmentDiscovery,
    private val directory: net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository? = null,
    private val members: net.lumalyte.lg.application.services.MemberService? = null,
    private val guilds: net.lumalyte.lg.application.services.GuildService? = null,
    private val quests: net.lumalyte.lg.application.services.QuestService? = null,
    private val lang: net.badgersmc.nexus.i18n.LangService? = null,
) : Listener {
    private val sessions = mutableMapOf<UUID, Instant>()
    private var cursor = 0

    /** Starts one scheduler owned by the plugin; disabling the plugin cancels it automatically. */
    fun start() {
        Bukkit.getOnlinePlayers().forEach { sessions[it.uniqueId] = Instant.now() }
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                val players = Bukkit.getOnlinePlayers().toList()
                if (players.isNotEmpty()) {
                    cursor %= players.size
                    safe {
                        val player = players[cursor++]
                        notifications.deliver(player)
                        discovery.prompt(player, sessions[player.uniqueId], true)
                        remindQuests(player)
                    }
                }
            },
            TICKS_PER_SECOND,
            TICKS_PER_SECOND,
        )
    }

    /** Delays the first discovery prompt until after the busy initial onboarding period. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val id = event.player.uniqueId
        val session = Instant.now()
        sessions[id] = session
        val delay = config.loadConfig().recruitment.onboardingDelaySeconds * TICKS_PER_SECOND
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (sessions[id] == session) {
                    Bukkit.getPlayer(id)?.let { player ->
                        safe {
                            discovery.prompt(player, sessions[player.uniqueId], false)
                        }
                    }
                }
            },
            delay,
        )
    }

    /** Closing applications follows every membership admission, including existing invitation commands. */
    @EventHandler
    fun onMemberJoin(event: GuildMemberJoinEvent) {
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                safe {
                    recruitment.closeOtherApplications(event.playerId, event.guildId)
                    announceJoin(event.playerId, event.guildId)
                }
            },
        )
    }

    internal fun announceJoin(playerId: UUID, guildId: UUID) {
        val joined = Bukkit.getPlayer(playerId)?.takeIf { it.isOnline } ?: return
        val guild = guilds?.getGuild(guildId) ?: return
        if (!recruitment.listings.profile(guild).joinAnnouncements) return
        val message = lang?.msg("dialog_v2.join_announcement", "player" to joined.name, "guild" to guild.name) ?: return
        members?.getGuildMembers(guildId)?.filter { it.playerId != playerId }?.forEach { member ->
            Bukkit.getPlayer(member.playerId)?.takeIf { it.isOnline && it.canSee(joined) }?.sendMessage(message)
        }
    }

    internal fun remindQuests(player: org.bukkit.entity.Player) {
        if (!player.hasPermission("lumaguilds.guild.quests")) return
        val own = members?.getPlayerGuilds(player.uniqueId) ?: return
        for (id in own) {
            val guild = guilds?.getGuild(id) ?: continue
            if (!recruitment.listings.profile(guild).questReminders) continue
            val week = quests?.activeQuestSet() ?: continue
            val remaining = quests.timeRemaining()
            if (remaining.isZero || remaining.isNegative || remaining > java.time.Duration.ofHours(1)) continue
            val message = lang?.msg("dialog_v2.quest_reminder", "guild" to guild.name) ?: continue
            if (directory?.consumeQuestReminder(player.uniqueId, id, week.weekId) != true) continue
            player.sendMessage(message.clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/guild quests")))
        }
    }

    /** Disconnected sessions cannot deliver an old onboarding timer after a reconnect. */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
    }

    /** Closed guilds retain history while unconfirmed applications become unavailable. */
    @EventHandler
    fun onDisband(event: GuildDisbandedEvent) = safe { recruitment.closeGuildApplications(event.guild.id) }

    /** Leaving provides one quiet shortcut without resetting the one-time onboarding marker. */
    @EventHandler
    fun onMemberRemoved(event: GuildMemberRemovedEvent) {
        Bukkit.getPlayer(event.playerId)?.let { player -> safe { discovery.onLeave(player) } }
    }

    @Suppress(
        "TooGenericExceptionCaught",
    ) // Scheduler boundary contains adapter failures without exposing private records or retrying payment.
    private fun safe(action: () -> Unit) {
        try {
            action()
        } catch (
            error: Exception,
        ) {
            plugin.logger.warning("Recruitment notification failed: ${error.javaClass.simpleName}")
        }
    }
}
