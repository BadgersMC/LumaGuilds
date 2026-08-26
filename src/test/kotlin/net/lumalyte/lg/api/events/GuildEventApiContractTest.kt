package net.lumalyte.lg.api.events

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuildEventApiContractTest {
    private val eventTypes = listOf(
        GuildBankDepositEvent::class.java,
        GuildBannerChangedEvent::class.java,
        GuildBannerSetEvent::class.java,
        GuildCreatedEvent::class.java,
        GuildDisbandedEvent::class.java,
        GuildHomeSetEvent::class.java,
        GuildLeaderboardRankChangeEvent::class.java,
        GuildLevelUpEvent::class.java,
        GuildMemberJoinEvent::class.java,
        GuildMemberRemovedEvent::class.java,
        GuildOwnershipTransferEvent::class.java,
        GuildRelationChangeEvent::class.java,
        GuildTrackingChangedEvent::class.java,
        GuildVaultPlacedEvent::class.java,
        GuildWarDeclaredEvent::class.java,
        GuildWarEndEvent::class.java,
        GuildWarKillEvent::class.java
    )

    private val constructorSignatures = mapOf(
        GuildBankDepositEvent::class.java to listOf(UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType),
        GuildBannerChangedEvent::class.java to listOf(UUID::class.java, Boolean::class.javaPrimitiveType),
        GuildBannerSetEvent::class.java to listOf(UUID::class.java, UUID::class.java),
        GuildCreatedEvent::class.java to listOf(Guild::class.java, UUID::class.java),
        GuildDisbandedEvent::class.java to listOf(Guild::class.java, Set::class.java, UUID::class.java),
        GuildHomeSetEvent::class.java to listOf(UUID::class.java, UUID::class.java),
        GuildLeaderboardRankChangeEvent::class.java to listOf(
            UUID::class.java,
            ExtendedLeaderboardType::class.java,
            LeaderboardPeriod::class.java,
            Int::class.javaObjectType,
            Int::class.javaPrimitiveType
        ),
        GuildLevelUpEvent::class.java to listOf(UUID::class.java, Int::class.javaPrimitiveType),
        GuildMemberJoinEvent::class.java to listOf(UUID::class.java, UUID::class.java),
        GuildMemberRemovedEvent::class.java to listOf(
            UUID::class.java,
            UUID::class.java,
            UUID::class.java,
            Boolean::class.javaPrimitiveType
        ),
        GuildOwnershipTransferEvent::class.java to listOf(UUID::class.java, UUID::class.java, UUID::class.java),
        GuildRelationChangeEvent::class.java to listOf(
            UUID::class.java,
            UUID::class.java,
            RelationType::class.java,
            Relation::class.java
        ),
        GuildTrackingChangedEvent::class.java to listOf(UUID::class.java, Boolean::class.javaPrimitiveType),
        GuildVaultPlacedEvent::class.java to listOf(UUID::class.java, UUID::class.java),
        GuildWarDeclaredEvent::class.java to listOf(UUID::class.java, UUID::class.java, UUID::class.java),
        GuildWarEndEvent::class.java to List(5) { UUID::class.java },
        GuildWarKillEvent::class.java to List(5) { UUID::class.java }
    )

    @Test
    fun `all public guild events remain synchronous Bukkit events`() {
        eventTypes.forEach { eventType ->
            assertTrue(Event::class.java.isAssignableFrom(eventType), eventType.name)
            assertFalse(Cancellable::class.java.isAssignableFrom(eventType), eventType.name)
        }
    }

    @Test
    fun `every public guild event exposes one shared handler list`() {
        eventTypes.forEach { eventType ->
            val staticHandlers = eventType.getMethod("getHandlerList").invoke(null)
            val instanceHandlers = eventType.getMethod("getHandlers")
            assertIs<HandlerList>(staticHandlers)
            assertEquals(HandlerList::class.java, instanceHandlers.returnType)
        }
    }

    @Test
    fun `public guild event constructor signatures remain compatible`() {
        constructorSignatures.forEach { (eventType, expected) ->
            assertEquals(expected, eventType.constructors.single().parameterTypes.toList(), eventType.name)
        }
    }

    @Test
    fun `public guild events remain synchronous`() {
        val eventRoot = Path.of("src/main/kotlin/net/lumalyte/lg/api/events")
        Files.walk(eventRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith("Event.kt") }
                .forEach { path -> assertFalse(Files.readString(path).contains("Event(true)"), path.toString()) }
        }
    }
}
