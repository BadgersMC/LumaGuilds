package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-NEXUS-DI-1/5: only the transitional composition roots may access the legacy container. */
class KoinFreeProductionSourceTest {
    private val root = File("src/main/kotlin/net/lumalyte/lg")
    private val legacyRoots = setOf("LumaGuilds.kt", "di/Modules.kt", "di/LegacyMenuServices.kt")
    private val forbidden = Regex("""\bKoinComponent\b|by\s+inject\b|org\.koin\.core\.component\.inject|\bgetKoin\s*\(|GlobalContext|KoinJavaComponent""")

    @Test
    fun `Bedrock forms do not inherit a legacy container fallback`() {
        assertFalse(org.koin.core.component.KoinComponent::class.java.isAssignableFrom(
            net.lumalyte.lg.interaction.menus.bedrock.BaseBedrockMenu::class.java,
        ))
    }

    @Test
    fun `production features do not resolve the global legacy container`() {
        assertTrue(root.isDirectory, "Production source directory must exist: ${root.absolutePath}")
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "The migration check must scan production Kotlin files")
        val offenders = files.filterNot { it.relativeTo(root).invariantSeparatorsPath in legacyRoots }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbidden.containsMatchIn(line)) "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
                }
            }
        assertEquals(emptyList(), offenders, "Legacy lookups remain:\n${offenders.joinToString("\n")}")
    }
}
