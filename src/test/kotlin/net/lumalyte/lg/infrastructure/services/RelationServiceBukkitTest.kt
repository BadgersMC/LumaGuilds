package net.lumalyte.lg.infrastructure.services

import dev.mizarc.bellclaims.application.persistence.RelationRepository
import dev.mizarc.bellclaims.application.services.MemberService
import dev.mizarc.bellclaims.domain.entities.Relation
import dev.mizarc.bellclaims.domain.entities.RelationType
import dev.mizarc.bellclaims.domain.entities.RelationStatus
import dev.mizarc.bellclaims.domain.entities.RankPermission
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RelationServiceBukkitTest {
    
    private lateinit var relationService: RelationServiceBukkit
    private lateinit var mockRelationRepository: RelationRepository
    private lateinit var mockMemberService: MemberService
    
    private lateinit var guildA: UUID
    private lateinit var guildB: UUID
    private lateinit var playerId: UUID
    private lateinit var actorId: UUID
    
    @BeforeEach
    fun setUp() {
        mockRelationRepository = mockk<RelationRepository>()
        mockMemberService = mockk<MemberService>()
        
        guildA = UUID.randomUUID()
        guildB = UUID.randomUUID()
        playerId = UUID.randomUUID()
        actorId = UUID.randomUUID()
        
        relationService = RelationServiceBukkit(mockRelationRepository, mockMemberService)
    }
    
    @Test
    fun `should successfully request alliance when permissions valid and no existing relation`() {
        // Given: Player has permission and no existing relation
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns null
        every { mockRelationRepository.add(any()) } returns true
        
        // When
        val result = relationService.requestAlliance(guildA, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.ALLY, result?.type)
        assertEquals(RelationStatus.PENDING, result?.status)
        verify { mockRelationRepository.add(any()) }
    }
    
    @Test
    fun `should fail to request alliance when player lacks permission`() {
        // Given: Player does not have permission
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns false
        
        // When
        val result = relationService.requestAlliance(guildA, guildB, actorId)
        
        // Then
        assertNull(result)
        verify(exactly = 0) { mockRelationRepository.add(any()) }
    }
    
    @Test
    fun `should fail to request alliance when relation already exists`() {
        // Given: Player has permission but relation already exists
        val existingRelation = createTestRelation(RelationType.ALLY, RelationStatus.ACTIVE)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns existingRelation
        
        // When
        val result = relationService.requestAlliance(guildA, guildB, actorId)
        
        // Then
        assertNull(result)
        verify(exactly = 0) { mockRelationRepository.add(any()) }
    }
    
    @Test
    fun `should successfully accept alliance request`() {
        // Given: Pending alliance request exists
        val pendingRelation = createTestRelation(RelationType.ALLY, RelationStatus.PENDING)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildB)
        every { mockMemberService.hasPermission(actorId, guildB, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getById(pendingRelation.id) } returns pendingRelation
        every { mockRelationRepository.update(any()) } returns true
        
        // When
        val result = relationService.acceptAlliance(pendingRelation.id, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.ALLY, result?.type)
        assertEquals(RelationStatus.ACTIVE, result?.status)
        verify { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should fail to accept alliance when relation is not pending`() {
        // Given: Active (not pending) alliance
        val activeRelation = createTestRelation(RelationType.ALLY, RelationStatus.ACTIVE)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildB)
        every { mockMemberService.hasPermission(actorId, guildB, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getById(activeRelation.id) } returns activeRelation
        
        // When
        val result = relationService.acceptAlliance(activeRelation.id, guildB, actorId)
        
        // Then
        assertNull(result)
        verify(exactly = 0) { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should successfully declare war`() {
        // Given: Player has permission and no existing relation
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns null
        every { mockRelationRepository.add(any()) } returns true
        
        // When
        val result = relationService.declareWar(guildA, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.ENEMY, result?.type)
        assertEquals(RelationStatus.ACTIVE, result?.status)
        verify { mockRelationRepository.add(any()) }
    }
    
    @Test
    fun `should update existing relation to enemy when declaring war`() {
        // Given: Existing alliance relation
        val existingRelation = createTestRelation(RelationType.ALLY, RelationStatus.ACTIVE)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns existingRelation
        every { mockRelationRepository.update(any()) } returns true
        
        // When
        val result = relationService.declareWar(guildA, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.ENEMY, result?.type)
        assertEquals(RelationStatus.ACTIVE, result?.status)
        assertNull(result?.expiresAt) // War should not expire
        verify { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should successfully request truce when guilds are enemies`() {
        // Given: Guilds are currently enemies
        val enemyRelation = createTestRelation(RelationType.ENEMY, RelationStatus.ACTIVE)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns enemyRelation
        every { mockRelationRepository.update(any()) } returns true
        
        val duration = Duration.ofDays(7)
        
        // When
        val result = relationService.requestTruce(guildA, guildB, actorId, duration)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.TRUCE, result?.type)
        assertEquals(RelationStatus.PENDING, result?.status)
        assertNotNull(result?.expiresAt)
        verify { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should fail to request truce when guilds are not enemies`() {
        // Given: Guilds are neutral (no relation)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns null
        
        val duration = Duration.ofDays(7)
        
        // When
        val result = relationService.requestTruce(guildA, guildB, actorId, duration)
        
        // Then
        assertNull(result)
        verify(exactly = 0) { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should successfully accept truce request`() {
        // Given: Pending truce request
        val pendingTruce = createTestRelation(RelationType.TRUCE, RelationStatus.PENDING, Instant.now().plusSeconds(604800))
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildB)
        every { mockMemberService.hasPermission(actorId, guildB, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getById(pendingTruce.id) } returns pendingTruce
        every { mockRelationRepository.update(any()) } returns true
        
        // When
        val result = relationService.acceptTruce(pendingTruce.id, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.TRUCE, result?.type)
        assertEquals(RelationStatus.ACTIVE, result?.status)
        verify { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should successfully request unenemy when guilds are enemies`() {
        // Given: Guilds are currently enemies
        val enemyRelation = createTestRelation(RelationType.ENEMY, RelationStatus.ACTIVE)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns enemyRelation
        every { mockRelationRepository.update(any()) } returns true
        
        // When
        val result = relationService.requestUnenemy(guildA, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.NEUTRAL, result?.type)
        assertEquals(RelationStatus.PENDING, result?.status)
        assertNull(result?.expiresAt)
        verify { mockRelationRepository.update(any()) }
    }
    
    @Test
    fun `should successfully accept unenemy request`() {
        // Given: Pending unenemy request
        val pendingUnenemy = createTestRelation(RelationType.NEUTRAL, RelationStatus.PENDING)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildB)
        every { mockMemberService.hasPermission(actorId, guildB, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getById(pendingUnenemy.id) } returns pendingUnenemy
        every { mockRelationRepository.remove(pendingUnenemy.id) } returns true
        
        // When
        val result = relationService.acceptUnenemy(pendingUnenemy.id, guildB, actorId)
        
        // Then
        assertNotNull(result)
        assertEquals(RelationType.NEUTRAL, result?.type)
        assertEquals(RelationStatus.ACTIVE, result?.status)
        verify { mockRelationRepository.remove(pendingUnenemy.id) }
    }
    
    @Test
    fun `should successfully reject request`() {
        // Given: Pending alliance request
        val pendingRelation = createTestRelation(RelationType.ALLY, RelationStatus.PENDING)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildB)
        every { mockMemberService.hasPermission(actorId, guildB, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getById(pendingRelation.id) } returns pendingRelation
        every { mockRelationRepository.update(any()) } returns true
        
        // When
        val result = relationService.rejectRequest(pendingRelation.id, guildB, actorId)
        
        // Then
        assertTrue(result)
        verify { mockRelationRepository.update(match { it.status == RelationStatus.REJECTED }) }
    }
    
    @Test
    fun `should successfully cancel request`() {
        // Given: Pending alliance request made by guildA
        val pendingRelation = createTestRelation(RelationType.ALLY, RelationStatus.PENDING)
        every { mockMemberService.getPlayerGuilds(actorId) } returns setOf(guildA)
        every { mockMemberService.hasPermission(actorId, guildA, RankPermission.MANAGE_RELATIONS) } returns true
        every { mockRelationRepository.getById(pendingRelation.id) } returns pendingRelation
        every { mockRelationRepository.remove(pendingRelation.id) } returns true
        
        // When
        val result = relationService.cancelRequest(pendingRelation.id, guildA, actorId)
        
        // Then
        assertTrue(result)
        verify { mockRelationRepository.remove(pendingRelation.id) }
    }
    
    @Test
    fun `should return correct relation type for active relations`() {
        // Given: Active alliance
        val allianceRelation = createTestRelation(RelationType.ALLY, RelationStatus.ACTIVE)
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns allianceRelation
        
        // When
        val relationType = relationService.getRelationType(guildA, guildB)
        
        // Then
        assertEquals(RelationType.ALLY, relationType)
    }
    
    @Test
    fun `should return neutral when no relation exists`() {
        // Given: No relation exists
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns null
        
        // When
        val relationType = relationService.getRelationType(guildA, guildB)
        
        // Then
        assertEquals(RelationType.NEUTRAL, relationType)
    }
    
    @Test
    fun `should return neutral when relation has expired`() {
        // Given: Expired truce (created in the past, expires in the past)
        val pastTime = Instant.now().minusSeconds(7200) // 2 hours ago
        val expiredTruce = Relation.create(
            guildA = guildA,
            guildB = guildB,
            type = RelationType.TRUCE,
            status = RelationStatus.ACTIVE,
            expiresAt = Instant.now().minusSeconds(3600), // 1 hour ago
            createdAt = pastTime
        ).copy(updatedAt = pastTime)
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns expiredTruce
        
        // When
        val relationType = relationService.getRelationType(guildA, guildB)
        
        // Then
        assertEquals(RelationType.NEUTRAL, relationType)
    }
    
    @Test
    fun `should process expired truces and revert to enemy`() {
        // Given: Expired truce (created in the past, expires in the past)
        val pastTime = Instant.now().minusSeconds(7200) // 2 hours ago
        val expiredTruce = Relation.create(
            guildA = guildA,
            guildB = guildB,
            type = RelationType.TRUCE,
            status = RelationStatus.ACTIVE,
            expiresAt = Instant.now().minusSeconds(3600), // 1 hour ago
            createdAt = pastTime
        ).copy(updatedAt = pastTime)
        every { mockRelationRepository.getExpiredRelations() } returns setOf(expiredTruce)
        every { mockRelationRepository.update(any()) } returns true
        
        // When
        val processedCount = relationService.processExpiredRelations()
        
        // Then
        assertEquals(1, processedCount)
        verify { mockRelationRepository.update(match { 
            it.type == RelationType.ENEMY && it.status == RelationStatus.ACTIVE && it.expiresAt == null
        }) }
    }
    
    @Test
    fun `should validate relation changes correctly`() {
        // Mock no existing relation for initial tests
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns null
        
        // When & Then: Valid transitions from neutral
        assertTrue(relationService.isValidRelationChange(guildA, guildB, RelationType.ALLY)) // Neutral -> Ally
        assertTrue(relationService.isValidRelationChange(guildA, guildB, RelationType.ENEMY)) // Can always declare war
        
        // Mock existing enemy relation for truce validation
        val enemyRelation = createTestRelation(RelationType.ENEMY, RelationStatus.ACTIVE)
        every { mockRelationRepository.getByGuilds(guildA, guildB) } returns enemyRelation
        assertTrue(relationService.isValidRelationChange(guildA, guildB, RelationType.TRUCE)) // Enemy -> Truce
        assertTrue(relationService.isValidRelationChange(guildA, guildB, RelationType.NEUTRAL)) // Enemy -> Neutral
        
        // Invalid transition: same guild
        assertFalse(relationService.isValidRelationChange(guildA, guildA, RelationType.ALLY))
    }
    
    private fun createTestRelation(
        type: RelationType, 
        status: RelationStatus, 
        expiresAt: Instant? = null
    ): Relation {
        return Relation.create(
            guildA = guildA,
            guildB = guildB,
            type = type,
            status = status,
            expiresAt = expiresAt
        )
    }
}
