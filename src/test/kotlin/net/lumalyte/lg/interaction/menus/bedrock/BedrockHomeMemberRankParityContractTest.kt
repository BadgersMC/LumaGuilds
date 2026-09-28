package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockHomeMemberRankParityContractTest {
    private val bedrockRoot = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock")
    private val menuFactory = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt").readText()

    @Test
    fun `Bedrock home access uses native forms and shared home mutations`() {
        val homeAccess = File(bedrockRoot, "BedrockHomeAccessMenu.kt")
        val allyAccess = File(bedrockRoot, "BedrockAllyHomeAccessMenu.kt")
        assertTrue(homeAccess.exists())
        assertTrue(allyAccess.exists())
        val homeSource = homeAccess.readText()
        val allySource = allyAccess.readText()
        assertTrue(menuFactory.contains("BedrockHomeAccessMenu"))
        assertTrue(menuFactory.contains("BedrockAllyHomeAccessMenu"))
        assertTrue(homeSource.contains("guildService.setHomeAllowedRanks"))
        assertTrue(homeSource.contains("RankPermission.MANAGE_HOME"))
        assertTrue(allySource.contains("guildService.setAllyHomeAllowedGuilds"))
        assertTrue(allySource.contains("RelationType.ALLY"))
    }

    @Test
    fun `Bedrock home menu exposes both access-management routes without changing paid activation`() {
        val source = File(bedrockRoot, "BedrockGuildHomeMenu.kt").readText()
        assertTrue(source.contains("createHomeAccessMenu"))
        assertTrue(source.contains("createAllyHomeAccessMenu"))
        assertTrue(source.contains("guildCostService.activateHome"))
        assertTrue(source.contains("HomeActivationCostResult.PaymentFailed"))
    }

    @Test
    fun `selected member flow exposes rank change and kick instead of TODO fallback`() {
        val list = File(bedrockRoot, "BedrockGuildMemberListMenu.kt").readText()
        val detail = File(bedrockRoot, "BedrockGuildMemberDetailMenu.kt")
        assertTrue(detail.exists())
        val detailSource = detail.readText()
        assertFalse(list.contains("TODO: Implement detailed member selection"))
        assertTrue(list.contains("BedrockGuildMemberDetailMenu"))
        assertTrue(detailSource.contains("createGuildMemberRankMenu"))
        assertTrue(detailSource.contains("createGuildKickConfirmationMenu"))
        assertTrue(detailSource.contains("RankPermission.MANAGE_MEMBERS"))
    }

    @Test
    fun `ordinary rank edits preserve identity and priority while using complete permission model`() {
        val management = File(bedrockRoot, "BedrockGuildRankManagementMenu.kt").readText()
        val creation = File(bedrockRoot, "BedrockRankCreationMenu.kt").readText()
        val edit = File(bedrockRoot, "BedrockRankEditMenu.kt").readText()
        assertTrue(management.contains("priority = rankToEdit.priority"))
        assertFalse(management.contains("priority = newPriority"))
        assertTrue(creation.contains("RankPermission.entries"))
        assertTrue(edit.contains("RankPermission.entries"))
        assertTrue(edit.contains("rankService.setRankPermissions"))
        assertTrue(edit.contains("rankService.renameRank"))
    }
}
