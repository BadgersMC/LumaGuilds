package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GuildGoldMutationOwnershipTest {
    @Test
    fun `interaction code cannot call vault gold mutation methods`() {
        val forbidden = Regex("\\b(depositGold|withdrawGold|setGoldBalance|addGoldBalance|subtractGoldBalance)\\s*\\(")
        Files.walk(Path.of("src/main/kotlin/net/lumalyte/lg/interaction")).use { paths ->
            val violations = paths.filter { it.toString().endsWith(".kt") }
                .filter { forbidden.containsMatchIn(Files.readString(it)) }
                .toList()
            assertTrue(violations.isEmpty(), violations.joinToString("\n"))
        }
    }

    @Test
    fun `gold menus and vault listener do not mutate through inventory manager`() {
        val files = listOf(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldDepositMenu.kt",
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GoldWithdrawMenu.kt",
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankMenu.kt",
            "src/main/kotlin/net/lumalyte/lg/interaction/listeners/VaultInventoryListener.kt"
        )
        val violations = files.filter { path ->
            val source = Files.readString(Path.of(path))
            "vaultInventoryManager.depositGold(" in source || "vaultInventoryManager.withdrawGold(" in source
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
