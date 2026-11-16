package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.rewardStatus
import net.lumalyte.lg.infrastructure.services.*
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Every page is rebuilt from the active catalog and committed guild state. */
internal class GuildChronicleMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val presenter: GuildDialogPresenter = GuildMenuDialog(),
    private val initialRewardId: String? = null,
    private val returnToBrowser: (() -> Unit)? = null,
    private val initialPrestige: Boolean = false,
    private val reviewPrestige: (() -> Unit)? = null,
) : Menu, KoinComponent {
    private val chronicle: GuildChronicleService by inject()
    private val members: MemberService by inject()
    private val purchases: GuildRewardPurchaseService by inject()
    private val config: ConfigService by inject()
    private val lang: LangService by inject()
    private val prestigeService: GuildPrestigeService by inject()
    private var page: Int? = null
    private var branch = ChronicleBranch.ALL
    private var prestigeTab = 0

    override fun open() {
        load { snapshot ->
            if (page == null) page = (snapshot.state.level - 1) / 10
            when {
                initialPrestige -> prestige()
                initialRewardId != null -> detail(initialRewardId)
                else -> tree()
            }
        }
    }

    private val loader: net.lumalyte.lg.interaction.menus.MenuLoader by lazy {
        getKoin().getOrNull() ?: net.lumalyte.lg.interaction.menus.MenuLoader.Immediate
    }

    /** REQ-266: the chronicle read runs off the main thread; [show] runs back on it. */
    private fun load(show: (GuildChronicle) -> Unit) = load({ Unit }) { snapshot, _ -> show(snapshot) }

    private fun <X> load(extra: () -> X, show: (GuildChronicle, X) -> Unit) {
        val token = navigator.currentNavigationToken()
        val wantedPage = page ?: 0
        val wantedBranch = branch
        loader.load(player, { navigator.isNavigationCurrent(token) },
            { chronicle.read(player.uniqueId, guild.id, wantedPage, wantedBranch) to extra() }) { (snapshot, more) ->
            if (snapshot == null) player.sendMessage(lang.msg("chapter_two_rewards.unavailable")) else show(snapshot, more)
        }
    }

    private fun tree() {
        load { snapshot ->
            val cards = snapshot.levels.flatMap { it.offers }.map { offer ->
                val lines = listOf(
                    Component.text(offer.reward.name),
                    lang.gui("chapter_two_rewards.level_price", "level" to offer.reward.level, "price" to offer.reward.price),
                    effect(offer.reward.effect),
                    lang.rewardStatus(offer.status),
                ) + if (snapshot.goal.rewardId == offer.reward.id) listOf(lang.gui("chronicle.tracked")) else emptyList()
                referenceItem(icon(offer.reward.effect), lines,
                    referenceAction(Component.text(offer.reward.name), icon(offer.reward.effect)) { detail(offer.reward.id) })
            }.ifEmpty { listOf(referenceItem("lg_reward", listOf(lang.gui("chronicle.empty")))) }
            val actions = buildList {
                if (snapshot.page > 0) add(referenceAction(lang.gui("chronicle.earlier"), "lg_page_prev") { page = snapshot.page - 1; tree() })
                if (snapshot.page < 9) add(referenceAction(lang.gui("chronicle.later"), "lg_page_next") { page = snapshot.page + 1; tree() })
                snapshot.goal.rewardId?.let { id -> add(referenceAction(lang.gui("chronicle.goal"), "lg_reward") { detail(id) }) }
                add(referenceAction(lang.gui("chronicle.automatic"), "lg_bank") { automatic() })
                add(referenceAction(lang.gui("chronicle.prestige.action"), "lg_prestige") { prestige() })
            }
            show(cards, actions, referenceAction(lang.gui("recruitment.dialog.back"), "lg_back", navigator::goBack),
                lang.gui("chronicle.range", "guild" to guild.name, "level" to snapshot.state.level,
                    "first" to snapshot.levels.first().level, "last" to snapshot.levels.last().level),
                ChronicleBranch.entries.map { category -> referenceAction(branchLabel(category), "lg_reward") { branch = category; tree() } })
        }
    }

    private fun detail(id: String) {
        load { snapshot ->
            val offer = snapshot.state.entitlements.offers.find { it.reward.id == id } ?: return@load
            val reward = offer.reward
            val benefits = snapshot.state.entitlements
            val cards = listOf(
                referenceItem(icon(reward.effect), listOf(Component.text(reward.name), lang.rewardStatus(offer.status),
                    effect(reward.effect), lang.gui("chapter_two_rewards.level_price", "level" to reward.level, "price" to reward.price))),
                referenceItem("lg_reward", listOf(
                    if (offer.status == RewardOfferStatus.AVAILABLE || offer.status == RewardOfferStatus.LOCKED)
                        impact(reward.effect, benefits) else lang.rewardStatus(offer.status),
                    if (snapshot.goal.rewardId == id) lang.gui("chronicle.tracked") else lang.gui("chronicle.shared"))),
                referenceItem("lg_sources", listOf(
                    lang.gui("chapter_two_rewards.capacity_bank", "capacity" to benefits.bankCapacity),
                    lang.gui("chapter_two_rewards.capacity_home_member", "homes" to benefits.homeCapacity, "members" to net.lumalyte.lg.interaction.menus.memberCapacityShown(guild.id, benefits.memberCapacity)),
                    lang.gui("chapter_two_rewards.multipliers", "cooldown" to benefits.homeCooldownMultiplier, "fee" to benefits.withdrawalFeeMultiplier))),
                referenceItem("lg_prestige", earningDetails(offer)),
            )
            val actions = buildList {
                if (snapshot.canTrack) add(referenceAction(
                    if (snapshot.goal.rewardId == id) lang.gui("chronicle.untrack") else lang.gui("chronicle.track"), "lg_reward",
                ) {
                    val result = chronicle.track(player.uniqueId, guild.id, snapshot.goal,
                        if (snapshot.goal.rewardId == id) null else id)
                    player.sendMessage(when (result) {
                        ChronicleGoalWrite.SAVED -> lang.msg("chronicle.saved")
                        ChronicleGoalWrite.CONFLICT -> lang.msg("chronicle.conflict")
                        ChronicleGoalWrite.DENIED -> lang.msg("chronicle.denied")
                        ChronicleGoalWrite.INVALID -> lang.msg("chronicle.invalid")
                        ChronicleGoalWrite.UNAVAILABLE -> lang.msg("chapter_two_rewards.unavailable")
                    })
                    detail(id)
                })
                // The existing quote service checks both purchase permissions afresh.
                if (offer.status == RewardOfferStatus.AVAILABLE &&
                    members.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_GUILD_SETTINGS) &&
                    members.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.WITHDRAW_FROM_BANK)
                ) add(referenceAction(lang.gui("chronicle.review"), "lg_bank") {
                    val quote = purchases.quote(player.uniqueId, guild.id, id)
                    if (quote == null) { player.sendMessage(lang.msg("chapter_two_rewards.purchase.no_quote")); detail(id) }
                    else {
                        val token = navigator.currentNavigationToken()
                        GuildRewardPurchaseMenu(player, quote, reward.name, { detail(id) },
                            getKoin().getOrNull<GuildService>()?.getGuild(guild.id)?.guiTheme ?: guild.guiTheme,
                            { valid(token) }).open()
                    }
                })
            }
            show(cards, actions, referenceAction(lang.gui("recruitment.dialog.back"), "lg_back") {
                returnToBrowser?.invoke() ?: tree()
            },
                lang.gui("chronicle.detail", "guild" to guild.name, "level" to snapshot.state.level))
        }
    }

    /** Information only: purchasing and prestiging remain behind existing quotes and confirmations. */
    private fun prestige() {
        load({ prestigeService.overview(guild.id) }) { snapshot, loaded ->
            val overview = loaded ?: run {
                player.sendMessage(lang.msg("chapter_two_rewards.unavailable"))
                return@load
            }
            val retained = snapshot.state.entitlements.offers.filter {
                it.status == RewardOfferStatus.PERMANENT && !it.reward.permanentOnPurchase
            }
            val cards = prestigeCards(overview, retained)
            val hall = getKoin().getOrNull<net.lumalyte.lg.application.services.HallThemeService>()?.let {
                referenceAction(lang.gui("hall.display.title"), "lg_prestige") {
                    navigator.openMenu(GuildHallDisplayMenu(navigator, player, guild, overview.prestigeCount))
                }
            }
            val actions = listOfNotNull(prestigeReview(overview), hall) +
                referenceAction(lang.gui("chronicle.back_tree"), "lg_back") { tree() }
            show(cards, actions, referenceAction(lang.gui("recruitment.dialog.back"), "lg_back", navigator::goBack),
                lang.gui("chronicle.prestige.subtitle"), listOf(
                    referenceAction(lang.gui("chronicle.prestige.overview"), "lg_prestige") { prestigeTab = 0; prestige() },
                    referenceAction(lang.gui("chronicle.prestige.milestones"), "lg_prestige") { prestigeTab = 1; prestige() },
                    referenceAction(lang.gui("chronicle.prestige.legacy"), "lg_prestige") { prestigeTab = 2; prestige() },
                ))
        }
    }

    private fun prestigeCards(overview: PrestigeOverview, retained: List<RewardOffer>): List<GuildDialogItem> {
        val settings = config.loadConfig().progression.prestige
        return when (prestigeTab) {
            0 -> prestigeOverviewCards(overview)
            1 -> (1..overview.maxPrestigeCount).map { tier -> milestoneCard(tier, overview, settings) }
            2 -> prestigeRetainedCards(retained)
            else -> emptyList()
        }
    }

    /** REQ-262: everything a tier gives, with the perks read from the same settings the prestige uses. */
    private fun milestoneCard(tier: Int, overview: PrestigeOverview,
        settings: net.lumalyte.lg.config.PrestigeConfig): GuildDialogItem {
        val perks = settings.perks
        val lines = buildList {
            add(lang.gui("chronicle.prestige.milestone_title", "tier" to PrestigeNumeral.of(tier)))
            add(when {
                tier <= overview.prestigeCount -> lang.gui("chronicle.prestige.milestone_earned")
                tier == overview.prestigeCount + 1 -> lang.gui("chronicle.prestige.milestone_next", "fee" to settings.feeFor(tier - 1))
                else -> lang.gui("chronicle.prestige.milestone_fee", "fee" to settings.feeFor(tier - 1))
            })
            add(lang.gui("chronicle.prestige.milestone_home"))
            add(lang.gui("chronicle.prestige.milestone_keep"))
            add(lang.gui("chronicle.prestige.milestone_theme"))
            if (tier == 1) {
                val all = config.loadConfig()
                if (all.guild.memberCapacity.enabled) add(lang.gui("chronicle.prestige.milestone_member_cap"))
                if (all.discordGuildRoles.enabled) add(lang.gui("chronicle.prestige.milestone_discord"))
            }
            if (tier == perks.secondAllyHomeTier) add(lang.gui("chronicle.prestige.milestone_ally_home"))
            if (tier == perks.extraStallTier) add(lang.gui("chronicle.prestige.milestone_stall"))
            if (tier == perks.secondVaultTier) add(lang.gui("chronicle.prestige.milestone_vault"))
            add(lang.gui("chronicle.prestige.milestone_announce"))
        }
        val badge = net.lumalyte.lg.utils.NexoItemProvider.getItemStackOrFallback("lg_prestige") {
            org.bukkit.inventory.ItemStack.of(org.bukkit.Material.NETHER_STAR)
        }
        return GuildDialogItem(badge, Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines), lines)
    }

    private fun prestigeOverviewCards(overview: PrestigeOverview): List<GuildDialogItem> = buildList {
        add(referenceItem("lg_prestige", listOf(
            lang.gui("chronicle.prestige.title"),
            lang.gui("chronicle.prestige.progress", "count" to PrestigeNumeral.of(overview.prestigeCount),
                "max" to PrestigeNumeral.of(overview.maxPrestigeCount), "level" to overview.currentLevel),
            lang.gui("chronicle.prestige.home_bonus", "count" to overview.prestigeCount),
            overview.nextFee?.let { lang.gui("chronicle.prestige.fee", "fee" to it) }
                ?: lang.gui("chronicle.prestige.maximum"),
        )))
        add(referenceItem("lg_sources", listOf(
            lang.gui("chronicle.prestige.earn_title"),
            lang.gui("chronicle.prestige.earn_level"),
            lang.gui("chronicle.prestige.earn_purchase"),
            lang.gui("chronicle.prestige.earn_confirm"),
        )))
        add(referenceItem("lg_home_add", listOf(
            lang.gui("chronicle.prestige.preserved_title"),
            lang.gui("chronicle.prestige.preserved_homes"),
            lang.gui("chronicle.prestige.preserved_rewards"),
            lang.gui("chronicle.prestige.reset"),
            lang.gui("chronicle.prestige.chapter"),
        )))
    }

    private fun prestigeRetainedCards(retained: List<RewardOffer>): List<GuildDialogItem> {
        if (retained.isEmpty()) return listOf(referenceItem("lg_prestige", listOf(lang.gui("chronicle.prestige.none"))))
        return retained.map { offer ->
            referenceItem("lg_prestige", listOf(
                lang.gui("chronicle.prestige.seal"), Component.text(offer.reward.name), effect(offer.reward.effect),
                lang.gui("chronicle.prestige.retained"),
            ), referenceAction(Component.text(offer.reward.name), "lg_prestige") { detail(offer.reward.id) })
        }
    }

    private fun prestigeReview(overview: PrestigeOverview): GuildDialogAction? = reviewPrestige
        ?.takeIf { overview.enabled && overview.currentLevel == 100 }
        ?.takeIf { overview.prestigeCount < overview.maxPrestigeCount && overview.choices.isNotEmpty() }
        ?.let { referenceAction(lang.gui("chronicle.prestige.review"), "lg_prestige", it) }

    private fun earningDetails(offer: RewardOffer): List<Component> = listOf(
        when {
            offer.reward.permanentOnPurchase -> lang.gui("chronicle.earning.purchase_permanent")
            offer.status == RewardOfferStatus.PERMANENT -> lang.gui("chronicle.prestige.seal")
            else -> lang.gui("chronicle.earning.optional")
        },
        lang.gui("chronicle.earning.level_price", "level" to offer.reward.level, "price" to offer.reward.price),
        lang.gui("chronicle.earning.purchase"),
        when {
            offer.reward.permanentOnPurchase -> lang.gui("chronicle.earning.home_kept")
            offer.status == RewardOfferStatus.PERMANENT -> lang.gui("chronicle.prestige.retained")
            else -> lang.gui("chronicle.earning.retain")
        },
    )

    private fun automatic() {
        load { snapshot ->
            val cards = snapshot.levels.map { level -> referenceItem("lg_bank", listOf(
                lang.gui("chronicle.auto_level", "level" to level.level, "capacity" to level.automaticBankCapacity))) }
            show(cards, emptyList(), referenceAction(lang.gui("chronicle.back_tree"), "lg_back") { tree() },
                lang.gui("chronicle.auto_explanation"))
        }
    }

    private fun valid(token: Long) = player.isOnline && navigator.isNavigationCurrent(token) &&
        members.getMember(player.uniqueId, guild.id) != null

    private fun show(items: List<GuildDialogItem>, actions: List<GuildDialogAction>, back: GuildDialogAction,
        subtitle: Component, tabs: List<GuildDialogAction> = emptyList()) {
        val token = navigator.currentNavigationToken()
        if (!valid(token)) return
        if (!presenter.show(player, GuildDialogPage(lang.gui("chronicle.title"), items, actions, back,
                columns = 2, theme = getKoin().getOrNull<GuildService>()?.getGuild(guild.id)?.guiTheme ?: guild.guiTheme, subtitle = subtitle, bodyActions = tabs),
                { valid(token) }, { player.sendMessage(lang.msg("chapter_two_rewards.unavailable")) })) {
            player.sendMessage(lang.msg("chapter_two_rewards.unavailable"))
        }
    }

    private fun branchLabel(value: ChronicleBranch) = when (value) {
        ChronicleBranch.ALL -> lang.gui("chronicle.all")
        ChronicleBranch.TREASURY -> lang.gui("chronicle.treasury")
        ChronicleBranch.TRAVEL -> lang.gui("chronicle.travel")
        ChronicleBranch.OUTPOSTS -> lang.gui("chronicle.outposts")
    }

    private fun effect(value: RewardEffect): Component = when (value) {
        is RewardEffect.BankCapacity -> lang.gui("chronicle.bank_effect", "amount" to value.amount)
        is RewardEffect.HomeCapacity -> lang.gui("chronicle.home_effect", "amount" to value.amount)
        is RewardEffect.HomeCooldown -> lang.gui("chronicle.cooldown_effect", "percent" to (value.multiplier * 100).toInt(),
        "seconds" to net.lumalyte.lg.domain.values.HomeTravelTimes.countdownSeconds(value.multiplier))
        is RewardEffect.WithdrawalFee -> lang.gui("chronicle.fee_effect", "percent" to (value.multiplier * 100).toInt())
        RewardEffect.AllyHomes -> lang.gui("chronicle.ally_effect")
    }

    private fun impact(value: RewardEffect, current: RewardEntitlements): Component = when (value) {
        is RewardEffect.BankCapacity -> {
            val ceiling = config.loadConfig().bank.maxBankBalance.toLong()
            val after = current.bankCapacity + minOf(value.amount, (ceiling - current.bankCapacity).coerceAtLeast(0))
            lang.gui("chronicle.change", "current" to current.bankCapacity, "after" to after)
        }
        is RewardEffect.HomeCapacity -> lang.gui("chronicle.change", "current" to current.homeCapacity,
            "after" to Math.addExact(current.homeCapacity, value.amount))
        is RewardEffect.HomeCooldown -> lang.gui("chronicle.percent_change",
            "current" to (current.homeCooldownMultiplier * 100).toInt(),
            "after" to (minOf(current.homeCooldownMultiplier, value.multiplier) * 100).toInt())
        is RewardEffect.WithdrawalFee -> lang.gui("chronicle.percent_change",
            "current" to (current.withdrawalFeeMultiplier * 100).toInt(),
            "after" to (minOf(current.withdrawalFeeMultiplier, value.multiplier) * 100).toInt())
        RewardEffect.AllyHomes -> if (current.allyHomes) lang.gui("chronicle.ally_owned") else lang.gui("chronicle.ally_after")
    }

    private fun icon(value: RewardEffect) = when (value) {
        is RewardEffect.BankCapacity, is RewardEffect.WithdrawalFee -> "lg_bank"
        is RewardEffect.HomeCapacity -> "lg_home_add"
        is RewardEffect.HomeCooldown, RewardEffect.AllyHomes -> "lg_home_teleport"
    }
}
