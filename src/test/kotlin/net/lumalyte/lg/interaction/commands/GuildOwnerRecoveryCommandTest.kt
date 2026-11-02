package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.RemoteConsoleCommandSender
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercise the complete confirmation contract against the real language bundle.
 * Descriptive regression names, JUnit lifecycle fields, and literal command/expiry fixtures keep
 * each case readable; related permission and confirmation cases intentionally share this fixture.
 */
@Suppress("FunctionMaxLength", "LateinitUsage", "MagicNumber", "StringLiteralDuplication", "TooManyFunctions")
internal class GuildOwnerRecoveryCommandTest {
    @TempDir
    lateinit var dataFolder: Path

    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var plugin: Plugin
    private lateinit var members: MemberService
    private lateinit var command: GuildOwnerRecoveryCommand
    private lateinit var preview: OwnerRecoveryPreview
    private var clock = Instant.EPOCH
    private var issuedTokens = 0L
    private val audits = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        player = server.addPlayer("RecoveryAdmin")
        plugin = mockk(relaxed = true)
        every { plugin.isEnabled } returns true
        members = mockk()
        preview = createPreview()
        every { members.previewOwnerRecovery(preview.guildId, preview.member.playerId) } returns
            OwnerRecoveryPreviewResult.Ready(preview)
        every { members.recoverOwnership(preview) } returns OwnerRecoveryResult.Recovered(preview)
        command =
            GuildOwnerRecoveryCommand(
                members, createLanguage(), { clock }, { UUID(0, ++issuedTokens).toString() }, audits::add,
            )
    }

    private fun createPreview(): OwnerRecoveryPreview {
        val guildId = UUID.randomUUID()
        return OwnerRecoveryPreview(
            guildId,
            "Example Guild",
            Member(UUID.randomUUID(), guildId, UUID.randomUUID(), Instant.EPOCH),
            "Co-Owner",
            UUID.randomUUID(),
            "Owner",
        )
    }

    private fun createLanguage(): LangService {
        return LangService(
            object : LangHost {
                override val dataFolder: File = this@GuildOwnerRecoveryCommandTest.dataFolder.toFile()
                override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
            },
            Locale("en_US"),
            LumaGuildsLang::class.java,
        )
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun previewShowsExactRecoveryAndDoesNotMutate() {
        authorize()
        command.execute(player, previewArgs())

        verify(exactly = 1) { members.previewOwnerRecovery(preview.guildId, preview.member.playerId) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        val display = playerMessages().joinToString("\n")
        assertPreviewDetails(display)
        assertFalse(display.contains("\\n"), "Preview must render line breaks, not backslash-n")
        assertEquals(1, audits.size)
        assertTrue(audits.single().contains("outcome=PREVIEW"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["none", "base", "specialist", "override"])
    fun bothAdministrativePermissionsAreRequired(permissionSet: String) {
        if (permissionSet == "base" || permissionSet == "override") grant(GuildOwnerRecoveryCommand.BASE_PERMISSION)
        if (permissionSet == "specialist") grant(GuildOwnerRecoveryCommand.PERMISSION)
        if (permissionSet == "override") grant("bellclaims.admin")

        command.execute(player, previewArgs())
        command.execute(player, confirmArgs())

        verify(exactly = 0) { members.previewOwnerRecovery(any(), any()) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertTrue(playerMessages().all { it.contains("permission") })
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-uuid", "1-1-1-1-1", "", "00000000-0000-0000-0000-000000000000-extra"])
    fun malformedGuildOrMemberUuidCannotReachStorage(value: String) {
        authorize()
        command.execute(player, arrayOf("owner", "recover", value, preview.member.playerId.toString()))
        command.execute(player, arrayOf("owner", "recover", preview.guildId.toString(), value))

        verify(exactly = 0) { members.previewOwnerRecovery(any(), any()) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertTrue(playerMessages().all { it.contains("Usage:") })
    }

    @Test
    fun unsupportedVerbsAndArgumentCountsCannotReachStorage() {
        authorize()
        val inputs =
            listOf(
                arrayOf("owner"),
                arrayOf("owner", "recover"),
                previewArgs().copyOf().also { it[1] = "set" },
                previewArgs() + "confirm",
                confirmArgs().copyOf().also { it[4] = "force" },
                confirmArgs() + "extra",
            )
        inputs.forEach { command.execute(player, it) }

        verify(exactly = 0) { members.previewOwnerRecovery(any(), any()) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertEquals(inputs.size, playerMessages().size)
    }

    @Test
    fun uppercaseCanonicalUuidsResolveToTheSameMember() {
        authorize()
        command.execute(player, previewArgs().map { it.uppercase() }.toTypedArray())
        verify(exactly = 1) { members.previewOwnerRecovery(preview.guildId, preview.member.playerId) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
    }

    @Test
    fun matchingConfirmationCommitsOnceAndLogsExactActorAndRanks() {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        clock = clock.plusSeconds(59)
        command.execute(player, confirmArgs())

        verify(exactly = 1) { members.recoverOwnership(preview) }
        assertTrue(playerMessages().single().contains("Recovered ownership"))
        assertRecoveryAudit()

        command.execute(player, confirmArgs())
        verify(exactly = 1) { members.recoverOwnership(preview) }
        assertInvalidConfirmation()
    }

    @Test
    fun consoleCanPreviewAndConfirmAnOfflineMember() {
        val console = server.consoleSender
        command.execute(console, previewArgs())
        verify(exactly = 0) { members.recoverOwnership(any()) }
        while (console.nextMessage() != null) { /* Drain the preview. */ }

        command.execute(console, confirmArgs())

        verify(exactly = 1) { members.recoverOwnership(preview) }
        assertTrue(console.nextMessage()?.contains("Recovered ownership") == true)
        assertTrue(audits.any { it.contains("actor=CONSOLE") && it.contains("outcome=RECOVERED") })
        assertEquals(null, server.getPlayer(preview.member.playerId))
    }

    @Test
    fun confirmationCannotBeUsedByAnotherAdministrator() {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        val other = server.addPlayer("OtherAdmin")
        authorize(other)
        command.execute(other, confirmArgs())

        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertTrue(other.nextMessage()?.contains("Confirmation is missing") == true)
        command.execute(player, confirmArgs())
        verify(exactly = 1) { members.recoverOwnership(preview) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["guild", "member", "token"])
    fun confirmationCannotChangeTheApprovedTargetOrToken(changed: String) {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        val wrong = confirmArgs()
        val changedIndex =
            when (changed) {
                "guild" -> 2
                "member" -> 3
                else -> 5
            }
        wrong[changedIndex] = UUID.randomUUID().toString()
        command.execute(player, wrong)

        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertInvalidConfirmation()
        command.execute(player, confirmArgs())
        verify(exactly = 1) { members.recoverOwnership(preview) }
    }

    @Test
    fun confirmationWithoutPreviewIsRejected() {
        authorize()
        command.execute(player, confirmArgs())
        verify(exactly = 0) { members.previewOwnerRecovery(any(), any()) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertInvalidConfirmation()
    }

    @ParameterizedTest
    @ValueSource(longs = [60, 61])
    fun confirmationExpiresAtSixtySeconds(seconds: Long) {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        clock = clock.plusSeconds(seconds)
        command.execute(player, confirmArgs())

        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertInvalidConfirmation()
    }

    @Test
    fun newPreviewInvalidatesPreviousConfirmation() {
        authorize()
        command.execute(player, previewArgs())
        command.execute(player, previewArgs())
        playerMessages()
        command.execute(player, confirmArgs())
        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertInvalidConfirmation()

        command.execute(player, confirmArgs(token(2)))
        verify(exactly = 1) { members.recoverOwnership(preview) }
    }

    @Test
    fun permissionLossConsumesPendingRecoveryEvenIfPermissionIsLaterRestored() {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        player.addAttachment(plugin, GuildOwnerRecoveryCommand.PERMISSION, false)
        command.execute(player, confirmArgs())
        assertTrue(playerMessages().single().contains("permission"))
        grant(GuildOwnerRecoveryCommand.PERMISSION)
        command.execute(player, confirmArgs())

        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertInvalidConfirmation()
    }

    @ParameterizedTest
    @EnumSource(OwnerRecoveryRejection::class)
    fun previewRejectionsHaveReadableMessagesAndCannotBeConfirmed(reason: OwnerRecoveryRejection) {
        authorize()
        every { members.previewOwnerRecovery(preview.guildId, preview.member.playerId) } returns
            OwnerRecoveryPreviewResult.Rejected(reason)
        command.execute(player, previewArgs())
        val message = playerMessages().single()
        assertTrue(message.isNotBlank())
        assertFalse(message.contains("admin.owner_recovery"), "Every rejection needs a translated message")
        assertFalse(message.contains("Recovered ownership"))
        command.execute(player, confirmArgs())

        verify(exactly = 0) { members.recoverOwnership(any()) }
        assertInvalidConfirmation()
        assertTrue(audits.any { it.contains("outcome=PREVIEW_REJECTED_$reason") })
    }

    @ParameterizedTest
    @EnumSource(
        value = OwnerRecoveryRejection::class,
        names = ["ALREADY_OWNED", "STALE_PREVIEW", "STORAGE_FAILURE"],
    )
    fun commitRejectionIsNotSuccessAndConsumesConfirmation(reason: OwnerRecoveryRejection) {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        every { members.recoverOwnership(preview) } returns OwnerRecoveryResult.Rejected(reason)
        command.execute(player, confirmArgs())

        verify(exactly = 1) { members.recoverOwnership(preview) }
        assertFalse(playerMessages().single().contains("Recovered ownership"))
        assertTrue(audits.any { it.contains("outcome=REJECTED_$reason") })
        assertTrue(audits.none { it.contains("outcome=RECOVERED") })
        command.execute(player, confirmArgs())
        verify(exactly = 1) { members.recoverOwnership(preview) }
        assertInvalidConfirmation()
    }

    @Test
    fun previewStorageExceptionCreatesNoConfirmation() {
        authorize()
        every { members.previewOwnerRecovery(preview.guildId, preview.member.playerId) } throws
            IllegalStateException("Unavailable")
        command.execute(player, previewArgs())
        assertTrue(playerMessages().single().contains("Could not confirm"))
        command.execute(player, confirmArgs())

        verify(exactly = 0) { members.recoverOwnership(any()) }
        verify(exactly = 1) { members.previewOwnerRecovery(preview.guildId, preview.member.playerId) }
        assertInvalidConfirmation()
    }

    @Test
    fun commitExceptionReportsUncertaintyWithoutRetryingOrReusingConfirmation() {
        authorize()
        command.execute(player, previewArgs())
        playerMessages()
        every { members.recoverOwnership(preview) } throws IllegalStateException("Commit outcome unavailable")
        command.execute(player, confirmArgs())
        val message = playerMessages().single()
        assertTrue(message.contains("Could not confirm"))
        assertTrue(message.contains("current ownership"))
        assertFalse(message.contains("No recovery was performed"))
        assertTrue(audits.any { it.contains("outcome=STORAGE_FAILURE_IllegalStateException") })
        assertTrue(audits.none { it.contains("outcome=RECOVERED") })
        command.execute(player, confirmArgs())

        verify(exactly = 1) { members.recoverOwnership(preview) }
        assertInvalidConfirmation()
    }

    @Test
    fun aCommandBlockCannotRecoverOwnershipEvenWithAdministrativePermissions() {
        val block = mockk<BlockCommandSender>(relaxed = true)
        every { block.hasPermission(any<String>()) } returns true
        command.execute(block, previewArgs())

        verify(exactly = 0) { members.previewOwnerRecovery(any(), any()) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        verify { block.sendMessage(match<Component> { isSenderRestriction(it) }) }
    }

    @Test
    fun aRemoteConsoleCannotRecoverOwnershipEvenWithAdministrativePermissions() {
        val remote = mockk<RemoteConsoleCommandSender>(relaxed = true)
        every { remote.hasPermission(any<String>()) } returns true
        command.execute(remote, previewArgs())
        command.execute(remote, confirmArgs())

        verify(exactly = 0) { members.previewOwnerRecovery(any(), any()) }
        verify(exactly = 0) { members.recoverOwnership(any()) }
        verify(exactly = 2) {
            remote.sendMessage(match<Component> { isSenderRestriction(it) })
        }
    }

    private fun isSenderRestriction(message: Component): Boolean {
        val text = PlainTextComponentSerializer.plainText().serialize(message)
        return text.contains("player administrator") && text.contains("console")
    }

    private fun assertPreviewDetails(display: String) {
        val expectedValues =
            listOf(
                preview.guildName,
                preview.guildId.toString(),
                preview.member.playerId.toString(),
                preview.currentRankName,
                preview.member.rankId.toString(),
                preview.ownerRankName,
                preview.ownerRankId.toString(),
                "Current Owners:",
                "no changes made",
                "60 seconds",
                token(1),
            )
        for (expected in expectedValues) {
            assertTrue(display.contains(expected), "Preview is missing $expected")
        }
    }

    private fun assertRecoveryAudit() {
        val committed = audits.single { it.contains("outcome=RECOVERED") }
        val expectedValues =
            listOf(
                "actor=PLAYER:${player.uniqueId}",
                "guild=${preview.guildId}",
                "member=${preview.member.playerId}",
                "oldRank=${preview.member.rankId}",
                "ownerRank=${preview.ownerRankId}",
            )
        for (expected in expectedValues) assertTrue(committed.contains(expected))
        assertTrue(audits.none { it.contains(token(1)) }, "Confirmation tokens must not appear in audit logs")
    }

    private fun authorize(target: PlayerMock = player) {
        target.addAttachment(plugin, GuildOwnerRecoveryCommand.BASE_PERMISSION, true)
        target.addAttachment(plugin, GuildOwnerRecoveryCommand.PERMISSION, true)
    }

    private fun grant(permission: String) {
        player.addAttachment(plugin, permission, true)
    }

    private fun previewArgs(): Array<String> =
        arrayOf("owner", "recover", preview.guildId.toString(), preview.member.playerId.toString())

    private fun confirmArgs(confirmationToken: String = token(1)): Array<String> =
        previewArgs() + arrayOf("confirm", confirmationToken)

    private fun token(number: Long): String = UUID(0, number).toString()

    private fun playerMessages(): List<String> = generateSequence { player.nextMessage() }.toList()

    private fun assertInvalidConfirmation() {
        assertTrue(playerMessages().single().contains("Confirmation is missing"))
    }
}
