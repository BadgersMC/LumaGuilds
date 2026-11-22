package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import net.lumalyte.lg.config.MainConfig
import net.milkbowl.vault.chat.Chat
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerMetadataServiceVaultDispatcherTest {
    @Test
    fun `async metadata lookups run on supplied dispatcher instead of hardcoded IO`() {
        val playerId = UUID.randomUUID()
        val offline = mockk<OfflinePlayer>()
        val metadata = mockk<Chat>()
        var invokedOnThread: String? = null
        mockkStatic(Bukkit::class)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nexus-test-virtual-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            every { Bukkit.getOfflinePlayer(playerId) } returns offline
            every { metadata.getPlayerInfoInteger(null, offline, any(), any()) } answers {
                invokedOnThread = Thread.currentThread().name
                17
            }
            val service = PlayerMetadataServiceVault(metadata, MainConfig(), dispatcher)
            assertEquals(17, runBlocking { service.getPlayerClaimLimitAsync(playerId) })
            assertTrue(invokedOnThread?.startsWith("nexus-test-virtual-io") == true)
        } finally {
            dispatcher.close()
            unmockkStatic(Bukkit::class)
        }
    }
}
