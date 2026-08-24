package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.infrastructure.i18n.LocaleSourceScanner
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class MenuLocalizationTest {
    private val sourceRoot = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus")

    @Test
    fun `Java menu sources contain no hardcoded player visible copy`() {
        val javaMenuRoots = listOf(
            sourceRoot.resolve("guild"),
            sourceRoot.resolve("common/ConfirmationMenu.kt"),
            sourceRoot.resolve("management"),
            sourceRoot.resolve("misc"),
        )

        val candidates = javaMenuRoots
            .map(LocaleSourceScanner::scan)
            .flatMap { it.playerTextCandidates }
            // Parties are an obsolete feature scheduled for source removal, not migration.
            .filterNot {
                val fileName = it.file.fileName.toString()
                fileName.contains("Party") || fileName == "GuildSelectionMenu.kt"
            }
            .map { "${it.file}:${it.line} ${it.source}" }

        assertEquals(emptyList<String>(), candidates, candidates.joinToString("\n"))
    }
}
