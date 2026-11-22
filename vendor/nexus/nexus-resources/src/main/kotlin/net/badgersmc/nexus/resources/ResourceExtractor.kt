package net.badgersmc.nexus.resources

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * One-shot resource extraction helpers. Designed for the recurring "copy bundled
 * default to data folder if user hasn't customised it yet" pattern that every
 * Nexus-based plugin re-implements (lang files, default configs, migration SQL).
 *
 * **Never overwrites an existing user file** unless the caller explicitly opts in
 * via [overwriteIfNewerVersion].
 *
 * Every public function comes in two flavours:
 *
 * - `extractIfMissing(plugin, ...)` — convenience for the common case.
 * - `extractIfMissing(classLoader, dataFolder, ...)` — primary form, also used
 *   by unit tests where instantiating a real [JavaPlugin] is impossible.
 */
object ResourceExtractor {

    // ------------------------------------------------------------------
    // JavaPlugin convenience overloads
    // ------------------------------------------------------------------

    fun extractIfMissing(
        plugin: JavaPlugin,
        resourcePath: String,
        target: File = defaultTarget(plugin, resourcePath)
    ): File? = extractIfMissing(plugin.javaClass.classLoader, resourcePath, target)

    fun extractDirectory(
        plugin: JavaPlugin,
        resourcePrefix: String,
        targetDir: File
    ): List<File> = extractDirectory(plugin.javaClass.classLoader, resourcePrefix, targetDir, pluginJar(plugin))

    fun overwriteIfNewerVersion(
        plugin: JavaPlugin,
        resourcePath: String,
        target: File,
        currentVersion: Int,
        bundledVersion: Int
    ): Boolean = overwriteIfNewerVersion(plugin.javaClass.classLoader, resourcePath, target, currentVersion, bundledVersion)

    fun defaultTarget(plugin: JavaPlugin, resourcePath: String): File {
        return File(plugin.dataFolder, resourcePath.trimStart('/'))
    }

    // ------------------------------------------------------------------
    // Primary (classloader-driven) API
    // ------------------------------------------------------------------

    /**
     * Extract [resourcePath] into [target] **only if [target] does not yet
     * exist**. Returns the [target] file, or `null` when the resource is
     * missing from the classpath.
     */
    fun extractIfMissing(
        classLoader: ClassLoader,
        resourcePath: String,
        target: File
    ): File? {
        if (target.exists()) return target
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Failed to create directory ${parent.absolutePath}")
        }
        val stream = openResource(classLoader, resourcePath) ?: return null
        stream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    /**
     * Extract every classpath resource whose path starts with [resourcePrefix]
     * into [targetDir], preserving directory structure under that prefix.
     * Existing files are skipped — pair with [overwriteIfNewerVersion] for
     * opt-in overwrites.
     *
     * [explicitJar] short-circuits the (usually correct) attempt to locate the
     * caller's JAR via its [ProtectionDomain][java.security.ProtectionDomain].
     */
    fun extractDirectory(
        classLoader: ClassLoader,
        resourcePrefix: String,
        targetDir: File,
        explicitJar: File? = null
    ): List<File> {
        val normalisedPrefix = resourcePrefix.trim('/')
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IllegalStateException("Failed to create directory ${targetDir.absolutePath}")
        }

        val jarFile = explicitJar ?: locateClassLoaderJar(classLoader)
        if (jarFile != null && jarFile.isFile) {
            return extractDirectoryFromJar(jarFile, normalisedPrefix, targetDir)
        }
        return extractDirectoryFromExplodedClasspath(classLoader, normalisedPrefix, targetDir)
    }

    /**
     * Overwrite [target] with the bundled resource only when [bundledVersion]
     * is strictly greater than [currentVersion]. Intended for templated
     * defaults the consumer updates with a version bump.
     */
    fun overwriteIfNewerVersion(
        classLoader: ClassLoader,
        resourcePath: String,
        target: File,
        currentVersion: Int,
        bundledVersion: Int
    ): Boolean {
        if (bundledVersion <= currentVersion) return false
        val stream = openResource(classLoader, resourcePath) ?: return false
        target.parentFile?.mkdirs()
        stream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun openResource(classLoader: ClassLoader, resourcePath: String): InputStream? {
        val cleaned = resourcePath.trimStart('/')
        return classLoader.getResourceAsStream(cleaned)
            ?: classLoader.getResourceAsStream("/$cleaned")
    }

    private fun pluginJar(plugin: JavaPlugin): File? = try {
        val location = plugin.javaClass.protectionDomain.codeSource?.location ?: return null
        val file = File(location.toURI())
        if (file.isFile) file else null
    } catch (_: Exception) {
        null
    }

    private fun locateClassLoaderJar(classLoader: ClassLoader): File? = try {
        // We pick a known Nexus resource to discover the JAR URL. If the loader
        // doesn't have it, we have no fast path — that's fine, we'll fall back
        // to the exploded-classpath walker.
        val probe = classLoader.getResource("net/badgersmc/nexus/resources/ResourceExtractor.class")
            ?: return null
        urlToJarFile(probe)
    } catch (_: Exception) {
        null
    }

    private fun urlToJarFile(url: URL): File? {
        val raw = url.toString()
        val jarPart = when {
            raw.startsWith("jar:") -> raw.substringAfter("jar:").substringBefore("!/")
            raw.endsWith(".jar") -> raw
            else -> return null
        }
        val cleaned = jarPart.removePrefix("file:")
        return try {
            File(URLDecoder.decode(cleaned, StandardCharsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDirectoryFromJar(
        jarFile: File,
        prefix: String,
        targetDir: File
    ): List<File> {
        val targetRoot = targetDir.canonicalFile
        return ZipFile(jarFile).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.startsWith("$prefix/") || it.name == prefix }
                .mapNotNull { entry ->
                    val relative = entry.name.removePrefix("$prefix/")
                    if (relative.isEmpty()) return@mapNotNull null
                    val outFile = safeChild(targetRoot, relative) ?: return@mapNotNull null
                    if (outFile.exists()) return@mapNotNull null
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile
                }
                .toList()
        }
    }

    /**
     * Resolve [relative] under [root] and reject any path that escapes the
     * root directory via `..` segments or absolute components. Defends against
     * crafted zip entries (Zip Slip).
     */
    private fun safeChild(root: File, relative: String): File? {
        if (relative.startsWith("/") || relative.startsWith("\\")) return null
        val candidate = File(root, relative).canonicalFile
        return if (candidate.toPath().startsWith(root.toPath())) candidate else null
    }

    /**
     * Fallback used in unit tests (and during development when classes run from
     * an exploded `build/classes/` directory). We look for the prefix as a
     * resource URL and, when it resolves to a `file:` location, walk the
     * directory and copy non-directory files preserving structure.
     */
    private fun extractDirectoryFromExplodedClasspath(
        classLoader: ClassLoader,
        prefix: String,
        targetDir: File
    ): List<File> =
        classLoader.getResources(prefix).toList()
            .filter { it.protocol == "file" }
            .mapNotNull { url -> runCatching { File(url.toURI()) }.getOrNull() }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown().filter { it.isFile }.mapNotNull { source ->
                    val relative = source.relativeTo(root).path.replace(File.separatorChar, '/')
                    val outFile = File(targetDir, relative)
                    if (outFile.exists()) return@mapNotNull null
                    outFile.parentFile?.mkdirs()
                    source.inputStream().use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile
                }.toList()
            }
}
