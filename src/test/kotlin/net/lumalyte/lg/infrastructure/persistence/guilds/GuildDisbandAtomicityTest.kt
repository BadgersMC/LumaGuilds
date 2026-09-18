package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.infrastructure.services.*
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.bukkit.Bukkit
import org.junit.jupiter.api.Test
import org.koin.core.context.*
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildDisbandAtomicityTest : RewardSqlTestFixture() {
    @Test fun `failed cooldown receipt preserves dependent rows caches and world state`() = exercise(fail = true)
    @Test fun `committed disband removes dependent rows caches and then world state`() = exercise(fail = false)

    private fun exercise(fail: Boolean) {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guilds = GuildRepositorySQLite(storage)
        val ranks = RankRepositorySQLite(storage)
        val members = MemberRepositorySQLite(storage)
        val relations = RelationRepositorySQLite(storage)
        val history = MembershipHistoryRepositorySQLite(storage)
        val creator = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Disband", createdAt = Instant.now())
        val other = Guild(UUID.randomUUID(), "Neighbor", createdAt = Instant.now())
        assertTrue(guilds.addCreated(guild, creator))
        assertTrue(guilds.add(other))
        val rank = Rank(UUID.randomUUID(), guild.id, "Owner")
        assertTrue(ranks.add(rank))
        val member = Member(creator, guild.id, rank.id, Instant.now())
        assertTrue(members.add(member))
        assertTrue(history.openStint(creator, guild.id))
        val ordered = listOf(guild.id, other.id).sortedBy { it.toString() }
        val relation = Relation(UUID.randomUUID(), ordered[0], ordered[1], RelationType.ALLY, createdAt = Instant.now())
        assertTrue(relations.add(relation))
        if (fail) {
            val body = if (storage.dialect == SqlDialect.MARIADB)
                "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected'"
            else "BEGIN SELECT RAISE(ABORT, 'injected'); END"
            storage.connection.executeUpdate("CREATE TRIGGER reject_disband_history BEFORE UPDATE ON guild_creators $body")
        }
        val vault = mockk<GuildVaultService>(relaxed = true)
        val holograms = mockk<VaultHologramService>(relaxed = true)
        every { vault.getVaultLocation(guild) } returns mockk(relaxed = true)
        every { vault.prepareDisband(guild) } returns { vault.removeVaultChest(guild, true) }
        every { vault.removeVaultChest(guild, true) } answers {
            assertNull(guilds.getById(guild.id), "World cleanup must follow commit")
            VaultResult.Success(guild)
        }
        val memberService = mockk<MemberService> { every { getGuildMembers(guild.id) } returns setOf(member) }
        val config = mockk<ConfigService>(relaxed = true)
        every { config.loadConfig().guild.creationCooldown } returns net.lumalyte.lg.domain.values.GuildCreationCooldown()
        stopKoin()
        startKoin { modules(module { single<ConfigService> { config } }) }
        mockkStatic(Bukkit::class)
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)
        try {
            val service = GuildServiceBukkit(guilds, ranks, members, mockk(), memberService,
                mockk(), vault, holograms, relations, history, mockk())
            assertEquals(!fail, service.disbandGuild(guild.id, UUID(0, 0)))
            val expected = if (fail) 1 else 0
            for (table in listOf("members", "ranks")) {
                assertEquals(expected, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM $table WHERE guild_id = ?", guild.id.toString())!!.getInt("n"))
            }
            assertEquals(expected, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM relations")!!.getInt("n"))
            assertEquals(fail, members.getByPlayerAndGuild(creator, guild.id) != null)
            assertEquals(fail, ranks.getById(rank.id) != null)
            assertEquals(fail, relations.getAll().contains(relation))
            assertEquals(fail, guilds.getById(guild.id) != null)
            assertEquals(fail, history.getByPlayer(creator).single().departedAt == null)
            if (fail) {
                assertNull(guilds.creationCooldownUntil(creator))
                verify(exactly = 0) { vault.removeVaultChest(any(), any()); holograms.removeHologram(any()) }
            } else {
                assertNotNull(guilds.creationCooldownUntil(creator))
                verify(exactly = 1) { vault.removeVaultChest(guild, true) }
            }
        } finally {
            unmockkStatic(Bukkit::class)
            stopKoin()
        }
    }
}
