package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PublicPlayerGuildSnapshotSqlTest : RewardSqlTestFixture() {
    private lateinit var storage: Storage<Database>
    private val player = UUID.randomUUID()
    private val guild = UUID.randomUUID()

    @BeforeEach fun setup() {
        storage = openStorage()
        storage.connection.getConnection().use { connection -> connection.createStatement().use { sql ->
            sql.execute("CREATE TABLE members(player_id VARCHAR(36) NOT NULL,guild_id VARCHAR(36) NOT NULL)")
            sql.execute("CREATE TABLE guilds(id VARCHAR(36) PRIMARY KEY,name VARCHAR(80) NOT NULL,level INTEGER NOT NULL)")
            sql.execute("CREATE TABLE guild_banners(guild_id VARCHAR(36) NOT NULL,base_color VARCHAR(20),patterns TEXT,is_active INTEGER)")
        } }
        storage.connection.executeUpdate("INSERT INTO guilds VALUES (?,?,?)", guild.toString(), "Public guild", 42)
        storage.connection.executeUpdate("INSERT INTO members VALUES (?,?)", player.toString(), guild.toString())
        storage.connection.executeUpdate("INSERT INTO guild_banners VALUES (?,?,?,?)", guild.toString(), "BLACK", "BORDER:YELLOW", 1)
    }
    @Test fun `one database snapshot returns actual public membership and known empty player`() {
        val absent=UUID.randomUUID()
        val snapshot=PublicPlayerGuildSnapshotSql(storage).build(listOf(player,absent))
        val items=snapshot["items"] as List<*>
        val row=items[0] as Map<*,*>
        val entry=(row["guildMemberships"] as List<*>).single() as Map<*,*>
        assertEquals(setOf("id","name","level","banner"),entry.keys)
        assertEquals("Public guild",entry["name"])
        assertEquals(42,entry["level"])
        assertEquals(mapOf("baseColor" to "BLACK","patterns" to listOf(mapOf("type" to "BORDER","color" to "YELLOW"))),entry["banner"])
        assertEquals(emptyList<Any>(),(items[1] as Map<*,*>)["guildMemberships"])
    }

    @Test fun `ambiguous banners or unavailable tables fail closed`() {
        val reader=PublicPlayerGuildSnapshotSql(storage)
        storage.connection.executeUpdate("INSERT INTO guild_banners VALUES (?,?,?,?)",guild.toString(),"WHITE","",1)
        assertThrows(IllegalStateException::class.java) { reader.build(listOf(player)) }
        storage.connection.executeUpdate("DROP TABLE members")
        assertThrows(Exception::class.java) { reader.build(listOf(player)) }
    }

    @Test fun `invalid banner data cannot become a valid empty design`() {
        storage.connection.executeUpdate("UPDATE guild_banners SET patterns = NULL WHERE guild_id = ?",guild.toString())
        assertThrows(IllegalArgumentException::class.java) { PublicPlayerGuildSnapshotSql(storage).build(listOf(player)) }
    }
}
