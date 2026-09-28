package net.lumalyte.lg.interaction.commands

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class WarCutoverAdminContractTest {
    private val source = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/commands/LumaGuildsCommand.kt"
    ).toFile().readText()

    @Test
    fun chapterWarCutoverIsExplicitConfirmedAndAdminGated() {
        assertTrue(source.contains("\"warcutover\" -> handleWarCutover"))
        assertTrue(source.contains("args[1] != \"CONFIRM\""))
        assertTrue(source.contains("sender.hasPermission(\"bellclaims.admin\")"))
        assertTrue(source.contains("warService.resetChapterCutoverState(sender.name)"))
    }
}