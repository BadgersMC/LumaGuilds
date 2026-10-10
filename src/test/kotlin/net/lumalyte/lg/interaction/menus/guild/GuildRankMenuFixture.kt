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
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Shared real-server and locale fixture for rank menu rendering. */
internal abstract class GuildRankMenuFixture {
    /** Isolated locale storage for the real translation service. */
    @TempDir var directory: Path? = null
    protected var player: PlayerMock by Delegates.notNull()
    protected var ranks: RankService by Delegates.notNull()
    protected val guild = Guild(UUID.randomUUID(), "Enthusiast", createdAt = Instant.EPOCH)
    protected val expectedColor = checkNotNull(TextColor.fromHexString("#f99801"))
    protected val rankId = UUID.randomUUID()
    protected val member get() = Member(player.uniqueId, guild.id, rankId, Instant.EPOCH)

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
        stopKoin()
        startKoin { modules(createMenuModule(plugin)) }
    }

    private fun createMenuModule(plugin: Plugin): org.koin.core.module.Module {
        val members = createMembers()
        val lang = createLanguage()
        val guilds = createGuilds()
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig()
        return module {
            single<Plugin> { plugin }
            single { members }
            single { ranks }
            single { lang }
            single { guilds }
            single { config }
            single { mockk<RelationService>(relaxed = true) }
            single { mockk<BankService>(relaxed = true) }
            single { mockk<WarService>(relaxed = true) }
            single { mockk<NexoEmojiService>(relaxed = true) }
            single { mockk<MenuFactory>(relaxed = true) }
        }
    }

    private fun createGuilds(): GuildService {
        val guilds = mockk<GuildService>(relaxed = true)
        every { guilds.getGuild(guild.id) } returns guild
        every { guilds.hasPermission(any(), any(), any()) } returns true
        every { guilds.getHome(guild.id, "Home") } returns
            GuildHome(UUID.randomUUID(), Position3D(0, 0, 0))
        return guilds
    }

    private fun createMembers(): MemberService {
        val members = mockk<MemberService>()
        every { members.getGuildMembers(guild.id) } returns setOf(member)
        every { members.getMemberCount(guild.id) } returns 1
        every { members.getMembersByRank(guild.id, rankId) } returns setOf(member)
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

    protected fun bindRank(storedName: String): Rank {
        val rank = Rank(rankId, guild.id, storedName)
        every { ranks.getRank(rankId) } returns rank
        every { ranks.getPlayerRank(player.uniqueId, guild.id) } returns rank
        every { ranks.listRanks(guild.id) } returns setOf(rank)
        every { ranks.getHighestRank(guild.id) } returns rank
        every { ranks.hasPermission(any(), any(), any()) } returns true
        every { ranks.isPlayerRank(any(), any(), any()) } returns false
        every { ranks.getRankCount(guild.id) } returns 1
        return rank
    }

    protected fun assertRankInventory(screen: String) {
        val lines =
            player.openInventory.topInventory.contents.filterNotNull().flatMap { item ->
                listOfNotNull(item.itemMeta.itemName()) + (item.lore() ?: emptyList())
            }
        val rankLines =
            lines.filter {
                PlainTextComponentSerializer.plainText().serialize(it).contains("ꜰᴏᴜɴᴅᴇʀ") ||
                    PlainTextComponentSerializer.plainText().serialize(it).contains("Founder")
            }
        assertTrue(rankLines.isNotEmpty(), "$screen omitted the rank: $lines")
        rankLines.forEach { line ->
            assertFalse(PlainTextComponentSerializer.plainText().serialize(line).contains("f99801"), screen)
            assertTrue(hasColor(line, expectedColor), screen)
        }
    }

    protected fun hasColor(component: Component, color: TextColor): Boolean =
        component.color() == color || component.children().any { hasColor(it, color) }
}
