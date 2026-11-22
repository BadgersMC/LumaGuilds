package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral proof per Bedrock family (REQ-NEXUS-DI-1..3): forms built by MenuFactory or by a parent
 * form receive the factory's explicit bundle, use the injected MenuServices instances, resolve them only
 * on first use, and keep their navigation targets. Koin is never started.
 */
class BedrockWorkerFamilyBehaviorTest {
    private val guild = Guild(UUID.randomUUID(), "Worker Guild", createdAt = java.time.Instant.EPOCH)
    private val logger = Logger.getAnonymousLogger()

    private fun navigatorCapturing(): Pair<MenuNavigator, MutableList<Menu>> {
        val opened = mutableListOf<Menu>()
        val navigator = mockk<MenuNavigator>(relaxed = true)
        every { navigator.openMenu(capture(opened)) } returns Unit
        return navigator to opened
    }

    @Test
    fun `bank family - factory form uses injected BankService lazily`() {
        assertNull(GlobalContext.getOrNull())
        val bank = mockk<BankService>(relaxed = true)
        every { bank.getBalance(guild.id) } returns 250
        val fixture = BedrockWorkerFixture(mapOf(BankService::class to bank))
        assertEquals(0, fixture.bedrockProviderCalls, "no Bedrock bundle at factory construction")

        val menu = assertIs<BedrockGuildBankMenu>(fixture.factory.createGuildBankMenu(mockk(relaxed = true), fixture.player, guild))
        assertSame(fixture.deps(), menu.storedDependencies())
        assertEquals(1, fixture.bedrockProviderCalls)
        assertFalse(menu.lazyDelegate("bankService").isInitialized(), "BankService must resolve on first use")

        menu.getForm()
        assertTrue(menu.lazyDelegate("bankService").isInitialized())
        verify(exactly = 1) { bank.getBalance(guild.id) }
    }

    @Test
    fun `claims family - factory claim list uses injected ListPlayerClaims`() {
        val listClaims = mockk<ListPlayerClaims>()
        every { listClaims.execute(any()) } returns emptyList()
        val fixture = BedrockWorkerFixture(mapOf(ListPlayerClaims::class to listClaims))

        val menu = assertIs<BedrockClaimListMenu>(fixture.factory.createClaimListMenu(mockk(relaxed = true), fixture.player))
        assertSame(fixture.deps(), menu.storedDependencies())
        assertFalse(menu.lazyDelegate("listPlayerClaims").isInitialized())
        menu.getForm()
        verify(exactly = 1) { listClaims.execute(fixture.playerId) }
    }

    @Test
    fun `members family - management forwards bundle to every child form`() {
        val members = mockk<MemberService>(relaxed = true)
        every { members.getGuildMembers(guild.id) } returns emptySet()
        val fixture = BedrockWorkerFixture(mapOf(MemberService::class to members))
        val (navigator, opened) = navigatorCapturing()
        val menu = BedrockGuildMemberManagementMenu(navigator, fixture.player, guild, logger, fixture.deps())

        listOf("0", "1", "2", "3", "4").forEach { menu.getForm().respond(it) }

        assertEquals(
            listOf(
                BedrockGuildMemberListMenu::class, BedrockGuildInviteMenu::class, BedrockGuildKickMenu::class,
                BedrockGuildPromotionMenu::class, BedrockGuildPromotionMenu::class,
            ),
            opened.map { it::class },
        )
        opened.forEach { assertSame(fixture.deps(), (it as BaseBedrockMenu).storedDependencies()) }
        verify(atLeast = 1) { members.getGuildMembers(guild.id) }
    }

    @Test
    fun `control panel opens direct child forms with the same bundle`() {
        val fixture = BedrockWorkerFixture()
        val (navigator, opened) = navigatorCapturing()
        val menu = BedrockGuildControlPanelMenu(navigator, fixture.player, guild, logger, fixture.deps())

        menu.getForm().respond("4")
        menu.getForm().respond("6")

        assertIs<BedrockGuildMemberListMenu>(opened[0])
        assertIs<BedrockGuildBankMenu>(opened[1])
        opened.forEach { assertSame(fixture.deps(), (it as BaseBedrockMenu).storedDependencies()) }
    }

    @Test
    fun `wars family - inline and declaration forms keep the explicit bundle`() {
        val wars = mockk<WarService>(relaxed = true)
        every { wars.getWarsForGuild(guild.id) } returns emptyList()
        val fixture = BedrockWorkerFixture(mapOf(WarService::class to wars))
        val (navigator, opened) = navigatorCapturing()
        val menu = BedrockGuildWarManagementMenu(navigator, fixture.player, guild, logger, fixture.deps())

        menu.getForm().respond("0")
        menu.getForm().respond("2")

        assertEquals(2, opened.size)
        assertIs<BedrockGuildWarDeclarationMenu>(opened[1])
        opened.forEach { assertSame(fixture.deps(), (it as BaseBedrockMenu).storedDependencies()) }
        verify { wars.getWarsForGuild(guild.id) }
    }

    @Test
    fun `relations family - inline allies form keeps the explicit bundle`() {
        val relations = mockk<RelationService>(relaxed = true)
        every { relations.getGuildRelations(guild.id) } returns emptySet()
        every { relations.getGuildRelationsByType(guild.id, any()) } returns emptySet()
        val fixture = BedrockWorkerFixture(mapOf(RelationService::class to relations))
        val (navigator, opened) = navigatorCapturing()
        val menu = BedrockGuildRelationsMenu(navigator, fixture.player, guild, logger, fixture.deps())

        menu.getForm().respond("0")

        assertEquals(1, opened.size)
        assertSame(fixture.deps(), (opened.single() as BaseBedrockMenu).storedDependencies())
        verify { relations.getGuildRelationsByType(guild.id, any()) }
    }

    @Test
    fun `settings optional cosmetic unlock service keeps holiday themes locked when absent`() {
        val locked = GuiTheme.entries.first { it.requiresUnlock }
        fun available(fixture: BedrockWorkerFixture): Boolean {
            val menu = BedrockGuildSettingsMenu(mockk(relaxed = true), fixture.player, guild, logger, fixture.deps())
            return BedrockGuildSettingsMenu::class.java.getDeclaredMethod("isThemeAvailable", GuiTheme::class.java)
                .apply { isAccessible = true }.invoke(menu, locked) as Boolean
        }
        assertFalse(available(BedrockWorkerFixture()), "absent optional service keeps unlockable themes locked")

        val unlocks = mockk<GuildCosmeticUnlockService>()
        every { unlocks.isThemeAvailable(guild.id, locked) } returns true
        assertTrue(available(BedrockWorkerFixture(mapOf(GuildCosmeticUnlockService::class to unlocks))))
        verify(exactly = 1) { unlocks.isThemeAvailable(guild.id, locked) }
    }

    @Test
    fun `BedrockFormUtils labels use the LangService passed by the caller`() {
        val lang = mockk<LangService>()
        every { lang.msg("bedrock.form_utils.label.section", *anyVararg()) } returns Component.text("Bank")
        every { lang.msg("bedrock.form_utils.label.error", *anyVararg()) } returns Component.text("Oops")

        val header = BedrockFormUtils.createSectionHeader(lang, "Bank")
        val error = BedrockFormUtils.createErrorLabel(lang, "Oops")

        assertEquals("Bank", header.text())
        assertEquals("Oops", error.text())
        verify(exactly = 1) { lang.msg("bedrock.form_utils.label.section", "text" to "Bank") }
        verify(exactly = 1) { lang.msg("bedrock.form_utils.label.error", "text" to "Oops") }
    }
}
