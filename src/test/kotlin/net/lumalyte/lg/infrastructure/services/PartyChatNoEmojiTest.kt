package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.ChatChannel
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class PartyChatNoEmojiTest {
    @Test
    fun `party message renders for a guild without an emoji or PlaceholderAPI`(@TempDir folder: File) {
        val sender = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Cedar", createdAt = Instant.EPOCH)
        val player = mockk<Player> { every { name } returns "Sender" }
        val host = object : LangHost {
            override val dataFolder = folder
            override val resourceClassLoader = LumaGuildsLang::class.java.classLoader
        }
        val chat = ChatServiceBukkit(
            chatSettingsRepository = mockk(),
            memberService = mockk { every { getPlayerGuilds(sender) } returns setOf(guild.id) },
            guildService = mockk { every { getGuild(guild.id) } returns guild },
            relationService = mockk(),
            partyService = mockk(),
            nexoEmojiService = mockk { every { getEmojiPlaceholder(null) } returns "" },
            configService = mockk { every { loadConfig() } returns MainConfig() },
            rankService = mockk(),
            preferenceRepository = mockk { every { getByPlayerId(sender) } returns null },
            partyRepository = mockk(),
            lang = LangService(host, Locale("en_US"), LumaGuildsLang::class.java),
            chatRankSettings = mockk(),
        )

        mockkStatic(Bukkit::class) {
            every { Bukkit.getPlayer(sender) } returns player
            every { Bukkit.getPluginManager() } returns mockk { every { isPluginEnabled("PlaceholderAPI") } returns false }
            assertEquals("[P] Cedar Sender: hello party", chat.formatMessage(sender, "hello party", ChatChannel.PARTY))
        }
    }
}
