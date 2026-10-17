package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class WarKillLifecycleContractTest {
    private val source = Files.readString(Path.of(
        "src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/WarKillTrackingListener.kt"
    ))

    @Test
    fun `decisive kill resolves only after kill side effects`() {
        val handler = source.substringAfter("fun onPlayerDeath(event: PlayerDeathEvent)")
        val resolve = handler.indexOf("resolveReachedKillTarget")
        val event = handler.indexOf("GuildWarKillEvent(")
        val xp = handler.indexOf("awardWarKillExperience")
        val killerMessage = handler.indexOf("notification.war.kill.killer")
        val victimMessage = handler.indexOf("notification.war.kill.victim")

        assertTrue(resolve > event, "War must remain active while GuildWarKillEvent is fired")
        assertTrue(resolve > xp, "War must remain active while kill XP is awarded")
        assertTrue(resolve > killerMessage, "War must remain active while killer feedback is sent")
        assertTrue(resolve > victimMessage, "War must remain active while victim feedback is sent")
    }

    @Test
    fun `war expiry maintenance has a production scheduler`() {
        val pluginSource = Files.readString(Path.of(
            "src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt"
        ))

        assertTrue(
            pluginSource.contains("warService.processExpiredWars()"),
            "Expired war processing must be scheduled in production, not only exposed as a dead service method"
        )
    }
}
