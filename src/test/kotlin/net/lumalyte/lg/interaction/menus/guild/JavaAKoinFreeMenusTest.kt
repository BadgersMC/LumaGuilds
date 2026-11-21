package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.interaction.menus.MenuServices
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-DI-1 umbrella regression for worker java-a: every guild menu in the partition is free of
 * `KoinComponent` and receives its collaborators through an explicit [MenuServices] constructor parameter.
 */
class JavaAKoinFreeMenusTest {
    private val partition: List<Class<*>> = listOf(
        AllianceRequestMenu::class.java,
        AlliesListMenu::class.java,
        AllyHomeAccessMenu::class.java,
        DescriptionEditorMenu::class.java,
        EnemiesListMenu::class.java,
        EnemyDeclarationMenu::class.java,
        GoldDepositMenu::class.java,
        GoldWithdrawMenu::class.java,
        GuildAchievementsMenu::class.java,
        GuildBankAutomationMenu::class.java,
        GuildBankBudgetMenu::class.java,
        GuildBankMenu::class.java,
        GuildBankSecurityMenu::class.java,
        GuildBankStatisticsMenu::class.java,
        GuildBankTransactionHistoryMenu::class.java,
        GuildBannerMenu::class.java,
        GuildChronicleMenu::class.java,
        GuildControlPanelMenu::class.java,
        GuildDashboard::class.java,
        GuildDisbandConfirmationMenu::class.java,
        GuildEmojiMenu::class.java,
        EmojiSelectionMenu::class.java,
        GuildHomeMenu::class.java,
        GuildInfoMenu::class.java,
        GuildInviteConfirmationMenu::class.java,
        GuildInviteMenu::class.java,
        GuildKickConfirmationMenu::class.java,
        GuildKickMenu::class.java,
        GuildLeaveConfirmationMenu::class.java,
        GuildListMenu::class.java,
        GuildMemberContributionsMenu::class.java,
        GuildMemberListMenu::class.java,
        GuildMemberManagementMenu::class.java,
        GuildMemberRankConfirmationMenu::class.java,
        GuildMemberRankMenu::class.java,
        GuildModeMenu::class.java,
        GuildPrestigeConfirmationMenu::class.java,
        GuildProgressionMenu::class.java,
        GuildRewardPurchaseMenu::class.java,
        GuildStatisticsMenu::class.java,
    )

    @Test
    fun everyPartitionMenuIsKoinFreeAndTakesExplicitMenuServices() {
        stopKoin()
        assertNull(GlobalContext.getOrNull(), "no global Koin may be started for this regression")
        assertEquals(40, partition.toSet().size, "39 manifest files declare 40 menu classes")
        partition.forEach { type ->
            assertFalse(KoinComponent::class.java.isAssignableFrom(type), "${type.simpleName} must not be a KoinComponent")
            assertTrue(
                type.declaredConstructors.any { MenuServices::class.java in it.parameterTypes },
                "${type.simpleName} must declare an explicit MenuServices constructor parameter",
            )
        }
    }
}
