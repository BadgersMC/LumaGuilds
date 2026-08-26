package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pr9TechDebtContractTest {

    @Test
    fun `dead shop integration and dependency are absent`() {
        val service = Path.of(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/ShopIntegrationService.kt"
        )
        val buildScript = Files.readString(Path.of("build.gradle.kts"))

        assertFalse(Files.exists(service), "ShopIntegrationService must remain removed")
        assertFalse(buildScript.contains("enthusiamarket-api.jar"), "Dead shop API dependency must remain removed")
    }

    @Test
    fun `Nexo glyph resolution does not reflect into FontManager`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/NexoEmojiService.kt"
        ))

        assertFalse(source.contains("getFontManager"), "FontManager must be accessed through Nexo's public API")
        assertFalse(source.contains("glyphFromName\", String::class.java"), "glyphFromName must not be invoked reflectively")
    }

    @Test
    fun `Nexo public API is declared as an optional plugin dependency`() {
        val pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml"))

        assertTrue(
            Regex("softdepend:.*\\bNexo\\b").containsMatchIn(pluginYaml),
            "Nexo must be a soft dependency so Paper exposes its API classes"
        )
    }

    @Test
    fun `tech stack does not declare the removed shop API`() {
        val techStack = Files.readString(Path.of("docs/tech-stack.md"))

        assertFalse(techStack.contains("enthusiamarket-api.jar"))
    }
}
