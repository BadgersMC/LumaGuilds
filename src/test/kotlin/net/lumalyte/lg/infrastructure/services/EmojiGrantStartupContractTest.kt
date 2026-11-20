package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * REQ-217 delivery: guild logo grants go through LuckPerms console commands, which fail while
 * worlds are still loading. Startup reconciliation must wait for the first server tick.
 */
internal class EmojiGrantStartupContractTest {
    @Test
    fun startupReconciliationWaitsForTheFirstTick() {
        val main = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        val scheduled = Regex(
            "server\\.scheduler\\.runTask\\(this, Runnable \\{\\s*val emojiResult = get\\(\\)\\.get<[\\w.]+GuildEmojiGrantService>\\(\\)\\.reconcileAll\\(\\)",
        )
        assertTrue(scheduled.containsMatchIn(main))
    }
}
