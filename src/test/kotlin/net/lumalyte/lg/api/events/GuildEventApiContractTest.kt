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
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

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

    private val payloadContracts = mapOf(
        GuildBankDepositEvent::class to listOf("guildId" to false, "playerId" to false, "amount" to false),
        GuildBannerChangedEvent::class to listOf("guildId" to false, "hasActiveBanner" to false),
        GuildBannerSetEvent::class to listOf("guildId" to false, "playerId" to false),
        GuildCreatedEvent::class to listOf("guild" to false, "ownerId" to false),
        GuildDisbandedEvent::class to listOf("guild" to false, "memberIds" to false, "actorId" to false),
        GuildHomeSetEvent::class to listOf("guildId" to false, "playerId" to false),
        GuildLeaderboardRankChangeEvent::class to listOf(
            "guildId" to false,
            "leaderboardType" to false,
            "period" to false,
            "oldRank" to true,
            "newRank" to false
        ),
        GuildLevelUpEvent::class to listOf("guildId" to false, "newLevel" to false),
        GuildMemberJoinEvent::class to listOf("guildId" to false, "playerId" to false),
        GuildMemberRemovedEvent::class to listOf(
            "guildId" to false,
            "playerId" to false,
            "actorId" to false,
            "wasKicked" to false
        ),
        GuildOwnershipTransferEvent::class to listOf("guildId" to false, "oldOwnerId" to false, "newOwnerId" to false),
        GuildRelationChangeEvent::class to listOf(
            "guild1" to false,
            "guild2" to false,
            "newRelationType" to false,
            "relation" to false
        ),
        GuildTrackingChangedEvent::class to listOf("guildId" to false, "enabled" to false),
        GuildVaultPlacedEvent::class to listOf("guildId" to false, "playerId" to false),
        GuildWarDeclaredEvent::class to listOf(
            "declaringGuildId" to false,
            "defendingGuildId" to false,
            "actorId" to false
        ),
        GuildWarEndEvent::class to listOf(
            "warId" to false,
            "winnerGuildId" to true,
            "loserGuildId" to true,
            "declaringGuildId" to false,
            "defendingGuildId" to false
        ),
        GuildWarKillEvent::class to listOf(
            "warId" to false,
            "killerId" to false,
            "victimId" to false,
            "killerGuildId" to false,
            "victimGuildId" to false
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

            assertEquals(expected.map { it.first }, constructor.parameters.map { it.name }, eventType.qualifiedName)
            expected.forEach { (name, nullable) ->
                val property = requireNotNull(properties[name]) { "${eventType.qualifiedName}.$name" }
                assertEquals(KVisibility.PUBLIC, property.visibility, "${eventType.qualifiedName}.$name")
                assertEquals(nullable, property.returnType.isMarkedNullable, "${eventType.qualifiedName}.$name")
                assertEquals(
                    constructor.parameters.single { it.name == name }.type,
                    property.returnType,
                    "${eventType.qualifiedName}.$name"
                )
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
