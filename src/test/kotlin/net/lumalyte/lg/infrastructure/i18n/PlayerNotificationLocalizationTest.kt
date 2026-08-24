package net.lumalyte.lg.infrastructure.i18n

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class PlayerNotificationLocalizationTest {
    private val sourceRoot = Path.of(System.getProperty("user.dir")).resolve("src/main/kotlin")

    @Test
    fun `active notification adapters contain no hardcoded player copy`() {
        val activeAdapters = listOf(
            "net/lumalyte/lg/infrastructure/listeners/VaultProtectionListener.kt",
            "net/lumalyte/lg/infrastructure/listeners/WarKillTrackingListener.kt",
            "net/lumalyte/lg/infrastructure/services/ChatServiceBukkit.kt",
            "net/lumalyte/lg/infrastructure/services/TeleportationService.kt",
            "net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkit.kt",
            "net/lumalyte/lg/interaction/listeners/GuildChatListener.kt",
            "net/lumalyte/lg/interaction/listeners/BannerSelectionListener.kt",
            "net/lumalyte/lg/utils/GuildDisplayUtils.kt",
            "net/lumalyte/lg/utils/MenuItemBuilder.kt",
        )

        val candidates = activeAdapters
            .map(sourceRoot::resolve)
            .map(LocaleSourceScanner::scan)
            .flatMap { it.playerTextCandidates }
            .map { "${it.file}:${it.line} ${it.source}" }

        assertEquals(emptyList<String>(), candidates, candidates.joinToString("\n"))
    }

    @Test
    fun `non-player section signs have exact file classifications`() {
        val expected = mapOf(
            "net/lumalyte/lg/infrastructure/services/ChatServiceBukkit.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/infrastructure/placeholders/LumaGuildsExpansion.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/infrastructure/persistence/migrations/GuildNameSanitizer.kt" to PlayerTextClassification.PERSISTENCE_LITERAL,
            "net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt" to PlayerTextClassification.PERSISTENCE_LITERAL,
            "net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/interaction/menus/bedrock/BedrockTagEditorMenu.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/utils/ColorCodeUtils.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/utils/GuildDisplayUtils.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/utils/GuildResolver.kt" to PlayerTextClassification.COLOR_CODE_UTILITY,
            "net/lumalyte/lg/utils/MenuTitleBuilder.kt" to PlayerTextClassification.GLYPH_MARKUP,
        )
        expected.forEach { (file, classification) ->
            assertEquals(classification, LocaleSourceScanner.classificationFor(sourceRoot.resolve(file)), file)
        }
        assertEquals(
            null,
            LocaleSourceScanner.classificationFor(sourceRoot.resolve("net/lumalyte/lg/infrastructure/services/TeleportationService.kt")),
        )
    }
}
