package net.lumalyte.lg.application.services

import dev.mizarc.bellclaims.domain.entities.AuditRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID

class AuditServiceTest {
    
    private lateinit var actorId: UUID
    private lateinit var guildId: UUID
    private lateinit var claimId: UUID
    
    @BeforeEach
    fun setUp() {
        actorId = UUID.randomUUID()
        guildId = UUID.randomUUID()
        claimId = UUID.randomUUID()
    }
    
    @Test
    fun `should create audit record with all fields`() {
        val time = Instant.now()
        val action = "TEST_ACTION"
        val details = "Test action details"
        
        val auditRecord = AuditRecord(
            id = UUID.randomUUID(),
            time = time,
            actorId = actorId,
            guildId = guildId,
            action = action,
            details = details
        )
        
        assertEquals(actorId, auditRecord.actorId)
        assertEquals(guildId, auditRecord.guildId)
        assertEquals(action, auditRecord.action)
        assertEquals(details, auditRecord.details)
        assertEquals(time, auditRecord.time)
    }
    
    @Test
    fun `should create audit record without guild ID`() {
        val time = Instant.now()
        val action = "PLAYER_ACTION"
        
        val auditRecord = AuditRecord(
            id = UUID.randomUUID(),
            time = time,
            actorId = actorId,
            guildId = null,
            action = action,
            details = null
        )
        
        assertEquals(actorId, auditRecord.actorId)
        assertNull(auditRecord.guildId)
        assertEquals(action, auditRecord.action)
        assertNull(auditRecord.details)
        assertEquals(time, auditRecord.time)
    }
    
    @Test
    fun `should create audit record without details`() {
        val time = Instant.now()
        val action = "SIMPLE_ACTION"
        
        val auditRecord = AuditRecord(
            id = UUID.randomUUID(),
            time = time,
            actorId = actorId,
            guildId = guildId,
            action = action,
            details = null
        )
        
        assertEquals(actorId, auditRecord.actorId)
        assertEquals(guildId, auditRecord.guildId)
        assertEquals(action, auditRecord.action)
        assertNull(auditRecord.details)
        assertEquals(time, auditRecord.time)
    }
    
    @Test
    fun `should validate action is not blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuditRecord(
                id = UUID.randomUUID(),
                time = Instant.now(),
                actorId = actorId,
                guildId = guildId,
                action = "",
                details = null
            )
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            AuditRecord(
                id = UUID.randomUUID(),
                time = Instant.now(),
                actorId = actorId,
                guildId = guildId,
                action = "   ",
                details = null
            )
        }
    }
    
    @Test
    fun `should accept valid action names`() {
        val validActions = listOf(
            "RANK_CHANGE",
            "RELATION_CHANGE",
            "WAR_STATE_CHANGE",
            "GUILD_MODE_CHANGE",
            "CLAIM_CREATE",
            "CLAIM_DELETE",
            "BANK_DEPOSIT",
            "BANK_WITHDRAWAL"
        )
        
        validActions.forEach { action ->
            val auditRecord = AuditRecord(
                id = UUID.randomUUID(),
                time = Instant.now(),
                actorId = actorId,
                guildId = guildId,
                action = action,
                details = null
            )
            assertEquals(action, auditRecord.action)
        }
    }
    
    @Test
    fun `should handle long details text`() {
        val longDetails = "This is a very long detailed description of an action that contains " +
                "multiple sentences and provides comprehensive information about what happened " +
                "during the execution of the action, including context, reasons, and outcomes."
        
        val auditRecord = AuditRecord(
            id = UUID.randomUUID(),
            time = Instant.now(),
            actorId = actorId,
            guildId = guildId,
            action = "LONG_DETAILS_ACTION",
            details = longDetails
        )
        
        assertEquals(longDetails, auditRecord.details)
    }
    
    @Test
    fun `should handle special characters in details`() {
        val specialDetails = "Action with special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?"
        
        val auditRecord = AuditRecord(
            id = UUID.randomUUID(),
            time = Instant.now(),
            actorId = actorId,
            guildId = guildId,
            action = "SPECIAL_CHARS_ACTION",
            details = specialDetails
        )
        
        assertEquals(specialDetails, auditRecord.details)
    }
    
    @Test
    fun `should handle unicode characters in details`() {
        val unicodeDetails = "Action with unicode: 🎮⚔️🏰💰🎯"
        
        val auditRecord = AuditRecord(
            id = UUID.randomUUID(),
            time = Instant.now(),
            actorId = actorId,
            guildId = guildId,
            action = "UNICODE_ACTION",
            details = unicodeDetails
        )
        
        assertEquals(unicodeDetails, auditRecord.details)
    }
    
    @Test
    fun `should create multiple audit records with different IDs`() {
        val time = Instant.now()
        val action = "MULTIPLE_ACTIONS"
        
        val auditRecord1 = AuditRecord(
            id = UUID.randomUUID(),
            time = time,
            actorId = actorId,
            guildId = guildId,
            action = action,
            details = "First action"
        )
        
        val auditRecord2 = AuditRecord(
            id = UUID.randomUUID(),
            time = time,
            actorId = actorId,
            guildId = guildId,
            action = action,
            details = "Second action"
        )
        
        assertNotEquals(auditRecord1.id, auditRecord2.id)
        assertEquals(auditRecord1.time, auditRecord2.time)
        assertEquals(auditRecord1.actorId, auditRecord2.actorId)
        assertEquals(auditRecord1.guildId, auditRecord2.guildId)
        assertEquals(auditRecord1.action, auditRecord2.action)
    }
}
