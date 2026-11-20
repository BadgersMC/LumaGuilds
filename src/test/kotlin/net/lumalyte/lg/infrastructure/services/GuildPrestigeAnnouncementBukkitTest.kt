package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildPrestigeAnnouncementBukkitTest {
    @TempDir lateinit var dataFolder: Path

    private fun lang() = LangService(object : LangHost {
        override val dataFolder: File = this@GuildPrestigeAnnouncementBukkitTest.dataFolder.toFile()
        override val resourceClassLoader = LumaGuildsLang::class.java.classLoader
    }, Locale("en_US"), LumaGuildsLang::class.java)

    @Test
    fun `broadcast resolves actual guild and formats each prestige tier`() {
        val guild = Guild(UUID.randomUUID(), "Enthusiast", createdAt = Instant.EPOCH)
        val guilds = mockk<GuildService>()
        every { guilds.getGuild(guild.id) } returns guild
        val sent = mutableListOf<Component>()
        val adapter = GuildPrestigeAnnouncementBukkit(guilds, lang()) { sent += it }
        (1..6).forEach { adapter.announce(guild.id, it) }
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI").map {
            "✦ Enthusiast has reached Guild Prestige $it!"
        }, sent.map(PlainTextComponentSerializer.plainText()::serialize))
        val json = GsonComponentSerializer.gson().serialize(sent.first())
        assertTrue(json.contains("gold"))
        assertTrue(json.contains("light_purple"))
    }

    @Test
    fun `the prestige-applied message names the new tier in Roman numerals`() {
        val text = PlainTextComponentSerializer.plainText().serialize(lang().msg(
            "menu.guild_progression.prestige.feedback.applied",
            "reward" to "Bank Extension 3", "fee" to 30000,
            "count" to net.lumalyte.lg.domain.values.PrestigeNumeral.of(4),
        ))
        assertEquals("Guild prestige complete. Bank Extension 3 is now permanent. Fee: 30000 Gold Ore. Your guild is now Prestige IV.", text)
    }

    @Test
    fun `guild names remain literal text rather than interactive markup`() {
        val name = "<click:run_command:/op>"
        val guild = Guild(UUID.randomUUID(), name, createdAt = Instant.EPOCH)
        val guilds = mockk<GuildService>()
        every { guilds.getGuild(guild.id) } returns guild
        val sent = mutableListOf<Component>()
        GuildPrestigeAnnouncementBukkit(guilds, lang()) { sent += it }.announce(guild.id, 1)
        assertEquals("✦ $name has reached Guild Prestige I!",
            PlainTextComponentSerializer.plainText().serialize(sent.single()))
        assertFalse(GsonComponentSerializer.gson().serialize(sent.single()).contains("clickEvent"))
    }
}
