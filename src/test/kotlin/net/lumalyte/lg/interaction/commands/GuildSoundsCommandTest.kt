package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.domain.values.GuildSoundCatalog
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-254: staff hear guild sounds themselves; nothing else happens. */
class GuildSoundsCommandTest {
    private val keys = mutableListOf<String>()
    private val lang = mockk<LangService>().also {
        every { it.msg(any()) } answers { keys += firstArg<String>(); Component.text("x") }
        every { it.msg(any(), *anyVararg()) } answers { keys += firstArg<String>(); Component.text("x") }
    }
    private val played = mutableListOf<String>()
    private val scheduled = sortedMapOf<Long, MutableList<() -> Unit>>()
    private val command = GuildSoundsCommand(lang, { _, entry -> played += entry.id },
        { ticks, task -> scheduled.getOrPut(ticks) { mutableListOf() } += task })
    private val player = mockk<Player>(relaxed = true).also {
        every { it.hasPermission(GuildSoundsCommand.PERMISSION) } returns true
        every { it.uniqueId } returns UUID.randomUUID()
        every { it.isOnline } returns true
    }
    private fun run(vararg args: String) = command.execute(player, arrayOf("sounds", *args))
    private fun runScheduled() = scheduled.values.toList().flatten().forEach { it() }

    @Test
    fun `needs the permission and a player`() {
        every { player.hasPermission(GuildSoundsCommand.PERMISSION) } returns false
        run("war-victory")
        assertEquals(listOf("admin.common.no_permission"), keys)
        keys.clear()
        val console = mockk<CommandSender>(relaxed = true).also { every { it.hasPermission(any<String>()) } returns true }
        command.execute(console, arrayOf("sounds"))
        assertEquals(listOf("admin.sounds.players_only"), keys)
        assertTrue(played.isEmpty())
    }

    @Test
    fun `lists every sound, plays one by id and rejects unknown ids`() {
        run()
        assertEquals(GuildSoundCatalog.entries.size, keys.count { it == "admin.sounds.entry" })
        run("War-Victory")
        assertEquals(listOf("war-victory"), played)
        run("nope")
        assertEquals("admin.sounds.unknown", keys.last())
    }

    @Test
    fun `all plays every sound once in order and a new command cancels it`() {
        run("all")
        runScheduled()
        assertEquals(GuildSoundCatalog.ids, played)
        assertEquals("admin.sounds.finished", keys.last())
        assertTrue(scheduled.keys.zipWithNext().all { (a, b) -> b - a >= GuildSoundsCommand.GAP_TICKS })

        played.clear(); scheduled.clear()
        run("all")
        run("stop")
        runScheduled()
        assertTrue(played.isEmpty())
    }
}
