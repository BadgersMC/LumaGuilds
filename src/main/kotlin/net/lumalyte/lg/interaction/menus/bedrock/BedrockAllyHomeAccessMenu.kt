package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.UUID
import java.util.logging.Logger

/**
 * Bedrock version of the allied-home access page (REQ-182): one button per ally
 * opens a form to allow the ally, pick its ranks and block its players; another
 * button lists blocked players to unblock.
 */
class BedrockAllyHomeAccessMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()
    private val progressionService: net.lumalyte.lg.application.services.ProgressionService by inject()

    override fun getForm(): Form {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            return statusForm(lang.bedrock("menu.ally_home_access.permission_denied"))
        }
        // Allied homes need the Allied Waypoints reward; say so instead of an empty page.
        if (!progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS)) {
            return statusForm(lang.bedrock("menu.allied_homes.locked.feedback_v2"))
        }
        val current = guildService.getGuild(guild.id)
            ?: return statusForm(lang.bedrock("bedrock.ally_home_access.missing_guild"))
        val allies = activeAllies()
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder()
            .title(lang.bedrock("menu.ally_home_access.title"))
            .content(
                if (allies.isEmpty()) {
                    lang.bedrock("bedrock.ally_home_access.no_allies")
                } else {
                    lang.bedrock("menu.ally_home_access.info.description") + "\n" +
                        lang.bedrock("menu.ally_home_access.info.instructions")
                }
            )

        // REQ-180: set, move or remove our own allied homes from here too.
        for (slot in 1..guildService.allyHomeSlots(guild.id)) {
            val home = guildService.getAllyHome(guild.id, slot)
            builder = builder.button(
                if (home == null) lang.bedrock("bedrock.allied_homes.set", "slot" to slot)
                else lang.bedrock("bedrock.allied_homes.move", "slot" to slot),
            )
            actions += { setAlliedHome(slot) }
            if (home != null) {
                builder = builder.button(lang.bedrock("bedrock.allied_homes.remove", "slot" to slot))
                actions += { removeAlliedHome(slot) }
            }
        }

        allies.forEach { (allyId, allyName) ->
            val allowed = allyId in current.allyHomeAllowedGuilds
            val label = if (allowed) {
                lang.bedrock("menu.ally_home_access.ally.allowed", "guild" to allyName)
            } else {
                lang.bedrock("menu.ally_home_access.ally.denied", "guild" to allyName)
            }
            builder = builder.button(label)
            actions += { showAllyForm(allyId, allyName) }
        }
        val blocked = guildService.allyHomeAccess(guild.id).blockedPlayers
        if (blocked.isNotEmpty()) {
            builder = builder.button(lang.bedrock("bedrock.ally_home_access.blocked_button", "count" to blocked.size))
            actions += { showBlockedForm(blocked) }
        }
        builder = builder.button(lang.bedrock("menu.ally_home_access.back"))
        actions += { bedrockNavigator.goBack() }

        return builder
            .validResultHandler { response ->
                runOnServerThread {
                    onFormResponseReceived()
                    actions.getOrNull(response.clickedButtonId())?.invoke()
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                runOnServerThread {
                    onFormResponseReceived()
                    bedrockNavigator.goBack()
                }
            }
            .build()
    }

    private fun activeAllies(): List<Pair<UUID, String>> =
        relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
            .mapNotNull { relation ->
                val otherId = relation.getOtherGuild(guild.id)
                guildService.getGuild(otherId)?.let { otherId to it.name }
            }
            .sortedBy { it.second.lowercase() }

    private fun setAlliedHome(slot: Int) {
        val here = net.lumalyte.lg.domain.entities.GuildHome(
            player.location.world.uid,
            net.lumalyte.lg.domain.values.Position3D(player.location.blockX, player.location.blockY, player.location.blockZ),
        )
        player.sendMessage(
            if (guildService.setAllyHome(guild.id, here, player.uniqueId, slot)) lang.msg("menu.allied_homes.feedback.set")
            else lang.msg("menu.allied_homes.feedback.failed"),
        )
        open()
    }

    private fun removeAlliedHome(slot: Int) {
        player.sendMessage(
            if (guildService.removeAllyHome(guild.id, player.uniqueId, slot)) lang.msg("menu.allied_homes.feedback.removed")
            else lang.msg("menu.allied_homes.feedback.failed"),
        )
        open()
    }

    /** One form per ally: allowed, which ranks, which players are blocked. */
    private fun showAllyForm(allyId: UUID, allyName: String) {
        val current = guildService.getGuild(guild.id) ?: return open()
        val access = guildService.allyHomeAccess(guild.id)
        val picks = access.visitorRanks[allyId]
        val ranks = rankService.listRanks(allyId).sortedBy { it.priority }
        val members = memberService.getGuildMembers(allyId)
            .map { Bukkit.getOfflinePlayer(it.playerId) }
            .sortedBy { (it.name ?: it.uniqueId.toString()).lowercase() }
            .take(MAX_MEMBERS)
        var form = CustomForm.builder()
            .title(lang.bedrock("bedrock.ally_home_access.visitor.title", "guild" to allyName))
            .label(lang.bedrock("menu.ally_home_access.info.instructions"))
            .toggle(lang.bedrock("bedrock.ally_home_access.visitor.allowed", "guild" to allyName),
                allyId in current.allyHomeAllowedGuilds)
            .label(lang.bedrock("menu.ally_home_access.visitor.ranks_header"))
        ranks.forEach { rank ->
            form = form.toggle(lang.bedrock("bedrock.ally_home_access.visitor.rank", "rank" to rank.name),
                net.lumalyte.lg.application.services.AllyHomeRankPicks.isAllowed(picks, rank.id))
        }
        // REQ-183: our own ranks for visiting this ally (owner always can, so not listed).
        val outboundPick = guildService.allyHomeOutboundRanks(guild.id)[allyId]
        val ownerRank = rankService.getHighestRank(guild.id)?.id
        val ours = rankService.listRanks(guild.id).filter { it.id != ownerRank }.sortedBy { it.priority }
        val outboundDefaults = ours.filter { RankPermission.USE_ALLY_HOMES in it.permissions }.map { it.id }.toSet()
        form = form.label(lang.bedrock("bedrock.ally_home_access.visitor.ours_header", "guild" to allyName))
        ours.forEach { rank ->
            form = form.toggle(lang.bedrock("bedrock.ally_home_access.visitor.ours_rank", "rank" to rank.name),
                outboundPick?.contains(rank.id) ?: (rank.id in outboundDefaults))
        }
        form = form.label(lang.bedrock("bedrock.ally_home_access.visitor.members_header"))
        members.forEach { target ->
            form = form.toggle(
                lang.bedrock("bedrock.ally_home_access.visitor.block", "player" to (target.name ?: target.uniqueId.toString())),
                target.uniqueId in access.blockedPlayers,
            )
        }
        // Component order: 0 label, 1 allowed, 2 label, their ranks, label, our ranks, label, members.
        val firstRank = 3
        val firstOurs = firstRank + ranks.size + 1
        val firstMember = firstOurs + ours.size + 1
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.uniqueId, form
            .validResultHandler { response ->
                runOnServerThread {
                    onFormResponseReceived()
                    if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
                        player.sendMessage(lang.msg("menu.ally_home_access.permission_denied"))
                        return@runOnServerThread open()
                    }
                    var ok = true
                    val allowedSet = (guildService.getGuild(guild.id)?.allyHomeAllowedGuilds ?: emptySet()).toMutableSet()
                    if (response.asToggle(1)) allowedSet.add(allyId) else allowedSet.remove(allyId)
                    ok = guildService.setAllyHomeAllowedGuilds(guild.id, allowedSet, player.uniqueId) && ok
                    val chosen = ranks.filterIndexed { i, _ -> response.asToggle(firstRank + i) }.map { it.id }.toSet()
                    val all = ranks.map { it.id }.toSet()
                    val pick = if (chosen.containsAll(all)) null else chosen
                    if (pick != picks) ok = guildService.setAllyHomeVisitorRanks(guild.id, allyId, pick, player.uniqueId) && ok
                    val ourChosen = ours.filterIndexed { i, _ -> response.asToggle(firstOurs + i) }.map { it.id }.toSet()
                    // Leaving every toggle at its default keeps rank permissions in charge.
                    val ourPick = if (outboundPick == null && ourChosen == outboundDefaults) null else ourChosen
                    if (ourPick != outboundPick) {
                        ok = guildService.setAllyHomeOutboundRanks(guild.id, allyId, ourPick, player.uniqueId) && ok
                    }
                    members.forEachIndexed { i, target ->
                        val block = response.asToggle(firstMember + i)
                        if (block != (target.uniqueId in access.blockedPlayers)) {
                            ok = guildService.setAllyHomeBlocked(guild.id, target.uniqueId, block, player.uniqueId) && ok
                        }
                    }
                    player.sendMessage(
                        if (ok) lang.msg("bedrock.ally_home_access.saved") else lang.msg("menu.ally_home_access.save_failed"),
                    )
                    open()
                }
            }
            .closedOrInvalidResultHandler { _, _ -> runOnServerThread { onFormResponseReceived(); open() } }
            .build())
    }

    /** Unticking a player unblocks them. */
    private fun showBlockedForm(blocked: Set<UUID>) {
        val targets = blocked.map { Bukkit.getOfflinePlayer(it) }
            .sortedBy { (it.name ?: it.uniqueId.toString()).lowercase() }
        var form = CustomForm.builder()
            .title(lang.bedrock("bedrock.ally_home_access.blocked_title"))
            .label(lang.bedrock("bedrock.ally_home_access.blocked_hint"))
        targets.forEach { target ->
            form = form.toggle(target.name ?: target.uniqueId.toString(), true)
        }
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.uniqueId, form
            .validResultHandler { response ->
                runOnServerThread {
                    onFormResponseReceived()
                    var ok = true
                    targets.forEachIndexed { i, target ->
                        if (!response.asToggle(i + 1)) {
                            ok = guildService.setAllyHomeBlocked(guild.id, target.uniqueId, false, player.uniqueId) && ok
                        }
                    }
                    if (!ok) player.sendMessage(lang.msg("menu.ally_home_access.save_failed"))
                    open()
                }
            }
            .closedOrInvalidResultHandler { _, _ -> runOnServerThread { onFormResponseReceived(); open() } }
            .build())
    }

    private fun statusForm(message: String): Form = SimpleForm.builder()
        .title(lang.bedrock("menu.ally_home_access.title"))
        .content(message)
        .button(lang.bedrock("menu.ally_home_access.back"))
        .validResultHandler { runOnServerThread { bedrockNavigator.goBack() } }
        .closedOrInvalidResultHandler { _, _ -> runOnServerThread { bedrockNavigator.goBack() } }
        .build()

    private fun runOnServerThread(action: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    private companion object {
        const val MAX_MEMBERS = 40
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit
}
