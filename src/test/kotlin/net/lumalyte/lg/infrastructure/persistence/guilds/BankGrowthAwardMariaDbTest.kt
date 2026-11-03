package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Reuses the existing dedicated loopback XP fixture, never an operator-selected database. */
class BankGrowthAwardMariaDbTest : BankGrowthAwardContract() {
    override fun newStorage(): MariaDBStorage {
        val port = System.getenv("GUILD_SHOP_XP_TEST_MARIA_PORT")?.toIntOrNull()
        assumeTrue(port != null, "Disposable loopback MariaDB XP test instance not configured")
        return MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
    }
}
