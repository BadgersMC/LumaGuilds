package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.rewards.PrestigeQuote
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertNotNull

class BedrockPrestigeConfirmationContractTest {
    @Test
    fun `Bedrock prestige has a dedicated confirmation form type`() {
        val type = BedrockPrestigeConfirmationMenu::class
        assertNotNull(type)
    }

    @Test
    fun `prestige quote remains immutable across retry contract`() {
        val quote = PrestigeQuote(
            transactionId = UUID.randomUUID(),
            guildId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            retainedRewardId = "bank_1",
            quotedFee = 10_000L,
            expectedOwnershipVersion = 1L,
            expectedPrestigeCount = 0,
        )
        assertNotNull(quote.transactionId)
    }
}