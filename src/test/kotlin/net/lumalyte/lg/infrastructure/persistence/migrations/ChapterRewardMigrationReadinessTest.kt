package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.application.services.GuildRewardService
import net.lumalyte.lg.domain.rewards.GuildRewardRead
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.domain.rewards.RewardOfferStatus
import net.lumalyte.lg.domain.rewards.RewardReadSettings
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardOwnershipRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardStateRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChapterRewardMigrationReadinessTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `chapter one migration output is immediately consumable by reward read model`() {
        val guildId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val db = directory.resolve("lumaguilds.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
            connection.createStatement().use { s ->
                s.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT NOT NULL, level INTEGER NOT NULL)")
                s.execute("""CREATE TABLE guild_progression (
                    guild_id TEXT PRIMARY KEY, total_experience INTEGER NOT NULL, current_level INTEGER NOT NULL,
                    experience_this_level INTEGER NOT NULL, experience_for_next_level INTEGER NOT NULL,
                    last_level_up INTEGER, total_level_ups INTEGER NOT NULL, unlocked_perks TEXT NOT NULL,
                    created_at INTEGER NOT NULL, last_updated INTEGER NOT NULL
                )""")
                s.execute("CREATE TABLE guild_homes (guild_id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(guild_id,name))")
                s.execute("CREATE TABLE vault_gold (guild_id TEXT PRIMARY KEY, balance INTEGER NOT NULL)")
                s.execute("CREATE TABLE members (player_id TEXT PRIMARY KEY, guild_id TEXT NOT NULL)")
                s.execute("CREATE TABLE relations (id TEXT PRIMARY KEY)")
            }
            ChapterLifecycleSchema.create(connection, mariaDb = false)
            connection.createStatement().use { s ->
                s.execute("INSERT INTO guilds VALUES ('$guildId','Alpha',87)")
                s.execute("INSERT INTO guild_progression VALUES ('$guildId',4000000,87,1200,9999,NULL,86,'[\"OLD\"]',1,2)")
                s.execute("INSERT INTO guild_homes VALUES ('$guildId','main')")
                s.execute("INSERT INTO guild_homes VALUES ('$guildId','farm')")
                s.execute("INSERT INTO vault_gold VALUES ('$guildId',777)")
                s.execute("""INSERT INTO chapter_lifecycle
                    (chapter_id,chapter_name,phase,updated_at,version,backup_id)
                    VALUES ('chapter-1','Chapter 1','BACKED_UP',1,0,'backup-1')""")
                s.execute("""INSERT INTO chapter_backup_evidence
                    (backup_id,chapter_id,storage_ref,sha256,size_bytes,created_at,verified_at,verification_status,restore_verified_at)
                    VALUES ('backup-1','chapter-1','backup.db','abc',123,1,2,'VERIFIED',3)""")
            }

            ChapterOneToTwoMigrationSQL(
                connection,
                mariaDb = false,
                curve = ProgressionCurve(500.0, 1.15, 150, 100),
            ).apply("chapter-2-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)
        }

        val storage = VirtualThreadSQLiteStorage(directory.toFile())
        try {
            val catalog = RewardCatalog.chapterTwo()
            val owners = RewardOwnershipRepositorySQL(storage, catalog)
            val service = GuildRewardService(
                RewardStateRepositorySQL(storage, owners),
                catalog,
            ) {
                RewardReadSettings(
                    enabled = true,
                    bankCeiling = Long.MAX_VALUE,
                    memberCapacity = 50,
                )
            }

            val read = assertIs<GuildRewardRead.Available>(service.read(guildId))
            assertEquals(1, read.level)
            assertEquals(0L, read.version)
            assertEquals(2, read.entitlements.homeCapacity)
            assertEquals(50, read.entitlements.memberCapacity)
            assertTrue(read.entitlements.offers.none {
                it.status == RewardOfferStatus.PURCHASED || it.status == RewardOfferStatus.PERMANENT
            })

            assertEquals(
                1,
                storage.connection.getFirstColumn(
                    "SELECT COUNT(*) FROM guild_reward_accounts WHERE guild_id = ?",
                    guildId.toString(),
                ),
            )
            assertEquals(
                0,
                storage.connection.getFirstColumn(
                    "SELECT COUNT(*) FROM guild_reward_ownership WHERE guild_id = ?",
                    guildId.toString(),
                ),
            )
        } finally {
            storage.connection.close(5, TimeUnit.SECONDS)
        }
    }
}
