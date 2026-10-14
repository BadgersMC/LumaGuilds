package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Non-interrupting discovery and private digest notifications with durable replay protection. */
internal class GuildRecruitmentListener(
    private val plugin: Plugin,
    private val directory: RecruitmentDirectoryRepository,
    private val recruitment: GuildRecruitmentService,
    private val guilds: GuildService,
    private val members: MemberService,
    private val config: ConfigService,
    private val lang: LangService,
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
                    safe { deliverChanges(players[cursor++]) }
                }
            },
            20L,
            20L,
        )
    }

    /** Delays the first discovery prompt until after the busy initial onboarding period. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val id = event.player.uniqueId
        val session = Instant.now()
        sessions[id] = session
        val delay = config.loadConfig().recruitment.onboardingDelaySeconds * 20L
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (sessions[id] == session) Bukkit.getPlayer(id)?.let { player -> safe { prompt(player, false) } }
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
                    if (event.guildId in members.getPlayerGuilds(event.playerId)) {
                        recruitment.closeOtherApplications(
                            event.playerId,
                            event.guildId,
                        )
                    }
                }
            },
        )
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
        Bukkit.getPlayer(event.playerId)?.let { player ->
            safe {
                val guildless = members.getPlayerGuilds(player.uniqueId).isEmpty()
                if (guildless && directory.remindersEnabled(player.uniqueId) && player.hasPermission("lumaguilds.guild.lfg")) {
                    player.sendMessage(lang.msg("recruitment.discovery.leave").clickEvent(ClickEvent.runCommand("/g recruitment")))
                }
            }
        }
    }

    private fun prompt(
        player: Player,
        repeat: Boolean,
    ) {
        if (!player.isOnline || !player.hasPermission("lumaguilds.guild.lfg")) return
        if (members.getPlayerGuilds(player.uniqueId).isNotEmpty()) return
        val joined = sessions[player.uniqueId] ?: return
        if (Instant.now().isBefore(joined.plusSeconds(config.loadConfig().recruitment.onboardingDelaySeconds.toLong()))) return
        val minutes = config.loadConfig().recruitment.reminderMinutes
        if (repeat && minutes == 0) return
        val interval = if (repeat) Duration.ofMinutes(minutes.toLong()) else null
        if (directory.consumePrompt(player.uniqueId, Instant.now(), interval)) {
            player.sendMessage(lang.msg("recruitment.discovery.prompt").clickEvent(ClickEvent.runCommand("/g recruitment")))
        }
    }

    private fun deliverChanges(player: Player) {
        if (!player.hasPermission("lumaguilds.guild.lfg")) return
        val own = recruitment.mine(player.uniqueId)
        val inbox =
            guilds.getPlayerGuilds(player.uniqueId).filter {
                GuildRecruitmentPermissions.canReview(members, player.uniqueId, it.id)
            }.flatMap { recruitment.inbox(player.uniqueId, it.id) }
        val records = (own + inbox).distinctBy { it.id }.sortedBy { it.id.toString() }
        if (records.isNotEmpty()) {
            val text = records.joinToString(";") { "${it.id}:${it.status}" }
            val signature =
                MessageDigest.getInstance(
                    "SHA-256",
                ).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            if (directory.consumeNotification(player.uniqueId, signature)) {
                player.sendMessage(lang.msg("recruitment.discovery.updated").clickEvent(ClickEvent.runCommand("/g recruitment")))
            }
        }
        prompt(player, true)
    }

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
