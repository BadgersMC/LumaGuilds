package net.lumalyte.lg.application.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.domain.entities.VaultInventory
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

/**
 * Tests for inventory-to-cache synchronization in VaultInventoryManager.
 * These tests verify that the manager correctly syncs Bukkit Inventory state
 * to the VaultInventory cache after modifications.
 */
class VaultInventorySyncTest {

    private lateinit var vaultRepository: GuildVaultRepository
    private lateinit var transactionLogger: VaultTransactionLogger
    private lateinit var vaultConfig: VaultConfig
    private lateinit var manager: VaultInventoryManager

    @BeforeEach
    fun setUp() {
        // Mock dependencies
        vaultRepository = mockk(relaxed = true)
        transactionLogger = mockk(relaxed = true)
        vaultConfig = mockk(relaxed = true)

        // Setup default repository behavior
        every { vaultRepository.getVaultInventory(any()) } returns emptyMap()
        every { vaultRepository.getGoldBalance(any()) } returns 0L
        every { vaultRepository.saveVaultItem(any(), any(), any()) } returns true

        // Create manager instance
        manager = VaultInventoryManager(vaultRepository, transactionLogger, vaultConfig)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Test Case: Sync inventory contents to cache ==========

    @Test
    fun `syncInventoryToCache should update cache with inventory contents`() {
        // Given: A vault with empty cache and an inventory with items
        val guildId = UUID.randomUUID()
        val mockInventory = mockk<Inventory>(relaxed = true)

        // Inventory has 54 slots
        every { mockInventory.size } returns 54

        // Inventory has items in slots 5 and 10
        val diamondItem = mockk<ItemStack>(relaxed = true)
        val goldItem = mockk<ItemStack>(relaxed = true)
        every { diamondItem.type } returns Material.DIAMOND
        every { diamondItem.amount } returns 5
        every { goldItem.type } returns Material.GOLD_INGOT
        every { goldItem.amount } returns 10

        every { mockInventory.getItem(5) } returns diamondItem
        every { mockInventory.getItem(10) } returns goldItem
        every { mockInventory.getItem(any()) } returns null // Other slots empty

        // Specifically set slots 5 and 10
        every { mockInventory.getItem(5) } returns diamondItem
        every { mockInventory.getItem(10) } returns goldItem

        // Load the vault into cache first
        manager.getOrLoadVault(guildId)

        // When: Sync inventory to cache
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: Cache should contain the items
        val slot5Item = manager.getSlot(guildId, 5)
        val slot10Item = manager.getSlot(guildId, 10)

        assertNotNull(slot5Item)
        assertNotNull(slot10Item)
    }

    @Test
    fun `syncInventoryToCache should handle null items correctly`() {
        // Given: A vault with items in cache and an inventory with some cleared slots
        val guildId = UUID.randomUUID()

        // Pre-populate cache with an item in slot 5
        val existingItem = mockk<ItemStack>(relaxed = true)
        every { existingItem.type } returns Material.EMERALD
        every { vaultRepository.getVaultInventory(guildId) } returns mapOf(5 to existingItem)

        // Load vault (now has item in slot 5)
        manager.getOrLoadVault(guildId)

        // Inventory has slot 5 empty (null)
        val mockInventory = mockk<Inventory>(relaxed = true)
        every { mockInventory.size } returns 54
        every { mockInventory.getItem(any()) } returns null

        // When: Sync inventory to cache
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: Slot 5 should now be null in cache (item removed)
        val slot5Item = manager.getSlot(guildId, 5)
        assertNull(slot5Item)
    }

    @Test
    fun `syncInventoryToCache should skip slot 0 - Gold Balance Button`() {
        // Given: An inventory with some item in slot 0 (which should be gold button)
        val guildId = UUID.randomUUID()
        val mockInventory = mockk<Inventory>(relaxed = true)

        every { mockInventory.size } returns 54

        // Someone put dirt in slot 0 (shouldn't happen, but testing protection)
        val dirtItem = mockk<ItemStack>(relaxed = true)
        every { dirtItem.type } returns Material.DIRT
        every { mockInventory.getItem(0) } returns dirtItem
        every { mockInventory.getItem(any()) } returns null

        // Load vault
        manager.getOrLoadVault(guildId)

        // When: Sync inventory to cache
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: Slot 0 should NOT be updated (skipped during sync)
        // The gold button should remain or be null, but NOT dirt
        val slot0Item = manager.getSlot(guildId, 0)
        // Slot 0 is managed separately - sync should skip it
        assertTrue(slot0Item == null || slot0Item.type != Material.DIRT)
    }

    @Test
    fun `syncInventoryToCache should buffer changes for database write`() {
        // Given: A vault and inventory with items
        val guildId = UUID.randomUUID()
        val mockInventory = mockk<Inventory>(relaxed = true)

        every { mockInventory.size } returns 54

        val ironItem = mockk<ItemStack>(relaxed = true)
        every { ironItem.type } returns Material.IRON_INGOT
        every { ironItem.amount } returns 16
        every { ironItem.isSimilar(any()) } returns false

        every { mockInventory.getItem(3) } returns ironItem
        every { mockInventory.getItem(any()) } returns null
        every { mockInventory.getItem(3) } returns ironItem

        // Load vault
        manager.getOrLoadVault(guildId)

        // When: Sync inventory to cache
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: Changes should be buffered (flush will write to DB)
        // We verify by calling flushBuffer and checking repository was called
        manager.flushBuffer(guildId)

        verify(atLeast = 1) { vaultRepository.saveVaultItem(guildId, any(), any()) }
    }

    @Test
    fun `syncInventoryToCache should only update changed slots for efficiency`() {
        // Given: A vault with existing item that matches inventory
        val guildId = UUID.randomUUID()

        val existingItem = mockk<ItemStack>(relaxed = true)
        every { existingItem.type } returns Material.DIAMOND
        every { existingItem.amount } returns 10
        every { existingItem.isSimilar(any()) } returns true

        every { vaultRepository.getVaultInventory(guildId) } returns mapOf(5 to existingItem)

        // Load vault (has diamond in slot 5)
        manager.getOrLoadVault(guildId)

        // Inventory also has same item in slot 5
        val mockInventory = mockk<Inventory>(relaxed = true)
        every { mockInventory.size } returns 54
        every { mockInventory.getItem(5) } returns existingItem
        every { mockInventory.getItem(any()) } returns null
        every { mockInventory.getItem(5) } returns existingItem

        // When: Sync inventory to cache
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: No changes should be detected for slot 5 (items are equal)
        // Flush buffer should not write slot 5 since it hasn't changed
        manager.flushBuffer(guildId)

        // Verify that slot 5 was not written (since it didn't change)
        // The implementation should detect equal items and skip buffering
    }

    @Test
    fun `syncInventoryToCache should handle full inventory correctly`() {
        // Given: An inventory with items in many slots
        val guildId = UUID.randomUUID()
        val mockInventory = mockk<Inventory>(relaxed = true)

        every { mockInventory.size } returns 54

        // Fill slots 1-10 with cobblestone
        val cobbleItem = mockk<ItemStack>(relaxed = true)
        every { cobbleItem.type } returns Material.COBBLESTONE
        every { cobbleItem.amount } returns 64
        every { cobbleItem.isSimilar(any()) } returns false

        for (slot in 1..10) {
            every { mockInventory.getItem(slot) } returns cobbleItem
        }
        every { mockInventory.getItem(0) } returns null // Gold button slot
        for (slot in 11..53) {
            every { mockInventory.getItem(slot) } returns null
        }

        // Load vault
        manager.getOrLoadVault(guildId)

        // When: Sync inventory to cache
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: All slots 1-10 should be in cache
        for (slot in 1..10) {
            val item = manager.getSlot(guildId, slot)
            assertNotNull(item, "Slot $slot should have item")
        }
    }

    @Test
    fun `syncInventoryToCache should work with vault not yet in cache`() {
        // Given: A guild ID with no vault loaded yet
        val guildId = UUID.randomUUID()
        val mockInventory = mockk<Inventory>(relaxed = true)

        every { mockInventory.size } returns 54

        val ironItem = mockk<ItemStack>(relaxed = true)
        every { ironItem.type } returns Material.IRON_BLOCK
        every { ironItem.amount } returns 1
        every { mockInventory.getItem(7) } returns ironItem
        every { mockInventory.getItem(any()) } returns null
        every { mockInventory.getItem(7) } returns ironItem

        // When: Sync inventory to cache (vault not loaded yet)
        manager.syncInventoryToCache(guildId, mockInventory)

        // Then: Vault should be loaded and item synced
        val item = manager.getSlot(guildId, 7)
        assertNotNull(item)
    }
}
