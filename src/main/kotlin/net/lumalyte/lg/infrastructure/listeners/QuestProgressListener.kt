package net.lumalyte.lg.infrastructure.listeners

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import net.lumalyte.lg.api.events.GuildBankDepositEvent
import net.lumalyte.lg.api.events.GuildWarEndEvent
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestProgressContext
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.values.BlockPosition
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.inventory.CraftingRecipe
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.time.Instant

class QuestProgressListener(
    private val questService: QuestService,
    private val memberService: MemberService,
    private val provenance: BlockProvenanceRepository,
    private val provenanceOperations: BlockProvenanceOperationQueue,
) : Listener {
    private val logger = LoggerFactory.getLogger(QuestProgressListener::class.java)
    private var trackedMaterialsExpiresAt: Instant = Instant.EPOCH
    private var trackedTargets: Set<String> = emptySet()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerKill(event: PlayerDeathEvent) = safely("player kill") {
        event.entity.killer?.takeIf(::eligible)?.let { player ->
            incrementFor(player, QuestAction.KILL_PLAYERS, "minecraft:player/player", context = context(player))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMobKill(event: EntityDeathEvent) = safely("mob kill") {
        if (event.entity is Player) return@safely
        event.entity.killer?.takeIf(::eligible)?.let { player ->
            val target = "minecraft:entity/${event.entity.type.key.key}"
            incrementFor(player, QuestAction.KILL_MOBS, target, context = context(player))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) = safely("block break") {
        if (!eligible(event.player)) return@safely

        val customTarget = nexoBlockTarget(event.block)
        val target = customTarget ?: minecraftBlockTarget(event.block.type)
        val action = if (customTarget != null) {
            QuestAction.MINE_BLOCKS
        } else {
            val data = event.block.blockData
            if (data is Ageable && data.age >= data.maximumAge) QuestAction.HARVEST_CROPS else QuestAction.MINE_BLOCKS
        }

        val position = event.block.position()
        val playerId = event.player.uniqueId
        val snapshot = context(event.player, event.block)
        // Snapshot Bukkit state before leaving the tick thread. FIFO read precedes
        // ProgressionEventListener's MONITOR cleanup of this same broken block.
        background("block break") {
            val placed = shouldTrackProvenance(target) && provenance.wasPlayerPlaced(position)
            incrementCaptured(playerId, action, target, 1, snapshot.copy(playerPlacedBlock = placed))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) = safely("block place") {
        if (!eligible(event.player)) return@safely
        val target = nexoBlockTarget(event.block) ?: minecraftBlockTarget(event.block.type)
        val position = event.block.position()
        val playerId = event.player.uniqueId
        val snapshot = context(event.player, event.block)
        background("block place") {
            if (shouldTrackProvenance(target)) provenance.recordPlayerPlaced(position)
            incrementCaptured(playerId, QuestAction.PLACE_BLOCKS, target, 1, snapshot)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) = safely("craft") {
        val player = event.whoClicked as? Player ?: return@safely
        if (!eligible(player) || event.recipe !is CraftingRecipe) return@safely

        val result = event.recipe.result
        val craftedAmount = if (event.isShiftClick) {
            shiftCraftedAmount(
                result = result,
                matrix = event.inventory.matrix,
                destination = player.inventory.storageContents,
            )
        } else {
            result.amount.toLong()
        }
        if (craftedAmount <= 0) return@safely

        incrementFor(
            player,
            QuestAction.CRAFT_ITEMS,
            itemTarget(result),
            craftedAmount,
            context(player)
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelt(event: FurnaceExtractEvent) = safely("smelt") {
        if (!eligible(event.player)) return@safely
        incrementFor(
            event.player,
            QuestAction.SMELT_ITEMS,
            minecraftItemTarget(event.itemType),
            event.itemAmount.toLong(),
            context(event.player)
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) = safely("fish") {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH || !eligible(event.player)) return@safely
        val stack = (event.caught as? Item)?.itemStack
        val target = stack?.let(::itemTarget) ?: "minecraft:item/any"
        incrementFor(event.player, QuestAction.FISH, target, context = context(event.player))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) = safely("enchant") {
        if (!eligible(event.enchanter)) return@safely
        incrementFor(
            event.enchanter,
            QuestAction.ENCHANT_ITEMS,
            itemTarget(event.item),
            context = context(event.enchanter)
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBankDeposit(event: GuildBankDepositEvent) = safely("bank deposit") {
        val guildId = event.guildId
        val playerId = event.playerId
        val amount = event.amount.toLong()
        background("bank deposit") {
            questService.incrementProgress(
                guildId,
                QuestAction.DEPOSIT_BANK,
                "lumaguilds:bank/coins",
                amount,
                actorId = playerId,
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWarEnd(event: GuildWarEndEvent) = safely("war end") {
        event.winnerGuildId?.let {
            background("war end") { questService.incrementProgress(it, QuestAction.WIN_WARS, "lumaguilds:war/win") }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = safely("entity explosion") {
        val positions = event.blockList().map { it.position() }
        background("entity explosion") { provenance.removeAll(positions) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = safely("block explosion") {
        val positions = event.blockList().map { it.position() }
        background("block explosion") { provenance.removeAll(positions) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) = safely("piston extend") {
        val moves = event.blocks.asReversed().map { block ->
            block.position() to block.getRelative(event.direction).position()
        }
        if (moves.isNotEmpty()) background("piston extend") { provenance.moveAll(moves) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) = safely("piston retract") {
        val moves = event.blocks.map { block ->
            block.position() to block.getRelative(event.direction).position()
        }
        if (moves.isNotEmpty()) background("piston retract") { provenance.moveAll(moves) }
    }

    private fun incrementFor(
        player: Player,
        action: QuestAction,
        target: String,
        amount: Long = 1,
        context: QuestProgressContext = QuestProgressContext()
    ) {
        val playerId = player.uniqueId
        background("quest progress") { incrementCaptured(playerId, action, target, amount, context) }
    }

    private fun incrementCaptured(
        playerId: java.util.UUID,
        action: QuestAction,
        target: String,
        amount: Long,
        context: QuestProgressContext,
    ) {
        memberService.getPlayerGuilds(playerId).forEach { guildId ->
            questService.incrementProgress(
                guildId = guildId,
                action = action,
                targetId = target,
                amount = amount,
                context = context,
                actorId = playerId,
            )
        }
    }

    private fun context(player: Player): QuestProgressContext = context(player, player.location.block)

    private fun context(player: Player, block: Block) = QuestProgressContext(
        dimension = block.world.environment.name,
        biome = block.biome.name(),
        tool = player.inventory.itemInMainHand.type.name,
        x = block.x,
        y = block.y,
        z = block.z,
        transport = player.vehicle?.type?.name,
        usedElytra = player.isGliding
    )

    private fun eligible(player: Player): Boolean =
        player.gameMode != GameMode.CREATIVE && player.gameMode != GameMode.SPECTATOR

    private fun shouldTrackProvenance(target: String): Boolean {
        val now = Instant.now()
        if (!trackedMaterialsExpiresAt.isAfter(now)) {
            trackedTargets = questService.activeQuestSet()?.quests.orEmpty().asSequence()
                .filter { it.target.provenancePolicy == BlockProvenancePolicy.NATURAL_ONLY }
                .map { it.target.id }
                .toSet()
            trackedMaterialsExpiresAt = now.plusSeconds(30)
        }
        return target in trackedTargets || target.substringAfterLast('/').uppercase() in trackedTargets
    }

    private fun nexoBlockTarget(block: Block): String? {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) return null
        return runCatching { NexoBlocks.customBlockMechanic(block)?.itemID }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { "nexo:block/${it.lowercase()}" }
    }

    private fun itemTarget(stack: ItemStack): String {
        if (Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            runCatching { NexoItems.idFromItem(stack) }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?.let { return "nexo:item/${it.lowercase()}" }
        }
        return minecraftItemTarget(stack.type)
    }

    private fun minecraftBlockTarget(material: Material) = "minecraft:block/${material.key.key}"
    private fun minecraftItemTarget(material: Material) = "minecraft:item/${material.key.key}"
    private fun Block.position() = BlockPosition(world.uid, x, y, z)

    private fun background(operation: String, task: () -> Unit) {
        provenanceOperations.submit {
            try { task() }
            catch (error: Exception) { throw IllegalStateException("Weekly quest $operation failed", error) }
        }
    }

    private inline fun safely(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            logger.warn("Weekly quest $operation handler failed", error)
        }
    }
}

internal fun shiftCraftedAmount(
    result: ItemStack,
    matrix: Array<ItemStack?>,
    destination: Array<ItemStack?>,
): Long {
    val outputPerCraft = result.amount
    if (outputPerCraft <= 0 || result.type.isAir) return 0

    val craftsByIngredients = matrix.asSequence()
        .filterNotNull()
        .filterNot { it.type.isAir }
        .minOfOrNull { it.amount }
        ?: return 0

    val capacity = destination.sumOf { slot ->
        when {
            slot == null || slot.type.isAir -> result.maxStackSize
            slot.isSimilar(result) -> (slot.maxStackSize - slot.amount).coerceAtLeast(0)
            else -> 0
        }
    }
    val craftsByCapacity = capacity / outputPerCraft
    val crafts = minOf(craftsByIngredients, craftsByCapacity)
    return crafts.toLong() * outputPerCraft.toLong()
}
