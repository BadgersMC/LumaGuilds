package net.lumalyte.lg.interaction.commands

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.MainConfig
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.koin.core.context.*
import org.koin.dsl.module
import java.util.UUID

class GuildCreationFeedbackTest {
    @AfterEach fun cleanup() { stopKoin(); unmockkAll() }

    @Test fun cooldownPermissionOnlyBypassesTheCooldownGate() {
        stopKoin()
        val guilds = mockk<GuildService>()
        val repository = mockk<GuildRepository>()
        val config = mockk<ConfigService>()
        val lang = mockk<LangService>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        every { repository.creationCooldownUntil(any()) } returns java.time.Instant.now().plusSeconds(3600)
        every { config.loadConfig() } returns MainConfig(chapterTwoGoldCostsEnabled = false)
        every { guilds.createGuild(any(), any(), any()) } returns null
        startKoin { modules(module {
            single { guilds }; single { repository }; single { config }; single { lang }
        }) }
        every { player.hasPermission("lumaguilds.guild.creationcooldown.bypass") } returns false
        GuildCommand().onCreate(player, "Valid Name", null)
        verify(exactly = 0) { guilds.createGuild(any(), any(), any()) }
        every { player.hasPermission("lumaguilds.guild.creationcooldown.bypass") } returns true
        GuildCommand().onCreate(player, "Valid Name", null)
        verify(exactly = 1) { guilds.createGuild(any(), any(), any()) }
    }

    @Test fun bypassRetainsMembershipValidationAndPaidCreationFailures() {
        stopKoin()
        val guilds = mockk<GuildService>()
        val repository = mockk<GuildRepository>()
        val costs = mockk<GuildCostService>()
        val config = mockk<ConfigService>()
        val lang = mockk<LangService>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val playerId = UUID.randomUUID()
        every { player.uniqueId } returns playerId
        every { player.hasPermission("lumaguilds.guild.creationcooldown.bypass") } returns true
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        every { repository.creationCooldownUntil(any()) } returns java.time.Instant.now().plusSeconds(3600)
        every { config.loadConfig() } returns MainConfig(chapterTwoGoldCostsEnabled = true)
        startKoin { modules(module {
            single { guilds }; single { repository }; single { costs }; single { config }; single { lang }
        }) }
        GuildCommand().onCreate(player, "<red>Invalid", null)
        every { guilds.getPlayerGuilds(any()) } returns setOf(
            net.lumalyte.lg.domain.entities.Guild(UUID.randomUUID(), "Existing", createdAt = java.time.Instant.now()))
        GuildCommand().onCreate(player, "Valid Name", null)
        verify(exactly = 0) { costs.createGuild(any(), any(), any()) }
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        val results = listOf(GuildCreationCostResult.InsufficientGold,
            GuildCreationCostResult.PaymentUnavailable, GuildCreationCostResult.ConfigurationError,
            GuildCreationCostResult.PendingReview(UUID.randomUUID()))
        for (result in results) {
            every { costs.createGuild(any(), playerId, any()) } returns result
            GuildCommand().onCreate(player, "Valid Name", null)
        }
        verify(exactly = 4) { costs.createGuild(any(), playerId, any()) }
        verify(exactly = 0) { guilds.createGuild(any(), any(), any()) }
        every { repository.creationCooldownUntil(any()) } throws IllegalStateException("database unavailable")
        GuildCommand().onCreate(player, "Valid Name", null)
        verify(exactly = 4) { costs.createGuild(any(), any(), any()) }
        verify(exactly = 1) { lang.msg("guild_creation_cooldown.unavailable") }
    }

    @Test fun bypassPermissionDefaultsFalseEvenForOperatorsAndHasNoWildcardParent() {
        val description = org.bukkit.plugin.PluginDescriptionFile(
            requireNotNull(javaClass.classLoader.getResourceAsStream("plugin.yml")))
        val permission = description.permissions.single { it.name == "lumaguilds.guild.creationcooldown.bypass" }
        kotlin.test.assertEquals(org.bukkit.permissions.PermissionDefault.FALSE, permission.default)
        kotlin.test.assertFalse(permission.default.getValue(true))
        kotlin.test.assertFalse(permission.default.getValue(false))
        kotlin.test.assertTrue(description.permissions.none {
            it.children[permission.name] == true
        })
    }

    @Test fun uncertainAndPendingResultsDoNotClaimDuplicateNameOrSuccess() {
        stopKoin()
        val guilds = mockk<GuildService>()
        val repository = mockk<GuildRepository>()
        val costs = mockk<GuildCostService>()
        val config = mockk<ConfigService>()
        val lang = mockk<LangService>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        every { repository.creationCooldownUntil(any()) } returns null
        every { config.loadConfig() } returns MainConfig(chapterTwoGoldCostsEnabled = true)
        startKoin { modules(module {
            single { guilds }; single { repository }; single { costs }; single { config }; single { lang }
        }) }
        val tx = UUID.randomUUID()
        for (result in listOf(GuildCreationCostResult.Uncertain(null, tx), GuildCreationCostResult.PendingReview(tx))) {
            every { costs.createGuild(any(), any(), any()) } returns result
            GuildCommand().onCreate(player, "Valid Name", null)
        }
        verify(exactly = 2) { lang.msg("command.migrated.guild.create.payment_outcome_uncertain", *anyVararg()) }
        verify(exactly = 0) { lang.msg("command.migrated.guild.create.the_name_is_already_taken_by_another", *anyVararg()) }
        verify(exactly = 0) { lang.msg("command.migrated.guild.create.guild_created_successfully", *anyVararg()) }
        verify(exactly = 0) { guilds.createGuild(any(), any(), any()) }
    }
}
