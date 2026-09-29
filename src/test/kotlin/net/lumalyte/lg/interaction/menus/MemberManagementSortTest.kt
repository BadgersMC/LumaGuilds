package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberManagementSortTest {
    private val guildId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val ownerRankId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val officerRankId = UUID.fromString("00000000-0000-0000-0000-000000000011")
    private val missingRankId = UUID.fromString("00000000-0000-0000-0000-000000000099")

    @Test
    fun `rank sort puts highest rank first with stable name tie break and unknown rank last`() {
        val owner = member("00000000-0000-0000-0000-000000000101", ownerRankId)
        val officerZed = member("00000000-0000-0000-0000-000000000102", officerRankId)
        val officerAmy = member("00000000-0000-0000-0000-000000000103", officerRankId)
        val unknown = member("00000000-0000-0000-0000-000000000104", missingRankId)
        val names = mapOf(
            owner.playerId to "Owner",
            officerZed.playerId to "Zed",
            officerAmy.playerId to "amy",
            unknown.playerId to "Unknown",
        )
        val ranks = listOf(
            Rank(ownerRankId, guildId, "Owner", priority = 0),
            Rank(officerRankId, guildId, "Officer", priority = 10),
        )

        val sorted = sortMembersByRank(
            listOf(unknown, officerZed, owner, officerAmy),
            ranks,
        ) { member -> names.getValue(member.playerId) }

        assertEquals(
            listOf(owner.playerId, officerAmy.playerId, officerZed.playerId, unknown.playerId),
            sorted.map { it.playerId },
        )
    }

    private fun member(playerId: String, rankId: UUID) = Member(
        playerId = UUID.fromString(playerId),
        guildId = guildId,
        rankId = rankId,
        joinedAt = Instant.EPOCH,
    )
}
