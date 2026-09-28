package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildBankLayoutContractTest {
    private val source = Paths.get("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankMenu.kt")
        .toAbsolutePath().normalize().toFile().readText()

    @Test
    fun `main bank shell is four rows and keeps controls inside it`() {
        assertTrue(source.contains("ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4"))
        assertTrue(source.contains("StaticPane(0, 0, 9, 4"))
        assertFalse(Regex("""mainPane\.addItem\([^;]*,\s*\d+,\s*[4-9]\)""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source))
    }
}
