package net.lumalyte.lg.infrastructure.web.handlers

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class PlayerGuildSnapshotHandlerTest {
    @Test fun `requested player lookup exports guild identity without rosters permissions or locations`() {
        val player=UUID.randomUUID();val id=UUID.randomUUID();val absent=UUID.randomUUID()
        val members=mockk<MemberService>();val guilds=mockk<GuildService>();val banners=mockk<GuildBannerService>()
        every { members.getPlayerGuilds(player) } returns setOf(id,absent)
        val guild=mockk<Guild>();every { guild.name } returns "Actual guild";every { guild.level } returns 100
        every { guilds.getGuild(id) } returns guild;every { guilds.getGuild(absent) } returns null
        every { banners.getGuildBanner(id) } returns null
        val handler=PlayerGuildSnapshotHandler(members,guilds,banners)
        val row=(handler.build(listOf(player))["items"] as List<*>).single() as Map<*,*>
        val association=(row["guildMemberships"] as List<*>).single() as Map<*,*>
        assertEquals("Actual guild",association["name"]);assertEquals(100,association["level"])
        assertEquals(setOf("id","name","level","banner"),association.keys)
        every { members.getPlayerGuilds(player) } returns emptySet()
        assertEquals(emptyList<Any>(),((handler.build(listOf(player))["items"] as List<*>).single() as Map<*,*>)["guildMemberships"])
        assertThrows(IllegalArgumentException::class.java){handler.build(listOf(player,player))}
    }
}
