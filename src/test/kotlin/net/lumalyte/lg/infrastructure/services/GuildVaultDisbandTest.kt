package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.application.services.VaultResult
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildVaultDisbandTest {
    @Test fun `prepared disband preserves items across cascade deletion without rewriting deleted guild`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.createMockPlugin()
            val world = server.addSimpleWorld("vault")
            val block = world.getBlockAt(0, 64, 0)
            block.type = Material.CHEST
            val guild = Guild(UUID.randomUUID(), "Vault", createdAt = Instant.now(),
                vaultChestLocation = GuildVaultLocation(world.uid, 0, 64, 0), vaultStatus = VaultStatus.AVAILABLE)
            val guilds = mockk<GuildRepository>(relaxed = true)
            val vault = mockk<GuildVaultRepository>(relaxed = true)
            val manager = mockk<VaultInventoryManager>(relaxed = true)
            val cached = net.lumalyte.lg.infrastructure.vault.VaultInventory(guild.id)
            cached.setSlot(0, ItemStack(Material.GOLD_INGOT)) // Reserved balance button, never loot.
            cached.setSlot(1, ItemStack(Material.DIAMOND, 3))
            every { manager.getOrLoadVault(guild.id) } returns cached
            every { vault.getVaultInventory(guild.id) } returns mapOf(0 to ItemStack(Material.DIAMOND, 3))
            every { vault.getGoldBalance(guild.id) } returns 0L
            val service = GuildVaultServiceBukkit(plugin, guilds, vault, mockk(), mockk(), manager,
                mockk(relaxed = true), mockk(), null, mockk())
            val cleanup = service.prepareDisband(guild)
            assertEquals(Material.CHEST, block.type)
            verify(exactly = 0) { manager.markVaultAsBeingRemoved(any()); vault.clearVault(any()) }
            // The guild deletion can cascade to vault rows before world cleanup begins.
            every { vault.getVaultInventory(guild.id) } returns emptyMap()
            assertIs<VaultResult.Success<Guild>>(cleanup())
            assertEquals(Material.AIR, block.type)
            assertEquals(3, world.getEntitiesByClass(Item::class.java).sumOf { it.itemStack.amount })
            verify(exactly = 0) { guilds.update(any()); manager.forceFlush(any()) }
            verifyOrder {
                manager.markVaultAsBeingRemoved(guild.id)
                manager.clearCache(guild.id)
                manager.evictSharedInventory(guild.id)
                manager.unmarkVaultAsBeingRemoved(guild.id)
            }
        } finally { MockBukkit.unmock() }
    }
}
