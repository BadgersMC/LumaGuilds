package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.WarNotificationRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.application.services.WarNotificationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarNotification
import net.lumalyte.lg.domain.entities.WarNotificationKind
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class WarNotificationServiceBukkit(
    private val plugin: Plugin,
    private val repository: WarNotificationRepository,
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val warRepository: WarRepository,
    private val lang: LangService,
    private val toastSender: WarToastSender,
) : WarNotificationService {
    private val logger = LoggerFactory.getLogger(WarNotificationServiceBukkit::class.java)

    override fun declarationCreated(declaration: WarDeclaration) {
        val declaring = guildRepository.getById(declaration.declaringGuildId) ?: return
        val defending = guildRepository.getById(declaration.defendingGuildId) ?: return
        val objective = declaration.objectives.firstOrNull()?.description
        val createdAt = declaration.declaredAt.toEpochMilli()
        val recipients = warRepository.get(declaration.id)
            ?.takeIf { it.declarationNotificationExpected }
            ?.notificationRecipients

        enqueueForGuild(
            kind = WarNotificationKind.DECLARATION_RECEIVED,
            eventId = declaration.id,
            ownGuild = defending,
            opponent = declaring,
            recipientIds = recipients?.declarationReceived,
            durationSeconds = declaration.proposedDuration.seconds,
            objectiveCount = declaration.objectives.size,
            objectiveDescription = objective,
            wagerAmount = declaration.wagerAmount,
            terms = declaration.terms,
            expiresAt = declaration.expiresAt.toEpochMilli(),
            createdAt = createdAt,
        )
        enqueueForGuild(
            kind = WarNotificationKind.DECLARATION_SENT,
            eventId = declaration.id,
            ownGuild = declaring,
            opponent = defending,
            recipientIds = recipients?.declarationSent,
            durationSeconds = declaration.proposedDuration.seconds,
            objectiveCount = declaration.objectives.size,
            objectiveDescription = objective,
            wagerAmount = declaration.wagerAmount,
            terms = declaration.terms,
            expiresAt = declaration.expiresAt.toEpochMilli(),
            createdAt = createdAt,
        )
    }

    override fun warAccepted(war: War) {
        val declaring = guildRepository.getById(war.declaringGuildId) ?: return
        val defending = guildRepository.getById(war.defendingGuildId) ?: return
        val createdAt = war.startedAt?.toEpochMilli() ?: System.currentTimeMillis()
        val objective = war.objectives.firstOrNull()?.description
        val recipients = warRepository.get(war.id)
            ?.takeIf { it.acceptanceNotificationExpected }
            ?.notificationRecipients

        enqueueForGuild(
            kind = WarNotificationKind.WAR_ACCEPTED,
            eventId = war.id,
            ownGuild = declaring,
            opponent = defending,
            recipientIds = recipients?.acceptanceDeclaring,
            durationSeconds = war.duration.seconds,
            objectiveCount = war.objectives.size,
            objectiveDescription = objective,
            createdAt = createdAt,
        )
        enqueueForGuild(
            kind = WarNotificationKind.WAR_ACCEPTED,
            eventId = war.id,
            ownGuild = defending,
            opponent = declaring,
            recipientIds = recipients?.acceptanceDefending,
            durationSeconds = war.duration.seconds,
            objectiveCount = war.objectives.size,
            objectiveDescription = objective,
            createdAt = createdAt,
        )
    }

    override fun warEnded(war: War) {
        val winnerId = war.winner ?: return
        val loserId = war.loser ?: return
        val winner = guildRepository.getById(winnerId) ?: return
        val loser = guildRepository.getById(loserId) ?: return
        val record = warRepository.get(war.id)
        val stats = record?.stats
        val createdAt = war.endedAt?.toEpochMilli() ?: System.currentTimeMillis()
        val recipients = record
            ?.takeIf { it.resolutionNotificationExpected }
            ?.notificationRecipients

        enqueueForGuild(
            kind = WarNotificationKind.VICTORY,
            eventId = war.id,
            ownGuild = winner,
            opponent = loser,
            recipientIds = recipients?.victory,
            ownKills = killsFor(stats, war, winner.id),
            opponentKills = killsFor(stats, war, loser.id),
            createdAt = createdAt,
        )
        enqueueForGuild(
            kind = WarNotificationKind.DEFEAT,
            eventId = war.id,
            ownGuild = loser,
            opponent = winner,
            recipientIds = recipients?.defeat,
            ownKills = killsFor(stats, war, loser.id),
            opponentKills = killsFor(stats, war, winner.id),
            createdAt = createdAt,
        )
        Bukkit.broadcast(
            lang.msg(
                "notification.war.lifecycle.broadcast.resolved",
                "winner" to winner.name,
                "loser" to loser.name,
            )
        )
    }

    override fun deliverUnread(playerId: UUID) {
        val delivery = Runnable {
            Bukkit.getPlayer(playerId)
                ?.takeIf { it.isOnline }
                ?.let(::deliverUnreadNow)
        }
        if (Bukkit.isPrimaryThread()) {
            delivery.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, delivery)
        }
    }

    private fun enqueueForGuild(
        kind: WarNotificationKind,
        eventId: UUID,
        ownGuild: Guild,
        opponent: Guild,
        recipientIds: Set<UUID>? = null,
        durationSeconds: Long = 0,
        objectiveCount: Int = 0,
        objectiveDescription: String? = null,
        wagerAmount: Int = 0,
        terms: String? = null,
        expiresAt: Long? = null,
        ownKills: Int? = null,
        opponentKills: Int? = null,
        createdAt: Long,
    ) {
        val recipients = recipientIds ?: memberRepository.getByGuild(ownGuild.id).map { it.playerId }.toSet()
        recipients.forEach { playerId ->
            val notification = buildNotification(
                kind = kind,
                eventId = eventId,
                playerId = playerId,
                ownGuildId = ownGuild.id,
                opponentGuildId = opponent.id,
                opponentName = opponent.name,
                opponentBanner = opponent.banner,
                durationSeconds = durationSeconds,
                objectiveCount = objectiveCount,
                objectiveDescription = objectiveDescription,
                wagerAmount = wagerAmount,
                terms = terms,
                expiresAt = expiresAt,
                ownKills = ownKills,
                opponentKills = opponentKills,
                createdAt = createdAt,
            )
            runCatching { repository.add(notification) }
                .onFailure {
                    logger.error(
                        "Failed to persist ${kind.name} war notification for $playerId",
                        it,
                    )
                }
            deliverUnread(playerId)
        }
    }

    private fun buildNotification(
        kind: WarNotificationKind,
        eventId: UUID,
        playerId: UUID,
        ownGuildId: UUID,
        opponentGuildId: UUID,
        opponentName: String,
        opponentBanner: String?,
        durationSeconds: Long = 0,
        objectiveCount: Int = 0,
        objectiveDescription: String? = null,
        wagerAmount: Int = 0,
        terms: String? = null,
        expiresAt: Long? = null,
        ownKills: Int? = null,
        opponentKills: Int? = null,
        createdAt: Long,
    ): WarNotification = WarNotification(
        id = notificationId(kind, eventId, playerId),
        playerId = playerId,
        kind = kind,
        eventId = eventId,
        ownGuildId = ownGuildId,
        opponentGuildId = opponentGuildId,
        opponentName = opponentName,
        opponentBanner = opponentBanner,
        durationSeconds = durationSeconds,
        objectiveCount = objectiveCount,
        objectiveDescription = objectiveDescription,
        wagerAmount = wagerAmount,
        terms = terms,
        expiresAt = expiresAt,
        ownKills = ownKills,
        opponentKills = opponentKills,
        createdAt = createdAt,
    )

    internal fun reconcileMissingNotifications(): Int {
        fun recover(
            kind: WarNotificationKind,
            eventId: UUID,
            ownGuildId: UUID,
            opponentGuildId: UUID,
            recipientIds: Set<UUID>,
            createdAt: Long,
            durationSeconds: Long = 0,
            objectiveCount: Int = 0,
            objectiveDescription: String? = null,
            wagerAmount: Int = 0,
            terms: String? = null,
            expiresAt: Long? = null,
            ownKills: Int? = null,
            opponentKills: Int? = null,
        ): Int {
            if (recipientIds.isEmpty()) return 0
            val opponent = guildRepository.getById(opponentGuildId)
            var inserted = 0
            recipientIds.forEach { playerId ->
                val notification = buildNotification(
                    kind = kind,
                    eventId = eventId,
                    playerId = playerId,
                    ownGuildId = ownGuildId,
                    opponentGuildId = opponentGuildId,
                    opponentName = opponent?.name ?: opponentGuildId.toString().take(8),
                    opponentBanner = opponent?.banner,
                    durationSeconds = durationSeconds,
                    objectiveCount = objectiveCount,
                    objectiveDescription = objectiveDescription,
                    wagerAmount = wagerAmount,
                    terms = terms,
                    expiresAt = expiresAt,
                    ownKills = ownKills,
                    opponentKills = opponentKills,
                    createdAt = createdAt,
                )
                if (repository.add(notification)) inserted++
            }
            return inserted
        }

        var recovered = 0
        for (record in warRepository.getAll()) {
            var clearDeclarationMarker = false
            var clearAcceptanceMarker = false
            var clearResolutionMarker = false
            val declaration = record.declaration
            if (record.declarationNotificationExpected && declaration != null) {
                val createdAt = declaration.declaredAt.toEpochMilli()
                val objective = declaration.objectives.firstOrNull()?.description
                recovered += recover(
                    kind = WarNotificationKind.DECLARATION_RECEIVED,
                    eventId = declaration.id,
                    ownGuildId = declaration.defendingGuildId,
                    opponentGuildId = declaration.declaringGuildId,
                    recipientIds = record.notificationRecipients.declarationReceived,
                    durationSeconds = declaration.proposedDuration.seconds,
                    objectiveCount = declaration.objectives.size,
                    objectiveDescription = objective,
                    wagerAmount = declaration.wagerAmount,
                    terms = declaration.terms,
                    expiresAt = declaration.expiresAt.toEpochMilli(),
                    createdAt = createdAt,
                )
                recovered += recover(
                    kind = WarNotificationKind.DECLARATION_SENT,
                    eventId = declaration.id,
                    ownGuildId = declaration.declaringGuildId,
                    opponentGuildId = declaration.defendingGuildId,
                    recipientIds = record.notificationRecipients.declarationSent,
                    durationSeconds = declaration.proposedDuration.seconds,
                    objectiveCount = declaration.objectives.size,
                    objectiveDescription = objective,
                    wagerAmount = declaration.wagerAmount,
                    terms = declaration.terms,
                    expiresAt = declaration.expiresAt.toEpochMilli(),
                    createdAt = createdAt,
                )
                clearDeclarationMarker = true
            }

            val war = record.war
            val startedAt = war?.startedAt?.toEpochMilli()
            if (record.acceptanceNotificationExpected && war != null && startedAt != null) {
                val objective = war.objectives.firstOrNull()?.description
                recovered += recover(
                    kind = WarNotificationKind.WAR_ACCEPTED,
                    eventId = war.id,
                    ownGuildId = war.declaringGuildId,
                    opponentGuildId = war.defendingGuildId,
                    recipientIds = record.notificationRecipients.acceptanceDeclaring,
                    durationSeconds = war.duration.seconds,
                    objectiveCount = war.objectives.size,
                    objectiveDescription = objective,
                    createdAt = startedAt,
                )
                recovered += recover(
                    kind = WarNotificationKind.WAR_ACCEPTED,
                    eventId = war.id,
                    ownGuildId = war.defendingGuildId,
                    opponentGuildId = war.declaringGuildId,
                    recipientIds = record.notificationRecipients.acceptanceDefending,
                    durationSeconds = war.duration.seconds,
                    objectiveCount = war.objectives.size,
                    objectiveDescription = objective,
                    createdAt = startedAt,
                )
                clearAcceptanceMarker = true
            }

            val endedAt = war?.endedAt?.toEpochMilli()
            val winner = war?.winner
            val loser = war?.loser
            if (record.resolutionNotificationExpected && war != null && endedAt != null && winner != null && loser != null) {
                recovered += recover(
                    kind = WarNotificationKind.VICTORY,
                    eventId = war.id,
                    ownGuildId = winner,
                    opponentGuildId = loser,
                    recipientIds = record.notificationRecipients.victory,
                    ownKills = killsFor(record.stats, war, winner),
                    opponentKills = killsFor(record.stats, war, loser),
                    createdAt = endedAt,
                )
                recovered += recover(
                    kind = WarNotificationKind.DEFEAT,
                    eventId = war.id,
                    ownGuildId = loser,
                    opponentGuildId = winner,
                    recipientIds = record.notificationRecipients.defeat,
                    ownKills = killsFor(record.stats, war, loser),
                    opponentKills = killsFor(record.stats, war, winner),
                    createdAt = endedAt,
                )
                clearResolutionMarker = true
            }

            if (clearDeclarationMarker || clearAcceptanceMarker || clearResolutionMarker) {
                val cleared = record.copy(
                    notificationRecipients = record.notificationRecipients.copy(
                        declarationSent = if (clearDeclarationMarker) emptySet() else record.notificationRecipients.declarationSent,
                        declarationReceived = if (clearDeclarationMarker) emptySet() else record.notificationRecipients.declarationReceived,
                        acceptanceDeclaring = if (clearAcceptanceMarker) emptySet() else record.notificationRecipients.acceptanceDeclaring,
                        acceptanceDefending = if (clearAcceptanceMarker) emptySet() else record.notificationRecipients.acceptanceDefending,
                        victory = if (clearResolutionMarker) emptySet() else record.notificationRecipients.victory,
                        defeat = if (clearResolutionMarker) emptySet() else record.notificationRecipients.defeat,
                    ),
                    declarationNotificationExpected =
                        record.declarationNotificationExpected && !clearDeclarationMarker,
                    acceptanceNotificationExpected =
                        record.acceptanceNotificationExpected && !clearAcceptanceMarker,
                    resolutionNotificationExpected =
                        record.resolutionNotificationExpected && !clearResolutionMarker,
                )
                if (!warRepository.save(cleared)) {
                    logger.warn("War notification recovery marker changed concurrently for ${record.id}; will retry")
                }
            }
        }
        return recovered
    }

    private fun deliverUnreadNow(player: Player) {
        runCatching { reconcileMissingNotifications() }
            .onFailure { logger.error("Failed to reconcile durable war notifications", it) }
        val pending = runCatching { repository.getPending(player.uniqueId) }
            .onFailure { logger.error("Failed to load war notifications for ${player.uniqueId}", it) }
            .getOrDefault(emptyList())

        for (notification in pending) {
            if (!player.isOnline) return
            val delivered = runCatching {
                present(player, notification)
                true
            }.onFailure {
                logger.error("Failed to deliver war notification ${notification.id}", it)
            }.getOrDefault(false)
            if (delivered) {
                runCatching {
                    repository.markDelivered(notification.id, System.currentTimeMillis())
                }.onFailure {
                    logger.error("Failed to mark war notification ${notification.id} delivered", it)
                }
            }
        }
    }

    private fun present(player: Player, notification: WarNotification) {
        val title = toastTitle(notification.kind)
        val description = toastDescription(notification)
        val frame = toastFrame(notification.kind)
        val toastShown = toastSender.show(
            player,
            notification.id,
            title,
            description,
            bannerIcon(notification),
            frame,
        )

        if (!toastShown) {
            player.showTitle(
                Title.title(
                    title,
                    description,
                    Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(750),
                    ),
                )
            )
            player.playSound(
                player.location,
                fallbackSound(frame),
                1.0f,
                1.0f,
            )
        }

        sendChatDetails(player, notification)
    }

    private fun sendChatDetails(player: Player, notification: WarNotification) {
        player.sendMessage(lang.msg("notification.war.lifecycle.chat.divider"))
        player.sendMessage(
            lang.msg(
                chatSummaryKey(notification.kind),
                "guild" to notification.opponentName,
            )
        )

        if (notification.durationSeconds > 0) {
            player.sendMessage(
                lang.msg(
                    "notification.war.lifecycle.chat.duration",
                    "days" to Duration.ofSeconds(notification.durationSeconds).toDays(),
                )
            )
        }
        if (notification.objectiveCount > 0) {
            player.sendMessage(
                lang.msg(
                    "notification.war.lifecycle.chat.objectives",
                    "count" to notification.objectiveCount,
                )
            )
            notification.objectiveDescription?.let {
                player.sendMessage(
                    lang.msg(
                        "notification.war.lifecycle.chat.primary_objective",
                        "objective" to it,
                    )
                )
            }
        }
        if (notification.wagerAmount > 0) {
            player.sendMessage(
                lang.msg(
                    "notification.war.lifecycle.chat.wager",
                    "amount" to notification.wagerAmount,
                )
            )
        }
        notification.terms?.takeIf { it.isNotBlank() }?.let {
            player.sendMessage(
                lang.msg("notification.war.lifecycle.chat.terms", "terms" to it)
            )
        }
        notification.expiresAt?.let { expiresAt ->
            val millis = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
            val hours = (millis + HOUR_MILLIS - 1) / HOUR_MILLIS
            player.sendMessage(
                lang.msg("notification.war.lifecycle.chat.expires", "hours" to hours)
            )
        }
        if (notification.ownKills != null && notification.opponentKills != null) {
            player.sendMessage(
                lang.msg(
                    "notification.war.lifecycle.chat.score",
                    "own" to notification.ownKills,
                    "opponent" to notification.opponentKills,
                )
            )
        }

        player.sendMessage(
            lang.msg("notification.war.lifecycle.chat.action")
                .clickEvent(ClickEvent.runCommand("/g war"))
                .hoverEvent(
                    HoverEvent.showText(
                        lang.msg("notification.war.lifecycle.chat.action_hover")
                    )
                )
        )
        player.sendMessage(lang.msg("notification.war.lifecycle.chat.divider"))
    }

    private fun toastTitle(kind: WarNotificationKind): Component =
        lang.msg(toastTitleKey(kind))

    private fun toastDescription(notification: WarNotification): Component =
        lang.msg(
            toastDescriptionKey(notification.kind),
            "guild" to notification.opponentName,
        )

    private fun chatSummaryKey(kind: WarNotificationKind): String =
        "notification.war.lifecycle.chat.${kind.key}"

    private fun toastTitleKey(kind: WarNotificationKind): String =
        "notification.war.lifecycle.toast.${kind.key}.title"

    private fun toastDescriptionKey(kind: WarNotificationKind): String =
        "notification.war.lifecycle.toast.${kind.key}.description"

    private fun bannerIcon(notification: WarNotification): ItemStack {
        val stored = notification.opponentBanner?.let { encoded ->
            runCatching { encoded.deserializeToItemStack() }.getOrNull()
        }
        return stored?.takeIf {
            it.type.name.endsWith("_BANNER") &&
                !it.type.name.endsWith("_WALL_BANNER")
        }?.clone() ?: ItemStack(Material.WHITE_BANNER)
    }

    private fun toastFrame(kind: WarNotificationKind): WarToastFrame =
        when (kind) {
            WarNotificationKind.DECLARATION_RECEIVED -> WarToastFrame.CHALLENGE
            WarNotificationKind.DECLARATION_SENT -> WarToastFrame.TASK
            WarNotificationKind.WAR_ACCEPTED -> WarToastFrame.GOAL
            WarNotificationKind.VICTORY -> WarToastFrame.CHALLENGE
            WarNotificationKind.DEFEAT -> WarToastFrame.GOAL
        }

    private fun fallbackSound(frame: WarToastFrame): Sound =
        when (frame) {
            WarToastFrame.CHALLENGE -> Sound.UI_TOAST_CHALLENGE_COMPLETE
            WarToastFrame.GOAL -> Sound.ENTITY_PLAYER_LEVELUP
            WarToastFrame.TASK -> Sound.BLOCK_BELL_USE
        }

    private fun killsFor(
        stats: net.lumalyte.lg.domain.entities.WarStats?,
        war: War,
        guildId: UUID,
    ): Int? {
        if (stats == null) return null
        return when (guildId) {
            war.declaringGuildId -> stats.declaringGuildKills
            war.defendingGuildId -> stats.defendingGuildKills
            else -> null
        }
    }

    private fun notificationId(
        kind: WarNotificationKind,
        eventId: UUID,
        playerId: UUID,
    ): UUID = UUID.nameUUIDFromBytes(
        "lumaguilds-war-notification:v1:${kind.name}:$eventId:$playerId"
            .toByteArray(StandardCharsets.UTF_8)
    )

    private val WarNotificationKind.key: String
        get() = when (this) {
            WarNotificationKind.DECLARATION_RECEIVED -> "declaration_received"
            WarNotificationKind.DECLARATION_SENT -> "declaration_sent"
            WarNotificationKind.WAR_ACCEPTED -> "war_accepted"
            WarNotificationKind.VICTORY -> "victory"
            WarNotificationKind.DEFEAT -> "defeat"
        }

    companion object {
        private const val HOUR_MILLIS = 3_600_000L
    }
}
