package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.values.ChatChannel
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class ChatServiceInviteBlockTest {
    private val sender = UUID.randomUUID()
    private val blocker = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val partyId = UUID.randomUUID()

    private fun service(blocks: InviteBlockService?): ChatServiceBukkit {
        val settings =
            mockk<ChatSettingsRepository> {
                every { getVisibilitySettings(any()) } answers { mockk { every { partyChatVisible } returns true } }
            }
        val preferences =
            mockk<PlayerPartyPreferenceRepository> {
                every { getByPlayerId(sender) } returns
                    mockk { every { partyId } returns this@ChatServiceInviteBlockTest.partyId }
            }
        val parties =
            mockk<PartyRepository> {
                every { getById(partyId) } returns
                    mockk {
                        every { isActive() } returns true
                        every { id } returns partyId
                    }
            }
        val partyService =
            mockk<PartyService> { every { getOnlinePartyMembers(partyId) } returns setOf(sender, blocker, other) }
        return ChatServiceBukkit(
            settings, mockk(), mockk(), mockk(), partyService, mockk(), mockk(), mockk(),
            preferences, parties, mockk(), mockk(), blocks,
        )
    }

    /** Verifies party chat skips members who blocked the sender. */
    @Test
    fun partyFiltersBlockedSender() {
        val blocks =
            mockk<InviteBlockService> {
                every { hidesChat(any(), any()) } answers {
                    firstArg<UUID>() == blocker && secondArg<UUID>() == sender
                }
            }
        assertEquals(setOf(sender, other), service(blocks).getRecipientsForChannel(sender, ChatChannel.PARTY))
        assertEquals(setOf(sender, blocker, other), service(null).getRecipientsForChannel(sender, ChatChannel.PARTY))
    }
}
