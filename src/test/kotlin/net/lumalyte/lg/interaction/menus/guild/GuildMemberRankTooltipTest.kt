package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
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
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberListMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberRankConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberRankMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildPromotionMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildRankManagementMenu
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.component.DropdownComponent
import org.geysermc.cumulus.component.InputComponent
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.SimpleForm
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real member inventory, locale placeholders and text styling. */
internal class GuildMemberRankTooltipTest {
    /** Isolated locale storage for the real translation service. */
    @TempDir var directory: Path? = null
    private var player: PlayerMock by Delegates.notNull()
    private var ranks: RankService by Delegates.notNull()
    private val guild = Guild(UUID.randomUUID(), "Enthusiast", createdAt = Instant.EPOCH)
    private val expectedColor = checkNotNull(TextColor.fromHexString("#f99801"))
    private val rankId = UUID.randomUUID()
    private val member get() = Member(player.uniqueId, guild.id, rankId, Instant.EPOCH)

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
        assertTrue(hasColor(rankLine, expectedColor))
        assertEquals(storedName, ranks.getPlayerRank(player.uniqueId, guild.id)?.name)
    }

    private fun hasColor(component: Component, color: TextColor): Boolean =
        component.color() == color || component.children().any { hasColor(it, color) }

    /** Exercise every additional Java rank display through its inventory. */
    @ParameterizedTest
    @ValueSource(strings = ["&#f99801Founder", "#f99801Founder", "§#f99801Founder"])
    fun remainingJavaMenusRenderRank(storedName: String) {
        val rank = bindRank(storedName)
        val navigator = MenuNavigator(player)
        val menus = listOf(
            GuildInfoMenu(navigator, player, guild),
            GuildPromotionMenu(navigator, player, guild),
            GuildMemberRankMenu(navigator, player, guild, member),
            GuildMemberRankConfirmationMenu(navigator, player, guild, member, rank),
            HomeAccessMenu(navigator, player, guild, "Home"),
        )
        assertAll(menus.map { menu ->
            {
                menu.open()
                assertRankInventory(menu.javaClass.simpleName)
            }
        })
        assertEquals(storedName, rank.name)
    }

    private fun bindRank(storedName: String): Rank {
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

    private fun assertRankInventory(screen: String) {
        val lines = player.openInventory.topInventory.contents.filterNotNull().flatMap { item ->
            listOfNotNull(item.itemMeta.itemName()) + (item.lore() ?: emptyList())
        }
        val rankLines = lines.filter {
            PlainTextComponentSerializer.plainText().serialize(it).contains("ꜰᴏᴜɴᴅᴇʀ") ||
                PlainTextComponentSerializer.plainText().serialize(it).contains("Founder")
        }
        assertTrue(rankLines.isNotEmpty(), "$screen omitted the rank: $lines")
        rankLines.forEach { line ->
            assertFalse(PlainTextComponentSerializer.plainText().serialize(line).contains("f99801"), screen)
            assertTrue(hasColor(line, expectedColor), screen)
        }
    }

    /** Native forms use readable labels, with rank selection still indexed by ID. */
    @ParameterizedTest
    @ValueSource(strings = ["&#f99801Founder", "#f99801Founder", "§#f99801Founder"])
    fun bedrockRankFormsDoNotLeakCodes(storedName: String) {
        val rank = bindRank(storedName)
        val navigator = MenuNavigator(player)
        val logger = Logger.getAnonymousLogger()
        val assignment = BedrockGuildMemberRankMenu(navigator, player, guild, member, logger).getForm() as CustomForm
        assignment.content().filterNotNull().forEach { assertFalse(it.text().contains("f99801")) }
        val dropdown = assignment.content().filterIsInstance<DropdownComponent>().single()
        assertEquals(listOf("Founder"), dropdown.options())
        assertEquals(0, dropdown.defaultOption())
        val confirmation = BedrockGuildMemberRankConfirmationMenu(
            navigator, player, guild, member, rank, logger,
        ).getForm() as SimpleForm
        assertFalse(confirmation.content().contains("f99801"))
        assertEquals(storedName, rank.name)
    }

    /** Rank editor neighbors must render colors as well as the edited rank. */
    @ParameterizedTest
    @ValueSource(strings = ["&#f99801Founder", "#f99801Founder", "§#f99801Founder"])
    fun priorityNeighborsRenderRank(storedName: String) {
        val rank = bindRank(storedName).copy(priority = 1)
        val above = rank.copy(id = UUID.randomUUID(), priority = 0)
        val below = rank.copy(id = UUID.randomUUID(), priority = 2)
        every { ranks.listRanks(guild.id) } returns setOf(above, rank, below)
        RankEditMenu(MenuNavigator(player), player, guild, rank).open()
        assertRankInventory("RankEditMenu")
    }

    /** Member pickers and management labels hide syntax while the editor preserves it. */
    @ParameterizedTest
    @ValueSource(strings = ["&#f99801Founder", "#f99801Founder", "§#f99801Founder"])
    fun bedrockMemberAndManagementForms(storedName: String) {
        val rank = bindRank(storedName)
        val navigator = MenuNavigator(player)
        val logger = Logger.getAnonymousLogger()
        val members = BedrockGuildMemberListMenu(navigator, player, guild, logger).getForm() as SimpleForm
        members.buttons().filterNotNull().forEach { assertFalse(it.text().contains("f99801")) }
        val promotion = BedrockGuildPromotionMenu(navigator, player, guild, logger).getForm() as CustomForm
        assertFormLabels(promotion)
        val editing = BedrockGuildRankManagementMenu(navigator, player, guild, rank, logger).getForm() as CustomForm
        assertFormLabels(editing)
        assertEquals(storedName, editing.content().filterIsInstance<InputComponent>().first().defaultText())
    }

    private fun assertFormLabels(form: CustomForm) {
        form.content().filterNotNull().forEach { component ->
            assertFalse(component.text().contains("f99801"))
            if (component is DropdownComponent) {
                component.options().forEach { assertFalse(it.contains("f99801")) }
            }
        }
    }
}
