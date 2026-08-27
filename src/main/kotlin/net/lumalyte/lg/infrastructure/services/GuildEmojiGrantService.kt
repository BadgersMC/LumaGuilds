package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.EmojiGrantReconciliationResult
import net.lumalyte.lg.application.services.GuildEmojiGrantReconciler
import net.lumalyte.lg.application.services.GuildService
import java.util.Locale
import java.util.UUID

class GuildEmojiGrantService(
    private val guildService: GuildService,
    private val configService: ConfigService,
    private val reconciler: GuildEmojiGrantReconciler,
) {
    fun reconcileAll(): EmojiGrantReconciliationResult =
        reconciler.reconcileAll(configService.loadConfig().guild.emojiGrants)

    fun reconcileMember(playerId: UUID, guildId: UUID): EmojiGrantReconciliationResult =
        reconciler.reconcileMembership(playerId, guildId, resolveEmojiGrant(guildId))

    fun removeMember(playerId: UUID, guildId: UUID): EmojiGrantReconciliationResult =
        reconciler.removeMembership(playerId, guildId)

    fun reconcileGuild(guildId: UUID): EmojiGrantReconciliationResult =
        reconciler.reconcileGuild(guildId, resolveEmojiGrant(guildId))

    fun removeGuild(guildId: UUID): EmojiGrantReconciliationResult = reconciler.removeGuild(guildId)

    fun resolveEmojiGrant(guildId: UUID): String? =
        guildService.getGuild(guildId)?.let { resolveEmojiGrantByName(it.name) }

    fun resolveEmojiGrantByName(guildName: String): String? =
        configService.loadConfig().guild.emojiGrants[guildName.trim().lowercase(Locale.ROOT)]
}
