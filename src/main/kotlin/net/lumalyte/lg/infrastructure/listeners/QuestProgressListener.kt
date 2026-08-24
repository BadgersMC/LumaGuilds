package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestProgressContext
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.events.GuildBankDepositEvent
import net.lumalyte.lg.domain.events.GuildWarEndEvent
import net.lumalyte.lg.domain.values.BlockPosition
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import org.bukkit.GameMode
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
import org.slf4j.LoggerFactory
import java.time.Instant

class QuestProgressListener(
    private val questService: QuestService,
    private val memberService: MemberService,
    private val provenance: BlockProvenanceRepository,
    private val progressionConfigService: ProgressionConfigService
) : Listener {
    private val logger = LoggerFactory.getLogger(QuestProgressListener::class.java)
    private var trackedMaterialsExpiresAt: Instant = Instant.EPOCH
    private var trackedMaterials: Set<String> = emptySet()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerKill(event: PlayerDeathEvent) = safely("player kill") {
        event.entity.killer?.takeIf(::eligible)?.let { player ->
            incrementFor(player, QuestAction.KILL_PLAYERS, "PLAYER")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMobKill(event: EntityDeathEvent) = safely("mob kill") {
        if (event.entity is Player) return@safely
        event.entity.killer?.takeIf(::eligible)?.let { player ->
            incrementFor(player, QuestAction.KILL_MOBS, event.entity.type.name)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) = safely("block break") {
        if (!eligible(event.player)) return@safely
        if (!shouldTrackProvenance(event.block.type.name)) {
            val data = event.block.blockData
            val action = if (data is Ageable && data.age >= data.maximumAge) QuestAction.HARVEST_CROPS else QuestAction.MINE_BLOCKS
            incrementFor(event.player, action, event.block.type.name, context = context(event.player, event.block))
            return@safely
        }
        val position = event.block.position()
        val playerPlaced = provenance.wasPlayerPlaced(position)
        provenance.remove(position)
        val data = event.block.blockData
        val action = if (data is Ageable && data.age >= data.maximumAge) QuestAction.HARVEST_CROPS else QuestAction.MINE_BLOCKS
        incrementFor(
            event.player,
            action,
            event.block.type.name,
            context = context(event.player, event.block).copy(playerPlacedBlock = playerPlaced)
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) = safely("block place") {
        if (!eligible(event.player)) return@safely
        if (shouldTrackProvenance(event.block.type.name)) provenance.recordPlayerPlaced(event.block.position())
        incrementFor(event.player, QuestAction.PLACE_BLOCKS, event.block.type.name, context = context(event.player, event.block))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) = safely("craft") {
        val player = event.whoClicked as? Player ?: return@safely
        if (!eligible(player)) return@safely
        incrementFor(player, QuestAction.CRAFT_ITEMS, event.recipe.result.type.name, event.recipe.result.amount.toLong())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelt(event: FurnaceExtractEvent) = safely("smelt") {
        if (eligible(event.player)) incrementFor(event.player, QuestAction.SMELT_ITEMS, event.itemType.name, event.itemAmount.toLong())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) = safely("fish") {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH || !eligible(event.player)) return@safely
        val target = (event.caught as? Item)?.itemStack?.type?.name ?: "ANY"
        incrementFor(event.player, QuestAction.FISH, target)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) = safely("enchant") {
        if (eligible(event.enchanter)) incrementFor(event.enchanter, QuestAction.ENCHANT_ITEMS, event.item.type.name)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBankDeposit(event: GuildBankDepositEvent) = safely("bank deposit") {
        questService.incrementProgress(event.guildId, QuestAction.DEPOSIT_BANK, "COINS", event.amount.toLong())
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWarEnd(event: GuildWarEndEvent) = safely("war end") {
        event.winnerGuildId?.let { questService.incrementProgress(it, QuestAction.WIN_WARS, "WAR") }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = safely("entity explosion") {
        provenance.removeAll(event.blockList().map { it.position() })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = safely("block explosion") {
        provenance.removeAll(event.blockList().map { it.position() })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) = safely("piston extend") {
        provenance.moveAll(event.blocks.asReversed().map { block -> block.position() to block.getRelative(event.direction).position() })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) = safely("piston retract") {
        provenance.moveAll(event.blocks.map { block -> block.position() to block.getRelative(event.direction).position() })
    }

    private fun incrementFor(
        player: Player,
        action: QuestAction,
        target: String,
        amount: Long = 1,
        context: QuestProgressContext = QuestProgressContext()
    ) {
        memberService.getPlayerGuilds(player.uniqueId).forEach { guildId ->
            questService.incrementProgress(guildId, action, target, amount, context)
        }
    }

    private fun context(player: Player, block: Block) = QuestProgressContext(
        dimension = block.world.environment.name,
        biome = block.biome.name(),
        tool = player.inventory.itemInMainHand.type.name,
        y = block.y,
        transport = player.vehicle?.type?.name,
        usedElytra = player.isGliding
    )

    private fun eligible(player: Player): Boolean = player.gameMode != GameMode.CREATIVE && player.gameMode != GameMode.SPECTATOR

    private fun shouldTrackProvenance(material: String): Boolean {
        val now = Instant.now()
        if (!trackedMaterialsExpiresAt.isAfter(now)) {
            val configured = progressionConfigService.getProgressionConfig().quests.definitions.asSequence()
                .filter { it.provenancePolicy == BlockProvenancePolicy.NATURAL_ONLY.name }.map { it.target }
            val active = questService.activeQuestSet()?.quests.orEmpty().asSequence()
                .filter { it.target.provenancePolicy == BlockProvenancePolicy.NATURAL_ONLY }.map { it.target.id }
            trackedMaterials = (configured + active).toSet()
            trackedMaterialsExpiresAt = now.plusSeconds(30)
        }
        return material in trackedMaterials
    }

    private fun Block.position() = BlockPosition(world.uid, x, y, z)

    private inline fun safely(operation: String, block: () -> Unit) {
        try { block() } catch (error: Exception) { logger.warn("Weekly quest $operation handler failed", error) }
    }
}
