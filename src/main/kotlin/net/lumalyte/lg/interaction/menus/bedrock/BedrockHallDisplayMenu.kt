package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.HallThemeService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * REQ-261: Hall Display on Bedrock. Shows what the statue wears, picks open themes and wears a
 * whole owned theme; mixing single slots and choosing the skin are on Java.
 */
class BedrockHallDisplayMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val themes: HallThemeService by inject()
    private val members: MemberService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        val display = themes.display(guild.id)
        val manage = members.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)
        val content = when {
            display == null -> lang.bedrock("hall.feedback.failed")
            display.owned.isEmpty() -> lang.bedrock("hall.display.none_body")
            else -> HallSlot.entries.joinToString("\n") { slot ->
                val theme = display.wearing(slot)
                lang.bedrock("hall.display.bedrock_slot", "slot" to slot.label, "value" to (theme?.cosmetic(slot) ?: "-"))
            }
        }
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder().title(lang.bedrock("hall.display.title")).content(content)
        if (display != null && manage && display.picksLeft > 0) {
            builder = builder.button(lang.bedrock("hall.display.picks_title", "count" to display.picksLeft))
            actions += {
                BedrockPrestigeThemeMenu(menuNavigator, player, display.unowned, ::open, { theme ->
                    report(themes.earn(player.uniqueId, guild.id, theme))
                    open()
                }, logger).open()
            }
        }
        if (display != null && manage) {
            display.owned.forEach { theme: HallTheme ->
                builder = builder.button(lang.bedrock("hall.display.bedrock_wear", "theme" to theme.label))
                actions += {
                    report(themes.wearAll(player.uniqueId, guild.id, theme))
                    open()
                }
            }
        }
        if (display != null && manage) {
            // REQ-271: the statue wears a copy of the manager's own armor.
            builder = builder.button(lang.bedrock("hall.display.armor_copy"))
            actions += {
                val inv = player.inventory
                val worn = mapOf(
                    net.lumalyte.lg.domain.rewards.HallArmorPart.HEAD to inv.helmet,
                    net.lumalyte.lg.domain.rewards.HallArmorPart.CHEST to inv.chestplate,
                    net.lumalyte.lg.domain.rewards.HallArmorPart.LEGS to inv.leggings,
                    net.lumalyte.lg.domain.rewards.HallArmorPart.FEET to inv.boots,
                ).mapValues { (part, item) -> net.lumalyte.lg.infrastructure.services.hall.HallArmorItems.copy(part, item) }
                if (worn.values.all { it == null }) player.sendMessage(lang.msg("hall.feedback.armor_none"))
                else report(themes.setAllArmor(player.uniqueId, guild.id, worn))
                open()
            }
        }
        val preview = getKoin().getOrNull<net.lumalyte.lg.infrastructure.services.HallPreviewService>()
        if (display != null && preview != null) {
            builder = builder.button(lang.bedrock("hall.display.preview"))
            actions += {
                // REQ-264: same viewer-only statue as the Java Hall Display.
                val tier = getKoin().getOrNull<net.lumalyte.lg.application.services.GuildPrestigeService>()
                    ?.overview(guild.id)?.prestigeCount ?: 0
                val skin = display.skin ?: runCatching {
                    val top = getKoin().get<net.lumalyte.lg.application.services.RankService>().getHighestRank(guild.id)
                    members.getGuildMembers(guild.id).firstOrNull { it.rankId == top?.id }?.playerId
                }.getOrNull()
                preview.show(player, guild.name, tier, display, skin,
                    net.lumalyte.lg.infrastructure.services.HallPreviewService.guildBanner(guild))
                player.sendMessage(lang.msg("hall.feedback.preview",
                    "seconds" to net.lumalyte.lg.infrastructure.services.HallPreviewService.SECONDS,
                    "pose" to (display.wearing(HallSlot.POSE)?.cosmetic(HallSlot.POSE) ?: "-")))
            }
        }
        builder = builder.button(lang.bedrock("hall.pick.back"))
        actions += { bedrockNavigator.goBack() }
        return builder
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) actions.getOrNull(response.clickedButtonId())?.invoke()
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .build()
    }

    private fun report(outcome: HallThemeService.Outcome) {
        player.sendMessage(when (outcome) {
            HallThemeService.Outcome.DONE -> lang.msg("hall.feedback.saved")
            HallThemeService.Outcome.NOT_ALLOWED -> lang.msg("hall.feedback.not_allowed")
            HallThemeService.Outcome.NO_PICKS -> lang.msg("hall.feedback.no_picks")
            HallThemeService.Outcome.NOT_OWNED -> lang.msg("hall.feedback.not_owned")
            HallThemeService.Outcome.NOT_A_MEMBER -> lang.msg("hall.feedback.not_a_member")
            HallThemeService.Outcome.FAILED -> lang.msg("hall.feedback.failed")
        })
    }

    override fun handleResponse(player: Player, response: Any?) = Unit
}
