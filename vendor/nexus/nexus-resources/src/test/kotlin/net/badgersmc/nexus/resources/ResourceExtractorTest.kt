package net.badgersmc.nexus.resources

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ResourceExtractor]. Uses an isolated [URLClassLoader] pointed
 * at a temp directory acting as a fake JAR root. This avoids the need for
 * MockBukkit and keeps the tests fast.
 */
class ResourceExtractorTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataFolder: File
    private lateinit var resourceRoot: File
    private lateinit var classLoader: URLClassLoader

    @BeforeEach
    fun setUp() {
        dataFolder = File(tempDir, "plugin-data").also { it.mkdirs() }
        resourceRoot = Files.createTempDirectory("nexus-resources-test-").toFile()
        classLoader = URLClassLoader(arrayOf<URL>(resourceRoot.toURI().toURL()), null)
    }

    @AfterEach
    fun tearDown() {
        classLoader.close()
        resourceRoot.deleteRecursively()
    }

    private fun stub(path: String, content: String) {
        val target = File(resourceRoot, path.trimStart('/'))
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    @Test
    fun `extractIfMissing copies bundled resource on first run`() {
        stub("lang/en_US.yml", "prefix: hello")
        val target = File(dataFolder, "lang/en_US.yml")

        val result = ResourceExtractor.extractIfMissing(classLoader, "lang/en_US.yml", target)

        assertNotNull(result)
        assertEquals("prefix: hello", result.readText())
    }

    @Test
    fun `extractIfMissing leaves existing user file untouched`() {
        stub("lang/en_US.yml", "prefix: shipped")
        val target = File(dataFolder, "lang/en_US.yml").apply {
            parentFile.mkdirs()
            writeText("prefix: user-edited")
        }

        val result = ResourceExtractor.extractIfMissing(classLoader, "lang/en_US.yml", target)

        assertNotNull(result)
        assertEquals("prefix: user-edited", result.readText())
    }

    @Test
    fun `extractIfMissing returns null when resource missing from classpath`() {
        val target = File(dataFolder, "nope.yml")
        val result = ResourceExtractor.extractIfMissing(classLoader, "nope.yml", target)
        assertNull(result)
        assertFalse(target.exists())
    }

    @Test
    fun `extractIfMissing tolerates leading slash in resource path`() {
        stub("lang/en_US.yml", "ok")
        val target = File(dataFolder, "lang/en_US.yml")

        val result = ResourceExtractor.extractIfMissing(classLoader, "/lang/en_US.yml", target)

        assertNotNull(result)
        assertEquals("ok", result.readText())
    }

    @Test
    fun `overwriteIfNewerVersion replaces file only when bundled is newer`() {
        stub("lang/en_US.yml", "v2")
        val target = File(dataFolder, "lang/en_US.yml").apply {
            parentFile.mkdirs()
            writeText("v1")
        }

        val wrote = ResourceExtractor.overwriteIfNewerVersion(
            classLoader, "lang/en_US.yml", target, currentVersion = 1, bundledVersion = 2
        )

        assertTrue(wrote)
        assertEquals("v2", target.readText())
    }

    @Test
    fun `overwriteIfNewerVersion skips when versions equal`() {
        stub("lang/en_US.yml", "v2")
        val target = File(dataFolder, "lang/en_US.yml").apply {
            parentFile.mkdirs()
            writeText("v1")
        }

        val wrote = ResourceExtractor.overwriteIfNewerVersion(
            classLoader, "lang/en_US.yml", target, currentVersion = 2, bundledVersion = 2
        )

        assertFalse(wrote)
        assertEquals("v1", target.readText())
    }

    @Test
    fun `overwriteIfNewerVersion skips when bundled is older`() {
        stub("lang/en_US.yml", "v1")
        val target = File(dataFolder, "lang/en_US.yml").apply {
            parentFile.mkdirs()
            writeText("v2-user")
        }

        val wrote = ResourceExtractor.overwriteIfNewerVersion(
            classLoader, "lang/en_US.yml", target, currentVersion = 2, bundledVersion = 1
        )

        assertFalse(wrote)
        assertEquals("v2-user", target.readText())
    }

    @Test
    fun `extractDirectory copies every resource under prefix preserving structure`() {
        stub("migrations/V1__init.sql", "create table foo();")
        stub("migrations/V2__shops.sql", "create table shops();")
        stub("migrations/sub/V3__nested.sql", "alter table foo();")
        stub("other/ignore.sql", "should not appear")

        val targetDir = File(dataFolder, "migrations")
        val extracted = ResourceExtractor.extractDirectory(classLoader, "migrations", targetDir)

        assertEquals(3, extracted.size)
        assertTrue(File(targetDir, "V1__init.sql").exists())
        assertTrue(File(targetDir, "V2__shops.sql").exists())
        assertTrue(File(targetDir, "sub/V3__nested.sql").exists())
        assertFalse(File(targetDir, "ignore.sql").exists())
    }

    @Test
    fun `extractDirectory skips files that already exist`() {
        stub("migrations/V1__init.sql", "new content")
        val targetDir = File(dataFolder, "migrations").also { it.mkdirs() }
        val existing = File(targetDir, "V1__init.sql").apply { writeText("user content") }

        val extracted = ResourceExtractor.extractDirectory(classLoader, "migrations", targetDir)

        assertTrue(extracted.isEmpty())
        assertEquals("user content", existing.readText())
    }
}
