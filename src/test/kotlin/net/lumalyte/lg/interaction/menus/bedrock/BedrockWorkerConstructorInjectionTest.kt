package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.mockk
import io.mockk.mockkClass
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import java.util.UUID
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Umbrella regression for every class in the bedrock worker manifest (REQ-NEXUS-DI-1):
 * no direct KoinComponent supertype, a required non-null `dependencies: BedrockMenuDependencies`
 * constructor parameter forwarded to BaseBedrockMenu, and no delegated property that reaches Koin.
 * Koin is never started, so any remaining `by inject()` delegate fails when its Lazy is read.
 */
class BedrockWorkerConstructorInjectionTest {
    private val forms: List<KClass<out BaseBedrockMenu>> = listOf(
        BedrockAllyHomeAccessMenu::class,
        BedrockClaimFlagMenu::class,
        BedrockClaimIconMenu::class,
        BedrockClaimListMenu::class,
        BedrockClaimManagementMenu::class,
        BedrockClaimNamingMenu::class,
        BedrockClaimPlayerPermissionsMenu::class,
        BedrockClaimTransferMenu::class,
        BedrockClaimTrustMenu::class,
        BedrockClaimWidePermissionsMenu::class,
        BedrockConfirmationMenu::class,
        BedrockDescriptionEditorMenu::class,
        BedrockEditToolMenu::class,
        BedrockEmojiSelectionMenu::class,
        BedrockGuildBankAutomationMenu::class,
        BedrockGuildBankBudgetMenu::class,
        BedrockGuildBankMenu::class,
        BedrockGuildBankSecurityMenu::class,
        BedrockGuildBankStatisticsMenu::class,
        BedrockGuildBankTransactionHistoryMenu::class,
        BedrockGuildBannerMenu::class,
        BedrockGuildControlPanelMenu::class,
        BedrockGuildDisbandConfirmationMenu::class,
        BedrockGuildEmojiMenu::class,
        BedrockGuildGettingStartedMenu::class,
        BedrockGuildHomeMenu::class,
        BedrockGuildInfoMenu::class,
        BedrockGuildInviteConfirmationMenu::class,
        BedrockGuildInviteMenu::class,
        BedrockGuildKickConfirmationMenu::class,
        BedrockGuildKickMenu::class,
        BedrockGuildLeaderboardMenu::class,
        BedrockGuildLeaveConfirmationMenu::class,
        BedrockGuildListMenu::class,
        BedrockGuildMemberContributionsMenu::class,
        BedrockGuildMemberDetailMenu::class,
        BedrockGuildMemberListMenu::class,
        BedrockGuildMemberManagementMenu::class,
        BedrockGuildMemberRankConfirmationMenu::class,
        BedrockGuildMemberRankMenu::class,
        BedrockGuildModeMenu::class,
        BedrockGuildPartyManagementMenu::class,
        BedrockGuildProgressionInfoMenu::class,
        BedrockGuildPromotionMenu::class,
        BedrockGuildQuestLeaderboardMenu::class,
        BedrockGuildQuestsMenu::class,
        BedrockGuildRankListMenu::class,
        BedrockGuildRankManagementMenu::class,
        BedrockGuildRecruitmentMenu::class,
        BedrockGuildRelationBrowserMenu::class,
        BedrockGuildRelationsMenu::class,
        BedrockGuildSelectionMenu::class,
        BedrockGuildSettingsMenu::class,
        BedrockGuildStatisticsMenu::class,
        BedrockGuildWarDeclarationMenu::class,
        BedrockGuildWarManagementMenu::class,
        BedrockHomeAccessMenu::class,
        BedrockJoinRequirementsMenu::class,
        BedrockLfgBrowserMenu::class,
        BedrockPartyCreationMenu::class,
        BedrockPeaceAgreementMenu::class,
        BedrockPlayerChatSettingsMenu::class,
        BedrockPrestigeConfirmationMenu::class,
        BedrockPrestigeSelectionMenu::class,
        BedrockRankCreationMenu::class,
        BedrockRankEditMenu::class,
        BedrockRewardPurchaseMenu::class,
        BedrockTagEditorMenu::class,
    )

    @Test
    fun `manifest lists all 68 forms plus BedrockFormUtils`() {
        assertTrue(forms.size == 68, "expected 68 Bedrock forms, got ${forms.size}")
        assertTrue(forms.toSet().size == forms.size)
    }

    @Test
    fun `BedrockFormUtils is a stateless object that is not a KoinComponent`() {
        assertFalse(KoinComponent::class.java.isAssignableFrom(BedrockFormUtils::class.java))
        assertTrue(BedrockFormUtils::class.java.declaredFields.none { it.name.endsWith("\$delegate") })
        assertTrue(BedrockFormUtils::class.java.declaredFields.none { LangService::class.java.isAssignableFrom(it.type) })
        listOf("createSectionHeader", "createErrorLabel", "createSuccessLabel", "createInfoLabel").forEach { name ->
            val method = BedrockFormUtils::class.java.methods.firstOrNull { it.name == name }
            assertNotNull(method, name)
            assertSame(LangService::class.java, method.parameterTypes.first(), "$name receives LangService explicitly")
        }
    }

    @TestFactory
    fun `every Bedrock form takes explicit dependencies and never reaches Koin`(): List<DynamicTest> =
        forms.map { form -> DynamicTest.dynamicTest(form.simpleName!!) { verifyForm(form) } }

    private fun verifyForm(form: KClass<out BaseBedrockMenu>) {
        assertNull(GlobalContext.getOrNull(), "Koin must not be started")
        assertFalse(KoinComponent::class.java in form.java.interfaces, "${form.simpleName} declares KoinComponent")

        val constructor = assertNotNull(form.primaryConstructor)
        val dependencyParameter = constructor.parameters.firstOrNull { it.name == "dependencies" }
            ?: fail("${form.simpleName} has no dependencies constructor parameter")
        assertEquals(BedrockMenuDependencies::class.createType(), dependencyParameter.type, "${form.simpleName} dependencies must be required")
        assertFalse(dependencyParameter.isOptional, "${form.simpleName} dependencies must not default")

        val fixture = BedrockWorkerFixture()
        val deps = fixture.deps()
        constructor.isAccessible = true
        val args = constructor.parameters
            .filter { !it.isOptional || it == dependencyParameter }
            .associateWith { argumentFor(it, fixture, deps) }
        val menu = constructor.callBy(args)

        assertSame(deps, menu.storedDependencies(), "${form.simpleName} must forward dependencies to BaseBedrockMenu")
        generateSequence<Class<*>>(form.java) { it.superclass }
            .takeWhile { it != BaseBedrockMenu::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.name.endsWith("\$delegate") && Lazy::class.java.isAssignableFrom(it.type) }
            .forEach { field ->
                field.isAccessible = true
                val lazy = field.get(menu) as Lazy<*>
                try {
                    lazy.value
                } catch (error: Throwable) {
                    val koin = generateSequence(error) { it.cause }
                        .any { cause -> cause.stackTrace.any { it.className.startsWith("org.koin") } || cause.message?.contains("Koin") == true }
                    assertFalse(koin, "${form.simpleName}.${field.name} resolved through Koin: $error")
                }
            }
    }

    private fun argumentFor(parameter: KParameter, fixture: BedrockWorkerFixture, deps: BedrockMenuDependencies): Any? {
        val type = parameter.type.classifier as KClass<*>
        return when (type) {
            BedrockMenuDependencies::class -> deps
            MenuNavigator::class -> mockk<MenuNavigator>(relaxed = true)
            Player::class -> fixture.player
            Logger::class -> Logger.getAnonymousLogger()
            Guild::class -> Guild(UUID.randomUUID(), "Worker Guild", createdAt = java.time.Instant.EPOCH)
            UUID::class -> UUID.randomUUID()
            String::class -> "value"
            Int::class -> 0
            MutableSet::class, Set::class -> mutableSetOf<UUID>()
            Function0::class -> { -> Unit }
            else -> if (type.java.isEnum) type.java.enumConstants.first() else mockkClass(type, relaxed = true)
        }
    }
}
