package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.api.GuildInviteBlocksImpl
import net.lumalyte.lg.api.GuildInviteBlocksV2Impl
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.persistence.InviteBlockWriteOutcome
import net.lumalyte.lg.application.persistence.InviteBlockWriteResult
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.GuildInvitation
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Completion means the SQL result, cache, and synchronous lifecycle gate have all settled. */
internal class InviteBlockCompletionServiceTest {
    private val owner = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val repository = mockk<InviteBlockRepository>()
    private val members = mockk<MemberService>(relaxed = true)
    private val invitations = mockk<GuildInvitationRepository>(relaxed = true)
    private val service = InviteBlockServiceImpl(repository, members, invitations)
    private val api = GuildInviteBlocksV2Impl(service)
    private val committed = InviteBlockWriteResult(InviteBlockWriteOutcome.COMMITTED, cacheReconciled = true)

    @Test
    fun thrownInvitationCleanupCannotEraseCommittedBlock() {
        every { repository.setBlockedDurable(owner, target, true) } returns committed
        every { invitations.getByPlayer(owner) } throws IllegalStateException("Concurrent invitation mutation")

        assertNull(api.setBlockedDurable(owner, target, true).toCompletableFuture().join())

        verify(exactly = 1) { repository.setBlockedDurable(owner, target, true) }
        verify(exactly = 1) { invitations.getByPlayer(owner) }
    }

    @Test
    fun unconfirmedInvitationRemovalDoesNotScheduleRetryOrChangeCommit() {
        every { repository.setBlockedDurable(owner, target, true) } returns committed
        every { invitations.getByPlayer(owner) } returns listOf(
            GuildInvitation(guild, "Guild", owner, target, "Target"),
        )
        every { invitations.remove(owner, guild) } returns false

        assertNull(api.setBlockedDurable(owner, target, true).toCompletableFuture().join())

        verify(exactly = 1) { invitations.remove(owner, guild) }
    }

    @Test
    fun legacyCleanupExceptionDoesNotReportRollbackOfSavedBlock() {
        every { repository.setBlocked(owner, target, true) } returns true
        every { members.getPlayerGuilds(target) } throws IllegalStateException("Membership lookup failed")

        assertTrue(GuildInviteBlocksImpl(service).setBlocked(owner, target, true))
    }

    @Test
    fun durableReadFailureCannotBecomeAuthoritativeFalse() {
        every { repository.isBlocked(owner, target) } returns false
        every { repository.isBlockedDurable(owner, target) } throws IllegalStateException("Storage unavailable")

        assertFalse(GuildInviteBlocksImpl(service).isBlocked(owner, target))
        assertFailsWith<IllegalStateException> { api.isBlockedDurable(owner, target) }
    }

    @Test
    fun capturedProviderRejectsReadsAndWritesAfterClose() {
        service.close()
        service.close()

        assertFailsWith<IllegalStateException> { api.isBlockedDurable(owner, target) }
        assertFailsWith<CompletionException> {
            api.setBlockedDurable(owner, target, false).toCompletableFuture().join()
        }
        assertFalse(GuildInviteBlocksImpl(service).setBlocked(owner, target, false))
        verify(exactly = 0) {
            repository.isBlockedDurable(any(), any())
            repository.setBlockedDurable(any(), any(), any())
            repository.setBlocked(any(), any(), any())
        }
    }

    @Test
    fun closeDrainsInFlightMutationBeforeRejectingCapturedProvider() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closing = CountDownLatch(1)
        every { repository.setBlockedDurable(owner, target, false) } answers {
            entered.countDown()
            check(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            committed
        }
        val workers = Executors.newFixedThreadPool(WORKERS)
        try {
            val write = workers.submit<Void> {
                api.setBlockedDurable(owner, target, false).toCompletableFuture().join()
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val close = workers.submit {
                closing.countDown()
                service.close()
            }
            assertTrue(closing.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertFalse(close.isDone, "close must wait for the admitted synchronous write")
            release.countDown()
            assertNull(write.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            close.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertFailsWith<CompletionException> {
                api.setBlockedDurable(owner, target, false).toCompletableFuture().join()
            }
            verify(exactly = 1) { repository.setBlockedDurable(owner, target, false) }
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val WORKERS = 2
    }
}
