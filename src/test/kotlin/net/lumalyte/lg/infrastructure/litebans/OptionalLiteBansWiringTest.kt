package net.lumalyte.lg.infrastructure.litebans

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class OptionalLiteBansWiringTest {
    @Test
    fun `core koin graph does not load LiteBans bound classes`() {
        val source = File("src/main/kotlin/net/lumalyte/lg/di/Modules.kt").readText()
        val start = source.indexOf("fun guildsModule()")
        val end = source.indexOf("fun guildClaimsIntegrationModule()", start)
        val guildsModule = source.substring(start, end)

        assertFalse(guildsModule.contains("single<net.lumalyte.lg.infrastructure.litebans.LiteBansStrikeListener>"))
        assertFalse(guildsModule.contains("single<net.lumalyte.lg.infrastructure.litebans.StrikeBackfillService>"))
    }

    @Test
    fun `LiteBans classes are constructed only inside guarded runtime hook`() {
        val source = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        val start = source.indexOf("private fun registerLiteBansStrikeHook()")
        val end = source.indexOf("private fun registerRoseChatChannels()", start)
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

    @Test
    fun `EnthusiaStaff lifecycle feed is optional and preferred over LiteBans`() {
        val pluginYml = File("src/main/resources/plugin.yml").readText()
        val source = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()

        assertTrue(pluginYml.contains("EnthusiaStaff"))
        assertFalse(Regex("""(?m)^depend:.*EnthusiaStaff""").containsMatchIn(pluginYml))
        assertTrue(source.contains("registerEnthusiaStaffStrikeFeed()"))
        assertTrue(source.contains("liteBansStrikeHookRegistered || enthusiaStaffStrikeFeed != null"))
    }

    @Test
    fun `partial startup shutdown does not hard resolve vault services`() {
        val source = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        val start = source.indexOf("override fun onDisable()")
        val shutdown = source.substring(start)

        assertFalse(shutdown.contains("get().get<net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService>()"))
        assertFalse(shutdown.contains("get().get<net.lumalyte.lg.application.services.VaultBackupService>()"))
        assertFalse(shutdown.contains("get().get<net.lumalyte.lg.infrastructure.services.VaultHologramService>()"))
        assertTrue(shutdown.contains("getOrNull<net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService>()"))
    }
}
