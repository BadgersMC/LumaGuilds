package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.api.events.GuildCreatedEvent
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.UUID

/** A chat invitation never interrupts inventories; existing members are not retroactively enrolled. */
class GuildOnboardingListener(
    private val plugin: Plugin,
    private val repository: GuildOnboardingRepository,
    private val guilds: GuildService,
    private val members: MemberService,
    private val lang: LangService,
) : Listener {
    @EventHandler fun onCreated(event: GuildCreatedEvent) = enroll(event.ownerId, event.guild.id)
    @EventHandler fun onMemberJoin(event: GuildMemberJoinEvent) = enroll(event.playerId, event.guildId)
    @EventHandler fun onJoin(event: PlayerJoinEvent) {
        val id = event.player.uniqueId
        Bukkit.getScheduler().runTask(plugin, Runnable { guilds.getPlayerGuilds(id).forEach { deliver(id, it.id) } })
    }
    private fun enroll(player: UUID, guild: UUID) {
        if (repository.register(player, guild)) Bukkit.getScheduler().runTask(plugin, Runnable { deliver(player, guild) })
    }
    internal fun deliver(playerId: UUID, guildId: UUID) {
        val player = Bukkit.getPlayer(playerId) ?: return
        if (!player.isOnline || !player.hasPermission("lumaguilds.guild.help")) return
        val guild = guilds.getGuild(guildId) ?: return
        if (members.getMember(playerId, guildId) == null || !repository.consume(playerId, guildId)) return
        player.sendMessage(lang.msg("onboarding.prompt", "guild" to guild.name)
            .append(lang.msg("onboarding.open").clickEvent(ClickEvent.runCommand("/g start $guildId")))
            .append(lang.msg("onboarding.dismiss").clickEvent(ClickEvent.runCommand("/g start dismiss"))))
    }
}
