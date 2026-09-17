package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Runs identical contracts against SQLite by default, or an explicit disposable local MariaDB. */
open class RewardSqlTestFixture {
    @TempDir lateinit var directory: Path
    private val storages = mutableListOf<Storage<Database>>()
    private val mariaPort = System.getProperty("lg.test.mariadb.port")?.toInt()
    private val mariaUser = System.getenv("LG_TEST_MARIADB_USER") ?: "root"
    private val mariaPassword = System.getenv("LG_TEST_MARIADB_PASSWORD") ?: ""
    private val mariaSchema = "lg_reward_test_" + UUID.randomUUID().toString().replace("-", "")
    private var mariaCreated = false

    protected fun openStorage(): Storage<Database> {
        val storage = if (mariaPort == null) VirtualThreadSQLiteStorage(directory.toFile()) else {
            require(mariaPort in 1024..65535 && mariaPort != 3306)
            if (!mariaCreated) {
                mariaAdmin { connection -> connection.createStatement().use { it.execute("CREATE DATABASE $mariaSchema") } }
                mariaCreated = true
            }
            VirtualThreadMariaDBStorage("127.0.0.1", mariaPort, mariaSchema, mariaUser, mariaPassword, maxPoolSize = 4)
        }
        storages += storage
        return storage
    }

    protected fun closeStorage(storage: Storage<Database>) {
        storage.connection.close(5, TimeUnit.SECONDS)
        storages.remove(storage)
    }

    protected fun rejectInserts(storage: Storage<Database>, table: String) {
        require(table in setOf("guild_reward_ownership", "guild_reward_purchases", "guild_reward_accounts"))
        val body = if (storage.dialect == SqlDialect.MARIADB)
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected write failure'"
        else "BEGIN SELECT RAISE(ABORT, 'injected write failure'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_reward_insert BEFORE INSERT ON $table $body")
    }

    @AfterEach
    fun closeRewardDatabases() {
        try { storages.toList().forEach(::closeStorage) }
        finally {
            if (mariaCreated) mariaAdmin { connection ->
                require(mariaSchema.matches(Regex("lg_reward_test_[a-f0-9]{32}")))
                connection.createStatement().use { it.execute("DROP DATABASE $mariaSchema") }
            }
        }
    }

    private fun mariaAdmin(block: (java.sql.Connection) -> Unit) {
        DriverManager.getConnection("jdbc:mariadb://127.0.0.1:$mariaPort/", mariaUser, mariaPassword).use(block)
    }
}
