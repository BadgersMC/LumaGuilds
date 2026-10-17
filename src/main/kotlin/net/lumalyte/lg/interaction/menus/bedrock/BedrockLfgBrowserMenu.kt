package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger
import kotlin.math.ceil

/** Native Bedrock LFG browser backed by the same LfgService contract as Java. */
class BedrockLfgBrowserMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    logger: Logger,
    private val page: Int = 0
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val lfgService: LfgService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        val guilds = lfgService.getAvailableGuilds()
        val pageCount = maxOf(1, ceil(guilds.size / PAGE_SIZE.toDouble()).toInt())
        val safePage = page.coerceIn(0, pageCount - 1)
        val pageGuilds = guilds.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder()
            .title(
                lang.bedrock(
                    "bedrock.lfg_browser.title",
                    "page" to safePage + 1,
                    "pages" to pageCount
                )
            )
            .content(
                if (guilds.isEmpty()) {
                    lang.bedrock("bedrock.lfg_browser.empty")
                } else {
                    lang.bedrock("bedrock.lfg_browser.content", "total" to guilds.size)
                }
            )

        pageGuilds.forEach { guild ->
            builder = builder.button(guildButton(guild))
            actions += {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                bedrockNavigator.openMenu(
                    menuFactory.createJoinRequirementsMenu(menuNavigator, player, guild)
                )
            }
        }

        if (safePage > 0) {
            builder = builder.button(lang.bedrock("bedrock.lfg_browser.button.previous"))
            actions += {
                bedrockNavigator.openMenu(
                    BedrockLfgBrowserMenu(menuNavigator, player, logger, safePage - 1)
                )
            }
        }
        if (safePage + 1 < pageCount) {
            builder = builder.button(lang.bedrock("bedrock.lfg_browser.button.next"))
            actions += {
                bedrockNavigator.openMenu(
                    BedrockLfgBrowserMenu(menuNavigator, player, logger, safePage + 1)
                )
            }
        }
        builder = builder.button(lang.bedrock("bedrock.lfg_browser.button.close"))
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

    private fun guildButton(guild: Guild): String {
        val maximum = configService.loadConfig().guild.maxMembersPerGuild
        val members = memberService.getMemberCount(guild.id)
        val mode = if (guild.mode == GuildMode.PEACEFUL) {
            lang.bedrock("bedrock.lfg_browser.mode.peaceful")
        } else {
            lang.bedrock("bedrock.lfg_browser.mode.hostile")
        }
        val requirement = lfgService.getJoinRequirement(guild)
        val fee = if (requirement == null) {
            lang.bedrock("bedrock.lfg_browser.fee.none")
        } else {
            lang.bedrock(
                "bedrock.lfg_browser.fee.required",
                "amount" to requirement.amount,
                "currency" to formatCurrencyName(requirement.currencyName)
            )
        }
        return lang.bedrock(
            "bedrock.lfg_browser.guild",
            "guild" to guild.name,
            "level" to guild.level,
            "members" to members,
            "maximum" to maximum,
            "mode" to mode,
            "fee" to fee
        )
    }

    private fun formatCurrencyName(name: String): String =
        name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

    private fun runOnServerThread(action: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit

    companion object {
        private const val PAGE_SIZE = 8
    }
}
