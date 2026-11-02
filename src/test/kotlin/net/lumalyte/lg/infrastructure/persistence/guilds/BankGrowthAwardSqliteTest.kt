package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BankGrowthAwardSqliteTest : BankGrowthAwardContract() {
    @TempDir lateinit var directory: Path

    override fun newStorage() = VirtualThreadSQLiteStorage(directory.toFile())
}
