package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildHomeAccessState
import net.lumalyte.lg.application.persistence.VerifiedGuildHomeGrant
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildHomeAccessGrantRepositorySQLTest : RewardSqlTestFixture() {
    private val guildId = UUID.randomUUID()
    private val reviewer = UUID.randomUUID()
    private val world = UUID.randomUUID()
    private val legacyNames = (1..6).map { "legacy-$it" }
    private val prematureNames = (1..9).map { "outpost-$it" }

    private data class Subject(
        val storage: Storage<Database>,
        val guildRepository: GuildRepositorySQLite,
        val access: GuildHomeAccessGrantRepositorySQL,
    )

    private fun open(): Subject {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guilds = GuildRepositorySQLite(storage)
        assertTrue(guilds.add(Guild(guildId, "HomeAudit", level = 100, createdAt = Instant.now())))
        val owners = RewardOwnershipRepositorySQL(storage, RewardCatalog.chapterTwo())
        owners.initialize(guildId, 6)
        // The v50 core commit adds this column in production. Keep this
        // isolated v51 regression runnable before the integration cherry-pick.
        try {
            storage.connection.executeUpdate(
                "ALTER TABLE guild_reward_accounts ADD COLUMN base_home_slots_earned INTEGER NOT NULL DEFAULT 1",
            )
        } catch (e: java.sql.SQLException) {
            if (!e.message.orEmpty().contains("duplicate", ignoreCase = true)) throw e
        }
        storage.connection.executeUpdate(
            "UPDATE guild_reward_accounts SET base_home_slots_earned = 6 WHERE guild_id = ?",
            guildId.toString(),
        )
        (legacyNames + prematureNames).forEachIndexed { index, name ->
            storage.connection.executeUpdate(
                "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
                guildId.toString(), name, world.toString(), index * 5, 64, -index * 7,
            )
        }
        // Reopen the repository after planting legacy SQL rows, as a production startup does.
        return Subject(storage, GuildRepositorySQLite(storage), GuildHomeAccessGrantRepositorySQL(storage))
    }

    private fun classifications(): Map<String, VerifiedGuildHomeGrant> = buildMap {
        legacyNames.forEachIndexed { index, name ->
            put(name, VerifiedGuildHomeGrant(GuildHomeAccessState.ACTIVE_BASE, "BASE:${index + 1}"))
        }
        prematureNames.forEach { name ->
            put(name, VerifiedGuildHomeGrant(GuildHomeAccessState.LOCKED_PREMATURE, null))
        }
    }

    @Test
    fun `schema upgrade never automatically locks existing saved homes`() {
        val subject = open()
        assertFalse(subject.access.isManaged(guildId))
        assertEquals(GuildHomeAccessState.UNRECONCILED, subject.access.state(guildId, legacyNames.first()))
        assertEquals(GuildHomeAccessState.UNRECONCILED, subject.access.state(guildId, prematureNames.first()))
        assertFalse(subject.access.prepareEmptyGuild(guildId))
        assertFalse(subject.access.isManaged(guildId))
    }

    @Test
    fun `verified six originals stay active and nine extra homes lock without losing locations`() {
        val subject = open()
        val before = homeRows(subject.storage)
        assertFalse(subject.access.reconcileVerified(guildId, classifications() - "legacy-2", "verified backup", reviewer))
        assertEquals(before, homeRows(subject.storage))
        val forgery = classifications().toMutableMap()
        forgery["outpost-1"] = VerifiedGuildHomeGrant(GuildHomeAccessState.ACTIVE_BASE, "BASE:7")
        assertFalse(subject.access.reconcileVerified(guildId, forgery, "fake rank", reviewer))
        forgery["outpost-1"] = VerifiedGuildHomeGrant(GuildHomeAccessState.ACTIVE_PRESTIGE, "PRESTIGE:1")
        assertFalse(subject.access.reconcileVerified(guildId, forgery, "fake prestige", reviewer))
        assertFalse(subject.access.isManaged(guildId))
        assertTrue(subject.access.reconcileVerified(guildId, classifications(), "sha256:verified-ch1", reviewer))
        assertTrue(subject.access.isManaged(guildId))
        legacyNames.forEach {
            assertEquals(GuildHomeAccessState.ACTIVE_BASE, subject.access.state(guildId, it))
        }
        prematureNames.forEach {
            assertEquals(GuildHomeAccessState.LOCKED_PREMATURE, subject.access.state(guildId, it))
        }
        assertEquals(before, homeRows(subject.storage))

        // GuildRepositorySQLite rewrites ALL guild_homes rows on updates; access
        // must survive that and remain independent of their transient row IDs.
        val guild = assertNotNull(subject.guildRepository.getById(guildId))
        assertTrue(subject.guildRepository.update(guild.copy(description = "updated guild")))
        assertEquals(before, homeRows(subject.storage))
        assertEquals(GuildHomeAccessState.LOCKED_PREMATURE, subject.access.state(guildId, prematureNames.last()))
    }

    @Test
    fun `verified migration preserves twenty locked historical homes beyond physical cap`() {
        val subject = open()
        val extra = (10..20).map { "outpost-$it" }
        extra.forEachIndexed { index, name ->
            subject.storage.connection.executeUpdate(
                "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, 64, ?)",
                guildId.toString(), name, world.toString(), index * 3, index * -5,
            )
        }
        val before = homeRows(subject.storage)
        assertEquals(26, before.size)
        val classifications = classifications() + extra.associateWith {
            VerifiedGuildHomeGrant(GuildHomeAccessState.LOCKED_PREMATURE, null)
        }
        assertTrue(subject.access.reconcileVerified(guildId, classifications, "verified sha + audited 26 names", reviewer))
        assertEquals(before, homeRows(subject.storage))
        assertEquals(20, prematureNames.count {
            subject.access.state(guildId, it) == GuildHomeAccessState.LOCKED_PREMATURE
        } + extra.count {
            subject.access.state(guildId, it) == GuildHomeAccessState.LOCKED_PREMATURE
        })
        assertFalse(subject.access.canGrantNewHome(guildId, 6))
        // Reload once more: this test inserted the extra 11 homes via SQL after open().
        val reloaded = GuildRepositorySQLite(subject.storage)
        val saved = assertNotNull(reloaded.getById(guildId))
        assertTrue(reloaded.update(saved.copy(description = "persist with 26 names")))
        assertEquals(before, homeRows(subject.storage))
        assertEquals(GuildHomeAccessState.LOCKED_PREMATURE, subject.access.state(guildId, "outpost-20"))
    }

    @Test
    fun `one paid Outpost credit can restore exactly one locked location without gold`() {
        val subject = open()
        assertTrue(subject.access.reconcileVerified(guildId, classifications(), "sha256:verified-ch1", reviewer))
        createAcquisitionTable(subject.storage)
        val transactionId = UUID.randomUUID()
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_outpost_acquisitions " +
                "(transaction_id, guild_id, prestige_rank, reward_id, paid_amount, acquired_at) " +
                "VALUES (?, ?, 1, 'home-1', 100, 12345)",
            transactionId.toString(), guildId.toString(),
        )

        val before = homeRows(subject.storage)
        assertTrue(subject.access.assignPaidOutpost(guildId, transactionId, "outpost-1"))
        assertTrue(subject.access.assignPaidOutpost(guildId, transactionId, "outpost-1"))
        assertFalse(subject.access.assignPaidOutpost(guildId, transactionId, "outpost-2"))
        assertEquals(GuildHomeAccessState.ACTIVE_OUTPOST, subject.access.state(guildId, "outpost-1"))
        assertEquals(GuildHomeAccessState.LOCKED_PREMATURE, subject.access.state(guildId, "outpost-2"))
        assertEquals(before, homeRows(subject.storage))
        assertEquals(
            "outpost-1",
            subject.storage.connection.getFirstRow(
                "SELECT assigned_home_name FROM guild_outpost_acquisitions WHERE transaction_id = ?",
                transactionId.toString(),
            )?.getString("assigned_home_name"),
        )
        assertEquals(0, subject.storage.connection.getResults("SELECT * FROM guild_gold_operations WHERE guild_id = ?", guildId.toString()).size)
    }

    @Test
    fun `failed access grant update rolls back paid assignment and can retry`() {
        val subject = open()
        if (subject.storage.dialect == SqlDialect.MARIADB) return
        assertTrue(subject.access.reconcileVerified(guildId, classifications(), "sha256:verified-ch1", reviewer))
        createAcquisitionTable(subject.storage)
        val txId = UUID.randomUUID()
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_outpost_acquisitions " +
                "(transaction_id, guild_id, prestige_rank, reward_id, paid_amount, acquired_at) " +
                "VALUES (?, ?, 1, 'home-1', 100, 12345)",
            txId.toString(), guildId.toString(),
        )
        subject.storage.connection.executeUpdate("""
            CREATE TRIGGER sabotage_outpost_binding AFTER UPDATE OF assigned_home_name ON guild_outpost_acquisitions
            WHEN NEW.assigned_home_name IS NOT NULL
            BEGIN
                DELETE FROM guild_home_access_grants
                  WHERE guild_id = NEW.guild_id AND home_name = NEW.assigned_home_name;
            END
        """.trimIndent())
        assertFalse(subject.access.assignPaidOutpost(guildId, txId, "outpost-1"))
        assertEquals(
            null,
            subject.storage.connection.getFirstRow(
                "SELECT assigned_home_name FROM guild_outpost_acquisitions WHERE transaction_id = ?",
                txId.toString(),
            )?.getString("assigned_home_name"),
        )
        assertEquals(GuildHomeAccessState.LOCKED_PREMATURE, subject.access.state(guildId, "outpost-1"))

        subject.storage.connection.executeUpdate("DROP TRIGGER sabotage_outpost_binding")
        assertTrue(subject.access.assignPaidOutpost(guildId, txId, "outpost-1"))
        assertEquals(GuildHomeAccessState.ACTIVE_OUTPOST, subject.access.state(guildId, "outpost-1"))
    }

    @Test
    fun `new guild can use paid Outpost for eighth new location after seven free homes`() {
        val subject = open()
        createAcquisitionTable(subject.storage)
        val freshGuild = UUID.randomUUID()
        assertTrue(subject.guildRepository.add(Guild(freshGuild, "NewChapterTwo", level = 100, createdAt = Instant.now())))
        val owners = RewardOwnershipRepositorySQL(subject.storage, RewardCatalog.chapterTwo())
        owners.initialize(freshGuild, 1)
        subject.storage.connection.executeUpdate(
            "UPDATE guild_reward_accounts SET base_home_slots_earned = 6, prestige_count = 1 WHERE guild_id = ?",
            freshGuild.toString(),
        )
        assertTrue(subject.access.prepareEmptyGuild(freshGuild))
        val txId = UUID.randomUUID()
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_outpost_acquisitions " +
                "(transaction_id, guild_id, prestige_rank, reward_id, paid_amount, acquired_at) " +
                "VALUES (?, ?, 1, 'home-1', 200, 12345)",
            txId.toString(), freshGuild.toString(),
        )
        repeat(7) { index ->
            val name = "free-${index + 1}"
            assertTrue(subject.access.canGrantNewHome(freshGuild, 8))
            assertFalse(subject.access.willUsePaidOutpostForNewHome(freshGuild, 8))
            subject.storage.connection.executeUpdate(
                "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, 0, 64, 0)",
                freshGuild.toString(), name, world.toString(),
            )
            assertTrue(subject.access.grantNewHome(freshGuild, name, 8))
        }
        assertTrue(subject.access.canGrantNewHome(freshGuild, 8))
        assertTrue(subject.access.willUsePaidOutpostForNewHome(freshGuild, 8))
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, 'paid-8', ?, 0, 64, 0)",
            freshGuild.toString(), world.toString(),
        )
        assertTrue(subject.access.grantNewHome(freshGuild, "paid-8", 8))
        assertEquals(GuildHomeAccessState.ACTIVE_OUTPOST, subject.access.state(freshGuild, "paid-8"))
        assertFalse(subject.access.canGrantNewHome(freshGuild, 8))
        assertEquals(
            "paid-8",
            subject.storage.connection.getFirstRow(
                "SELECT assigned_home_name FROM guild_outpost_acquisitions WHERE transaction_id = ?",
                txId.toString(),
            )?.getString("assigned_home_name"),
        )
    }

    private fun createAcquisitionTable(storage: Storage<Database>) {
        val maria = storage.dialect == SqlDialect.MARIADB
        val id = if (maria) "VARCHAR(36)" else "TEXT"
        val reward = if (maria) "VARCHAR(64)" else "TEXT"
        val home = if (maria) "VARCHAR(128)" else "TEXT"
        val engine = if (maria) " ENGINE=InnoDB" else ""
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS guild_outpost_acquisitions (
                transaction_id $id NOT NULL PRIMARY KEY,
                guild_id $id NOT NULL,
                prestige_rank INTEGER NOT NULL,
                reward_id $reward NOT NULL,
                paid_amount BIGINT NOT NULL,
                acquired_at BIGINT NOT NULL,
                assigned_home_name $home,
                UNIQUE (guild_id, prestige_rank),
                UNIQUE (guild_id, reward_id)
            )$engine
        """.trimIndent())
    }

    private fun homeRows(storage: Storage<Database>): List<String> =
        storage.connection.getResults(
            "SELECT name, world_id, x, y, z FROM guild_homes WHERE guild_id = ? ORDER BY name",
            guildId.toString(),
        ).map {
            "${it.getString("name")}|${it.getString("world_id")}|${it.getInt("x")}|${it.getInt("y")}|${it.getInt("z")}"
        }
}
