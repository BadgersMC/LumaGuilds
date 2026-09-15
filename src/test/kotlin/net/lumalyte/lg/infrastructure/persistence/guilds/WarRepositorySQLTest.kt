package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.*
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: WarRepositorySQL

    @BeforeEach fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = WarRepositorySQL(storage)
    }

    @AfterEach fun cleanup() { storage.connection.close() }

    private fun record(): DurableWarRecord {
        val id = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val time = Instant.parse("2026-09-14T04:05:06.123456789Z")
        val objectives = setOf(WarObjective(type = ObjectiveType.KILLS, targetValue = 25, description = "Win 25 kills"))
        return DurableWarRecord(id = id,
            declaration = WarDeclaration(id = id, declaringGuildId = first, defendingGuildId = second,
                objectives = objectives, wagerAmount = 100, declaredAt = time, expiresAt = time.plusSeconds(86400)),
            war = War(id = id, declaringGuildId = first, defendingGuildId = second, declaredAt = time,
                startedAt = time, duration = Duration.ofHours(48), status = WarStatus.ACTIVE, objectives = objectives),
            stats = WarStats(id, declaringGuildKills = 4, defendingGuildKills = 3, lastUpdated = time),
            wager = WarWager(warId = id, declaringGuildId = first, defendingGuildId = second,
                declaringGuildWager = 100, defendingGuildWager = 100, createdAt = time),
            paymentPhase = WarPaymentPhase.ESCROWED,
            paymentAttempts = mapOf("declaring-debit" to 1, "defending-debit" to 1))
    }

    @Test fun `war declaration statistics and wager survive a new repository`() {
        val original = record()
        assertTrue(repository.save(original))
        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        val reopened = WarRepositorySQL(storage)
        assertEquals(original.copy(revision = 1), reopened.get(original.id))
        assertEquals(listOf(original.copy(revision = 1)), reopened.getAll())
    }

    @Test fun `stale writer cannot overwrite a newer settlement decision`() {
        val original = record()
        assertTrue(repository.save(original))
        val stale = repository.get(original.id)!!
        val other = WarRepositorySQL(storage)
        val chosen = stale.copy(paymentPhase = WarPaymentPhase.SETTLING, settlementChosen = true,
            settlementWinner = stale.war!!.declaringGuildId)
        assertTrue(other.save(chosen))
        assertFalse(repository.save(stale.copy(paymentPhase = WarPaymentPhase.REFUNDING)))
        assertEquals(chosen.copy(revision = 2), repository.get(original.id))
    }

    @Test fun `duplicate insert does not overwrite existing war`() {
        val original = record()
        assertTrue(repository.save(original))
        assertFalse(repository.save(original))
        assertEquals(original.copy(revision = 1), repository.get(original.id))
    }

    @Test fun `independent war records are not replaced by another save`() {
        val first = record()
        val second = record()
        assertTrue(repository.save(first))
        assertTrue(repository.save(second))
        assertEquals(setOf(first.id, second.id), WarRepositorySQL(storage).getAll().map { it.id }.toSet())
    }

    @Test fun `corrupt storage fails closed instead of returning an empty registry`() {
        val original = record()
        assertTrue(repository.save(original))
        storage.connection.executeUpdate("UPDATE guild_war_records SET payload = ? WHERE war_id = ?", "not-json", original.id.toString())
        assertFailsWith<IllegalStateException> { WarRepositorySQL(storage).getAll() }
    }

    @Test fun `settled phase cannot decode an unpaid escrow wager`() {
        val original = record()
        assertTrue(repository.save(original))
        storage.connection.executeUpdate(
            "UPDATE guild_war_records SET payload = replace(replace(payload, ?, ?), ?, ?) WHERE war_id = ?",
            "\"paymentPhase\":\"ESCROWED\"", "\"paymentPhase\":\"SETTLED\"",
            "\"settlementChosen\":false", "\"settlementChosen\":true", original.id.toString())
        assertFailsWith<IllegalStateException> { repository.get(original.id) }
    }

    @Test fun `unsupported stored format is rejected`() {
        val original = record()
        assertTrue(repository.save(original))
        storage.connection.executeUpdate("UPDATE guild_war_records SET payload = ? WHERE war_id = ?", "{\"version\":999,\"record\":{}}", original.id.toString())
        assertFailsWith<IllegalStateException> { repository.get(original.id) }
    }

    @Test fun `missing settlement flag cannot silently become an unchosen outcome`() {
        val original = record().copy(revision = 1)
        val json = JsonParser.parseString(WarRecordCodec.encode(original)).asJsonObject
        json.getAsJsonObject("record").add("settlementChosen", JsonNull.INSTANCE)
        assertFailsWith<IllegalStateException> { WarRecordCodec.decode(json.toString()) }
    }

    @Test fun `unknown payment phase is not accepted as a legacy empty phase`() {
        val json = JsonParser.parseString(WarRecordCodec.encode(record())).asJsonObject
        json.getAsJsonObject("record").addProperty("paymentPhase", "UNKNOWN_PHASE")
        assertFailsWith<IllegalStateException> { WarRecordCodec.decode(json.toString()) }
    }
}
