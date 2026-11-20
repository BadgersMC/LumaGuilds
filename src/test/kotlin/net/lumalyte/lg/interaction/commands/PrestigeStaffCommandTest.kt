package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PrestigeStaffInfo
import net.lumalyte.lg.application.services.PrestigeStaffService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.PrestigeAdjustmentResult
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import net.lumalyte.lg.domain.rewards.PrestigeResult
import net.lumalyte.lg.domain.rewards.RewardDefinition
import net.lumalyte.lg.domain.rewards.RewardEffect
import net.lumalyte.lg.domain.rewards.RewardKind
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-184 to REQ-187: staff commands preview first and act only with confirm. */
class PrestigeStaffCommandTest {
    private val guild = Guild(UUID.randomUUID(), "Vibe", createdAt = Instant.now())
    private val staff = mockk<PrestigeStaffService>(relaxed = true)
    private val guilds = mockk<GuildService>(relaxed = true).also {
        every { it.getGuildByName(any()) } returns null
        every { it.getGuildByName("Vibe") } returns guild
        every { it.getAllGuilds() } returns emptySet()
    }
    private val keys = mutableListOf<String>()
    private val lang = mockk<LangService>().also {
        every { it.msg(any()) } answers { keys += firstArg<String>(); Component.text("x") }
        every { it.msg(any(), *anyVararg()) } answers { keys += firstArg<String>(); Component.text("x") }
    }
    private val audits = mutableListOf<String>()
    private val sender = mockk<CommandSender>(relaxed = true).also {
        every { it.hasPermission(PrestigeStaffCommand.PERMISSION) } returns true
        every { it.name } returns "Fain"
    }
    private val command = PrestigeStaffCommand({ staff }, guilds, lang, { "Someone" }, { it }, { audits += it })
    private fun run(vararg args: String) = command.execute(sender, arrayOf("prestige", *args))

    @Test
    fun `without permission nothing runs`() {
        every { sender.hasPermission(PrestigeStaffCommand.PERMISSION) } returns false
        run("set", "Vibe", "2", "confirm")
        verify(exactly = 0) { staff.setCount(any(), any(), any()) }
        assertEquals(listOf("admin.common.no_permission"), keys)
    }

    @Test
    fun `info lists history and adjustments`() {
        every { staff.info(guild.id) } returns PrestigeStaffInfo(2, 6, 14, setOf("bank-1"),
            listOf(PrestigeRecord(UUID.randomUUID(), guild.id, UUID.randomUUID(), 1, "bank-1", Instant.EPOCH)),
            emptyList())
        run("info", "Vibe")
        assertEquals(listOf("admin.prestige.info.header", "admin.prestige.info.kept", "admin.prestige.info.record"), keys)
    }

    @Test
    fun `set previews, then applies with confirm and logs it`() {
        every { staff.info(guild.id) } returns PrestigeStaffInfo(3, 6, 1, setOf("bank-1"), emptyList(), emptyList())
        run("set", "Vibe", "1")
        verify(exactly = 0) { staff.setCount(any(), any(), any()) }
        assertEquals("admin.prestige.set.preview", keys.last())
        every { staff.setCount(any(), guild.id, 1) } returns PrestigeAdjustmentResult.Applied(3, 1, listOf("bank-1"))
        run("set", "Vibe", "1", "confirm")
        verify { staff.setCount(PrestigeStaffCommand.CONSOLE, guild.id, 1) }
        assertEquals("admin.prestige.set.done", keys.last())
        assertEquals(1, audits.size)
    }

    @Test
    fun `raising with set points to grant`() {
        every { staff.info(guild.id) } returns PrestigeStaffInfo(1, 6, 1, setOf("bank-1"), emptyList(), emptyList())
        run("set", "Vibe", "3", "confirm")
        verify(exactly = 0) { staff.setCount(any(), any(), any()) }
        assertEquals("admin.prestige.set.raise", keys.last())
    }

    @Test
    fun `grant lists choices for a missing or wrong reward and grants with confirm`() {
        every { staff.grantChoices(guild.id) } returns listOf(
            RewardDefinition("bank-1", "Bank Extension 1", 5, RewardKind.NUMERIC, 100, RewardEffect.BankCapacity(1_000)),
        )
        run("grant", "Vibe")
        run("grant", "Vibe", "nope", "confirm")
        verify(exactly = 0) { staff.grant(any(), any(), any()) }
        assertEquals(listOf("admin.prestige.grant.choices", "admin.prestige.grant.choices"), keys)
        every { staff.grant(any(), guild.id, "bank-1") } returns
            PrestigeResult.Applied(UUID.randomUUID(), "bank-1", 0, 0, 0, 2, 4, 0, 1)
        run("grant", "Vibe", "bank-1", "confirm")
        verify { staff.grant(PrestigeStaffCommand.CONSOLE, guild.id, "bank-1") }
        assertEquals("admin.prestige.grant.done", keys.last())
    }

    @Test
    fun `rerun previews the tiers and accepts a Roman numeral`() {
        val record = PrestigeRecord(UUID.randomUUID(), guild.id, UUID.randomUUID(), 2, "bank-1", Instant.EPOCH)
        every { staff.rerunTargets(guild.id, 2) } returns listOf(record)
        run("rerun", "Vibe", "II")
        verify(exactly = 0) { staff.rerun(any(), any()) }
        run("rerun", "Vibe", "2", "confirm")
        verify { staff.rerun(guild.id, 2) }
        assertEquals(listOf("admin.prestige.rerun.preview", "admin.prestige.rerun.done"), keys)
    }

    @Test
    fun `an unknown guild is reported`() {
        run("info", "Nobody")
        assertEquals(listOf("admin.prestige.guild_not_found"), keys)
    }

    @Test
    fun `preview plays the rank-up to the player only and changes nothing`() {
        val me = UUID.randomUUID()
        val player = mockk<org.bukkit.entity.Player>(relaxed = true).also {
            every { it.hasPermission(PrestigeStaffCommand.PERMISSION) } returns true
            every { it.uniqueId } returns me
            every { it.name } returns "Fain"
        }
        val played = mutableListOf<Triple<UUID, Int, String>>()
        val cmd = PrestigeStaffCommand({ staff }, guilds, lang, { "Someone" }, { it }, { audits += it },
            preview = { p, t, g -> played += Triple(p, t, g) }, ownGuildName = { "Everlights" })
        cmd.execute(player, arrayOf("prestige", "preview", "3"))
        cmd.execute(player, arrayOf("prestige", "preview", "vi", "Vibe"))
        cmd.execute(player, arrayOf("prestige", "preview", "7"))
        assertEquals(listOf(Triple(me, 3, "Everlights"), Triple(me, 6, "Vibe")), played)
        assertEquals(listOf("admin.prestige.preview.playing", "admin.prestige.preview.playing", "admin.prestige.preview.usage"), keys)
        assertEquals(emptyList(), audits)
        verify(exactly = 0) { staff.setCount(any(), any(), any()) }

        keys.clear()
        cmd.execute(sender, arrayOf("prestige", "preview", "2"))
        assertEquals(listOf("admin.prestige.preview.players_only"), keys)
    }
}
