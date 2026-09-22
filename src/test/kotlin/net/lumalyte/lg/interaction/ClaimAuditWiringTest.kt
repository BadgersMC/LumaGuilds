package net.lumalyte.lg.interaction

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimAuditWiringTest {
    @Test
    fun `claim info resolves the claims owner rather than the viewer`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/commands/InfoCommand.kt"
        ).readText()
        assertTrue(source.contains("Bukkit.getOfflinePlayer(claim.playerId)"))
        assertFalse(source.contains("Bukkit.getOfflinePlayer(player.uniqueId)"))
    }

    @Test
    fun `death handler actually removes claim tools from drops`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/listeners/ToolRemovalListener.kt"
        ).readText()
        val handler = source.substringAfter("fun onPlayerDeath")
            .substringBefore("private fun isKeyItem")
        assertTrue(handler.contains("event.drops.removeAll"))
    }

    @Test
    fun `bedrock claim transfers use durable transfer action`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockClaimTransferMenu.kt"
        ).readText()
        assertTrue(source.contains("offerPlayerTransferRequest.execute"))
        assertFalse(source.contains("claim.transferRequests["))
    }

    @Test
    fun `claim list and partition list share bounded pagination`() {
        val claimList = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/commands/ClaimListCommand.kt"
        ).readText()
        val partitions = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/commands/PartitionsCommand.kt"
        ).readText()
        assertTrue(claimList.contains("pageBounds(playerClaims.size, page)"))
        assertTrue(partitions.contains("pageBounds(partitions.size, page)"))
        assertFalse(partitions.contains("for (i in 0..9 + page)"))
    }
}
