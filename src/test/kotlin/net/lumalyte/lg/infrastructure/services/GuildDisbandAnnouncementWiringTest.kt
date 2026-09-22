package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class GuildDisbandAnnouncementWiringTest {
    @Test
    fun `disband event carries immutable pre-delete relation snapshot without constructor change`() {
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Snapshot",
            createdAt = Instant.EPOCH,
        )
        val relatedId = UUID.randomUUID()
        val source = mutableMapOf(relatedId to RelationType.ALLY)
        val event = GuildDisbandedEvent(guild, emptySet(), UUID.randomUUID())

        event.attachRelatedGuilds(source)
        source[relatedId] = RelationType.ENEMY

        assertEquals(mapOf(relatedId to RelationType.ALLY), event.relatedGuilds)
        assertEquals(3, GuildDisbandedEvent::class.java.constructors.single().parameterCount)
    }

    @Test
    fun `guild service snapshots relations before committed deletion and attaches them to event`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt"
        ).readText()
        val disband = source
            .substringAfter("override fun disbandGuild")
            .substringBefore("override fun renameGuild")

        val snapshotIndex = disband.indexOf("relationRepository.getByGuild(guildId)")
        val deleteIndex = disband.indexOf("guildRepository.removeWithCreationCooldown")
        val attachIndex = disband.indexOf("disbandedEvent.attachRelatedGuilds(relatedGuilds)")

        assertTrue(snapshotIndex >= 0)
        assertTrue(deleteIndex > snapshotIndex)
        assertTrue(attachIndex > deleteIndex)
        assertTrue(disband.contains("RelationType.ALLY"))
        assertTrue(disband.contains("RelationType.ENEMY"))
    }

    @Test
    fun `disband listener delegates chat and relationship notifications to announcement service`() {
        val listener = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/GuildDisbandedListener.kt"
        ).readText()

        assertTrue(
            listener.contains(
                "announcementService.announce(event.guild, event.memberIds, event.relatedGuilds)"
            )
        )
    }

    @Test
    fun `relationship toast path is edition agnostic`() {
        val service = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildDisbandAnnouncementServiceBukkit.kt"
        ).readText()

        assertTrue(service.contains("toastSender.show("))
        assertFalse(service.contains("Floodgate"))
        assertFalse(service.contains("isBedrock"))
        assertFalse(service.contains("JavaPlayer"))
    }

    @Test
    fun `dependency injection wires disband announcement service`() {
        val modules = File(
            "src/main/kotlin/net/lumalyte/lg/di/Modules.kt"
        ).readText()

        assertTrue(modules.contains("GuildDisbandAnnouncementServiceBukkit("))
        assertTrue(modules.contains("GuildDisbandedListener(get(), get(), get(), get())"))
    }
}
