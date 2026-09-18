package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildRewardPurchaseService
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockRewardPurchaseMenu
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.impl.FormDefinition
import org.geysermc.cumulus.form.impl.FormDefinitions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.koin.core.context.*
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.*

class GuildRewardPurchaseMenuTest {
    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var purchases: GuildRewardPurchaseService
    private lateinit var quote: RewardPurchaseRequest
    private var returned = 0

    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        player = server.addPlayer()
        quote = RewardPurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), player.uniqueId, "bank-1", 100, 0)
        purchases = mockk()
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } returns Component.text("localized")
            every { raw(any()) } returns "localized"
        }
        stopKoin()
        startKoin { modules(module {
            single<LangService> { lang }
            single<GuildRewardPurchaseService> { purchases }
            single<Plugin> { plugin }
        }) }
    }

    @AfterEach fun cleanup() {
        stopKoin()
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `Java viewing and cancellation never call purchase`() {
        val gui = GuildRewardPurchaseMenu(player, quote, "Bank I", { returned++ }).createGui()
        val cancel = gui.panes.flatMap { it.items }.single { it.item.type == Material.RED_DYE }
        cancel.callAction(mockk(relaxed = true))
        assertEquals(1, returned)
        verify { purchases wasNot Called }
    }

    @Test fun `Java failure leaves same quote for retry and completion refreshes catalog`() {
        val applied = RewardPurchaseResult.Applied(quote.transactionId, quote.rewardId, 100, 1000, 900, 1)
        every { purchases.confirm(player.uniqueId, quote) } returnsMany listOf(RewardPurchaseResult.Failed(quote.transactionId), applied)
        val gui = GuildRewardPurchaseMenu(player, quote, "Bank I", { returned++ }).createGui()
        val confirm = gui.panes.flatMap { it.items }.single { it.item.type == Material.LIME_DYE }
        confirm.callAction(mockk(relaxed = true))
        assertEquals(0, returned)
        confirm.callAction(mockk(relaxed = true))
        assertEquals(1, returned)
        verify(exactly = 2) { purchases.confirm(player.uniqueId, quote) }
    }

    private fun bedrock() = BedrockRewardPurchaseMenu(mockk<MenuNavigator>(relaxed = true), player,
        quote, "Bank I", { returned++ }, Logger.getLogger("RewardPurchaseMenuTest"))

    private fun respond(form: ModalForm, response: String) {
        val definition: FormDefinition<ModalForm, *, *> = FormDefinitions.instance().definitionFor(form)
        definition.handleFormResponse(form, response)
    }

    @Test fun `Bedrock confirm dispatches to server thread with captured quote`() {
        every { purchases.confirm(player.uniqueId, quote) } returns
            RewardPurchaseResult.Applied(quote.transactionId, quote.rewardId, 100, 1000, 900, 1)
        val form = bedrock().getForm() as ModalForm
        respond(form, "true")
        verify { purchases wasNot Called }
        server.scheduler.performOneTick()
        verify(exactly = 1) { purchases.confirm(player.uniqueId, quote) }
        assertEquals(1, returned)
    }

    @Test fun `Bedrock cancel and close never spend and disconnected confirm is ignored`() {
        val form = bedrock().getForm() as ModalForm
        respond(form, "false")
        server.scheduler.performOneTick()
        respond(form, "null")
        server.scheduler.performOneTick()
        assertEquals(2, returned)
        player.disconnect()
        respond(form, "true")
        server.scheduler.performOneTick()
        verify { purchases wasNot Called }
    }
}
