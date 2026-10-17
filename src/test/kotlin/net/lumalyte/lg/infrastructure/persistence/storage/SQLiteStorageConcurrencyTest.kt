package net.lumalyte.lg.infrastructure.persistence.storage

import co.aikar.idb.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class SQLiteStorageConcurrencyTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `standard sqlite storage waits for competing writer instead of failing busy`() {
        val directory = Files.createDirectories(tempDir.resolve("standard"))
        verifyStorage(SQLiteStorage(directory.toFile()))
    }

    @Test
    fun `virtual thread sqlite storage waits for competing writer instead of failing busy`() {
        val directory = Files.createDirectories(tempDir.resolve("virtual"))
        verifyStorage(VirtualThreadSQLiteStorage(directory.toFile()))
    }

    private fun verifyStorage(storage: Storage<Database>) {
        storage.connection.executeUpdate(
            "CREATE TABLE lock_test (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL)"
        )

        storage.connection.connection.use { probe ->
            probe.createStatement().use { statement ->
                statement.executeQuery("PRAGMA busy_timeout").use { rows ->
                    rows.next()
                    assertEquals(15_000, rows.getInt(1))
                }
            }
        }

        storage.connection.connection.use { blocker ->
            blocker.autoCommit = false
            blocker.prepareStatement("INSERT INTO lock_test(value) VALUES ('first')").use {
                assertEquals(1, it.executeUpdate())
            }

            val competingWrite = CompletableFuture.supplyAsync {
                storage.connection.executeUpdate(
                    "INSERT INTO lock_test(value) VALUES (?)",
                    "second",
                )
            }

            Thread.sleep(250)
            blocker.commit()

            assertEquals(1, competingWrite.get(5, TimeUnit.SECONDS))
        }

        assertEquals(
            2,
            storage.connection.getFirstRow("SELECT COUNT(*) AS total FROM lock_test")!!.getInt("total"),
        )
        storage.connection.close(5, TimeUnit.SECONDS)
    }
}
