package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.PlayerNotificationPreferenceRepository
import net.lumalyte.lg.application.services.GuildLoginNotificationService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.utils.createHead
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuildLoginNotificationServiceBukkit(
    private val memberService: MemberService,
    private val preferences: PlayerNotificationPreferenceRepository,
    private val lang: LangService,
    private val toastSender: ToastSender,
    private val startupAtMillis: Long = System.currentTimeMillis(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val playerLookup: (UUID) -> Player? = { Bukkit.getPlayer(it) },
    private val headFactory: (Player) -> ItemStack = { createHead(it) },
) : GuildLoginNotificationService {
    private val logger = LoggerFactory.getLogger(GuildLoginNotificationServiceBukkit::class.java)
    private val lastAnnouncedAt = ConcurrentHashMap<UUID, Long>()

    override fun onPlayerJoin(playerId: UUID) {
        val now = nowMillis()
        if (now - startupAtMillis < STARTUP_GRACE_MILLIS) return

        val joiner = playerLookup(playerId)?.takeIf { it.isOnline } ?: return
        val guildIds = memberService.getPlayerGuilds(playerId)
        if (guildIds.isEmpty()) return

        pruneReconnectHistory(now)
        val previous = lastAnnouncedAt[playerId]
        if (previous != null && now - previous < RECONNECT_COOLDOWN_MILLIS) return
        lastAnnouncedAt[playerId] = now

        val recipients = guildIds.asSequence()
            .flatMap { memberService.getGuildMembers(it).asSequence() }
            .map { it.playerId }
            .filter { it != playerId }
            .distinct()
            .mapNotNull { recipientId ->
                playerLookup(recipientId)
                    ?.takeIf { it.isOnline }
                    ?.let { recipientId to it }
            }
            .toList()
        if (recipients.isEmpty()) return

        val preferenceStates = runCatching {
            preferences.guildLoginStates(recipients.mapTo(mutableSetOf()) { it.first })
        }.onFailure {
            logger.error("Failed to load guild login notification preferences", it)
        }.getOrElse { return }

        val title = lang.msg(
            "notification.guild.member_login.title",
            "player" to joiner.name,
        )
        val description = lang.msg("notification.guild.member_login.description")
        val icon = runCatching { headFactory(joiner) }
            .getOrElse { ItemStack.of(Material.PLAYER_HEAD) }

        recipients.forEach { (recipientId, recipient) ->
            if (preferenceStates[recipientId] != true) return@forEach
            val toastId = UUID.nameUUIDFromBytes(
                "guild-login:$playerId:$recipientId:$now".toByteArray(StandardCharsets.UTF_8)
            )
            val shown = runCatching {
                toastSender.show(
                    recipient,
                    toastId,
                    title,
                    description,
                    icon,
                    ToastFrame.TASK,
                )
            }.onFailure {
                logger.warn(
                    "Failed to send guild login toast to $recipientId for $playerId",
                    it,
                )
            }.getOrDefault(false)

            if (!shown) {
                recipient.sendActionBar(
                    lang.msg(
                        "notification.guild.member_login.fallback",
                        "player" to joiner.name,
                    )
                )
                recipient.playSound(
                    recipient.location,
                    Sound.BLOCK_NOTE_BLOCK_CHIME,
                    0.35f,
                    1.35f,
                )
            }
        }
    }

    override fun isEnabled(playerId: UUID): Boolean =
        runCatching { preferences.isGuildLoginEnabled(playerId) }
            .onFailure { logger.error("Failed to load notification preference for $playerId", it) }
            .getOrDefault(false)

    override fun setEnabled(playerId: UUID, enabled: Boolean): Boolean =
        runCatching { preferences.setGuildLoginEnabled(playerId, enabled) }
            .onFailure { logger.error("Failed to save notification preference for $playerId", it) }
            .getOrDefault(false)

    private fun pruneReconnectHistory(now: Long) {
        lastAnnouncedAt.entries.removeIf { (_, announcedAt) ->
            now - announcedAt >= RECONNECT_COOLDOWN_MILLIS
        }
    }

    companion object {
        internal const val STARTUP_GRACE_MILLIS = 15_000L
        internal const val RECONNECT_COOLDOWN_MILLIS = 60_000L
    }
}
