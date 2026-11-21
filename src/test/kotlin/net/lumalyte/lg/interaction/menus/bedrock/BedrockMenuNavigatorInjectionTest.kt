package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BedrockMenuNavigatorInjectionTest {
    private val playerId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) {
        every { uniqueId } returns playerId
    }
    private val navigator = mockk<MenuNavigator>(relaxed = true)
    private val factory = mockk<MenuFactory>(relaxed = true)
    private val lang = mockk<LangService> {
        every { msg("bedrock.common.workflow.cancelled") } returns Component.text("Cancelled")
        every { msg("bedrock.common.workflow.restoring") } returns Component.text("Restoring")
    }

    @Test
    fun `navigator receives LangService explicitly without Koin component inheritance`() {
        val constructor = BedrockMenuNavigator::class.constructors.single()
        assertTrue(constructor.parameters.any { it.type.classifier == LangService::class && !it.isOptional })
        assertFalse(KoinComponent::class.java.isAssignableFrom(BedrockMenuNavigator::class.java))
        assertTrue(BedrockMenuNavigator::class.java.declaredFields.none { it.name == "lang\$delegate" })
    }

    @Test
    fun `cancel workflow clears only current player's state and sends injected translation`() {
        val ownedKey = "$playerId:flow"
        val otherKey = "${UUID.randomUUID()}:flow"
        FormStateManager.saveState(ownedKey, mapOf("step" to "one"))
        FormStateManager.saveState(otherKey, mapOf("step" to "other"))
        try {
            BedrockMenuNavigator(navigator, player, factory, lang).createCancelWorkflowHandler().run()
            assertNull(FormStateManager.restoreState(ownedKey))
            assertEquals("other", FormStateManager.restoreState(otherKey)?.get("step"))
            verify(exactly = 1) { navigator.clearMenuStack() }
            verify(exactly = 1) { player.sendMessage(Component.text("Cancelled")) }
        } finally {
            FormStateManager.clearState(ownedKey)
            FormStateManager.clearState(otherKey)
        }
    }

    @Test
    fun `recovery restores form data and navigates without Koin context`() {
        val key = "$playerId:timeout_recovery"
        val recovery = mockk<Menu>(relaxed = true)
        val data = mapOf("tab" to "guild")
        FormStateManager.saveState(key, data)
        try {
            BedrockMenuNavigator(navigator, player, factory, lang)
                .createRecoveryHandler(recovery).run()
            verify(exactly = 1) { player.sendMessage(Component.text("Restoring")) }
            verify(exactly = 1) { recovery.passData(data) }
            verify(exactly = 1) { navigator.openMenu(recovery) }
        } finally {
            FormStateManager.clearState(key)
        }
    }
}
