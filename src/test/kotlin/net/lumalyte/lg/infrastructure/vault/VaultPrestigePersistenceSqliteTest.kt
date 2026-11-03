package net.lumalyte.lg.infrastructure.vault

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildVaultRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real SQLite + mutable MockBukkit inventory proof. Never runs against production data.
 * Cover the persisted slot records, not only an in-memory MockK repository.
 */
class VaultPrestigePersistenceSqliteTest {
    @TempDir lateinit var temporaryDirectory: Path

    @BeforeEach fun setup() {
        MockBukkit.mock()
        mockkObject(GoldBalanceButton)
        every { GoldBalanceButton.createItem(any()) } returns ItemStack(Material.GOLD_INGOT)
    }

    @AfterEach fun teardown() {
        unmockkObject(GoldBalanceButton)
        MockBukkit.unmock()
    }

    @Test fun `occupied overflow survives prestige and process-like storage reopen then only deliberate withdrawal deletes row`() {
        val folder = Files.createDirectories(temporaryDirectory.resolve("guild-storage")).toFile()
        val guildId = UUID.randomUUID()

        val initialStorage = SQLiteStorage(folder)
        initialStorage.connection.executeUpdate(
            "CREATE TABLE vault_slots (guild_id TEXT NOT NULL, slot INTEGER NOT NULL, item_data TEXT NOT NULL, last_modified INTEGER NOT NULL, PRIMARY KEY(guild_id, slot))"
        )
        initialStorage.connection.executeUpdate(
            "CREATE TABLE vault_gold (guild_id TEXT PRIMARY KEY NOT NULL, balance INTEGER NOT NULL, last_modified INTEGER NOT NULL)"
        )
        val initialRepository = GuildVaultRepositorySQLite(initialStorage)
        assertTrue(initialRepository.saveVaultItem(guildId, 5, ItemStack(Material.DIAMOND, 5)))
        assertTrue(initialRepository.saveVaultItem(guildId, 24, ItemStack(Material.EMERALD, 24)))
        assertTrue(initialRepository.saveVaultItem(guildId, 53, ItemStack(Material.NETHERITE_INGOT, 1)))

        // Level 100: full 54-slot vault. Save as if its last viewer just closed.
        val beforePrestige = VaultInventoryManager(initialRepository, null, VaultConfig())
        val originalInventory = beforePrestige.getOrCreateSharedInventory(guildId, "Everlights", 54)
        assertEquals(54, originalInventory.size)
        assertEquals(24, originalInventory.getItem(24)?.amount)
        beforePrestige.syncInventoryToCache(guildId, originalInventory)
        beforePrestige.forceFlush(guildId)
        initialStorage.connection.close(5, TimeUnit.SECONDS)

        // Prestige resets the unlocked capacity to 9. A fresh repository/manager must
        // recover persisted slot 53 and expose it for withdrawal, without truncation.
        val prestigedStorage = SQLiteStorage(folder)
        val prestigedRepository = GuildVaultRepositorySQLite(prestigedStorage)
        val restartedManager = VaultInventoryManager(prestigedRepository, null, VaultConfig())
        val prestigedInventory = restartedManager.getOrCreateSharedInventory(guildId, "Everlights", 9)
        assertEquals(54, prestigedInventory.size)
        assertEquals(5, prestigedInventory.getItem(5)?.amount)
        assertEquals(24, prestigedInventory.getItem(24)?.amount)
        assertEquals(Material.NETHERITE_INGOT, prestigedInventory.getItem(53)?.type)

        // Closing without withdrawal must retain byte-for-byte rows, including overflow.
        val beforeClose = rowBytes(prestigedStorage, guildId)
        restartedManager.syncInventoryToCache(guildId, prestigedInventory)
        restartedManager.forceFlush(guildId)
        assertEquals(beforeClose, rowBytes(prestigedStorage, guildId))

        // Real mutable inventory removal must delete ONLY the withdrawn high slot.
        prestigedInventory.setItem(53, null)
        restartedManager.syncInventoryToCache(guildId, prestigedInventory)
        restartedManager.forceFlush(guildId)
        assertNull(prestigedRepository.getVaultItem(guildId, 53))
        assertEquals(beforeClose[5], rowBytes(prestigedStorage, guildId)[5])
        assertEquals(beforeClose[24], rowBytes(prestigedStorage, guildId)[24])
        prestigedStorage.connection.close(5, TimeUnit.SECONDS)

        // A second simulated restart reopens the actual SQLite file with two intact items.
        val finalStorage = SQLiteStorage(folder)
        try {
            val reopened = VaultInventoryManager(GuildVaultRepositorySQLite(finalStorage), null, VaultConfig())
                .getOrCreateSharedInventory(guildId, "Everlights", 9)
            assertEquals(27, reopened.size) // highest occupied slot is 24
            assertEquals(Material.DIAMOND, reopened.getItem(5)?.type)
            assertEquals(24, reopened.getItem(24)?.amount)
            assertTrue(53 >= reopened.size, "After deliberate withdrawal slot 53 need not stay visible")
        } finally {
            finalStorage.connection.close(5, TimeUnit.SECONDS)
        }
    }

    private fun rowBytes(storage: SQLiteStorage, guildId: UUID): Map<Int, String> =
        storage.connection.getResults(
            "SELECT slot, item_data FROM vault_slots WHERE guild_id = ? ORDER BY slot",
            guildId.toString(),
        ).associate { it.getInt("slot") to it.getString("item_data") }
}
