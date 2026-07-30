package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.hook.channel.ChannelProvider
import dev.rosewood.rosechat.hook.channel.rosechat.RoseChatChannel
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * RoseChat [Channel] backed by LumaGuilds guild membership.
 *
 * Three channel types matching `channels.yml`:
 * - `GUILD`   — messages visible only to fellow guild members
 * - `ALLY`    — messages visible to own guild + allied guilds
 * - `MODCHAT` — messages visible only to members with moderation permissions
 *
 * Recipients are computed at call time from live guild data, respecting
 * per-player chat visibility toggles stored in [ChatSettingsRepository].
 */
class LumaGuildsChannel(provider: ChannelProvider) : RoseChatChannel(provider), KoinComponent {

    private val memberService: MemberService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val chatSettingsRepository: ChatSettingsRepository by inject()

    /** Resolved from the `channel-type` key in `channels.yml` (default: GUILD). */
    lateinit var channelType: LumaGuildsChannelType
        private set

    override fun onLoad(id: String, config: ConfigurationSection) {
        super.onLoad(id, config)
        channelType = when {
            config.getString("channel-type", "GUILD").equals("ALLY", ignoreCase = true) ->
                LumaGuildsChannelType.ALLY
            config.getString("channel-type", "GUILD").equals("OFFICER", ignoreCase = true) ->
                LumaGuildsChannelType.MODCHAT
            config.getString("channel-type", "GUILD").equals("MODCHAT", ignoreCase = true) ->
                LumaGuildsChannelType.MODCHAT
            else -> LumaGuildsChannelType.GUILD
        }
    }

    override fun getMembers(): List<UUID> {
        val ids = mutableSetOf<UUID>()
        Bukkit.getOnlinePlayers().forEach { player ->
            val guilds = guildService.getPlayerGuilds(player.uniqueId)
            if (guilds.isEmpty()) return@forEach
            when (channelType) {
                LumaGuildsChannelType.MODCHAT -> {
                    if (guilds.any { hasModPerms(player.uniqueId, it) })
                        ids.add(player.uniqueId)
                }
                LumaGuildsChannelType.ALLY -> ids.add(player.uniqueId)
                LumaGuildsChannelType.GUILD -> ids.add(player.uniqueId)
            }
        }
        return ids.toList()
    }

    override fun getMemberCount(): Int = getMembers().size

    override fun canJoinByCommand(player: RosePlayer): Boolean =
        super.canJoinByCommand(player) && hasTeam(player)

    override fun onLogin(player: RosePlayer): Boolean =
        super.onLogin(player) && hasTeam(player)

    override fun getIntendedRecipients(sender: RosePlayer, includeSpies: Boolean): Set<Player> {
        val senderId = sender.player?.uniqueId ?: return emptySet()
        val senderGuilds = guildService.getPlayerGuilds(senderId)
        if (senderGuilds.isEmpty()) return emptySet()

        val recipients = mutableSetOf<Player>()

        when (channelType) {
            LumaGuildsChannelType.GUILD -> {
                senderGuilds.forEach { guildId ->
                    memberService.getGuildMembers(guildId).forEach { member ->
                        val player = Bukkit.getPlayer(member.playerId) ?: return@forEach
                        if (player.isOnline &&
                            chatSettingsRepository
                                .getVisibilitySettings(member.playerId)
                                .guildChatVisible
                        ) recipients.add(player)
                    }
                }
            }

            LumaGuildsChannelType.ALLY -> {
                senderGuilds.forEach { guildId ->
                    // Own guild members
                    memberService.getGuildMembers(guildId).forEach { member ->
                        val player = Bukkit.getPlayer(member.playerId) ?: return@forEach
                        if (player.isOnline &&
                            chatSettingsRepository
                                .getVisibilitySettings(member.playerId)
                                .allyChatVisible
                        ) recipients.add(player)
                    }
                    // Allied guild members
                    relationService.getGuildRelationsByType(guildId, RelationType.ALLY)
                        .filter { it.isActive() }
                        .forEach { relation ->
                            val alliedId = relation.getOtherGuild(guildId)
                            memberService.getGuildMembers(alliedId).forEach { member ->
                                val player = Bukkit.getPlayer(member.playerId) ?: return@forEach
                                if (player.isOnline &&
                                    chatSettingsRepository
                                        .getVisibilitySettings(member.playerId)
                                        .allyChatVisible
                                ) recipients.add(player)
                            }
                        }
                }
            }

            LumaGuildsChannelType.MODCHAT -> {
                senderGuilds.filter { hasModPerms(senderId, it) }.forEach { guildId ->
                    memberService.getGuildMembers(guildId)
                        .filter { hasModPerms(it.playerId, guildId) }
                        .forEach { member ->
                            val player = Bukkit.getPlayer(member.playerId) ?: return@forEach
                            if (player.isOnline) recipients.add(player)
                        }
                }
            }
        }

        return recipients
    }

    override fun canPlayerReceiveMessage(sender: RosePlayer, receiver: RosePlayer): Boolean {
        val senderId = sender.player?.uniqueId ?: return false
        val receiverId = receiver.player?.uniqueId ?: return false

        val senderGuilds = guildService.getPlayerGuilds(senderId).toSet()
        val receiverGuilds = guildService.getPlayerGuilds(receiverId).toSet()

        if (senderGuilds.isEmpty() || receiverGuilds.isEmpty()) return false

        // Honor receiver's per-channel-type visibility opt-out
        val settings = chatSettingsRepository.getVisibilitySettings(receiverId)
        val visibilityAllowed = when (channelType) {
            LumaGuildsChannelType.GUILD -> settings.guildChatVisible
            LumaGuildsChannelType.ALLY -> settings.allyChatVisible
            LumaGuildsChannelType.MODCHAT -> true
        }
        if (!visibilityAllowed) return false

        return when (channelType) {
            LumaGuildsChannelType.GUILD ->
                senderGuilds.intersect(receiverGuilds).isNotEmpty()

            LumaGuildsChannelType.ALLY ->
                senderGuilds.intersect(receiverGuilds).isNotEmpty() ||
                    senderGuilds.any { sg ->
                        receiverGuilds.any { rg -> relationService.getRelationType(sg, rg) == RelationType.ALLY }
                    }

            LumaGuildsChannelType.MODCHAT ->
                senderGuilds.intersect(receiverGuilds).any { shared ->
                    hasModPerms(senderId, shared) && hasModPerms(receiverId, shared)
                }
        }
    }

    private fun hasModPerms(playerId: UUID, guildId: UUID): Boolean =
        memberService.hasPermission(playerId, guildId, RankPermission.MANAGE_INVITES) ||
            memberService.hasPermission(playerId, guildId, RankPermission.KICK_MEMBERS)

    private fun hasTeam(player: RosePlayer): Boolean {
        val playerId = player.player?.uniqueId ?: return false
        return guildService.getPlayerGuilds(playerId).isNotEmpty()
    }
}
