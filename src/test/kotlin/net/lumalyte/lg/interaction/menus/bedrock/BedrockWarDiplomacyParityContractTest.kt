package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockWarDiplomacyParityContractTest {
    private val bedrock = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock")

    @Test
    fun `Bedrock declaration exposes Java-supported objective types through WarService`() {
        val source = File(bedrock, "BedrockGuildWarDeclarationMenu.kt").readText()
        assertTrue(source.contains("ObjectiveType.KILLS"))
        assertTrue(source.contains("ObjectiveType.TIME_SURVIVAL"))
        assertTrue(source.contains("ObjectiveType.CLAIMS_CAPTURED"))
        assertTrue(source.contains("listOf(12, 24, 48, 72)"))
        assertTrue(source.contains("claimsEnabled"))
        assertTrue(source.contains("warService.getWarKillWinTarget()"))
        assertTrue(source.contains("warService.createWarDeclaration"))
    }

    @Test
    fun `war declaration authorization is checked before render and mutation`() {
        val source = File(bedrock, "BedrockGuildWarDeclarationMenu.kt").readText()
        assertTrue(source.contains("RankPermission.DECLARE_WAR"))
        assertTrue(source.indexOf("RankPermission.DECLARE_WAR") < source.indexOf("return CustomForm.builder()"))
        assertTrue(source.substringAfter("private fun handleWarDeclaration").contains("RankPermission.DECLARE_WAR"))
        assertFalse(source.contains("bankService.withdraw("))
        assertFalse(source.contains("bankService.deposit("))
    }

    @Test
    fun `war management remains authoritative for declarations history stats and wagers`() {
        val source = File(bedrock, "BedrockGuildWarManagementMenu.kt").readText()
        listOf(
            "getPendingDeclarationsForGuild",
            "getDeclarationsByGuild",
            "acceptWarDeclaration",
            "rejectWarDeclaration",
            "cancelWarDeclaration",
            "getWarHistory",
            "getWarStats",
            "getWager"
        ).forEach { call -> assertTrue(source.contains("warService.$call")) }
    }

    @Test
    fun `peace and diplomacy remain service backed`() {
        val peace = File(bedrock, "BedrockPeaceAgreementMenu.kt").readText()
        val relations = File(bedrock, "BedrockGuildRelationsMenu.kt").readText()
        assertTrue(peace.contains("warService.proposePeaceAgreement"))
        assertTrue(relations.contains("relationService.requestAlliance"))
        assertTrue(relations.contains("relationService.requestTruce"))
        assertTrue(relations.contains("relationService.acceptAlliance"))
        assertTrue(relations.contains("relationService.acceptTruce"))
        assertTrue(relations.contains("relationService.acceptUnenemy"))
        assertTrue(relations.contains("relationService.rejectRequest"))
        assertTrue(relations.contains("relationService.cancelRequest"))
    }
}
