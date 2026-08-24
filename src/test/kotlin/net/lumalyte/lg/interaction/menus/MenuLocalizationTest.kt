package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.infrastructure.i18n.LocaleSourceScanner
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MenuLocalizationTest {
    private val sourceRoot = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus")

    @Test
    fun `Java menu sources contain no hardcoded player visible copy`() {
        val javaMenuRoots = listOf(
            sourceRoot.resolve("guild"),
            sourceRoot.resolve("common/ConfirmationMenu.kt"),
            sourceRoot.resolve("management"),
            sourceRoot.resolve("misc"),
        )

        val candidates = javaMenuRoots
            .map(LocaleSourceScanner::scan)
            .flatMap { it.playerTextCandidates }
            // Parties are an obsolete feature scheduled for source removal, not migration.
            .filterNot {
                val fileName = it.file.fileName.toString()
                fileName.contains("Party") || fileName == "GuildSelectionMenu.kt"
            }
            .map { "${it.file}:${it.line} ${it.source}" }

        assertEquals(emptyList<String>(), candidates, candidates.joinToString("\n"))
    }

    @Test
    fun `Guild emoji fallbacks remain MiniMessage until the outer template renders`() {
        val source = sourceRoot.resolve("guild/GuildEmojiMenu.kt").toFile().readText()

        assertFalse(
            source.contains("lang.legacy(\"menu.guild_emoji.current.not_set\")"),
            "Rendering current.not_set to legacy before injecting it into current.value introduces § codes into MiniMessage",
        )
        assertFalse(
            source.contains("lang.legacy(\"menu.guild_emoji.input.none\")"),
            "Rendering input.none to legacy before injecting it into input.current introduces § codes into MiniMessage",
        )
    }

    @Test
    fun `emoji selector uses the matching Nexo item with a paper fallback`() {
        val source = sourceRoot.resolve("guild/GuildEmojiMenu.kt").toFile().readText()

        kotlin.test.assertTrue(source.contains("NexoItemProvider.getItemStackOrFallback(\"lg_emoji_choice_${'$'}emojiName\")"))
        kotlin.test.assertTrue(source.contains("ItemStack.of(Material.PAPER)"))
    }

    @Test
    fun `progression menu keeps nested progress values as Components`() {
        val source = sourceRoot.resolve("guild/GuildProgressionMenu.kt").toFile().readText()

        assertFalse(source.contains("lang.legacy("))
        assertFalse(source.contains("setDisplayName("))
        assertFalse(source.contains("meta.lore ="))
    }

    @Test
    fun `relation and party menus keep nested localized values as Components`() {
        val menuPaths = listOf(
            "guild/AlliesListMenu.kt",
            "guild/EnemiesListMenu.kt",
            "guild/PartyModerationMenu.kt",
            "guild/PartyCreationMenu.kt",
        )

        val offenders = menuPaths.filter { relativePath ->
            sourceRoot.resolve(relativePath).toFile().readText().contains("lang.legacy(")
        }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `description and home menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/DescriptionEditorMenu.kt",
            "guild/HomeAccessMenu.kt",
        )

        val offenders = menuPaths.filter { relativePath ->
            sourceRoot.resolve(relativePath).toFile().readText().contains("lang.legacy(")
        }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `banner menu uses component GUI localization`() {
        val source = sourceRoot.resolve("guild/GuildBannerMenu.kt").toFile().readText()

        assertFalse(source.contains("lang.legacy("))
    }

    @Test
    fun `small claim and confirmation menus use component GUI localization`() {
        val menuPaths = listOf(
            "common/ConfirmationMenu.kt",
            "management/ClaimTransferMenu.kt",
            "management/ClaimFlagMenu.kt",
            "management/ClaimIconMenu.kt",
            "management/ClaimRenamingMenu.kt",
            "management/ClaimNamingMenu.kt",
            "management/ClaimPlayerMenu.kt",
            "management/ClaimPlayerSearchMenu.kt",
            "management/ClaimWidePermissionsMenu.kt",
            "management/ClaimCreationMenu.kt",
            "management/ClaimTrustMenu.kt",
            "management/ClaimManagementMenu.kt",
            "management/ClaimTransferNamingMenu.kt",
            "guild/AllyHomeAccessMenu.kt",
            "guild/GuildDisbandConfirmationMenu.kt",
            "guild/GuildLeaveConfirmationMenu.kt",
            "guild/GuildInviteConfirmationMenu.kt",
            "guild/GuildKickConfirmationMenu.kt",
            "guild/GuildMemberListMenu.kt",
            "guild/GuildMemberManagementMenu.kt",
            "guild/GuildMemberRankConfirmationMenu.kt",
            "guild/GuildRankListMenu.kt",
            "guild/GuildRankManagementMenu.kt",
            "misc/ClaimListMenu.kt",
        )

        val offenders = menuPaths.filter { relativePath ->
            sourceRoot.resolve(relativePath).toFile().readText().contains("lang.legacy(")
        }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `all Java inventory menus use component GUI localization`() {
        val offenders = sourceRoot.toFile()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.toPath().startsWith(sourceRoot.resolve("bedrock")) }
            .filter { it.readText().contains("lang.legacy(") }
            .map { it.relativeTo(sourceRoot.toFile()).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertEquals(emptyList<String>(), offenders, "Java menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild membership action menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildKickMenu.kt",
            "guild/GuildPromotionMenu.kt",
            "guild/GuildInviteMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild selection and alliance request menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildSelectionMenu.kt",
            "guild/AllianceRequestMenu.kt",
            "guild/LfgBrowserMenu.kt",
            "guild/WarGuildSelectionMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `bank security menu uses component GUI localization`() {
        val source = sourceRoot.resolve("guild/GuildBankSecurityMenu.kt").toFile().readText()

        assertFalse(source.contains("lang.legacy("))
    }

    @Test
    fun `bank automation and budget menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildBankAutomationMenu.kt",
            "guild/GuildBankBudgetMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `bank transaction history uses component GUI localization`() {
        val source = sourceRoot.resolve("guild/GuildBankTransactionHistoryMenu.kt").toFile().readText()

        assertFalse(source.contains("lang.legacy("))
    }

    @Test
    fun `bank contribution and statistics menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildMemberContributionsMenu.kt",
            "guild/GuildBankStatisticsMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `main bank menu uses component GUI localization`() {
        val source = sourceRoot.resolve("guild/GuildBankMenu.kt").toFile().readText()

        assertFalse(source.contains("lang.legacy("))
    }

    @Test
    fun `guild diplomacy request menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/EnemyDeclarationMenu.kt",
            "guild/IncomingRequestsMenu.kt",
            "guild/OutgoingRequestsMenu.kt",
            "guild/PeaceAgreementMenu.kt",
            "guild/TruceRequestMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild war menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildWarAcceptanceMenu.kt",
            "guild/GuildWarDeclarationMenu.kt",
            "guild/GuildWarManagementMenu.kt",
            "guild/WarObjectivesSelectionMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild relation mode and strike menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildModeMenu.kt",
            "guild/GuildRelationsMenu.kt",
            "guild/GuildStrikePenaltyMenu.kt",
            "guild/GuildStrikePenaltyConfirmMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `rank editor menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildMemberRankMenu.kt",
            "guild/PermissionCategoryMenu.kt",
            "guild/RankCreationMenu.kt",
            "guild/RankEditMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild overview menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildDashboard.kt",
            "guild/GuildHomeMenu.kt",
            "guild/GuildInfoMenu.kt",
            "guild/GuildStatisticsMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild party join and moderation menus use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildPartyManagementMenu.kt",
            "guild/JoinRequirementsMenu.kt",
            "guild/PlayerModerationMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `tool and claim permission menus use component GUI localization`() {
        val menuPaths = listOf(
            "misc/EditToolMenu.kt",
            "management/ClaimPlayerPermissionsMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `guild settings and tag editor use component GUI localization`() {
        val menuPaths = listOf(
            "guild/GuildSettingsMenu.kt",
            "guild/TagEditorMenu.kt",
        )
        val offenders = menuPaths.filter { sourceRoot.resolve(it).toFile().readText().contains("lang.legacy(") }

        assertEquals(emptyList<String>(), offenders, "Menus still bypassing component GUI styling: $offenders")
    }

    @Test
    fun `Java inventory item builders do not bypass GUI styling with raw locale text`() {
        val offenders = sourceRoot.toFile()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.toPath().startsWith(sourceRoot.resolve("bedrock")) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.contains(".name(lang.raw(") || line.contains(".lore(lang.raw(")) {
                        "${file.relativeTo(sourceRoot.toFile()).invariantSeparatorsPath}:${index + 1}"
                    } else null
                }
            }
            .toList()

        assertEquals(emptyList<String>(), offenders, "Raw locale text bypasses GUI styling: $offenders")
    }

    @Test
    fun `Bedrock forms use the Bedrock GUI localization boundary`() {
        val bedrockSources = sourceRoot.resolve("bedrock").toFile()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val legacyOffenders = bedrockSources
            .filter { it.readText().contains("lang.legacy(") }
            .map { it.relativeTo(sourceRoot.toFile()).invariantSeparatorsPath }
            .sorted()
        val rawDisplayOffenders = bedrockSources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (line.contains("lang.raw(") && !line.contains("DateTimeFormatter.ofPattern")) {
                    "${file.relativeTo(sourceRoot.toFile()).invariantSeparatorsPath}:${index + 1}"
                } else null
            }
        }

        assertEquals(emptyList<String>(), legacyOffenders, "Bedrock forms still use legacy localization: $legacyOffenders")
        assertEquals(emptyList<String>(), rawDisplayOffenders, "Bedrock display text bypasses small-caps styling: $rawDisplayOffenders")
    }
}
