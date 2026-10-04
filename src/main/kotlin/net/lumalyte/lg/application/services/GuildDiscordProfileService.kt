package net.lumalyte.lg.application.services

import net.lumalyte.lg.utils.RankNameContent

import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.utils.GuildDescriptionContent
import java.time.Instant
import java.util.UUID

data class GuildDiscordRankGroup(
    val name: String,
    val priority: Int,
    val members: List<String>,
)

data class GuildDiscordProfile(
    val guildId: UUID,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val mode: GuildMode,
    val recruiting: Boolean,
    val level: Int,
    val prestigeCount: Int,
    val totalExperience: Int,
    val experienceThisLevel: Int,
    val experienceForNextLevel: Int,
    val memberCount: Int,
    val memberLimit: Int?,
    val rankGroups: List<GuildDiscordRankGroup>,
    val allies: List<String>,
    val enemies: List<String>,
) {
    val progressFraction: Double
        get() = if (experienceForNextLevel <= 0) 1.0
        else (experienceThisLevel.toDouble() / experienceForNextLevel).coerceIn(0.0, 1.0)
}

sealed interface GuildDiscordProfileLookup {
    data class Found(val profile: GuildDiscordProfile) : GuildDiscordProfileLookup
    data class Ambiguous(val matches: List<String>) : GuildDiscordProfileLookup
    data object NotFound : GuildDiscordProfileLookup
}

/**
 * Builds the public, read-only guild view exposed to Discord.
 *
 * This deliberately has no Discord-role/link dependency: every LumaGuilds guild
 * is eligible for lookup. Bank, vault, audit and other private state is excluded.
 */
class GuildDiscordProfileService(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val rankService: RankService,
    private val progressionRepository: ProgressionRepository,
    private val prestigeService: GuildPrestigeService,
    private val relationService: RelationService,
    private val playerNameResolver: (UUID) -> String?,
) {
    fun lookup(query: String): GuildDiscordProfileLookup {
        val needle = query.trim()
        if (needle.isEmpty()) return GuildDiscordProfileLookup.NotFound

        val guilds = guildService.getAllGuilds()
        val exact = guilds.firstOrNull { it.name.equals(needle, ignoreCase = true) }
        if (exact != null) return GuildDiscordProfileLookup.Found(build(exact))

        val partial = guilds
            .filter { it.name.contains(needle, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }

        return when (partial.size) {
            0 -> GuildDiscordProfileLookup.NotFound
            1 -> GuildDiscordProfileLookup.Found(build(partial.single()))
            else -> GuildDiscordProfileLookup.Ambiguous(partial.take(MAX_AMBIGUOUS_MATCHES).map(Guild::name))
        }
    }

    fun get(guildId: UUID): GuildDiscordProfile? =
        guildService.getGuild(guildId)?.let(::build)

    fun build(guild: Guild): GuildDiscordProfile {
        val members = memberService.getGuildMembers(guild.id)
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankById = ranks.associateBy { it.id }
        val grouped = members.groupBy { it.rankId }

        val rankGroups = ranks.mapNotNull { rank ->
            val names = grouped[rank.id].orEmpty()
                .map { member -> resolvePlayerName(member.playerId) }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            if (names.isEmpty()) null else GuildDiscordRankGroup(RankNameContent.plain(rank.name), rank.priority, names)
        }.toMutableList()

        val unknownMembers = members
            .filter { it.rankId !in rankById }
            .map { resolvePlayerName(it.playerId) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (unknownMembers.isNotEmpty()) {
            rankGroups += GuildDiscordRankGroup("Members", Int.MAX_VALUE, unknownMembers)
        }

        val progression = progressionRepository.getGuildProgression(guild.id)
        val memberLimit = runCatching { memberService.getMemberLimit(guild.id) }
            .getOrNull()
            ?.takeIf { it > 0 }
        val prestigeCount = runCatching { prestigeService.overview(guild.id)?.prestigeCount }
            .getOrNull() ?: 0

        return GuildDiscordProfile(
            guildId = guild.id,
            name = guild.name,
            description = guild.description
                ?.let(GuildDescriptionContent::plainText)
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            createdAt = guild.createdAt,
            mode = guild.mode,
            recruiting = guild.isOpen,
            level = progression?.currentLevel ?: guild.level,
            prestigeCount = prestigeCount,
            totalExperience = progression?.totalExperience ?: 0,
            experienceThisLevel = progression?.experienceThisLevel ?: 0,
            experienceForNextLevel = progression?.experienceForNextLevel ?: 0,
            memberCount = members.size,
            memberLimit = memberLimit,
            rankGroups = rankGroups,
            allies = relationNames(guild.id, RelationType.ALLY),
            enemies = relationNames(guild.id, RelationType.ENEMY),
        )
    }

    private fun relationNames(guildId: UUID, type: RelationType): List<String> =
        runCatching {
            relationService.getGuildRelations(guildId)
                .asSequence()
                .filter { it.type == type && it.isActive() }
                .mapNotNull { relation -> guildService.getGuild(relation.getOtherGuild(guildId))?.name }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }.getOrDefault(emptyList())

    private fun resolvePlayerName(playerId: UUID): String =
        runCatching { playerNameResolver(playerId) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown-${playerId.toString().take(8)}"

    companion object {
        private const val MAX_AMBIGUOUS_MATCHES = 8
    }
}
