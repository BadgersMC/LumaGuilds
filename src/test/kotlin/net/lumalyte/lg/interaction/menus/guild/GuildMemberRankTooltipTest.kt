package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real member inventory, locale placeholders and text styling. */
class GuildMemberRankTooltipTest {
    @TempDir var directory: Path? = null
    private var player: PlayerMock by Delegates.notNull()
    private var ranks: RankService by Delegates.notNull()
    private val guild = Guild(UUID.randomUUID(), "Enthusiast", createdAt = Instant.EPOCH)

    /** Bind the normal menu services to a single known member. */
    @BeforeEach
    fun setup() {
        val server = MockBukkit.mock()
        player = server.addPlayer("FainNeito")
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        ranks = mockk()
        initializeServices(plugin)
    }

    private fun initializeServices(plugin: Plugin) {
        val members = createMembers()
        val lang = createLanguage()
        stopKoin()
        startKoin {
            modules(module {
                single<Plugin> { plugin }
                single { members }
                single { ranks }
                single { lang }
            })
        }
    }

    private fun createMembers(): MemberService {
        val members = mockk<MemberService>()
        every { members.getGuildMembers(guild.id) } returns setOf(
            Member(player.uniqueId, guild.id, UUID.randomUUID(), Instant.EPOCH),
        )
        return members
    }

    private fun createLanguage(): LangService {
        return LangService(
            object : LangHost {
                override val dataFolder: File = checkNotNull(directory).toFile()
                override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
            },
            Locale("en_US"),
            LumaGuildsLang::class.java,
        )
    }

    /** Release the shared mock server and service bindings. */
    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    /** Every accepted hex representation must reach the item lore as color. */
    @ParameterizedTest
    @ValueSource(strings = ["&#f99801Founder", "#f99801Founder", "§#f99801Founder"])
    fun hexRankColorRenders(storedName: String) {
        every { ranks.getPlayerRank(player.uniqueId, guild.id) } returns
            Rank(UUID.randomUUID(), guild.id, storedName)
        GuildMemberListMenu(MenuNavigator(player), player, guild).open()
        val lore = checkNotNull(player.openInventory.topInventory.getItem(0)?.lore())
        val rankLine = lore.first()
        val visible = PlainTextComponentSerializer.plainText().serialize(rankLine)
        assertFalse(visible.contains("f99801"), "Raw hex leaked into member lore: $visible")
        assertTrue(visible.contains("ꜰᴏᴜɴᴅᴇʀ"), "Visible rank name is missing: $visible")
        assertTrue(hasColor(rankLine, TextColor.color(0xf99801)))
        assertEquals(storedName, ranks.getPlayerRank(player.uniqueId, guild.id)?.name)
    }

    private fun hasColor(component: Component, color: TextColor): Boolean {
        return component.color() == color || component.children().any { hasColor(it, color) }
    }
}
