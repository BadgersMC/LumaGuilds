package net.lumalyte.lg.application.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for shared inventory functionality in VaultInventoryManager.
 * These tests verify that the manager creates and returns shared Bukkit Inventory
 * instances per guild, rather than creating separate instances per player.
 */
class VaultInventoryManagerSharedInventoryTest {

    private lateinit var vaultRepository: GuildVaultRepository
    private lateinit var transactionLogger: VaultTransactionLogger
    private lateinit var vaultConfig: VaultConfig
    private lateinit var manager: VaultInventoryManager
    private lateinit var mockServer: Server

    @BeforeEach
    fun setUp() {
        // Mock dependencies
        vaultRepository = mockk(relaxed = true)
        transactionLogger = mockk(relaxed = true)
        vaultConfig = mockk(relaxed = true)

        // Mock Bukkit server for inventory creation
        mockServer = mockk(relaxed = true)
        mockkStatic(Bukkit::class)
        every { Bukkit.getServer() } returns mockServer

        // Setup default repository behavior
        every { vaultRepository.getVaultInventory(any()) } returns emptyMap()
        every { vaultRepository.getGoldBalance(any()) } returns 0L

        // Create manager instance
        manager = VaultInventoryManager(vaultRepository, transactionLogger, vaultConfig)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Bukkit::class)
        clearAllMocks()
    }

    // ========== Test Case 3: Shared Inventory Instance Verification ==========

    @Test
    fun `getOrCreateSharedInventory should create new inventory on first call`() {
        // Given: A guild ID and mock inventory
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val capacity = 54
        val mockInventory = mockk<Inventory>(relaxed = true)

        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } returns mockInventory

        // When: First call to get shared inventory
        val inventory = manager.getOrCreateSharedInventory(guildId, guildName, capacity)

        // Then: Should return a valid inventory
        assertNotNull(inventory)

        // And: Bukkit.createInventory should have been called once
        verify(exactly = 1) {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        }
    }

    @Test
    fun `getOrCreateSharedInventory should return same instance on subsequent calls`() {
        // Given: A guild ID
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val capacity = 54
        val mockInventory = mockk<Inventory>(relaxed = true)

        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } returns mockInventory

        // When: Multiple calls to get shared inventory
        val inventory1 = manager.getOrCreateSharedInventory(guildId, guildName, capacity)
        val inventory2 = manager.getOrCreateSharedInventory(guildId, guildName, capacity)
        val inventory3 = manager.getOrCreateSharedInventory(guildId, guildName, capacity)

        // Then: All calls should return the exact same object (reference equality)
        assertSame(inventory1, inventory2, "Second call should return same instance")
        assertSame(inventory2, inventory3, "Third call should return same instance")

        // And: Bukkit.createInventory should only have been called ONCE
        verify(exactly = 1) {
            mockServer.createInventory(any<InventoryHolder>(), any<Int>(), any<net.kyori.adventure.text.Component>())
        }
    }

    @Test
    fun `getOrCreateSharedInventory should create different inventories for different guilds`() {
        // Given: Two different guild IDs
        val guildId1 = UUID.randomUUID()
        val guildId2 = UUID.randomUUID()
        val guildName1 = "Guild1"
        val guildName2 = "Guild2"
        val capacity = 54

        val mockInventory1 = mockk<Inventory>(relaxed = true)
        val mockInventory2 = mockk<Inventory>(relaxed = true)

        var callCount = 0
        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } answers {
            callCount++
            if (callCount == 1) mockInventory1 else mockInventory2
        }

        // When: Get shared inventory for each guild
        val inventory1 = manager.getOrCreateSharedInventory(guildId1, guildName1, capacity)
        val inventory2 = manager.getOrCreateSharedInventory(guildId2, guildName2, capacity)

        // Then: Should be different inventory instances
        assertNotSame(inventory1, inventory2, "Different guilds should have different inventories")

        // And: Bukkit.createInventory should have been called twice
        verify(exactly = 2) {
            mockServer.createInventory(any<InventoryHolder>(), any<Int>(), any<net.kyori.adventure.text.Component>())
        }
    }

    @Test
    fun `getOrCreateSharedInventory should create inventory with correct size`() {
        // Given: Various capacity sizes
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val expectedCapacity = 27 // 3 rows

        val mockInventory = mockk<Inventory>(relaxed = true)
        every { mockInventory.size } returns expectedCapacity

        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(expectedCapacity), any<net.kyori.adventure.text.Component>())
        } returns mockInventory

        // When: Get shared inventory with specific capacity
        val inventory = manager.getOrCreateSharedInventory(guildId, guildName, expectedCapacity)

        // Then: Inventory should have correct size
        assertEquals(expectedCapacity, inventory.size)

        // And: createInventory was called with correct capacity
        verify {
            mockServer.createInventory(any<InventoryHolder>(), eq(expectedCapacity), any<net.kyori.adventure.text.Component>())
        }
    }

    @Test
    fun `getOrCreateSharedInventory should set inventory holder correctly`() {
        // Given: A guild ID
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val capacity = 54

        var capturedHolder: InventoryHolder? = null
        val mockInventory = mockk<Inventory>(relaxed = true)

        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } answers {
            capturedHolder = firstArg()
            mockInventory
        }

        // When: Get shared inventory
        manager.getOrCreateSharedInventory(guildId, guildName, capacity)

        // Then: Holder should be set and contain correct guild info
        assertNotNull(capturedHolder, "Inventory holder should be set")
        // The holder should be a VaultInventoryHolder or similar that we can query
    }

    @Test
    fun `getOrCreateSharedInventory should load existing items from cache`() {
        // Given: A guild with existing items in cache
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val capacity = 54

        val existingItem = mockk<ItemStack>(relaxed = true)
        every { vaultRepository.getVaultInventory(guildId) } returns mapOf(5 to existingItem)
        every { vaultRepository.getGoldBalance(guildId) } returns 100L

        val mockInventory = mockk<Inventory>(relaxed = true)
        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } returns mockInventory

        // When: Get shared inventory
        manager.getOrCreateSharedInventory(guildId, guildName, capacity)

        // Then: Existing item should be set in inventory
        verify { mockInventory.setItem(5, existingItem) }
    }

    @Test
    fun `getOrCreateSharedInventory should set gold button in slot 0`() {
        // Given: A guild ID
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val capacity = 54

        every { vaultRepository.getGoldBalance(guildId) } returns 500L

        val mockInventory = mockk<Inventory>(relaxed = true)
        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } returns mockInventory

        // When: Get shared inventory
        manager.getOrCreateSharedInventory(guildId, guildName, capacity)

        // Then: Slot 0 should be set with gold button
        verify { mockInventory.setItem(eq(0), any()) }
    }

    // ========== Thread Safety Tests ==========

    @Test
    fun `concurrent calls to getOrCreateSharedInventory should return same instance`() {
        // Given: A guild ID and multiple threads
        val guildId = UUID.randomUUID()
        val guildName = "TestGuild"
        val capacity = 54
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val mockInventory = mockk<Inventory>(relaxed = true)
        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } returns mockInventory

        val results = mutableListOf<Inventory>()
        val resultsLock = Object()

        // When: Multiple threads request shared inventory concurrently
        repeat(threadCount) {
            executor.submit {
                try {
                    val inventory = manager.getOrCreateSharedInventory(guildId, guildName, capacity)
                    synchronized(resultsLock) {
                        results.add(inventory)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete within timeout")
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        // Then: All results should be the exact same instance
        assertEquals(threadCount, results.size, "All threads should have returned a result")
        val firstResult = results.first()
        results.forEach { result ->
            assertSame(firstResult, result, "All threads should receive same inventory instance")
        }

        // And: createInventory should only have been called once despite concurrent access
        verify(atMost = 1) {
            mockServer.createInventory(any<InventoryHolder>(), any<Int>(), any<net.kyori.adventure.text.Component>())
        }
    }

    @Test
    fun `getOrCreateSharedInventory should be safe under high concurrency with multiple guilds`() {
        // Given: Multiple guild IDs and threads
        val guildIds = List(5) { UUID.randomUUID() }
        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val capacity = 54

        // Create unique mock inventories for each guild
        val mockInventories = guildIds.associateWith { mockk<Inventory>(relaxed = true) }

        every {
            mockServer.createInventory(any<InventoryHolder>(), eq(capacity), any<net.kyori.adventure.text.Component>())
        } answers {
            // Return a new mock each time (simulating real Bukkit behavior)
            mockk<Inventory>(relaxed = true)
        }

        val results = mutableMapOf<UUID, MutableList<Inventory>>()
        val resultsLock = Object()

        // Initialize results map
        guildIds.forEach { results[it] = mutableListOf() }

        // When: Multiple threads request shared inventories for various guilds
        repeat(threadCount) { index ->
            executor.submit {
                try {
                    val guildId = guildIds[index % guildIds.size]
                    val inventory = manager.getOrCreateSharedInventory(guildId, "Guild$index", capacity)
                    synchronized(resultsLock) {
                        results[guildId]?.add(inventory)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for completion
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Then: Each guild should have received the same instance across all its requests
        results.forEach { (guildId, inventories) ->
            if (inventories.isNotEmpty()) {
                val first = inventories.first()
                inventories.forEach { inv ->
                    assertSame(first, inv, "All requests for guild $guildId should return same instance")
                }
            }
        }
    }
}
