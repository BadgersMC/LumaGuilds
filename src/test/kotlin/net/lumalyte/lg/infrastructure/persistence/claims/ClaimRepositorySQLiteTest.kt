package net.lumalyte.lg.infrastructure.persistence.claims

import dev.mizarc.bellclaims.application.persistence.ClaimRepository
import dev.mizarc.bellclaims.domain.entities.Claim
import dev.mizarc.bellclaims.domain.values.Position3D
import dev.mizarc.bellclaims.infrastructure.persistence.storage.SQLiteStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import co.aikar.idb.Database
import java.sql.SQLException
import java.util.*

class ClaimRepositorySQLiteTest {
    
    private lateinit var claimRepository: ClaimRepository
    private lateinit var mockDatabase: Database
    private lateinit var mockStorage: SQLiteStorage
    
    private lateinit var playerId: UUID
    private lateinit var teamId: UUID
    private lateinit var worldId: UUID
    private lateinit var claimId: UUID
    private lateinit var position: Position3D
    
    @BeforeEach
    fun setUp() {
        mockDatabase = mockk<Database>()
        mockStorage = mockk<SQLiteStorage>()
        
        every { mockStorage.connection } returns mockDatabase
        every { mockDatabase.executeUpdate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        every { mockDatabase.executeUpdate(any(), any()) } returns 1
        every { mockDatabase.executeUpdate(any()) } returns 1
        every { mockDatabase.getResults("SELECT * FROM claims") } returns emptyList()
        every { mockDatabase.getResults(any(), any()) } returns emptyList()
        
        playerId = UUID.randomUUID()
        teamId = UUID.randomUUID()
        worldId = UUID.randomUUID()
        claimId = UUID.randomUUID()
        position = Position3D(100, 64, 100)
        
        claimRepository = ClaimRepositorySQLite(mockStorage)
    }
    
    @Test
    fun `should create claims table with team_id column`() {
        // Given: Mock setup for table creation
        every { mockDatabase.executeUpdate(any()) } returns 1
        
        // When: Repository is initialized
        val newRepository = ClaimRepositorySQLite(mockStorage)
        
        // Then: Table creation should be attempted
        verify { mockDatabase.executeUpdate(any()) }
    }
    
    @Test
    fun `should add claim with team ownership`() {
        // Given: A claim with team ownership
        val claim = Claim(worldId, playerId, teamId, position, "TeamClaim")
        
        // When: Adding the claim
        val result = claimRepository.add(claim)
        
        // Then: Should succeed
        assertTrue(result)
        
        // Verify the claim is stored in memory
        val storedClaim = claimRepository.getById(claim.id)
        assertNotNull(storedClaim)
        assertEquals(teamId, storedClaim?.teamId)
        assertEquals(playerId, storedClaim?.playerId)
    }
    
    @Test
    fun `should add claim without team ownership`() {
        // Given: A claim without team ownership
        val claim = Claim(worldId, playerId, null, position, "IndividualClaim")
        
        // When: Adding the claim
        val result = claimRepository.add(claim)
        
        // Then: Should succeed
        assertTrue(result)
        
        // Verify the claim is stored in memory
        val storedClaim = claimRepository.getById(claim.id)
        assertNotNull(storedClaim)
        assertNull(storedClaim?.teamId)
        assertEquals(playerId, storedClaim?.playerId)
    }
    
    @Test
    fun `should get claim by name for team`() {
        // Given: A team claim
        val claim = Claim(worldId, playerId, teamId, position, "TeamClaim")
        claimRepository.add(claim)
        
        // When: Getting claim by team name
        val foundClaim = claimRepository.getByNameForTeam(teamId, "TeamClaim")
        
        // Then: Should find the claim
        assertNotNull(foundClaim)
        assertEquals("TeamClaim", foundClaim?.name)
        assertEquals(teamId, foundClaim?.teamId)
    }
    
    @Test
    fun `should get claim by name for player`() {
        // Given: An individual claim
        val claim = Claim(worldId, playerId, null, position, "IndividualClaim")
        claimRepository.add(claim)
        
        // When: Getting claim by player name
        val foundClaim = claimRepository.getByName(playerId, "IndividualClaim")
        
        // Then: Should find the claim
        assertNotNull(foundClaim)
        assertEquals("IndividualClaim", foundClaim?.name)
        assertNull(foundClaim?.teamId)
    }
    
    @Test
    fun `should get claims by team`() {
        // Given: Multiple team claims
        val claim1 = Claim(worldId, playerId, teamId, position, "TeamClaim1")
        val claim2 = Claim(worldId, playerId, teamId, Position3D(200, 64, 200), "TeamClaim2")
        val individualClaim = Claim(worldId, playerId, null, Position3D(300, 64, 300), "IndividualClaim")
        
        claimRepository.add(claim1)
        claimRepository.add(claim2)
        claimRepository.add(individualClaim)
        
        // When: Getting claims by team
        val teamClaims = claimRepository.getByTeam(teamId)
        
        // Then: Should return only team claims
        assertEquals(2, teamClaims.size)
        assertTrue(teamClaims.all { it.teamId == teamId })
    }
    
    @Test
    fun `should get claims by player`() {
        // Given: Multiple claims for a player
        val teamClaim = Claim(worldId, playerId, teamId, position, "TeamClaim")
        val individualClaim = Claim(worldId, playerId, null, Position3D(200, 64, 200), "IndividualClaim")
        
        claimRepository.add(teamClaim)
        claimRepository.add(individualClaim)
        
        // When: Getting claims by player
        val playerClaims = claimRepository.getByPlayer(playerId)
        
        // Then: Should return all claims for the player
        assertEquals(2, playerClaims.size)
        assertTrue(playerClaims.all { it.playerId == playerId })
    }
    
    @Test
    fun `should handle database errors gracefully`() {
        // Given: Database throws an exception
        every { mockDatabase.executeUpdate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws SQLException("Database error")
        
        // When: Adding a claim
        val claim = Claim(worldId, playerId, teamId, position, "TestClaim")
        
        // Then: Should throw DatabaseOperationException
        assertThrows(Exception::class.java) {
            claimRepository.add(claim)
        }
    }
}
