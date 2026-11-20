package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildInfoMenu
import org.geysermc.cumulus.form.SimpleForm
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-172: the prestige history reaches the real Java and Bedrock info views (in the server's small caps). */
internal class GuildInfoPrestigeMenuTest : GuildRankMenuFixture() {
    private fun stubHistory() {
        bindRank("Founder")
        val first = PrestigeRecord(UUID.randomUUID(), guild.id, UUID.randomUUID(), 1, "r", Instant.parse("2026-11-01T12:00:00Z"))
        val second = PrestigeRecord(UUID.randomUUID(), guild.id, UUID.randomUUID(), 2, "r", Instant.parse("2026-11-08T12:00:00Z"))
        val history = GlobalContext.get().get<PrestigeHistory>()
        every { history.history(guild.id) } returns listOf(first, second)
        every { history.firstToReach(1) } returns first.copy(transactionId = UUID.randomUUID())
        every { history.firstToReach(2) } returns second
    }

    @Test
    fun `java info menu lists each prestige with its date and server-first mark`() {
        stubHistory()
        GuildInfoMenu(MenuNavigator(player), player, guild).open()
        val lore = checkNotNull(player.openInventory.topInventory.getItem(5)?.lore())
            .map(PlainTextComponentSerializer.plainText()::serialize)
        // REQ-237: each line leads with that tier's badge glyph.
        assertTrue(lore[0].endsWith("ᴄᴜʀʀᴇɴᴛ: ᴘʀᴇꜱᴛɪɢᴇ II") && lore[0].startsWith("✦"), lore[0])
        assertTrue(lore[1].endsWith(" ᴘʀᴇꜱᴛɪɢᴇ I · 2026-11-01") && lore[1].startsWith("✦"), lore[1])
        assertTrue(lore[2].endsWith("ᴘʀᴇꜱᴛɪɢᴇ II · 2026-11-08 ✦ ꜰɪʀꜱᴛ ᴏɴ ᴛʜᴇ ꜱᴇʀᴠᴇʀ"), lore[2])
    }

    @Test
    fun `bedrock info form has a prestige section`() {
        stubHistory()
        val form = BedrockGuildInfoMenu(MenuNavigator(player), player, guild, Logger.getAnonymousLogger()).getForm() as SimpleForm
        assertTrue(form.content().contains("ᴄᴜʀʀᴇɴᴛ: ᴘʀᴇꜱᴛɪɢᴇ II"), form.content())
        assertTrue(form.content().contains("ᴘʀᴇꜱᴛɪɢᴇ II · Nov 08, 2026 ✦ ꜰɪʀꜱᴛ ᴏɴ ᴛʜᴇ ꜱᴇʀᴠᴇʀ"), form.content())
    }
}
