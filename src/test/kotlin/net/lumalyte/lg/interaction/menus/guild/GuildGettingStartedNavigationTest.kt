package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

class GuildGettingStartedNavigationTest {
    private val player = mockk<Player>(relaxed = true)
    private val service = mockk<GuildGettingStartedService>()
    private val factory = mockk<MenuFactory>(relaxed = true)
    private val navigator = mockk<MenuNavigator>(relaxed = true)
    private val prompts = mockk<GuildOnboardingRepository>(relaxed = true)
    private val lang = mockk<LangService>(relaxed = true)
    private val guild = Guild(UUID.randomUUID(), "Guide", createdAt = Instant.now())
    private var current: GuildStartSnapshot? = snapshot(GuildStartTopic.RANKS)
    private lateinit var navigation: GuildGettingStartedNavigation

    @BeforeEach fun setup() {
        stopKoin()
        every { player.isOnline } returns true
        every { service.snapshot(any(), any()) } answers { current }
        every { lang.msg(any()) } answers { Component.text(firstArg<String>()) }
        startKoin { modules(module {
            single { service }; single { factory }; single { navigator }; single { prompts }; single { lang }
        }) }
        navigation = GuildGettingStartedNavigation(navigator, player, guild.id)
    }
    @AfterEach fun cleanup() = stopKoin()

    @Test fun `revoked membership after display denies stale click`() {
        navigation.snapshot()
        current = null
        navigation.select(GuildStartTopic.RANKS)
        verify(exactly = 0) { navigator.openMenu(any()) }
    }
    @Test fun `revoked management permission after display denies stale click`() {
        navigation.snapshot()
        current = snapshot(null)
        navigation.select(GuildStartTopic.RANKS)
        verify(exactly = 0) { factory.createGuildRankManagementMenu(any(), any(), any()) }
    }
    @Test fun `allowed click opens existing rank editor`() {
        navigation.select(GuildStartTopic.RANKS)
        verify { factory.createGuildRankManagementMenu(navigator, player, guild) }
        verify { navigator.openMenu(any()) }
    }
    @Test fun `locked home routes to progression instead of purchase`() {
        current = GuildStartSnapshot(guild, listOf(GuildStartStep(GuildStartTopic.HOME,
            GuildStartState.LOCKED, GuildStartTopic.PROGRESSION, "onboarding.home_unlock")), 1, 5)
        navigation.select(GuildStartTopic.HOME)
        verify { factory.createGuildProgressionMenu(navigator, player, guild) }
        verify(exactly = 0) { factory.createGuildHomeMenu(any(), any(), any()) }
    }
    @Test fun `newcomer opens directory without creating or joining`() {
        current = GuildStartSnapshot(null, listOf(GuildStartStep(GuildStartTopic.MEMBERSHIP,
            GuildStartState.AVAILABLE, GuildStartTopic.MEMBERSHIP, "onboarding.details.membership")), 0, 0)
        navigation.select(GuildStartTopic.MEMBERSHIP)
        verify { factory.createGuildListMenu(navigator, player) }
    }
    @Test fun `read failure fails closed`() {
        every { service.snapshot(any(), any()) } throws IllegalStateException("read failed")
        navigation.select(GuildStartTopic.RANKS)
        verify(exactly = 0) { navigator.openMenu(any()) }
    }
    @Test fun `dismissal failure is visible and does not claim success`() {
        every { prompts.dismiss(any(), any()) } returns false
        navigation.dismiss()
        verify { lang.msg("onboarding.failed") }
        verify(exactly = 0) { lang.msg("onboarding.dismissed") }
    }

    private fun snapshot(action: GuildStartTopic?) = GuildStartSnapshot(guild,
        listOf(GuildStartStep(GuildStartTopic.RANKS, GuildStartState.AVAILABLE, action, "onboarding.permission")), 1, 5)
}
