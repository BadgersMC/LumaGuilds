package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class GuildListRepositorySQLTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildListRepositorySQL

    private val alpha = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val beta = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val charlie = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val weeklyStart = Instant.parse("2026-09-15T00:00:00Z")

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        storage.connection.executeUpdate(
            """CREATE TABLE guilds (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                level INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )""".trimIndent()
        )
        storage.connection.executeUpdate(
            """CREATE TABLE experience_transactions (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                amount INTEGER NOT NULL,
                source TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )""".trimIndent()
        )
        storage.connection.executeUpdate(
            """CREATE TABLE kills (
                id TEXT PRIMARY KEY,
                killer_guild_id TEXT,
                victim_guild_id TEXT,
                victim_id TEXT NOT NULL,
                timestamp TEXT NOT NULL
            )""".trimIndent()
        )
        insertGuild(alpha, "Alpha", 2, "2026-01-03 00:00:00")
        insertGuild(beta, "Beta", 1, "2026-01-01 00:00:00")
        insertGuild(charlie, "Charlie", 1, "2026-01-02 00:00:00")
        repository = GuildListRepositorySQL(storage)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close()
    }

    @Test
    fun `level and creation sorting are deterministic and page in SQL`() {
        val levelPage = repository.getPage(
            offset = 1,
            limit = 1,
            sortKey = GuildListSortKey.GUILD_LEVEL,
            ascending = true,
            weeklyStart = weeklyStart,
            claimsEnabled = true,
            uniqueKillWeight = 25,
        )
        assertEquals(listOf(charlie), levelPage.map { it.guildId })

        val creation = repository.getPage(
            offset = 0,
            limit = 3,
            sortKey = GuildListSortKey.CREATED_AT,
            ascending = true,
            weeklyStart = weeklyStart,
            claimsEnabled = true,
            uniqueKillWeight = 25,
        )
        assertEquals(listOf(beta, charlie, alpha), creation.map { it.guildId })
    }

    @Test
    fun `all time activity uses weighted progression transaction score`() {
        insertExperience(alpha, 10, "WAR_WON", "2026-09-20T12:00:00Z")
        insertExperience(beta, 20, "PLAYER_KILL", "2026-09-20T12:00:00Z")
        insertExperience(charlie, 20, "CLAIM_CREATED", "2026-09-20T12:00:00Z")

        val page = repository.getPage(
            offset = 0,
            limit = 3,
            sortKey = GuildListSortKey.ALL_TIME_ACTIVE,
            ascending = false,
            weeklyStart = weeklyStart,
            claimsEnabled = true,
            uniqueKillWeight = 25,
        )

        assertEquals(listOf(charlie, alpha, beta), page.map { it.guildId })
        assertEquals(listOf(40L, 30L, 20L), page.map { it.sortValue })
    }

    @Test
    fun `weekly activity counts each opposing victim once and ignores same guild kills`() {
        insertExperience(alpha, 5, "PLAYER_KILL", "2026-09-20T12:00:00Z")
        insertExperience(alpha, 100, "WAR_WON", "2026-09-10T12:00:00Z")
        insertExperience(beta, 20, "PLAYER_KILL", "2026-09-20T12:00:00Z")

        val victimA = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val victimB = UUID.fromString("10000000-0000-0000-0000-000000000002")
        insertKill(alpha, beta, victimA, "2026-09-20T12:00:00Z")
        insertKill(alpha, beta, victimA, "2026-09-20T13:00:00Z")
        insertKill(alpha, charlie, victimB, "2026-09-20T14:00:00Z")
        insertKill(alpha, alpha, UUID.randomUUID(), "2026-09-20T15:00:00Z")

        val page = repository.getPage(
            offset = 0,
            limit = 3,
            sortKey = GuildListSortKey.WEEKLY_ACTIVE,
            ascending = false,
            weeklyStart = weeklyStart,
            claimsEnabled = true,
            uniqueKillWeight = 25,
        )

        assertEquals(alpha, page.first().guildId)
        assertEquals(50L, page.first().sortValue)
        assertEquals(2, page.first().uniquePvpKills)
        assertEquals(0L, page[1].sortValue)
    }

    private fun insertGuild(id: UUID, name: String, level: Int, createdAt: String) {
        storage.connection.executeUpdate(
            "INSERT INTO guilds (id, name, level, created_at) VALUES (?, ?, ?, ?)",
            id.toString(), name, level, createdAt
        )
    }

    private fun insertExperience(guildId: UUID, amount: Int, source: String, timestamp: String) {
        storage.connection.executeUpdate(
            "INSERT INTO experience_transactions (id, guild_id, amount, source, timestamp) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            guildId.toString(),
            amount,
            source,
            Instant.parse(timestamp).toEpochMilli(),
        )
    }

    private fun insertKill(
        killerGuildId: UUID,
        victimGuildId: UUID,
        victimId: UUID,
        timestamp: String,
    ) {
        storage.connection.executeUpdate(
            "INSERT INTO kills (id, killer_guild_id, victim_guild_id, victim_id, timestamp) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            killerGuildId.toString(),
            victimGuildId.toString(),
            victimId.toString(),
            timestamp,
        )
    }
}
