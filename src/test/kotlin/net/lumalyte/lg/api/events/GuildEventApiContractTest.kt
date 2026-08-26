package net.lumalyte.lg.api.events

import io.mockk.mockk
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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.reflect.KVisibility
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf

class GuildEventApiContractTest {
    private data class PayloadContract(val name: String, val type: KType)

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

    private val payloadContracts = mapOf(
        GuildBankDepositEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("playerId", typeOf<UUID>()),
            PayloadContract("amount", typeOf<Int>())
        ),
        GuildBannerChangedEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("hasActiveBanner", typeOf<Boolean>())
        ),
        GuildBannerSetEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("playerId", typeOf<UUID>())
        ),
        GuildCreatedEvent::class to listOf(
            PayloadContract("guild", typeOf<Guild>()),
            PayloadContract("ownerId", typeOf<UUID>())
        ),
        GuildDisbandedEvent::class to listOf(
            PayloadContract("guild", typeOf<Guild>()),
            PayloadContract("memberIds", typeOf<Set<UUID>>()),
            PayloadContract("actorId", typeOf<UUID>())
        ),
        GuildHomeSetEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("playerId", typeOf<UUID>())
        ),
        GuildLeaderboardRankChangeEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("leaderboardType", typeOf<ExtendedLeaderboardType>()),
            PayloadContract("period", typeOf<LeaderboardPeriod>()),
            PayloadContract("oldRank", typeOf<Int?>()),
            PayloadContract("newRank", typeOf<Int>())
        ),
        GuildLevelUpEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("newLevel", typeOf<Int>())
        ),
        GuildMemberJoinEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("playerId", typeOf<UUID>())
        ),
        GuildMemberRemovedEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("playerId", typeOf<UUID>()),
            PayloadContract("actorId", typeOf<UUID>()),
            PayloadContract("wasKicked", typeOf<Boolean>())
        ),
        GuildOwnershipTransferEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("oldOwnerId", typeOf<UUID>()),
            PayloadContract("newOwnerId", typeOf<UUID>())
        ),
        GuildRelationChangeEvent::class to listOf(
            PayloadContract("guild1", typeOf<UUID>()),
            PayloadContract("guild2", typeOf<UUID>()),
            PayloadContract("newRelationType", typeOf<RelationType>()),
            PayloadContract("relation", typeOf<Relation>())
        ),
        GuildTrackingChangedEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("enabled", typeOf<Boolean>())
        ),
        GuildVaultPlacedEvent::class to listOf(
            PayloadContract("guildId", typeOf<UUID>()),
            PayloadContract("playerId", typeOf<UUID>())
        ),
        GuildWarDeclaredEvent::class to listOf(
            PayloadContract("declaringGuildId", typeOf<UUID>()),
            PayloadContract("defendingGuildId", typeOf<UUID>()),
            PayloadContract("actorId", typeOf<UUID>())
        ),
        GuildWarEndEvent::class to listOf(
            PayloadContract("warId", typeOf<UUID>()),
            PayloadContract("winnerGuildId", typeOf<UUID?>()),
            PayloadContract("loserGuildId", typeOf<UUID?>()),
            PayloadContract("declaringGuildId", typeOf<UUID>()),
            PayloadContract("defendingGuildId", typeOf<UUID>())
        ),
        GuildWarKillEvent::class to listOf(
            PayloadContract("warId", typeOf<UUID>()),
            PayloadContract("killerId", typeOf<UUID>()),
            PayloadContract("victimId", typeOf<UUID>()),
            PayloadContract("killerGuildId", typeOf<UUID>()),
            PayloadContract("victimGuildId", typeOf<UUID>())
        )
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
        eventInstances().forEach { event ->
            val eventType = event.javaClass
            val staticHandlers = eventType.getMethod("getHandlerList").invoke(null)
            assertIs<HandlerList>(staticHandlers)
            assertSame(staticHandlers, event.handlers, eventType.name)
            assertFalse(event.isAsynchronous, eventType.name)
        }
    }

    @Test
    fun `public guild event constructor signatures remain compatible`() {
        constructorSignatures.forEach { (eventType, expected) ->
            assertEquals(expected, eventType.constructors.single().parameterTypes.toList(), eventType.name)
        }
    }

    @Test
    fun `public guild event payload properties and nullability remain compatible`() {
        payloadContracts.forEach { (eventType, expected) ->
            val constructor = requireNotNull(eventType.primaryConstructor)
            val properties = eventType.memberProperties.associateBy { it.name }

            assertEquals(expected.map { it.name }, constructor.parameters.map { it.name }, eventType.qualifiedName)
            expected.forEach { (name, type) ->
                val property = requireNotNull(properties[name]) { "${eventType.qualifiedName}.$name" }
                assertEquals(KVisibility.PUBLIC, property.visibility, "${eventType.qualifiedName}.$name")
                assertEquals(type, property.returnType, "${eventType.qualifiedName}.$name")
                assertEquals(type, constructor.parameters.single { it.name == name }.type, "${eventType.qualifiedName}.$name")
            }
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

    private fun eventInstances(): List<Event> {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val third = UUID.randomUUID()
        val fourth = UUID.randomUUID()
        val fifth = UUID.randomUUID()
        val guild = mockk<Guild>()

        return listOf(
            GuildBankDepositEvent(first, second, 1),
            GuildBannerChangedEvent(first, true),
            GuildBannerSetEvent(first, second),
            GuildCreatedEvent(guild, first),
            GuildDisbandedEvent(guild, setOf(first), second),
            GuildHomeSetEvent(first, second),
            GuildLeaderboardRankChangeEvent(
                first,
                ExtendedLeaderboardType.entries.first(),
                LeaderboardPeriod.entries.first(),
                null,
                1
            ),
            GuildLevelUpEvent(first, 1),
            GuildMemberJoinEvent(first, second),
            GuildMemberRemovedEvent(first, second, third, true),
            GuildOwnershipTransferEvent(first, second, third),
            GuildRelationChangeEvent(first, second, RelationType.entries.first(), mockk<Relation>()),
            GuildTrackingChangedEvent(first, true),
            GuildVaultPlacedEvent(first, second),
            GuildWarDeclaredEvent(first, second, third),
            GuildWarEndEvent(first, null, null, fourth, fifth),
            GuildWarKillEvent(first, second, third, fourth, fifth)
        )
    }
}
