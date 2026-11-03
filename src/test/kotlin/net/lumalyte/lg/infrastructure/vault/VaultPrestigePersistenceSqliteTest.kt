package net.lumalyte.lg.infrastructure.vault

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildVaultRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.migrations.VaultCapacityUnlockSchema
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
        initialStorage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY, level INTEGER NOT NULL)")
        initialStorage.connection.executeUpdate(
            "CREATE TABLE guild_progression (guild_id TEXT PRIMARY KEY, current_level INTEGER NOT NULL)"
        )
        initialStorage.connection.executeUpdate(
            "CREATE TABLE guild_reward_accounts (guild_id TEXT PRIMARY KEY, prestige_count INTEGER NOT NULL, base_home_slots_earned INTEGER NOT NULL)"
        )
        initialStorage.connection.executeUpdate(
            "CREATE TABLE chapter_migration_receipts (guild_id TEXT, before_level INTEGER, migration_kind TEXT, dry_run INTEGER, status TEXT)"
        )
        initialStorage.connection.executeUpdate("INSERT INTO guilds (id,level) VALUES (?,100)", guildId.toString())
        initialStorage.connection.executeUpdate("INSERT INTO guild_progression (guild_id,current_level) VALUES (?,100)", guildId.toString())
        initialStorage.connection.executeUpdate(
            "INSERT INTO guild_reward_accounts (guild_id,prestige_count,base_home_slots_earned) VALUES (?,0,6)",
            guildId.toString()
        )
        initialStorage.connection.connection.use { VaultCapacityUnlockSchema.create(it, mariaDb = false, backfill = true) }
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

        // Prestige resets CURRENT LEVEL to 1, but not permanent vault capacity.
        // A fresh repository/manager must keep slot 53 writable, not just visible.
        val prestigedStorage = SQLiteStorage(folder)
        prestigedStorage.connection.executeUpdate("UPDATE guilds SET level = 1 WHERE id = ?", guildId.toString())
        prestigedStorage.connection.executeUpdate(
            "UPDATE guild_progression SET current_level = 1 WHERE guild_id = ?", guildId.toString()
        )
        prestigedStorage.connection.executeUpdate(
            "UPDATE guild_reward_accounts SET prestige_count = 1 WHERE guild_id = ?", guildId.toString()
        )
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

        // Even with the highest row EMPTY, the permanent unlocked capacity is
        // still 54 after another process restart and accepts new deposits.
        val finalStorage = SQLiteStorage(folder)
        try {
            val finalRepository = GuildVaultRepositorySQLite(finalStorage)
            val finalManager = VaultInventoryManager(finalRepository, null, VaultConfig())
            val reopened = finalManager.getOrCreateSharedInventory(guildId, "Everlights", 9)
            assertEquals(54, reopened.size)
            assertEquals(54, (reopened.holder as net.lumalyte.lg.interaction.inventory.VaultInventoryHolder).getCapacity())
            assertEquals(Material.DIAMOND, reopened.getItem(5)?.type)
            assertEquals(24, reopened.getItem(24)?.amount)
            assertNull(reopened.getItem(53))
            reopened.setItem(53, ItemStack(Material.GOLDEN_APPLE, 2))
            finalManager.syncInventoryToCache(guildId, reopened)
            finalManager.forceFlush(guildId)
            assertEquals(2, finalRepository.getVaultItem(guildId, 53)?.amount)

            // Legacy prestige backfill, without any prior v52 capacity record or
            // occupied upper row: prestige_count=1 alone proves all six rows.
            val legacyId = UUID.randomUUID()
            finalStorage.connection.executeUpdate("INSERT INTO guilds (id,level) VALUES (?,1)", legacyId.toString())
            finalStorage.connection.executeUpdate(
                "INSERT INTO guild_progression (guild_id,current_level) VALUES (?,1)", legacyId.toString()
            )
            finalStorage.connection.executeUpdate(
                "INSERT INTO guild_reward_accounts (guild_id,prestige_count,base_home_slots_earned) VALUES (?,1,1)",
                legacyId.toString()
            )
            val legacyInventory = finalManager.getOrCreateSharedInventory(legacyId, "Legacy", 9)
            assertEquals(54, legacyInventory.size)
            assertEquals(
                54,
                (legacyInventory.holder as net.lumalyte.lg.interaction.inventory.VaultInventoryHolder).getCapacity(),
            )
            assertEquals(emptyMap<Int, String>(), rowBytes(finalStorage, legacyId))
            assertEquals(54, finalRepository.getPermanentCapacity(legacyId, 9))
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
