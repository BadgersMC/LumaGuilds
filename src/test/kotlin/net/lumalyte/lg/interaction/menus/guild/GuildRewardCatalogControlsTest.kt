package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildProgressionInfoMenu
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.SimpleForm
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
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.*

class GuildRewardCatalogControlsTest {
    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var purchases: GuildRewardPurchaseService
    private lateinit var progression: ProgressionService
    private lateinit var members: MemberService
    private val guild = Guild(UUID.randomUUID(), "Catalog", createdAt = Instant.now())
    private val statuses = RewardOfferStatus.entries

    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        player = server.addPlayer()
        purchases = mockk(relaxed = true)
        every { purchases.quote(any(), any(), any()) } returns null
        members = mockk(relaxed = true)
        every { members.getMember(player.uniqueId, guild.id) } returns mockk()
        progression = mockk(relaxed = true)
        val entitlements = RewardEntitlementResolver(RewardCatalog.chapterTwo()).resolve(100, RewardOwnership())
        val offers = statuses.mapIndexed { index, status -> entitlements.offers[index].copy(status = status) }
        every { progression.getRewardState(guild.id) } returns GuildRewardRead.Available(100, 0, entitlements.copy(offers = offers))
        every { progression.getSourceUsage(guild.id) } returns emptyList()
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
            every { raw(any()) } answers { firstArg<String>() }
        }
        stopKoin()
        startKoin { modules(module {
            single<LangService> { lang }
            single<GuildRewardPurchaseService> { purchases }
            single<ProgressionService> { progression }
            single<ProgressionRepository> { mockk(relaxed = true) }
            single<MemberService> { members }
            single<Plugin> { plugin }
        }) }
    }

    @AfterEach fun cleanup() {
        unmockkConstructor(ChestGui::class)
        unmockkStatic(JavaPlugin::class)
        stopKoin()
        MockBukkit.unmock()
    }

    @Test fun `Java offers only advertise and quote available rewards`() {
        val panes = mutableListOf<Pane>()
        mockkConstructor(ChestGui::class)
        every { anyConstructed<ChestGui>().addPane(capture(panes)) } just Runs
        every { anyConstructed<ChestGui>().show(any()) } just Runs
        val menu = GuildProgressionMenu(mockk(), player, guild, mockk(), members, progression, mockk(), mockk(), mockk())
        GuildProgressionMenu::class.java.getDeclaredMethod("openRewardCatalog").apply { isAccessible = true }.invoke(menu)
        val books = panes.flatMap { it.items }.filter { it.item.type == Material.BOOK }
        assertEquals(5, books.size)
        assertEquals(1, books.count { it.item.lore()!!.size == 3 })
        assertEquals(4, books.count { it.item.lore()!!.size == 2 })
        for (book in books) {
            book.callAction(mockk(relaxed = true))
        }
        verify(exactly = 1) { purchases.quote(player.uniqueId, guild.id, any()) }
    }

    @Test fun `Bedrock unavailable selections never request a purchase quote`() {
        val menu = spyk(BedrockGuildProgressionInfoMenu(mockk(relaxed = true), player, guild, Logger.getLogger("CatalogTest")))
        every { menu.open() } just Runs
        for ((index, status) in statuses.withIndex()) {
            if (status == RewardOfferStatus.AVAILABLE) continue
            val form = menu.getForm() as SimpleForm
            val definition: FormDefinition<SimpleForm, *, *> = FormDefinitions.instance().definitionFor(form)
            definition.handleFormResponse(form, index.toString())
            server.scheduler.performOneTick()
        }
        verify(exactly = 0) { purchases.quote(any(), any(), any()) }
    }
}

