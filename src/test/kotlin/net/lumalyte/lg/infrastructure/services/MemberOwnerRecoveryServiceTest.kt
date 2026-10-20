package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class MemberOwnerRecoveryServiceTest {
    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val ownerRank = Rank(UUID.randomUUID(), guildId, "Owner", 0)
    private val memberRank = Rank(UUID.randomUUID(), guildId, "Co-owner", 1)
    private val member = Member(playerId, guildId, memberRank.id, Instant.EPOCH)
    private val preview = OwnerRecoveryPreview(guildId, "Guild", member, memberRank.name, ownerRank.id, ownerRank.name)
    private val members = mockk<MemberRepository>()
    private val ranks = mockk<RankRepository>()
    private val override = mockk<AdminOverrideService>()
    private val invalidated = mutableListOf<UUID>()

    private fun service(invalidate: (UUID) -> Unit = { invalidated.add(it) }) = MemberServiceBukkit(
        members,
        ranks,
        mockk(),
        mockk(),
        mockk(),
        mockk(),
        override,
        invalidateClaimPermissionCache = invalidate,
    )

    @Test
    fun previewUsesAuthoritativeRepositoryWithoutPublishingPermissionChanges() {
        val ready = OwnerRecoveryPreviewResult.Ready(preview)
        every { members.previewOwnerRecovery(guildId, playerId) } returns ready

        assertEquals(ready, service().previewOwnerRecovery(guildId, playerId))

        assertEquals(emptyList(), invalidated)
        verify(exactly = 0) { members.recoverOwnership(any()) }
        verify(exactly = 0) { members.update(any()) }
    }

    @Test
    fun committedRecoveryInvalidatesOnlyRecoveredMember() {
        every { members.recoverOwnership(preview) } returns OwnerRecoveryResult.Recovered(preview)

        assertEquals(OwnerRecoveryResult.Recovered(preview), service().recoverOwnership(preview))

        assertEquals(listOf(playerId), invalidated)
        verify(exactly = 1) { members.recoverOwnership(preview) }
        verify(exactly = 0) { members.update(any()) }
    }

    @ParameterizedTest
    @EnumSource(OwnerRecoveryRejection::class)
    fun rejectedRecoveryDoesNotRefreshPermissionsOrUseOrdinaryRankUpdate(reason: OwnerRecoveryRejection) {
        val rejected = OwnerRecoveryResult.Rejected(reason)
        every { members.recoverOwnership(preview) } returns rejected

        assertEquals(rejected, service().recoverOwnership(preview))

        assertEquals(emptyList(), invalidated)
        verify(exactly = 0) { members.update(any()) }
    }

    @Test
    fun cacheRefreshFailureDoesNotMisreportOrRepeatCommittedRecovery() {
        every { members.recoverOwnership(preview) } returns OwnerRecoveryResult.Recovered(preview)

        val result = service { throw IllegalStateException("test cache failure") }.recoverOwnership(preview)

        assertEquals(OwnerRecoveryResult.Recovered(preview), result)
        verify(exactly = 1) { members.recoverOwnership(preview) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun directOwnerRemovalIsRejectedForBothSelfAndAdminOverride(self: Boolean) {
        val owner = member.copy(rankId = ownerRank.id)
        every { members.isPlayerInGuild(playerId, guildId) } returns true
        every { members.getByPlayerAndGuild(playerId, guildId) } returns owner
        every { ranks.getById(ownerRank.id) } returns ownerRank
        every { override.hasOverride(actorId) } returns true

        assertFalse(service().removeMember(playerId, guildId, if (self) playerId else actorId))

        verify(exactly = 0) { members.remove(any(), any()) }
        assertEquals(emptyList(), invalidated)
    }

    @Test
    fun invalidMemberRankFailsClosedBeforeRemoval() {
        every { members.isPlayerInGuild(playerId, guildId) } returns true
        every { members.getByPlayerAndGuild(playerId, guildId) } returns member
        every { ranks.getById(memberRank.id) } returns memberRank.copy(guildId = UUID.randomUUID())

        assertFalse(service().removeMember(playerId, guildId, playerId))

        verify(exactly = 0) { members.remove(any(), any()) }
    }

    @Test
    fun nonOwnerRemovalHonorsRepositoryRejectionAfterConcurrentRecovery() {
        every { members.isPlayerInGuild(playerId, guildId) } returns true
        every { members.getByPlayerAndGuild(playerId, guildId) } returns member
        every { ranks.getById(memberRank.id) } returns memberRank
        every { members.remove(playerId, guildId) } returns false

        assertFalse(service().removeMember(playerId, guildId, playerId))

        verify(exactly = 1) { members.remove(playerId, guildId) }
        assertEquals(emptyList(), invalidated)
    }
}
