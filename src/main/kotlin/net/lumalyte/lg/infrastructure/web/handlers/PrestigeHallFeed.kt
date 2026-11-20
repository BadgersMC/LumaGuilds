package net.lumalyte.lg.infrastructure.web.handlers

import com.google.gson.GsonBuilder
import net.lumalyte.lg.application.persistence.DisbandedPrestigeGuilds
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.PrestigeAdministration
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.config.PrestigeConfig
import net.lumalyte.lg.domain.rewards.AppliedPrestige
import net.lumalyte.lg.domain.rewards.HallRanking
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.domain.values.PrestigeRolePlan
import net.lumalyte.lg.infrastructure.web.dto.BannerDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeGuildDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeGuildRefDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeHistoryDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeRecentDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeRewardDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeSnapshotDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeTierDto
import net.lumalyte.lg.infrastructure.web.dto.PrestigeTotalsDto
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * REQ-301: builds the Hall of Prestige feed the website reads. Everything comes from the prestige
 * receipts, staff corrections and membership history already kept for the in-game Hall and the
 * prestige Discord roles, so the page, the Hall statues and the roles always agree.
 */
class PrestigeHallFeed(
    private val history: PrestigeHistory,
    private val adjustments: PrestigeAdministration?,
    private val disbanded: DisbandedPrestigeGuilds,
    private val stints: MembershipHistoryRepository,
    private val guilds: Guilds,
    private val catalog: RewardCatalog,
    private val config: () -> PrestigeConfig,
    /** Whether the Tier III extra market stall is live (it needs EnthusiaMarket). */
    private val stallsLive: () -> Boolean,
    private val clock: () -> Instant = Instant::now,
) {
    /** The guild details the feed shows, behind one seam so tests need no Bukkit services. */
    interface Guilds {
        fun name(guildId: UUID): String?
        fun tag(guildId: UUID): String?
        fun level(guildId: UUID): Int
        /** Current members with when they joined. */
        fun members(guildId: UUID): Map<UUID, Instant>
        fun banner(guildId: UUID): BannerDto?
    }

    fun build(): PrestigeSnapshotDto {
        val settings = config()
        val applied = history.applied().sortedWith(compareBy<AppliedPrestige> { it.record.appliedAt }.thenBy { it.record.transactionId.toString() })
        val byGuild = applied.groupBy { it.record.guildId }
        val firsts = TIERS.associateWith { tier -> applied.firstOrNull { it.record.tier == tier } }.filterValues { it != null }
            .mapValues { it.value!! }

        val ranked = HallRanking.rank(history.standings(), Int.MAX_VALUE)
            .filter { guilds.name(it.guildId) != null }
        val entries = ranked.mapNotNull { standing -> guildEntry(standing.guildId, standing.prestige, byGuild[standing.guildId].orEmpty(), firsts) }
            .take(MAX_GUILDS)
            .mapIndexed { index, guild -> guild.copy(hallRank = index + 1) }

        val tiers = TIERS.map { tier ->
            PrestigeTierDto(
                tier = tier,
                roman = ROMAN[tier - 1],
                fee = settings.fees.getOrNull(tier - 1) ?: 0L,
                perks = perks(settings, tier),
                first = firsts[tier]?.let { ref(it.record.guildId, it.record.appliedAt) },
                guildCount = entries.count { it.tier == tier },
            )
        }
        val recent = applied.asReversed().take(MAX_RECENT).map { item ->
            val ref = ref(item.record.guildId, item.record.appliedAt)
            PrestigeRecentDto(ref.guildId, ref.name, item.record.tier.coerceIn(TIERS), ref.reachedAt, ref.disbanded)
        }
        val totals = PrestigeTotalsDto(
            prestiges = applied.size,
            // Staff-granted guilds have no receipt but still hold a tier.
            guildsPrestiged = (byGuild.keys.map { it.toString() } + entries.map { it.id }).toSet().size,
            tiersClaimed = firsts.size,
            goldSpent = applied.sumOf { it.feePaid.coerceAtLeast(0L) },
        )
        val body = PrestigeSnapshotDto(
            generatedAt = "",
            revision = "",
            maxTier = settings.maxCount.coerceIn(TIERS),
            tiers = tiers,
            guilds = entries,
            recent = recent,
            totals = totals,
        )
        return body.copy(generatedAt = time(clock()), revision = revision(body))
    }

    private fun guildEntry(guildId: UUID, tier: Int, records: List<AppliedPrestige>, firsts: Map<Int, AppliedPrestige>): PrestigeGuildDto? {
        val current = tier.coerceIn(TIERS)
        val reached = PrestigeRolePlan.milestones(records.map { it.record }, adjustments?.adjustments(guildId).orEmpty())
        val members = guilds.members(guildId)
        val stints = stints.getByGuild(guildId)
        val timeline = (1..current).mapNotNull { level ->
            val at = reached[level] ?: return@mapNotNull null
            val receipt = records.firstOrNull { it.record.tier == level }
            val roster = PrestigeRolePlan.rosterAt(at, stints, members).sortedBy { it.toString() }
            PrestigeHistoryDto(
                tier = level,
                reachedAt = time(at),
                firstOnServer = firsts[level]?.record?.guildId == guildId,
                keptReward = receipt?.record?.retainedRewardId?.let { id -> catalog.find(id)?.let { PrestigeRewardDto(it.id, it.name.take(48)) } },
                feePaid = receipt?.feePaid,
                credited = roster.take(MAX_CREDITED).map { it.toString() },
                creditedTotal = roster.size,
            )
        }
        if (timeline.isEmpty() || timeline.last().tier != current) return null
        return PrestigeGuildDto(
            id = guildId.toString(),
            name = guilds.name(guildId).orEmpty().take(32),
            tag = guilds.tag(guildId)?.take(32)?.takeIf { it.isNotBlank() },
            tier = current,
            hallRank = 0,
            reachedAt = timeline.last().reachedAt,
            level = guilds.level(guildId).coerceAtLeast(0),
            memberCount = members.size,
            banner = guilds.banner(guildId)?.let { it.copy(patterns = it.patterns.take(16)) },
            history = timeline,
        )
    }

    private fun ref(guildId: UUID, at: Instant): PrestigeGuildRefDto {
        val active = guilds.name(guildId)
        val gone = if (active == null) disbanded.disbanded(guildId) else null
        return PrestigeGuildRefDto(guildId.toString(), (active ?: gone?.name ?: "Disbanded guild").take(32), time(at), active == null)
    }

    private fun perks(settings: PrestigeConfig, tier: Int): List<String> = buildList {
        if (settings.perks.secondAllyHomeTier == tier) add("second_ally_home")
        if (settings.perks.extraStallTier == tier && stallsLive()) add("extra_stall")
        if (settings.perks.secondVaultTier == tier) add("second_vault")
    }

    private fun time(at: Instant): String = at.truncatedTo(ChronoUnit.SECONDS).toString()

    private fun revision(body: PrestigeSnapshotDto): String =
        MessageDigest.getInstance("SHA-256").digest(GSON.toJson(body).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        val TIERS = 1..6
        val ROMAN = listOf("I", "II", "III", "IV", "V", "VI")
        const val MAX_GUILDS = 200
        const val MAX_RECENT = 10
        const val MAX_CREDITED = 100
        /** Nulls are part of the contract, so they are written out. */
        val GSON = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    }
}
