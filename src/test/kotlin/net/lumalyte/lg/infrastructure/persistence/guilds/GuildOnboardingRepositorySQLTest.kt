package net.lumalyte.lg.infrastructure.persistence.guilds

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GuildOnboardingRepositorySQLTest : RewardSqlTestFixture() {
    @Test fun `concurrent deliveries claim one pending prompt`() {
        val repository = GuildOnboardingRepositorySQL(openStorage())
        val player = UUID.randomUUID()
        val guild = UUID.randomUUID()
        assertTrue(repository.register(player, guild))
        java.util.concurrent.Executors.newFixedThreadPool(2).use { executor ->
            val attempts = (1..2).map { executor.submit<Boolean> { repository.consume(player, guild) } }
            kotlin.test.assertEquals(1, attempts.count { it.get(5, java.util.concurrent.TimeUnit.SECONDS) })
        }
    }
    @Test fun `new memberships consume once across restart and duplicate events`() {
        val storage = openStorage()
        val repository = GuildOnboardingRepositorySQL(storage)
        val player = UUID.randomUUID()
        val guild = UUID.randomUUID()
        assertFalse(repository.consume(player, guild))
        assertTrue(repository.register(player, guild))
        assertTrue(repository.consume(player, guild))
        assertTrue(repository.register(player, guild))
        assertFalse(GuildOnboardingRepositorySQL(storage).consume(player, guild))
        val otherGuild = UUID.randomUUID()
        assertTrue(repository.register(player, otherGuild))
        assertTrue(repository.consume(player, otherGuild))
    }

    @Test fun `dismiss persists before delivery and is idempotent`() {
        val repository = GuildOnboardingRepositorySQL(openStorage())
        val player = UUID.randomUUID()
        val guild = UUID.randomUUID()
        assertTrue(repository.register(player, guild))
        assertTrue(repository.dismiss(player, guild))
        assertTrue(repository.dismiss(player, guild))
        assertFalse(repository.consume(player, guild))
    }

    @Test fun `failed writes do not consume or report dismissal success`() {
        val storage = openStorage()
        val repository = GuildOnboardingRepositorySQL(storage)
        val player = UUID.randomUUID()
        val guild = UUID.randomUUID()
        assertTrue(repository.register(player, guild))
        val body = if (storage.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB)
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'failure'"
        else "BEGIN SELECT RAISE(ABORT, 'failure'); END"
        storage.connection.executeUpdate("CREATE TRIGGER fail_prompt BEFORE UPDATE ON guild_onboarding_prompts $body")
        assertFalse(repository.consume(player, guild))
        assertFalse(repository.dismiss(player, guild))
        storage.connection.executeUpdate("DROP TRIGGER fail_prompt")
        assertTrue(repository.consume(player, guild))
    }
}
