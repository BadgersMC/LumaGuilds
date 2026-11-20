package net.lumalyte.lg.infrastructure.web.handlers

import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildBannerService
import java.time.Instant
import java.util.UUID

/** Requested public identities only. No rosters, permissions, homes or bank data. */
class PlayerGuildSnapshotHandler(private val members: MemberService, private val guilds: GuildService,
    private val banners: GuildBannerService) {
    fun build(players: List<UUID>): Map<String, Any?> {
        require(players.size in 1..50 && players.distinct().size == players.size)
        return mapOf("generatedAt" to Instant.now().toString(), "items" to players.map { player ->
            val memberships = members.getPlayerGuilds(player).sortedBy(UUID::toString).mapNotNull { id ->
                guilds.getGuild(id)?.let { guild ->
                    val banner = banners.getGuildBanner(id)?.designData?.let { design -> mapOf(
                        "baseColor" to design.baseColor.name, "patterns" to design.patterns.map { pattern ->
                            mapOf("type" to pattern.type, "color" to pattern.color.name)
                        }) }
                    mapOf("id" to id.toString(), "name" to guild.name, "level" to guild.level, "banner" to banner)
                }
            }
            require(memberships.size <= 20)
            mapOf("id" to player.toString(), "guildMemberships" to memberships)
        })
    }
}
