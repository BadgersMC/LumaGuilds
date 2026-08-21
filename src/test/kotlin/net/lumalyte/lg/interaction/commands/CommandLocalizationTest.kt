package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.VaultBackupService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.infrastructure.i18n.LocaleSourceScanner
import net.lumalyte.lg.interaction.commands.admin.BankCreditCommand
import net.lumalyte.lg.interaction.commands.admin.VaultRollbackCommand
import org.bukkit.command.Command
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.command.MessageTarget
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class CommandLocalizationTest {

    @TempDir
    lateinit var dataFolder: Path

    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var permissionPlugin: Plugin
    private lateinit var command: Command
    private lateinit var bankService: BankService
    private lateinit var guildService: GuildService
    private lateinit var memberService: MemberService
    private lateinit var configService: ConfigService
    private lateinit var lang: LangService

    private val plainText = PlainTextComponentSerializer.plainText()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        player = server.addPlayer("Ada")
        permissionPlugin = mockk(relaxed = true) {
            every { isEnabled } returns true
        }
        command = mockk(relaxed = true)
        bankService = mockk(relaxed = true)
        guildService = mockk(relaxed = true)
        memberService = mockk(relaxed = true)
        configService = mockk(relaxed = true) {
            every { loadConfig() } returns MainConfig()
        }
        lang = LangService(
            object : LangHost {
                override val dataFolder: File = this@CommandLocalizationTest.dataFolder.toFile()
                override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
            },
            Locale("en_US"),
            LumaGuildsLang::class.java,
        )
        stopKoin()
        startKoin {
            modules(module {
                single { lang }
                single { guildService }
                single { memberService }
                single { bankService }
                single { configService }
            })
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        MockBukkit.unmock()
    }

    @Test
    fun `permission denial renders localized plain text`() {
        BankCreditCommand(bankService).onCommand(player, command, "bankcredit", emptyArray())

        assertEquals(
            "You don't have permission to use this command",
            localized("admin.common.no_permission"),
        )
        assertEquals(
            listOf("You don't have permission to use this command"),
            player.renderedMessages(),
        )
    }

    @Test
    fun `player-only denial renders localized plain text`() {
        val backups = mockk<VaultBackupService>(relaxed = true)

        VaultRollbackCommand(guildService, backups)
            .onCommand(server.consoleSender, command, "vaultrollback", emptyArray())

        assertEquals(
            "This command can only be used by players",
            localized("admin.common.player_only"),
        )
        assertEquals(
            listOf("This command can only be used by players"),
            server.consoleSender.renderedMessages(),
        )
    }

    @Test
    fun `successful guild balance action renders guild and balance replacements`() {
        val guild = guild("Starlight")
        every { guildService.getPlayerGuilds(player.uniqueId) } returns setOf(guild)
        every { bankService.getBalance(guild.id) } returns 12_345

        GuildCommand().onBalance(player, null)

        assertEquals(
            "[LumaGuilds] Starlight's balance: $12,345",
            localized(
                "command.guild.balance.success",
                "guild" to guild.name,
                "balance" to "12,345",
            ),
        )
        assertEquals(
            listOf("[LumaGuilds] Starlight's balance: $12,345"),
            player.renderedMessages(),
        )
    }

    @Test
    fun `bank failure renders localized failure feedback`() {
        player.addAttachment(permissionPlugin, "lumaguilds.admin.bank.credit", true)
        val target = server.addPlayer("Grace")
        every { bankService.depositPlayer(target.uniqueId, 75, any()) } returns false

        BankCreditCommand(bankService)
            .onCommand(player, command, "bankcredit", arrayOf(target.name, "75"))

        assertEquals(
            "✗ Failed to credit player",
            localized("admin.bank_credit.failure"),
        )
        assertEquals(
            listOf(
                "Crediting 75 coins to Grace...",
                "✗ Failed to credit player",
                "Check server logs for details (Vault economy may be unavailable)",
            ),
            player.renderedMessages(),
        )
    }

    @Test
    fun `war permission failure renders localized feedback`() {
        val guild = guild("Starlight")
        every { guildService.getPlayerGuilds(player.uniqueId) } returns setOf(guild)
        every {
            memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)
        } returns false

        GuildCommand().onWar(player)

        assertEquals(
            "You don't have permission to manage wars for your guild.",
            localized("command.guild.war.no_permission"),
        )
        assertEquals(
            listOf(
                "You don't have permission to manage wars for your guild.",
                "You need the DECLARE_WAR permission to access war management.",
            ),
            player.renderedMessages(),
        )
    }

    @Test
    fun `successful admin result renders amount player and balance replacements`() {
        player.addAttachment(permissionPlugin, "lumaguilds.admin.bank.credit", true)
        val target = server.addPlayer("Grace")
        every { bankService.depositPlayer(target.uniqueId, 75, any()) } returns true
        every { bankService.getPlayerBalance(target.uniqueId) } returns 1_250

        BankCreditCommand(bankService)
            .onCommand(player, command, "bankcredit", arrayOf(target.name, "75"))

        assertEquals(
            "✓ Successfully credited 75 coins to Grace",
            localized(
                "admin.bank_credit.success",
                "amount" to 75,
                "player" to target.name,
            ),
        )
        assertEquals(
            listOf(
                "Crediting 75 coins to Grace...",
                "✓ Successfully credited 75 coins to Grace",
                "New balance: 1250 coins",
            ),
            player.renderedMessages(),
        )
        assertEquals(
            listOf("✓ You have been credited 75 coins by an administrator"),
            target.renderedMessages(),
        )
    }

    @Test
    fun `unknown help topic keeps the localized help command clickable`() {
        GuildCommand().onHelp(player, "missing")

        val message = requireNotNull(player.nextComponentMessage())
        assertEquals(
            "/g help",
            message.findRunCommandClick("/g help")?.clickEvent()?.value(),
        )
    }

    @Test
    fun `unsafe home feedback translates typed safety issue and confirmation`() {
        val allowed = GuildCommand().checkHomeSafety(
            player,
            Location(null, 0.0, 64.0, 0.0),
            "/guild home confirm",
        )

        assertEquals(false, allowed)
        assertEquals(
            listOf(
                "[Warning] That home looks unsafe: Invalid world or location.",
                "Type /guild home confirm within 10s to teleport anyway.",
            ),
            player.renderedMessages(),
        )
        net.lumalyte.lg.utils.GuildHomeSafety.consumePending(player)
    }

    @Test
    fun `interactive guild tag failure is localized by command adapter`() {
        val guild = guild("Starlight")
        every { guildService.getPlayerGuilds(player.uniqueId) } returns setOf(guild)
        every {
            memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)
        } returns true

        GuildCommand().onTag(player, "<click:run_command:'/op me'>Click")

        assertEquals(
            listOf("Guild tags cannot contain interactive 'click' tags. Use colors and formatting only."),
            player.renderedMessages(),
        )
    }

    @Test
    fun `command sources contain no hardcoded legacy player text`() {
        val sourceRoot = Path.of(System.getProperty("user.dir")).resolve("src/main/kotlin/net/lumalyte/lg")
        val commandOwnedRoots = listOf(
            sourceRoot.resolve("interaction/commands"),
            sourceRoot.resolve("interaction/help"),
            sourceRoot.resolve("utils/GuildHomeSafety.kt"),
            sourceRoot.resolve("utils/GuildTagValidator.kt"),
        )

        assertEquals(
            emptyList<String>(),
            commandOwnedRoots.flatMap { LocaleSourceScanner.scan(it).playerTextCandidates }
                .map { "${it.file}:${it.line} ${it.source}" },
        )
    }

    private fun guild(name: String): Guild = Guild(
        id = UUID.randomUUID(),
        name = name,
        createdAt = Instant.parse("2026-08-20T12:00:00Z"),
    )

    private fun localized(key: String, vararg placeholders: Pair<String, Any>): String =
        plainText.serialize(lang.msg(key, *placeholders))

    private fun MessageTarget.renderedMessages(): List<String> = buildList {
        while (true) {
            val message: Component = nextComponentMessage() ?: break
            add(plainText.serialize(message))
        }
    }
}

private fun Component.allComponents(): List<Component> =
    listOf(this) + children().flatMap { it.allComponents() }

private fun Component.findRunCommandClick(command: String): Component? =
    allComponents().firstOrNull {
        it.clickEvent()?.action() == net.kyori.adventure.text.event.ClickEvent.Action.RUN_COMMAND &&
            it.clickEvent()?.value() == command
    }
