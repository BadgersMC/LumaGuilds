package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildDisbandAnnouncementService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.UUID

class GuildDisbandAnnouncementServiceBukkit(
    private val memberService: MemberService,
    private val lang: LangService,
    private val toastSender: ToastSender,
    private val playerLookup: (UUID) -> Player? = { Bukkit.getPlayer(it) },
    private val broadcaster: (Component) -> Unit = { Bukkit.broadcast(it) },
    private val iconFactory: () -> ItemStack = { ItemStack.of(Material.WHITE_BANNER) },
) : GuildDisbandAnnouncementService {
    private val logger = LoggerFactory.getLogger(GuildDisbandAnnouncementServiceBukkit::class.java)

    override fun announce(
        guild: Guild,
        formerMemberIds: Set<UUID>,
        relatedGuilds: Map<UUID, RelationType>,
    ) {
        runCatching {
            broadcaster(
                lang.msg(
                    "notification.guild.disband.broadcast",
                    "guild" to guild.name,
                )
            )
        }.onFailure {
            logger.error("Failed to broadcast disbandment for guild ${guild.id}", it)
        }

        val recipientRelations = linkedMapOf<UUID, RelationType>()
        relatedGuilds.entries
            .asSequence()
            .filter { it.value == RelationType.ALLY || it.value == RelationType.ENEMY }
            .sortedBy { it.key.toString() }
            .forEach { (relatedGuildId, relationType) ->
                collectRecipients(
                    relatedGuildId,
                    relationType,
                    formerMemberIds,
                    recipientRelations,
                )
            }

        if (recipientRelations.isEmpty()) return
        val icon = runCatching(iconFactory)
            .getOrElse { ItemStack.of(Material.WHITE_BANNER) }

        recipientRelations.forEach { (recipientId, relationType) ->
            val recipient = playerLookup(recipientId)
                ?.takeIf { it.isOnline }
                ?: return@forEach

            val title = when (relationType) {
                RelationType.ALLY ->
                    lang.msg("notification.guild.disband.toast.ally.title", "guild" to guild.name)
                RelationType.ENEMY ->
                    lang.msg("notification.guild.disband.toast.enemy.title", "guild" to guild.name)
                else -> return@forEach
            }
            val description = when (relationType) {
                RelationType.ALLY ->
                    lang.msg("notification.guild.disband.toast.ally.description", "guild" to guild.name)
                RelationType.ENEMY ->
                    lang.msg("notification.guild.disband.toast.enemy.description", "guild" to guild.name)
                else -> return@forEach
            }
            val frame = if (relationType == RelationType.ENEMY) {
                ToastFrame.CHALLENGE
            } else {
                ToastFrame.TASK
            }

            val toastId = UUID.nameUUIDFromBytes(
                "guild-disband:${guild.id}:$recipientId:${relationType.name}"
                    .toByteArray(StandardCharsets.UTF_8)
            )
            val shown = runCatching {
                toastSender.show(
                    recipient,
                    toastId,
                    title,
                    description,
                    icon,
                    frame,
                )
            }.onFailure {
                logger.warn(
                    "Failed to send ${relationType.name.lowercase()} disband toast " +
                        "to $recipientId for ${guild.id}",
                    it,
                )
            }.getOrDefault(false)

            if (!shown) {
                recipient.sendActionBar(
                    lang.msg(
                        "notification.guild.disband.toast.fallback",
                        "guild" to guild.name,
                    )
                )
            }
        }
    }

    private fun collectRecipients(
        relatedGuildId: UUID,
        relationType: RelationType,
        formerMemberIds: Set<UUID>,
        recipientRelations: MutableMap<UUID, RelationType>,
    ) {
        val members = runCatching { memberService.getGuildMembers(relatedGuildId) }
            .onFailure {
                logger.warn(
                    "Failed to resolve members for related guild $relatedGuildId",
                    it,
                )
            }
            .getOrElse { return }

        members.forEach { member ->
            if (member.playerId in formerMemberIds) return@forEach
            val current = recipientRelations[member.playerId]
            recipientRelations[member.playerId] = preferredRelation(current, relationType)
        }
    }

    private fun preferredRelation(
        current: RelationType?,
        incoming: RelationType,
    ): RelationType = if (current == RelationType.ENEMY || incoming == RelationType.ENEMY) {
        RelationType.ENEMY
    } else {
        RelationType.ALLY
    }
}
