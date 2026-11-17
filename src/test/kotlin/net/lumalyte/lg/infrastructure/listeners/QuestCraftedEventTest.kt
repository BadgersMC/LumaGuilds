package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.event.inventory.ItemCraftedEvent
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import java.util.concurrent.Executor

/**
 * The 26.2 Paper event reports completed crafting, rather than pre-click
 * recipe/inventory estimates. MockBukkit can verify listener accounting,
 * but not the server's real Ctrl+Q packet-to-event behavior.
 */
class QuestCraftedEventTest {
    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    @Test
    fun `one completed eye per event counts all 64 even when issued as repeated drops`() {
        val f = fixture()
        repeat(64) {
            Bukkit.getPluginManager().callEvent(ItemCraftedEvent(f.player, ItemStack(Material.ENDER_EYE, 1)))
        }

        verify(exactly = 64) {
            f.quests.incrementProgress(
                f.guildId, QuestAction.CRAFT_ITEMS, "minecraft:item/ender_eye",
                1L, any(), f.player.uniqueId,
            )
        }
    }

    @Test
    fun `four completed batches of 16 eyes count 64 outputs`() {
        val f = fixture()
        repeat(4) {
            Bukkit.getPluginManager().callEvent(ItemCraftedEvent(f.player, ItemStack(Material.ENDER_EYE, 16)))
        }

        verify(exactly = 4) {
            f.quests.incrementProgress(
                f.guildId, QuestAction.CRAFT_ITEMS, "minecraft:item/ender_eye",
                16L, any(), f.player.uniqueId,
            )
        }
    }

    @Test
    fun `multi output recipes credit result item amounts rather than a single operation`() {
        val f = fixture()
        Bukkit.getPluginManager().callEvent(ItemCraftedEvent(f.player, ItemStack(Material.STICK, 4)))

        verify(exactly = 1) {
            f.quests.incrementProgress(
                f.guildId, QuestAction.CRAFT_ITEMS, "minecraft:item/stick",
                4L, any(), f.player.uniqueId,
            )
        }
    }

    @Test
    fun `creative players and empty results cannot earn crafting quest progress`() {
        val f = fixture()
        f.player.gameMode = GameMode.CREATIVE
        Bukkit.getPluginManager().callEvent(ItemCraftedEvent(f.player, ItemStack(Material.ENDER_EYE, 16)))
        f.player.gameMode = GameMode.SURVIVAL
        Bukkit.getPluginManager().callEvent(ItemCraftedEvent(f.player, ItemStack(Material.AIR)))

        verify(exactly = 0) { f.quests.incrementProgress(any(), any(), any(), any(), any(), any()) }
    }

    private fun fixture(): Fixture {
        val server = MockBukkit.mock()
        val world = server.addSimpleWorld("quest-crafting")
        val player = server.addPlayer()
        player.teleport(world.spawnLocation)
        val plugin = MockBukkit.createMockPlugin()
        val guildId = UUID.randomUUID()
        val quests = mockk<QuestService>(relaxed = true)
        val members = mockk<MemberService>()
        every { members.getPlayerGuilds(player.uniqueId) } returns setOf(guildId)

        val listener = QuestProgressListener(
            quests, members,
            mockk<BlockProvenanceRepository>(relaxed = true),
            BlockProvenanceOperationQueue(Executor { it.run() }),
            plugin,
        )
        Bukkit.getPluginManager().registerEvents(listener, plugin)
        return Fixture(player, guildId, quests)
    }

    private data class Fixture(val player: Player, val guildId: UUID, val quests: QuestService)
}
