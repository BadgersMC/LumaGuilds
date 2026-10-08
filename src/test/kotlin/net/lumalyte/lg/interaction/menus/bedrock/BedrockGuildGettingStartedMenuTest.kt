package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.impl.FormDefinition
import org.geysermc.cumulus.form.impl.FormDefinitions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

class BedrockGuildGettingStartedMenuTest {
    @AfterEach fun cleanup() { stopKoin(); MockBukkit.unmock() }

    @Test fun `current native form opens existing rank menu on server tick`() {
        val fixture = Fixture()
        fixture.respond()
        verify(exactly = 0) { fixture.factory.createGuildRankManagementMenu(any(), any(), any()) }
        fixture.tick()
        verify(exactly = 1) { fixture.factory.createGuildRankManagementMenu(any(), any(), any()) }
    }
    @Test fun `queued native response rechecks current membership`() {
        val fixture = Fixture()
        fixture.respond()
        every { fixture.service.snapshot(any(), any()) } returns null
        fixture.tick()
        verify(exactly = 0) { fixture.factory.createGuildRankManagementMenu(any(), any(), any()) }
    }
    @Test fun `navigation change discards queued old form response`() {
        val fixture = Fixture()
        fixture.respond()
        fixture.navigator.invalidateCurrentNavigation()
        fixture.tick()
        verify(exactly = 0) { fixture.factory.createGuildRankManagementMenu(any(), any(), any()) }
    }

    private class Fixture {
        private val server = MockBukkit.mock()
        private val player = server.addPlayer()
        val service = mockk<GuildGettingStartedService>()
        val factory = mockk<MenuFactory>(relaxed = true)
        val navigator = MenuNavigator(player)
        private val guild = Guild(UUID.randomUUID(), "Guide", createdAt = Instant.now())
        private val form: SimpleForm
        init {
            stopKoin()
            val plugin = MockBukkit.createMockPlugin()
            val lang = mockk<LangService>()
            every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
            every { service.snapshot(any(), any()) } returns GuildStartSnapshot(guild,
                GuildStartTopic.entries.map { GuildStartStep(it, GuildStartState.AVAILABLE, it, "onboarding.permission") }, 1, 5)
            startKoin { modules(module {
                single { service }; single { factory }; single { lang }; single<Plugin> { plugin }
            }) }
            form = BedrockGuildGettingStartedMenu(navigator, player, guild.id, Logger.getAnonymousLogger()).getForm() as SimpleForm
        }
        fun respond() {
            val definition: FormDefinition<SimpleForm, *, *> = FormDefinitions.instance().definitionFor(form)
            definition.handleFormResponse(form, "2")
        }
        fun tick() = server.scheduler.performOneTick()
    }
}
