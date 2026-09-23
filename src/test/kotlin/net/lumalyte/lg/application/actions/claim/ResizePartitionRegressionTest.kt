package net.lumalyte.lg.application.actions.claim

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.actions.claim.partition.ResizePartition
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.results.claim.partition.ResizePartitionResult
import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResizePartitionRegressionTest {
    @Test
    fun `resize replaces old area when calculating required blocks`() = runBlocking {
        val fixture = fixture(limit = 5)

        val result = fixture.action.execute(
            fixture.partition.id,
            Position2D(1, 1),
            Position2D(2, 1),
        )

        val insufficient = assertIs<ResizePartitionResult.InsufficientBlocks>(result)
        assertEquals(1, insufficient.requiredExtraBlocks)
    }

    @Test
    fun `resize reports remaining blocks after replacing old area`() = runBlocking {
        val fixture = fixture(limit = 10)

        val result = fixture.action.execute(
            fixture.partition.id,
            Position2D(1, 1),
            Position2D(2, 1),
        )

        val success = assertIs<ResizePartitionResult.Success>(result)
        assertEquals(4, success.remainingBlocks)
    }

    @Test
    fun `resize reports storage error when persistence fails`() = runBlocking {
        val fixture = fixture(limit = 10, updateSucceeds = false)

        val result = fixture.action.execute(
            fixture.partition.id,
            Position2D(1, 1),
            Position2D(2, 1),
        )

        assertIs<ResizePartitionResult.StorageError>(result)
    }

    private fun fixture(limit: Int, updateSucceeds: Boolean = true): Fixture {
        val owner = UUID.randomUUID()
        val world = UUID.randomUUID()
        val claim = Claim(
            worldId = world,
            playerId = owner,
            position = Position3D(0, 64, 0),
            name = "Resize",
        )
        val partition = Partition(
            claim.id,
            Area(Position2D(0, 0), Position2D(1, 1)),
        )

        val claims = mockk<ClaimRepository>()
        val partitions = mockk<PartitionRepository>()
        val metadata = mockk<PlayerMetadataService>()
        val config = mockk<MainConfig>()

        every { claims.getById(claim.id) } returns claim
        every { claims.getByPlayer(owner) } returns setOf(claim)
        every { partitions.getById(partition.id) } returns partition
        every { partitions.getByClaim(claim.id) } returns setOf(partition)
        every { partitions.getByPosition(any()) } returns setOf(partition)
        every { partitions.getByChunk(any()) } returns emptySet()
        every { partitions.update(any()) } returns updateSucceeds
        coEvery { metadata.getPlayerClaimBlockLimitAsync(owner) } returns limit
        every { config.minimumPartitionSize } returns 1
        every { config.distanceBetweenClaims } returns 0

        return Fixture(
            ResizePartition(claims, partitions, metadata, config),
            partition,
        )
    }

    private data class Fixture(
        val action: ResizePartition,
        val partition: Partition,
    )
}
