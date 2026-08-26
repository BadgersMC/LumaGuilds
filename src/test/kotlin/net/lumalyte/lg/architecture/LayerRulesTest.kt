package net.lumalyte.lg.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayerRulesTest {

    private val forbiddenDomainPrefixes = setOf(
        "org.bukkit",
        "org.koin",
        "co.aikar",
        "net.kyori"
    )

    @Test
    fun `domain layer depends on nothing outside domain and kotlin stdlib`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("domain", "net.lumalyte.lg.domain..")
            val application = Layer("application", "net.lumalyte.lg.application..")
            val infrastructure = Layer("infrastructure", "net.lumalyte.lg.infrastructure..")
            domain.dependsOnNothing()
        }
    }

    @Test
    fun `application layer depends only on domain`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("domain", "net.lumalyte.lg.domain..")
            val application = Layer("application", "net.lumalyte.lg.application..")
            val infrastructure = Layer("infrastructure", "net.lumalyte.lg.infrastructure..")
            application.dependsOn(domain)
        }
    }

    @Test
    fun `infrastructure layer depends only on application and domain`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("domain", "net.lumalyte.lg.domain..")
            val application = Layer("application", "net.lumalyte.lg.application..")
            val infrastructure = Layer("infrastructure", "net.lumalyte.lg.infrastructure..")
            infrastructure.dependsOn(application, domain)
        }
    }

    @Test
    fun `domain source imports no forbidden framework packages`() {
        val domainRoot = Path.of("src/main/kotlin/net/lumalyte/lg/domain")
        val violations = mutableListOf<String>()

        Files.walk(domainRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { path ->
                    Files.readAllLines(path)
                        .map(String::trim)
                        .filter { it.startsWith("import ") }
                        .map { it.removePrefix("import ").substringBefore(" as ") }
                        .filter { imported ->
                            forbiddenDomainPrefixes.any { prefix ->
                                imported == prefix || imported.startsWith("$prefix.")
                            }
                        }
                        .forEach { imported -> violations += "$path imports $imported" }
                }
        }

        assertTrue(violations.isEmpty(), violations.sorted().joinToString("\n"))
    }

    @Test
    fun `implementation guide lists the executable forbidden prefixes`() {
        val guide = Files.readString(Path.of("docs/implementation.md"))
        val documented = Regex("(?ms)forbidden:\\s*\\n((?:\\s+- [^\\n]+\\n?)+)")
            .find(guide)
            ?.groupValues
            ?.get(1)
            ?.lineSequence()
            ?.map { it.trim().removePrefix("- ") }
            ?.filter(String::isNotBlank)
            ?.toSet()

        assertEquals(forbiddenDomainPrefixes, documented)
    }
}
