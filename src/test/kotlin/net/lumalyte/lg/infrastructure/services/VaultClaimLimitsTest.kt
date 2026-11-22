package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import net.lumalyte.lg.config.MainConfig
import net.milkbowl.vault.chat.Chat
import org.bukkit.Server
import org.bukkit.plugin.RegisteredServiceProvider
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VaultClaimLimitsTest {
    @Test fun vaultWithoutAChatProviderKeepsConfiguredLimits() {
        val server = mockk<Server>()
        every { server.pluginManager.getPlugin("Vault") } returns mockk()
        every { server.servicesManager.getRegistration(Chat::class.java) } returns null
        val limits = playerMetadataService(server, MainConfig(claimLimit = 3, claimBlockLimit = 800), Dispatchers.IO)
        val player = UUID.randomUUID()
        assertEquals(3, limits.getPlayerClaimLimit(player))
        assertEquals(800, limits.getPlayerClaimBlockLimit(player))
    }

    @Test fun installedChatProviderSuppliesPlayerOverrides() {
        val server = mockk<Server>()
        val chat = mockk<Chat>()
        val registered = mockk<RegisteredServiceProvider<Chat>> { every { provider } returns chat }
        every { server.pluginManager.getPlugin("Vault") } returns mockk()
        every { server.servicesManager.getRegistration(Chat::class.java) } returns registered
        assertIs<PlayerMetadataServiceVault>(playerMetadataService(server, MainConfig(), Dispatchers.IO))
    }
}
