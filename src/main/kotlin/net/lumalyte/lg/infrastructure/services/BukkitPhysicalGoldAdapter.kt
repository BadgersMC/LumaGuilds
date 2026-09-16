package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ExternalTransferResult
import net.lumalyte.lg.application.services.PhysicalGoldPort
import net.lumalyte.lg.application.services.PhysicalGoldReservation
import net.lumalyte.lg.application.services.PhysicalReservationResult
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BukkitPhysicalGoldAdapter(
    private val playerLookup: (UUID) -> Player?,
    private val baseMaterial: Material,
    private val blockMaterial: Material?,
    private val blockValue: Long,
    private val overflowDelivery: (Player, Collection<ItemStack>) -> Unit = { player, items ->
        items.forEach { player.world.dropItemNaturally(player.location, it) }
    }
) : PhysicalGoldPort {
    init {
        require(baseMaterial.isItem && !baseMaterial.isAir)
        require(blockMaterial == null || (blockMaterial.isItem && !blockMaterial.isAir && blockMaterial != baseMaterial))
        require(blockValue > 0)
    }

    private data class ReservationState(
        val public: PhysicalGoldReservation,
        val stacks: List<ItemStack>,
        val source: Inventory?
    )

    private data class DepositWindow(val inventory: Inventory, val excludedSlots: Set<Int>)
    private val depositWindows = mutableMapOf<UUID, DepositWindow>()

    /** Synchronous server-thread scope: the same reservation policy can consume a deposit window. */
    fun <T> withDepositInventory(playerId: UUID, inventory: Inventory, excludedSlots: Set<Int>, action: () -> T): T {
        check(org.bukkit.Bukkit.isPrimaryThread()) { "Physical currency requires the server thread" }
        check(playerId !in depositWindows) { "A deposit window is already active" }
        val player = requireNotNull(playerLookup(playerId))
        depositWindows[playerId] = DepositWindow(inventory, excludedSlots)
        try {
            return action()
        } finally {
            depositWindows.remove(playerId)
            // Return only items still present, never a reservation whose outcome is uncertain.
            for (slot in 0 until inventory.size) {
                if (slot in excludedSlots) continue
                val item = inventory.getItem(slot) ?: continue
                inventory.setItem(slot, null)
                val overflow = player.inventory.addItem(item).values
                if (overflow.isNotEmpty()) overflowDelivery(player, overflow)
            }
        }
    }

    private fun contents(playerId: UUID, player: Player): Array<ItemStack?> {
        val window = depositWindows[playerId] ?: return player.inventory.storageContents
        return window.inventory.contents.mapIndexed { index, item ->
            if (index in window.excludedSlots) null else item?.clone()
        }.toTypedArray()
    }

    private val reservations = ConcurrentHashMap<UUID, ReservationState>()
    private val delivered = ConcurrentHashMap.newKeySet<UUID>()

    override fun availableValue(playerId: UUID): Long? {
        val player = playerLookup(playerId) ?: return null
        val contents = contents(playerId, player)
        return availableValue(
            contents.filterNotNull().filter { it.type == baseMaterial }.sumOf { it.amount.toLong() },
            contents.filterNotNull().filter { it.type == blockMaterial }.sumOf { it.amount.toLong() },
            blockValue)
    }

    override fun reserve(playerId: UUID, requestedValue: Long): PhysicalReservationResult {
        val player = playerLookup(playerId) ?: return PhysicalReservationResult.Unavailable
        val contents = contents(playerId, player)
        val baseCount = contents.filterNotNull().filter { it.type == baseMaterial }.sumOf { it.amount.toLong() }
        val blockCount = contents.filterNotNull().filter { it.type == blockMaterial }.sumOf { it.amount.toLong() }
        val selection = selectExact(baseCount, blockCount, blockValue, requestedValue)
            ?: return PhysicalReservationResult.Insufficient
        val removed = mutableListOf<ItemStack>()
        remove(contents, baseMaterial, selection.base, removed)
        blockMaterial?.let { remove(contents, it, selection.blocks, removed) }
        val window = depositWindows[playerId]
        if (window == null) player.inventory.storageContents = contents
        else contents.forEachIndexed { slot, item ->
            if (slot !in window.excludedSlots) window.inventory.setItem(slot, item)
        }
        val reservation = PhysicalGoldReservation(UUID.randomUUID(), playerId, requestedValue)
        reservations[reservation.id] = ReservationState(reservation, removed, window?.inventory)
        return PhysicalReservationResult.Reserved(reservation)
    }

    override fun commit(reservation: PhysicalGoldReservation): Boolean =
        reservations.remove(reservation.id)?.public == reservation

    override fun restore(reservation: PhysicalGoldReservation): Boolean {
        val state = reservations[reservation.id] ?: return false
        if (state.public != reservation) return false
        val player = playerLookup(reservation.playerId) ?: return false
        val overflow = (state.source ?: player.inventory).addItem(*state.stacks.map(ItemStack::clone).toTypedArray()).values
        if (overflow.isNotEmpty()) overflowDelivery(player, overflow)
        reservations.remove(reservation.id, state)
        return true
    }

    override fun deliver(playerId: UUID, value: Long, transactionId: UUID): ExternalTransferResult {
        if (!delivered.add(transactionId)) return ExternalTransferResult.Applied
        val player = playerLookup(playerId) ?: run {
            delivered.remove(transactionId)
            return ExternalTransferResult.Unavailable
        }
        val blocks = if (blockMaterial == null) 0 else value / blockValue
        val base = if (blockMaterial == null) value else value % blockValue
        val stacks = buildList {
            blockMaterial?.let { addStacks(this, it, blocks) }
            addStacks(this, baseMaterial, base)
        }
        return try {
            val overflow = player.inventory.addItem(*stacks.toTypedArray()).values
            if (overflow.isNotEmpty()) overflowDelivery(player, overflow)
            ExternalTransferResult.Applied
        } catch (error: Exception) {
            delivered.remove(transactionId)
            ExternalTransferResult.Failed(error.message ?: "physical delivery failed")
        }
    }

    data class DenominationSelection(val base: Long, val blocks: Long)

    companion object {
        fun fromConfig(playerLookup: (UUID) -> Player?, config: net.lumalyte.lg.config.VaultConfig): BukkitPhysicalGoldAdapter {
            val base = requireNotNull(Material.matchMaterial(config.physicalCurrencyMaterial)) {
                "Unknown physical currency material: ${config.physicalCurrencyMaterial}"
            }
            val mappings = config.compressableBlocks.map { it.split(':') }
                .filter { it.size == 3 && Material.matchMaterial(it[1]) == base }
            require(mappings.size <= 1) { "Canonical gold supports one compressed denomination per currency" }
            val mapping = mappings.singleOrNull()
            val compressed = mapping?.let { requireNotNull(Material.matchMaterial(it[0])) {
                "Unknown compressed currency material: ${it[0]}"
            } }
            val ratio = mapping?.let { requireNotNull(it[2].toLongOrNull()) { "Invalid currency compression ratio" } } ?: 1L
            return BukkitPhysicalGoldAdapter(playerLookup, base, compressed, ratio)
        }

        fun availableValue(baseCount: Long, blockCount: Long, blockValue: Long): Long? = try {
            Math.addExact(baseCount, Math.multiplyExact(blockCount, blockValue))
        } catch (_: ArithmeticException) {
            null
        }

        fun selectExact(baseCount: Long, blockCount: Long, blockValue: Long, requestedValue: Long): DenominationSelection? {
            if (baseCount < 0 || blockCount < 0 || blockValue <= 0 || requestedValue <= 0) return null
            var blocks = minOf(blockCount, requestedValue / blockValue)
            while (blocks >= 0) {
                val base = requestedValue - blocks * blockValue
                if (base <= baseCount) return DenominationSelection(base, blocks)
                blocks--
            }
            return null
        }

        private fun remove(contents: Array<ItemStack?>, material: Material, count: Long, removed: MutableList<ItemStack>) {
            var remaining = count
            for (index in contents.indices) {
                if (remaining == 0L) break
                val stack = contents[index] ?: continue
                if (stack.type != material) continue
                val taken = minOf(remaining, stack.amount.toLong()).toInt()
                removed += stack.clone().also { it.amount = taken }
                stack.amount -= taken
                if (stack.amount == 0) contents[index] = null
                remaining -= taken
            }
            check(remaining == 0L) { "Exact physical reservation changed during removal" }
        }

        private fun addStacks(target: MutableList<ItemStack>, material: Material, count: Long) {
            var remaining = count
            while (remaining > 0) {
                val amount = minOf(remaining, material.maxStackSize.toLong()).toInt()
                target += ItemStack(material, amount)
                remaining -= amount
            }
        }
    }
}
