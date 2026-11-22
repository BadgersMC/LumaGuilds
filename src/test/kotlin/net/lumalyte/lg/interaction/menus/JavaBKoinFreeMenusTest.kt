package net.lumalyte.lg.interaction.menus

import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-DI-1 umbrella regression for worker java-b: every class in the partition is Koin-free and
 * receives its collaborators through an explicit `services: MenuServices` constructor parameter.
 */
class JavaBKoinFreeMenusTest {
    private val partition = listOf(
        "guild.GuildPartyManagementMenu",
        "guild.GuildPromotionMenu",
        "guild.GuildRankListMenu",
        "guild.GuildRankManagementMenu",
        "guild.GuildRecruitmentMenu",
        "guild.GuildRelationBrowserMenu",
        "guild.GuildRelationsMenu",
        "guild.GuildSelectionMenu",
        "guild.GuildSettingsMenu",
        "guild.GuildStallMenu",
        "guild.GuildStrikePenaltyConfirmMenu",
        "guild.GuildStrikePenaltyMenu",
        "guild.GuildWarAcceptanceMenu",
        "guild.GuildWarDeclarationMenu",
        "guild.GuildWarManagementMenu",
        "guild.HomeAccessMenu",
        "guild.IncomingRequestsMenu",
        "guild.JoinRequirementsMenu",
        "guild.LfgBrowserMenu",
        "guild.OutgoingRequestsMenu",
        "guild.PartyModerationMenu",
        "guild.PeaceAgreementMenu",
        "guild.PermissionCategoryMenu",
        "guild.PlayerChatSettingsMenu",
        "guild.PlayerModerationMenu",
        "guild.RankCreationMenu",
        "guild.RankEditMenu",
        "guild.RecruitmentController",
        "guild.TagEditorMenu",
        "guild.TruceRequestMenu",
        "guild.WarGuildSelectionMenu",
        "guild.WarObjectivesSelectionMenu",
        "management.ClaimCreationMenu",
        "management.ClaimFlagMenu",
        "management.ClaimIconMenu",
        "management.ClaimManagementMenu",
        "management.ClaimNamingMenu",
        "management.ClaimPlayerPermissionsMenu",
        "management.ClaimPlayerSearchMenu",
        "management.ClaimRenamingMenu",
        "management.ClaimTransferMenu",
        "management.ClaimTransferNamingMenu",
        "management.ClaimWidePermissionsMenu",
        "misc.ClaimListMenu",
        "misc.EditToolMenu",
    ).map { Class.forName("net.lumalyte.lg.interaction.menus.$it") }

    @Test
    fun partitionCoversTheWholeManifest() {
        assertEquals(45, partition.size)
        assertEquals(45, partition.toSet().size)
    }

    @Test
    fun noPartitionClassIsAKoinComponent() {
        assertNull(GlobalContext.getOrNull(), "regression must not rely on a started global Koin")
        val offenders = partition.filter { KoinComponent::class.java.isAssignableFrom(it) }.map { it.simpleName }
        assertEquals(emptyList(), offenders, "classes still implementing KoinComponent")
    }

    @Test
    fun everyPartitionClassTakesExplicitMenuServices() {
        val missing = partition.filterNot { type ->
            type.declaredConstructors.any { ctor -> ctor.parameterTypes.any { it == MenuServices::class.java } }
        }.map { it.simpleName }
        assertEquals(emptyList(), missing, "classes without a MenuServices constructor parameter")
    }

    @Test
    fun servicesParameterIsNamedServices() {
        partition.forEach { type ->
            val ctor = type.kotlin.constructors.first { c -> c.parameters.any { it.type.classifier == MenuServices::class } }
            val parameter = ctor.parameters.single { it.type.classifier == MenuServices::class }
            assertEquals("services", parameter.name, type.simpleName)
            assertTrue(!parameter.isOptional, "${type.simpleName}.services must be required")
        }
    }
}
