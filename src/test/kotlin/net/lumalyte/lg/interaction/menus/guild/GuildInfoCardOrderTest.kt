package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarStats
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-237: prestige sits under the name and wars come last on the guild card. */
internal class GuildInfoCardOrderTest : GuildRankMenuFixture() {
    @Test
    fun `guild card shows prestige first and active wars last`() {
        bindRank("Founder")
        val record = PrestigeRecord(UUID.randomUUID(), guild.id, UUID.randomUUID(), 2, "r", Instant.parse("2026-11-08T12:00:00Z"))
        val history = GlobalContext.get().get<PrestigeHistory>()
        every { history.history(guild.id) } returns listOf(record)
        every { history.firstToReach(2) } returns record
        val wars = GlobalContext.get().get<WarService>()
        val war = mockk<War>(relaxed = true)
        every { war.isActive } returns true
        every { war.declaringGuildId } returns guild.id
        every { war.defendingGuildId } returns UUID.randomUUID()
        every { war.id } returns UUID.randomUUID()
        every { wars.getWarsForGuild(guild.id) } returns listOf(war)
        every { wars.getWarStats(any()) } returns WarStats(war.id)
        every { wars.killTargetFor(war) } returns 10

        GuildInfoMenu(MenuNavigator(player), player, guild).open()
        val lore = checkNotNull(player.openInventory.topInventory.getItem(4)?.lore())
            .map(PlainTextComponentSerializer.plainText()::serialize)
        assertTrue(lore.first().contains("ᴘʀᴇꜱᴛɪɢᴇ II"), lore.toString())
        assertTrue(lore.last().contains("10"), lore.toString())
        assertEquals("ᴀᴄᴛɪᴠᴇ ᴡᴀʀꜱ", lore[lore.size - 2].trim(), lore.toString())
    }
}
