package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.values.ClaimPermission
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BedrockClaimPermissionEditorTest {
    private val claimId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    @Test
    fun `wide save only grants and revokes changed permissions`() {
        val grants = mutableListOf<ClaimPermission>()
        val revokes = mutableListOf<ClaimPermission>()
        val editor = BedrockClaimPermissionEditor(
            grantWide = { _, permission -> grants += permission; true },
            revokeWide = { _, permission -> revokes += permission; true },
            grantPlayer = { _, _, _ -> true },
            revokePlayer = { _, _, _ -> true }
        )

        editor.saveWide(
            claimId,
            current = setOf(ClaimPermission.BUILD, ClaimPermission.CONTAINER),
            submitted = setOf(ClaimPermission.BUILD, ClaimPermission.DOOR)
        )

        assertEquals(listOf(ClaimPermission.DOOR), grants)
        assertEquals(listOf(ClaimPermission.CONTAINER), revokes)
    }

    @Test
    fun `player save applies changes to the selected player`() {
        val grants = mutableListOf<Triple<UUID, UUID, ClaimPermission>>()
        val revokes = mutableListOf<Triple<UUID, UUID, ClaimPermission>>()
        val editor = BedrockClaimPermissionEditor(
            grantWide = { _, _ -> true },
            revokeWide = { _, _ -> true },
            grantPlayer = { claim, player, permission -> grants += Triple(claim, player, permission); true },
            revokePlayer = { claim, player, permission -> revokes += Triple(claim, player, permission); true }
        )

        editor.savePlayer(
            claimId,
            playerId,
            current = setOf(ClaimPermission.HARVEST),
            submitted = setOf(ClaimPermission.REDSTONE)
        )

        assertEquals(listOf(Triple(claimId, playerId, ClaimPermission.REDSTONE)), grants)
        assertEquals(listOf(Triple(claimId, playerId, ClaimPermission.HARVEST)), revokes)
    }

    @Test
    fun `save reports failure and stops applying later changes`() {
        val attempted = mutableListOf<ClaimPermission>()
        val editor = BedrockClaimPermissionEditor(
            grantWide = { _, permission -> attempted += permission; false },
            revokeWide = { _, permission -> attempted += permission; true },
            grantPlayer = { _, _, _ -> true },
            revokePlayer = { _, _, _ -> true }
        )

        val saved = editor.saveWide(
            claimId,
            current = setOf(ClaimPermission.CONTAINER),
            submitted = setOf(ClaimPermission.BUILD)
        )

        assertFalse(saved)
        assertEquals(1, attempted.size)
    }
}
