package net.lumalyte.lg.application.actions.claim

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.lumalyte.lg.application.actions.claim.transfer.AcceptTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.OfferPlayerTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.WithdrawPlayerTransferRequest
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.results.claim.transfer.AcceptTransferRequestResult
import net.lumalyte.lg.application.results.claim.transfer.OfferPlayerTransferRequestResult
import net.lumalyte.lg.application.results.claim.transfer.WithdrawPlayerTransferRequestResult
import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertIs

class ClaimTransferRegressionTest {
    @Test
    fun `offer persists durable request instead of mutating claim only`() {
        val claim = claim()
        val receiver = UUID.randomUUID()
        val claims = mockk<ClaimRepository>()
        val requests = mockk<ClaimTransferRequestRepository>()
        every { claims.getById(claim.id) } returns claim
        every { requests.hasActive(claim.id, receiver, any()) } returns false
        every { requests.offer(claim.id, receiver, any()) } returns true

        val result = OfferPlayerTransferRequest(claims, requests).execute(claim.id, receiver)

        assertIs<OfferPlayerTransferRequestResult.Success>(result)
        verify(exactly = 1) { requests.offer(claim.id, receiver, match { it > 0 }) }
        verify(exactly = 0) { claims.update(any()) }
    }

    @Test
    fun `withdraw removes an existing durable request`() {
        val claim = claim()
        val receiver = UUID.randomUUID()
        val claims = mockk<ClaimRepository>()
        val requests = mockk<ClaimTransferRequestRepository>()
        every { claims.getById(claim.id) } returns claim
        every { requests.hasActive(claim.id, receiver, any()) } returns true
        every { requests.withdraw(claim.id, receiver) } returns true

        val result = WithdrawPlayerTransferRequest(claims, requests).execute(claim.id, receiver)

        assertIs<WithdrawPlayerTransferRequestResult.Success>(result)
        verify(exactly = 1) { requests.withdraw(claim.id, receiver) }
    }

    @Test
    fun `accept checks name collision in receiver namespace`() {
        val oldOwner = UUID.randomUUID()
        val receiver = UUID.randomUUID()
        val claim = claim(owner = oldOwner)
        val claims = mockk<ClaimRepository>()
        val requests = mockk<ClaimTransferRequestRepository>()
        val partitions = mockk<PartitionRepository>()
        val metadata = mockk<PlayerMetadataService>()

        every { claims.getById(claim.id) } returns claim
        every { requests.hasActive(claim.id, receiver, any()) } returns true
        every { metadata.getPlayerClaimLimit(receiver) } returns 10
        every { claims.getByPlayer(receiver) } returns emptySet()
        every { metadata.getPlayerClaimBlockLimit(receiver) } returns 1000
        every { partitions.getByClaim(any()) } returns emptySet()
        every { claims.getByName(receiver, "Home") } returns claim(owner = receiver)

        val result = AcceptTransferRequest(claims, metadata, partitions, requests)
            .execute(claim.id, receiver, "Home")

        assertIs<AcceptTransferRequestResult.NameAlreadyExists>(result)
        verify(exactly = 1) { claims.getByName(receiver, "Home") }
        verify(exactly = 0) { claims.getByName(oldOwner, "Home") }
        verify(exactly = 0) { claims.update(any()) }
    }

    @Test
    fun `accept persists new owner and clears all outstanding requests`() {
        val receiver = UUID.randomUUID()
        val claim = claim()
        val claims = mockk<ClaimRepository>()
        val requests = mockk<ClaimTransferRequestRepository>()
        val partitions = mockk<PartitionRepository>()
        val metadata = mockk<PlayerMetadataService>()
        every { claims.getById(claim.id) } returns claim
        every { requests.hasActive(claim.id, receiver, any()) } returns true

        every { metadata.getPlayerClaimLimit(receiver) } returns 10
        every { claims.getByPlayer(receiver) } returns emptySet()
        every { metadata.getPlayerClaimBlockLimit(receiver) } returns 1000
        every { partitions.getByClaim(any()) } returns emptySet()
        every { claims.getByName(receiver, "Received") } returns null
        every { requests.consumeClaim(claim.id, receiver) } returns true
        every { claims.updateIfOwnedBy(any(), claim.playerId) } returns true

        val result = AcceptTransferRequest(claims, metadata, partitions, requests)
            .execute(claim.id, receiver, "Received")

        assertIs<AcceptTransferRequestResult.Success>(result)
        verify {
            claims.updateIfOwnedBy(
                match { it.id == claim.id && it.playerId == receiver && it.name == "Received" },
                claim.playerId,
            )
        }
        verifyOrder {
            requests.consumeClaim(claim.id, receiver)
            claims.updateIfOwnedBy(
                match { it.id == claim.id && it.playerId == receiver && it.name == "Received" },
                claim.playerId,
            )
        }
    }

    @Test
    fun `accept does not change ownership when receiver offer was already consumed`() {
        val receiver = UUID.randomUUID()
        val claim = claim()
        val claims = mockk<ClaimRepository>()
        val requests = mockk<ClaimTransferRequestRepository>()
        val partitions = mockk<PartitionRepository>()
        val metadata = mockk<PlayerMetadataService>()
        every { claims.getById(claim.id) } returns claim
        every { requests.hasActive(claim.id, receiver, any()) } returns true
        every { metadata.getPlayerClaimLimit(receiver) } returns 10
        every { claims.getByPlayer(receiver) } returns emptySet()
        every { metadata.getPlayerClaimBlockLimit(receiver) } returns 1000
        every { partitions.getByClaim(any()) } returns emptySet()
        every { claims.getByName(receiver, "Received") } returns null
        every { requests.consumeClaim(claim.id, receiver) } returns false

        val result = AcceptTransferRequest(claims, metadata, partitions, requests)
            .execute(claim.id, receiver, "Received")

        assertIs<AcceptTransferRequestResult.NoActiveTransferRequest>(result)
        verify(exactly = 0) { claims.updateIfOwnedBy(any(), any()) }
    }

    private fun claim(
        owner: UUID = UUID.randomUUID(),
    ) = Claim(
        worldId = UUID.randomUUID(),
        playerId = owner,
        position = Position3D(0, 64, 0),
        name = "Claim",
    )
}
