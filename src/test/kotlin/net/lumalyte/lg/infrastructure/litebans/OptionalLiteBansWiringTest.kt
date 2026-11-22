package net.lumalyte.lg.infrastructure.litebans

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class OptionalLiteBansWiringTest {
    @Test
    fun `core Nexus graph does not load LiteBans bound classes`() {
        val bindings = File("src/main/kotlin/net/lumalyte/lg/di/nexus").listFiles()!!
            .filter { it.extension == "kt" }
        assertTrue(bindings.isNotEmpty())
        bindings.forEach { file ->
            val source = file.readText()
            assertFalse("LiteBansStrikeListener(" in source, file.name)
            assertFalse("StrikeBackfillService(" in source, file.name)
        }
    }

    @Test
    fun `LiteBans classes are constructed only inside guarded runtime hook`() {
        val source = File("src/main/kotlin/net/lumalyte/lg/di/runtime/GuildListenerRegistration.kt").readText()
        val start = source.indexOf("private fun registerLiteBansStrikeHook()")
        val end = source.indexOf("private fun parsePriority(", start)
        val hook = source.substring(start, end)

        assertTrue(hook.contains("liteBansStrikeHookRegistered || enthusiaStaffStrikeFeed != null"))
        assertTrue(hook.contains("LiteBansStrikeListener("))
        assertTrue(hook.contains("StrikeBackfillService("))
        assertTrue(hook.contains("catch (e: LinkageError)"))
    }

    @Test
    fun `LiteBans remains a soft dependency`() {
        val pluginYml = File("src/main/resources/plugin.yml").readText()
        assertTrue(pluginYml.contains("softdepend:"))
        assertTrue(pluginYml.contains("LiteBans"))
        assertFalse(Regex("""(?m)^depend:.*LiteBans""").containsMatchIn(pluginYml))
    }

    /** Staff remains optional and takes precedence over legacy LiteBans wiring. */
    @DisplayName("EnthusiaStaff lifecycle feed is optional and preferred over LiteBans")
    @Test
    fun optionalStaffPreferred() {
        val pluginYml = File("src/main/resources/plugin.yml").readText()
        val source = File("src/main/kotlin/net/lumalyte/lg/di/runtime/GuildListenerRegistration.kt").readText()

        assertTrue(pluginYml.contains("EnthusiaStaff"))
        assertFalse(Regex("""(?m)^depend:.*EnthusiaStaff""").containsMatchIn(pluginYml))
        assertTrue(source.contains("registerEnthusiaStaffStrikeFeed()"))
        assertTrue(source.contains("private var enthusiaStaffStrikeFeed: AutoCloseable? = null"))
        assertTrue(source.contains("liteBansStrikeHookRegistered || enthusiaStaffStrikeFeed != null"))
    }

    @Test
    fun `partial startup shutdown does not hard resolve vault services`() {
        // Shutdown only releases what startup acquired: onDisable resolves nothing, and each vault
        // stop is recorded after its start (GuildRuntimeLifecycleTest proves partial-start release).
        val plugin = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        val disable = plugin.substringAfter("override fun onDisable()").substringBefore("fun initConfig()")
        assertFalse(disable.contains("graph()"))
        assertFalse(disable.contains("getBean"))

        val schedules = File("src/main/kotlin/net/lumalyte/lg/di/runtime/GuildScheduleRegistration.kt").readText()
        listOf(
            "autoSave.start()" to "\"vault auto-save\") { autoSave.stop() }",
            "backups.startAutoBackup(" to "\"vault backups\") { backups.stopAutoBackup() }",
            "holograms.start()" to "\"vault holograms\") { holograms.stop() }",
        ).forEach { (start, stop) ->
            assertTrue(schedules.indexOf(start) in 0 until schedules.indexOf(stop), "$stop must be recorded after $start")
        }
    }
}
