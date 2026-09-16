package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BukkitPhysicalGoldAdapterTest {
    @org.junit.jupiter.api.io.TempDir lateinit var directory: java.nio.file.Path
    private lateinit var storage: net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
    private lateinit var journal: PhysicalGoldJournal
    @org.junit.jupiter.api.BeforeEach
    fun setupServer() {
        org.mockbukkit.mockbukkit.MockBukkit.mock()
        storage = net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage(directory.toFile())
        journal = PhysicalGoldJournal(storage)
    }

    @org.junit.jupiter.api.AfterEach
    fun stopServer() { storage.connection.close(5, java.util.concurrent.TimeUnit.SECONDS); org.mockbukkit.mockbukkit.MockBukkit.unmock() }

    @Test fun `reservation receipt and exact items survive adapter recreation`() {
        val player = io.mockk.spyk(org.mockbukkit.mockbukkit.MockBukkit.getMock()!!.addPlayer())
        io.mockk.every { player.saveData() } answers { Unit } // MockBukkit does not implement disk persistence.
        val original = org.bukkit.inventory.ItemStack(org.bukkit.Material.RAW_GOLD, 10)
        val meta = original.itemMeta
        meta.displayName(net.kyori.adventure.text.Component.text("Original gold"))
        original.itemMeta = meta
        player.inventory.setItem(0, original.clone())
        val transaction = java.util.UUID.randomUUID()
        val adapter = BukkitPhysicalGoldAdapter({ player }, org.bukkit.Material.RAW_GOLD, null, 1, journal)
        val reservation = (try { adapter.reserve(transaction, player.uniqueId, 10) } catch (error: Throwable) {
            throw AssertionError("Physical reservation failed", error)
        } as net.lumalyte.lg.application.services.PhysicalReservationResult.Reserved).reservation
        val restarted = BukkitPhysicalGoldAdapter({ player }, org.bukkit.Material.RAW_GOLD, null, 1, PhysicalGoldJournal(storage))
        assertEquals(reservation, restarted.reservation(transaction))
        org.junit.jupiter.api.Assertions.assertTrue(restarted.restore(reservation))
        org.junit.jupiter.api.Assertions.assertTrue(restarted.restore(reservation))
        assertEquals(original, player.inventory.getItem(0))
    }

    @Test
    fun `recovery completes held physical credit from durable reservation without removing again`() {
        val player = io.mockk.spyk(org.mockbukkit.mockbukkit.MockBukkit.getMock()!!.addPlayer())
        io.mockk.every { player.saveData() } answers { Unit }
        player.inventory.setItem(0, org.bukkit.inventory.ItemStack(org.bukkit.Material.RAW_GOLD, 10))
        val sql = net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL(storage)
        val guild = java.util.UUID.randomUUID(); val id = java.util.UUID.randomUUID()
        val mutation = net.lumalyte.lg.domain.gold.GuildGoldMutation(id, guild, player.uniqueId,
            net.lumalyte.lg.domain.gold.GuildGoldRoute.PHYSICAL_ITEM, net.lumalyte.lg.domain.gold.GuildGoldDirection.CREDIT, 10, 0, "deposit")
        sql.prepare(mutation); sql.beginExternal(id, "DEPOSIT")
        BukkitPhysicalGoldAdapter({ player }, org.bukkit.Material.RAW_GOLD, null, 1, journal).reserve(id, player.uniqueId, 10)
        sql.applyExternalCredit(mutation, 1_000)
        val restartedAdapter = BukkitPhysicalGoldAdapter({ player }, org.bukkit.Material.RAW_GOLD, null, 1, PhysicalGoldJournal(storage))
        val gold = net.lumalyte.lg.application.services.GuildGoldService(sql,
            net.lumalyte.lg.application.services.GuildGoldPolicyProvider {
                net.lumalyte.lg.domain.gold.GuildGoldPolicy(1, 1_000, 1.0, 1_000, 0.0, 0.0, 0, 0, 1_000, 1_000, false) },
            net.lumalyte.lg.application.services.GuildGoldCapacityProvider { net.lumalyte.lg.domain.gold.GuildGoldCapacity(1_000, 0) },
            physicalGold = restartedAdapter)
        gold.reconcilePending(Long.MAX_VALUE)
        gold.reconcilePending(Long.MAX_VALUE)
        assertEquals(net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.APPLIED, sql.findOperation(id)?.status)
        assertEquals(10, sql.getBalance(guild))
        assertEquals("COMMITTED", PhysicalGoldJournal(storage).get(id)?.phase)
        assertEquals(0, player.inventory.storageContents.filterNotNull().sumOf { it.amount })
    }

    @Test
    fun `interrupted inventory save retains receipt and cannot be committed or restored automatically`() {
        val player = io.mockk.spyk(org.mockbukkit.mockbukkit.MockBukkit.getMock()!!.addPlayer())
        io.mockk.every { player.saveData() } throws IllegalStateException("disk write uncertain")
        player.inventory.setItem(0, org.bukkit.inventory.ItemStack(org.bukkit.Material.RAW_GOLD, 10))
        val id = java.util.UUID.randomUUID()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            BukkitPhysicalGoldAdapter({ player }, org.bukkit.Material.RAW_GOLD, null, 1, journal).reserve(id, player.uniqueId, 10)
        }
        val restarted = BukkitPhysicalGoldAdapter({ player }, org.bukkit.Material.RAW_GOLD, null, 1, PhysicalGoldJournal(storage))
        val receipt = requireNotNull(restarted.reservation(id))
        assertEquals("REMOVING", journal.get(id)?.phase)
        assertEquals(net.lumalyte.lg.application.services.PhysicalCommitResult.Unknown, restarted.commit(receipt))
        org.junit.jupiter.api.Assertions.assertFalse(restarted.restore(receipt))
    }

    @Test
    fun `configured currency accepts no compression mapping`() {
        val config = net.lumalyte.lg.config.VaultConfig(
            physicalCurrencyMaterial = "DIAMOND", compressableBlocks = emptyList())
        val adapter = BukkitPhysicalGoldAdapter.fromConfig({ null }, config, journal)
        assertEquals(net.lumalyte.lg.application.services.PhysicalReservationResult.Unavailable,
            adapter.reserve(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), 1))
    }

    @Test
    fun `duplicate denomination must be rejected before touching inventory`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            BukkitPhysicalGoldAdapter({ null }, org.bukkit.Material.RAW_GOLD, org.bukkit.Material.RAW_GOLD, 9, journal)
        }
    }

    @Test
    fun `one configured block converts to nine base units`() {
        assertEquals(9, BukkitPhysicalGoldAdapter.availableValue(baseCount = 0, blockCount = 1, blockValue = 9))
    }

    @Test
    fun `mixed stacks reserve an exact partial requested value`() {
        assertEquals(
            BukkitPhysicalGoldAdapter.DenominationSelection(base = 1, blocks = 2),
            BukkitPhysicalGoldAdapter.selectExact(baseCount = 5, blockCount = 3, blockValue = 9, requestedValue = 19)
        )
    }

    @Test
    fun `reservation fails when value exists but cannot be represented exactly`() {
        assertNull(BukkitPhysicalGoldAdapter.selectExact(baseCount = 0, blockCount = 2, blockValue = 9, requestedValue = 10))
    }
}
