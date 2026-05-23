package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Tests for the v22 balance consolidation: folding the bank_transactions ledger (store A) and the
 * legacy guilds.bank_balance column (store C) into the unified vault_gold balance (store B).
 */
class GuildBalanceConsolidatorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection
    private val logger = ComponentLogger.logger("test")

    @BeforeEach
    fun setUp() {
        val file: File = tempDir.resolve("test.db").toFile()
        connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        connection.autoCommit = true
        connection.createStatement().use { st ->
            st.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT NOT NULL, bank_balance INTEGER NOT NULL DEFAULT 0)")
            st.execute("CREATE TABLE vault_gold (guild_id TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0, last_modified INTEGER NOT NULL)")
            st.execute("CREATE TABLE bank_transactions (id TEXT PRIMARY KEY, guild_id TEXT NOT NULL, actor_id TEXT, type TEXT NOT NULL, amount INTEGER NOT NULL, fee INTEGER NOT NULL DEFAULT 0)")
        }
    }

    @AfterEach
    fun tearDown() = connection.close()

    private fun addGuild(id: String, bankBalance: Int) {
        connection.createStatement().use { it.execute("INSERT INTO guilds (id, name, bank_balance) VALUES ('$id', 'G-$id', $bankBalance)") }
    }

    private fun addVaultGold(id: String, balance: Int) {
        connection.createStatement().use { it.execute("INSERT INTO vault_gold (guild_id, balance, last_modified) VALUES ('$id', $balance, 0)") }
    }

    private fun addTx(id: String, guildId: String, type: String, amount: Int, fee: Int = 0) {
        connection.createStatement().use { it.execute("INSERT INTO bank_transactions (id, guild_id, actor_id, type, amount, fee) VALUES ('$id', '$guildId', 'a', '$type', $amount, $fee)") }
    }

    private fun vaultBalance(id: String): Int =
        connection.createStatement().use { st ->
            st.executeQuery("SELECT balance FROM vault_gold WHERE guild_id='$id'").use { rs -> if (rs.next()) rs.getInt("balance") else -1 }
        }

    private fun legacyBalance(id: String): Int =
        connection.createStatement().use { st ->
            st.executeQuery("SELECT bank_balance FROM guilds WHERE id='$id'").use { rs -> if (rs.next()) rs.getInt("bank_balance") else -1 }
        }

    @Test
    fun `folds ledger and legacy into existing vault balance`() {
        // Guild has 100 in vault, ledger nets 250 (300 deposit - 50 withdrawal), legacy column 30.
        addGuild("g1", bankBalance = 30)
        addVaultGold("g1", 100)
        addTx("t1", "g1", "DEPOSIT", 300)
        addTx("t2", "g1", "WITHDRAWAL", 50)

        GuildBalanceConsolidator.consolidate(connection, logger)

        assertEquals(380, vaultBalance("g1")) // 100 + (300-50) + 30
        assertEquals(0, legacyBalance("g1"))  // legacy column zeroed
    }

    @Test
    fun `creates vault row for guild that has no existing vault entry`() {
        addGuild("g2", bankBalance = 75)
        addTx("t1", "g2", "DEPOSIT", 200)
        // no vault_gold row for g2

        GuildBalanceConsolidator.consolidate(connection, logger)

        assertEquals(275, vaultBalance("g2")) // 0 + 200 + 75
    }

    @Test
    fun `withdrawal fee is subtracted from folded ledger`() {
        addGuild("g3", bankBalance = 0)
        addVaultGold("g3", 0)
        addTx("t1", "g3", "DEPOSIT", 100)
        addTx("t2", "g3", "WITHDRAWAL", 20, fee = 5)

        GuildBalanceConsolidator.consolidate(connection, logger)

        assertEquals(75, vaultBalance("g3")) // 100 - 20 - 5
    }

    @Test
    fun `negative net is clamped to zero`() {
        addGuild("g4", bankBalance = 0)
        addVaultGold("g4", 10)
        addTx("t1", "g4", "DEDUCTION", 1000)

        GuildBalanceConsolidator.consolidate(connection, logger)

        assertEquals(0, vaultBalance("g4")) // max(0, 10 + (-1000) + 0)
    }

    @Test
    fun `guild with no legacy balance and no ledger is left untouched`() {
        addGuild("g5", bankBalance = 0)
        addVaultGold("g5", 500)

        GuildBalanceConsolidator.consolidate(connection, logger)

        assertEquals(500, vaultBalance("g5"))
    }

    @Test
    fun `returns an accurate per-guild fold report and only for changed guilds`() {
        addGuild("g6", bankBalance = 40)
        addVaultGold("g6", 10)
        addTx("t1", "g6", "DEPOSIT", 100)
        // g7 has nothing to fold and must be excluded from the report.
        addGuild("g7", bankBalance = 0)
        addVaultGold("g7", 500)

        val report = GuildBalanceConsolidator.consolidate(connection, logger)

        assertEquals(1, report.size)
        val fold = report.single()
        assertEquals("g6", fold.guildId)
        assertEquals(10, fold.oldVault)
        assertEquals(100, fold.ledger)
        assertEquals(40, fold.legacy)
        assertEquals(150, fold.newVault)
    }
}
