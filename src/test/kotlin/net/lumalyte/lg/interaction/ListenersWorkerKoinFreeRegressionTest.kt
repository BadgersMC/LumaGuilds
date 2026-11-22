package net.lumalyte.lg.interaction

import co.aikar.commands.BaseCommand
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener
import net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener
import net.lumalyte.lg.infrastructure.listeners.PlayerSessionListenerDependencies
import net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener
import net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener
import net.lumalyte.lg.infrastructure.listeners.VaultProtectionListenerDependencies
import net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildTeamListener
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansionDependencies
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannel
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannelDependencies
import net.lumalyte.lg.infrastructure.services.RecruitmentPresentation
import net.lumalyte.lg.infrastructure.services.RecruitmentReader
import net.lumalyte.lg.interaction.commands.AddFlagCommand
import net.lumalyte.lg.interaction.commands.BedrockCacheStatsCommand
import net.lumalyte.lg.interaction.commands.ClaimCommand
import net.lumalyte.lg.interaction.commands.ClaimCommandDependencies
import net.lumalyte.lg.interaction.commands.ClaimListCommand
import net.lumalyte.lg.interaction.commands.ClaimMenuCommand
import net.lumalyte.lg.interaction.commands.ClaimOverrideCommand
import net.lumalyte.lg.interaction.commands.DescriptionCommand
import net.lumalyte.lg.interaction.commands.FullscreenAnnounceCommand
import net.lumalyte.lg.interaction.commands.GuildCommand
import net.lumalyte.lg.interaction.commands.GuildCommandDependencies
import net.lumalyte.lg.interaction.commands.InfoCommand
import net.lumalyte.lg.interaction.commands.LumaGuildsCommand
import net.lumalyte.lg.interaction.commands.LumaGuildsCommandDependencies
import net.lumalyte.lg.interaction.commands.PartitionsCommand
import net.lumalyte.lg.interaction.commands.PartyChatCommand
import net.lumalyte.lg.interaction.commands.PartyChatCommandDependencies
import net.lumalyte.lg.interaction.commands.QuickAllyChatCommand
import net.lumalyte.lg.interaction.commands.QuickAnnounceCommand
import net.lumalyte.lg.interaction.commands.QuickGuildChatCommand
import net.lumalyte.lg.interaction.commands.QuickModChatCommand
import net.lumalyte.lg.interaction.commands.RemoveCommand
import net.lumalyte.lg.interaction.commands.RemoveFlagCommand
import net.lumalyte.lg.interaction.commands.RenameCommand
import net.lumalyte.lg.interaction.commands.TrustAllCommand
import net.lumalyte.lg.interaction.commands.TrustCommand
import net.lumalyte.lg.interaction.commands.TrustListCommand
import net.lumalyte.lg.interaction.commands.UntrustAllCommand
import net.lumalyte.lg.interaction.commands.UntrustCommand
import net.lumalyte.lg.interaction.commands.admin.BankCreditCommand
import net.lumalyte.lg.interaction.commands.admin.RemoveVaultCommand
import net.lumalyte.lg.interaction.commands.admin.VaultRollbackCommand
import net.lumalyte.lg.interaction.listeners.BannerSelectionListener
import net.lumalyte.lg.interaction.listeners.ClaimAnchorListener
import net.lumalyte.lg.interaction.listeners.ClaimAnchorListenerDependencies
import net.lumalyte.lg.interaction.listeners.ClaimDestructionListener
import net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener
import net.lumalyte.lg.interaction.listeners.CloseInventoryListener
import net.lumalyte.lg.interaction.listeners.EditToolListener
import net.lumalyte.lg.interaction.listeners.EditToolListenerDependencies
import net.lumalyte.lg.interaction.listeners.EditToolVisualisingListener
import net.lumalyte.lg.interaction.listeners.HarvestReplantListener
import net.lumalyte.lg.interaction.listeners.MoveToolListener
import net.lumalyte.lg.interaction.listeners.PartitionUpdateListener
import net.lumalyte.lg.interaction.listeners.PartyChatListener
import net.lumalyte.lg.interaction.listeners.PartyChatListenerDependencies
import net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener
import net.lumalyte.lg.interaction.listeners.ToolRemovalListener
import net.lumalyte.lg.interaction.listeners.VaultInventoryListener
import net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Umbrella regression for the `listeners` partition (REQ-NEXUS-DI-1..3): every command, listener,
 * placeholder expansion and service in the manifest receives explicit constructor dependencies, is not a
 * KoinComponent, and its source contains no Koin lookups. Koin is never started here.
 */
class ListenersWorkerKoinFreeRegressionTest {
    /** Manifest class -> constructor parameter type that proves the explicit shape. */
    private val shapes: Map<KClass<*>, KClass<*>> = mapOf(
        CombatAntiGriefListener::class to Lazy::class,
        PlayerSessionListener::class to PlayerSessionListenerDependencies::class,
        ProgressionEventListener::class to Function0::class,
        VaultProtectionListener::class to VaultProtectionListenerDependencies::class,
        WarKillTrackingListener::class to Lazy::class,
        GuildTeamListener::class to Function0::class,
        LumaGuildsExpansion::class to LumaGuildsExpansionDependencies::class,
        LumaGuildsChannel::class to LumaGuildsChannelDependencies::class,
        RecruitmentReader::class to LangService::class,
        AddFlagCommand::class to ClaimCommandDependencies::class,
        BedrockCacheStatsCommand::class to Lazy::class,
        ClaimCommand::class to ClaimCommandDependencies::class,
        ClaimListCommand::class to Lazy::class,
        ClaimMenuCommand::class to Lazy::class,
        ClaimOverrideCommand::class to Lazy::class,
        DescriptionCommand::class to ClaimCommandDependencies::class,
        FullscreenAnnounceCommand::class to Lazy::class,
        GuildCommand::class to GuildCommandDependencies::class,
        InfoCommand::class to ClaimCommandDependencies::class,
        LumaGuildsCommand::class to LumaGuildsCommandDependencies::class,
        PartitionsCommand::class to ClaimCommandDependencies::class,
        PartyChatCommand::class to PartyChatCommandDependencies::class,
        QuickAllyChatCommand::class to Lazy::class,
        QuickAnnounceCommand::class to Lazy::class,
        QuickGuildChatCommand::class to Lazy::class,
        QuickModChatCommand::class to Lazy::class,
        RemoveCommand::class to ClaimCommandDependencies::class,
        RemoveFlagCommand::class to ClaimCommandDependencies::class,
        RenameCommand::class to ClaimCommandDependencies::class,
        TrustAllCommand::class to ClaimCommandDependencies::class,
        TrustCommand::class to ClaimCommandDependencies::class,
        TrustListCommand::class to ClaimCommandDependencies::class,
        UntrustAllCommand::class to ClaimCommandDependencies::class,
        UntrustCommand::class to ClaimCommandDependencies::class,
        BankCreditCommand::class to Lazy::class,
        RemoveVaultCommand::class to Lazy::class,
        VaultRollbackCommand::class to Lazy::class,
        BannerSelectionListener::class to Lazy::class,
        ClaimAnchorListener::class to ClaimAnchorListenerDependencies::class,
        ClaimDestructionListener::class to Lazy::class,
        ClaimPvpProtectionListener::class to Lazy::class,
        CloseInventoryListener::class to Lazy::class,
        EditToolListener::class to EditToolListenerDependencies::class,
        EditToolVisualisingListener::class to Lazy::class,
        HarvestReplantListener::class to Lazy::class,
        MoveToolListener::class to Lazy::class,
        PartitionUpdateListener::class to Lazy::class,
        PartyChatListener::class to PartyChatListenerDependencies::class,
        PlayerClaimProtectionListener::class to Lazy::class,
        ToolRemovalListener::class to Lazy::class,
        VaultInventoryListener::class to Lazy::class,
        WorldClaimProtectionListener::class to Lazy::class,
    )

    /** Constructed generically: no superclass/init side effects beyond field setup. */
    private val constructible: List<KClass<*>> = shapes.keys.filterNot {
        it == ProgressionEventListener::class || it == LumaGuildsChannel::class || it == RecruitmentReader::class
    }

    @Test
    fun koinIsNotStarted() {
        assertNull(GlobalContext.getOrNull())
    }

    @Test
    fun noManifestClassIsAKoinComponent() {
        val offenders = (shapes.keys + RecruitmentPresentation::class)
            .filter { KoinComponent::class.java.isAssignableFrom(it.java) }
            .map { it.simpleName }
        assertEquals(emptyList(), offenders)
        assertEquals(53, shapes.size + 1, "every manifest file except the RecruitmentPresentation object is listed")
    }

    @Test
    fun everyManifestClassExposesItsExplicitConstructorShape() {
        val missing = shapes.filterNot { (type, parameter) ->
            type.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any { parameter.java.isAssignableFrom(it) }
            }
        }.keys.map { it.simpleName }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun recruitmentPresentationBookReceivesExplicitCollaborators() {
        val book = RecruitmentPresentation::class.java.declaredMethods.single { it.name == "book" }
        assertTrue(PlatformDetectionService::class.java in book.parameterTypes.toList())
        assertTrue(LangService::class.java in book.parameterTypes.toList())
    }

    @Test
    fun explicitConstructionPreservesOriginalFirstUseTiming() {
        constructible.forEach { type ->
            val wiring = ExplicitWiring()
            wiring.build(type)
            // Vault protection already snapshots its config in the legacy constructor; preserve that first use.
            val expected = if (type == VaultProtectionListener::class) listOf("VaultProtectionListenerDependencies.configService") else emptyList()
            assertEquals(expected, wiring.initialized(), "${type.simpleName} changed constructor resolution timing")
            assertEquals(emptyMap(), wiring.providerCalls, "${type.simpleName} invoked providers at construction")
        }
    }

    @Test
    fun manifestSourcesContainNoKoinLookups() {
        val forbidden = Regex("KoinComponent|org\\.koin|by inject|getKoin|GlobalContext")
        val files = (shapes.keys + RecruitmentPresentation::class)
            .map { type ->
                val sourceSet = if (BaseCommand::class.java.isAssignableFrom(type.java)) "test" else "main"
                val path = requireNotNull(type.qualifiedName).replace('.', '/')
                File("src/$sourceSet/kotlin/$path.kt")
            }
        assertEquals(emptyList(), files.filterNot(File::isFile).map(File::getPath), "manifest sources must exist")
        assertEquals(53, files.size)
        val offenders = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList(), offenders)
    }
}
