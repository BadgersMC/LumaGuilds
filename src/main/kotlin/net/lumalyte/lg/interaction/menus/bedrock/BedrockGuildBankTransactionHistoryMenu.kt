package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.inject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Logger

/** Bounded, filterable Bedrock view of authoritative guild-bank transactions. */
class BedrockGuildBankTransactionHistoryMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val bankService: BankService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    private var typeFilter: TransactionType? = null
    private var memberFilter: UUID? = null
    private var dateRange: HistoryDateRange = HistoryDateRange.ALL
    private var searchQuery: String = ""
    private var page: Int = 0

    override fun getForm(): Form {
        val transactions = bankService.getTransactionHistory(guild.id, HISTORY_LOAD_LIMIT)
            .sortedByDescending(BankTransaction::timestamp)
        val actorNames = resolveActorNames(transactions)
        val members = memberService.getGuildMembers(guild.id)
            .map { member ->
                member.playerId to (
                    actorNames[member.playerId]
                        ?: Bukkit.getOfflinePlayer(member.playerId).name
                        ?: lang.bedrock("bedrock.bank.history.unknown_player")
                    )
            }
            .sortedBy { it.second.lowercase() }

        val filtered = transactions.filter { transaction ->
            matches(transaction, actorNames)
        }
        val pageCount = maxOf(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pageCount - 1)
        val pageItems = filtered.drop(page * PAGE_SIZE).take(PAGE_SIZE)

        val typeOptions = listOf(lang.bedrock("bedrock.bank.history.filter.type_all")) +
            TransactionType.entries.map(::transactionTypeLabel)
        val memberOptions = listOf(lang.bedrock("bedrock.bank.history.filter.member_all")) +
            members.map { it.second }
        val dateOptions = HistoryDateRange.entries.map(::dateRangeLabel)
        val pageOptions = (1..pageCount).map { value ->
            lang.bedrock("bedrock.bank.history.filter.page_option", "page" to value)
        }

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.bank.history.title", "guild" to guild.name))
            .label(
                lang.bedrock(
                    "bedrock.bank.history.filter.summary",
                    "shown" to pageItems.size,
                    "matching" to filtered.size,
                    "loaded" to transactions.size,
                    "limit" to HISTORY_LOAD_LIMIT
                )
            )
            .dropdown(
                lang.bedrock("bedrock.bank.history.filter.type"),
                typeOptions,
                typeFilter?.let { TransactionType.entries.indexOf(it) + 1 } ?: 0
            )
            .dropdown(
                lang.bedrock("bedrock.bank.history.filter.member"),
                memberOptions,
                memberFilter?.let { selected ->
                    members.indexOfFirst { it.first == selected }.takeIf { it >= 0 }?.plus(1)
                } ?: 0
            )
            .dropdown(
                lang.bedrock("bedrock.bank.history.filter.date"),
                dateOptions,
                HistoryDateRange.entries.indexOf(dateRange)
            )
            .input(
                lang.bedrock("bedrock.bank.history.filter.search"),
                lang.bedrock("bedrock.bank.history.filter.search_placeholder"),
                searchQuery
            )
            .dropdown(
                lang.bedrock("bedrock.bank.history.filter.page"),
                pageOptions,
                page
            )
            .label(renderPage(pageItems, actorNames, pageCount, filtered.size))
            .validResultHandler { response ->
                val typeIndex = response.next() as? Int ?: 0
                val memberIndex = response.next() as? Int ?: 0
                val dateIndex = response.next() as? Int ?: 0
                val submittedSearch = (response.next() as? String ?: searchQuery).trim()
                val submittedPage = response.next() as? Int ?: page
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    typeFilter = if (typeIndex == 0) null else TransactionType.entries.getOrNull(typeIndex - 1)
                    memberFilter = if (memberIndex == 0) null else members.getOrNull(memberIndex - 1)?.first
                    dateRange = HistoryDateRange.entries.getOrElse(dateIndex) { HistoryDateRange.ALL }
                    searchQuery = submittedSearch
                    page = submittedPage.coerceAtLeast(0)
                    onFormResponseReceived()
                    open()
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

    private fun matches(transaction: BankTransaction, actorNames: Map<UUID, String>): Boolean {
        if (typeFilter != null && transaction.type != typeFilter) return false
        if (memberFilter != null && transaction.actorId != memberFilter) return false
        val cutoff = dateRange.cutoffSeconds?.let { Instant.now().minusSeconds(it) }
        if (cutoff != null && transaction.timestamp.isBefore(cutoff)) return false
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            val actor = actorNames[transaction.actorId]?.lowercase().orEmpty()
            val description = transaction.description?.lowercase().orEmpty()
            if (query !in actor && query !in description) return false
        }
        return true
    }

    private fun resolveActorNames(transactions: List<BankTransaction>): Map<UUID, String> =
        transactions.map(BankTransaction::actorId).distinct().associateWith { actorId ->
            Bukkit.getOfflinePlayer(actorId).name
                ?: lang.bedrock("bedrock.bank.history.unknown_player")
        }

    private fun renderPage(
        transactions: List<BankTransaction>,
        actorNames: Map<UUID, String>,
        pageCount: Int,
        matchingCount: Int
    ): String {
        if (transactions.isEmpty()) return lang.bedrock("bedrock.bank.history.empty")
        val formatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.bank.history.date_format"))
            .withZone(ZoneId.systemDefault())
        val rows = transactions.joinToString("\n\n") { transaction ->
            renderTransaction(transaction, actorNames[transaction.actorId].orEmpty(), formatter)
        }
        return listOf(
            lang.bedrock(
                "bedrock.bank.history.page_header",
                "page" to page + 1,
                "pages" to pageCount,
                "count" to matchingCount
            ),
            rows
        ).joinToString("\n\n")
    }

    private fun renderTransaction(
        transaction: BankTransaction,
        actorName: String,
        formatter: DateTimeFormatter
    ): String {
        val description = transaction.description.orEmpty()
        val timestamp = formatter.format(transaction.timestamp)
        return when (transaction.type) {
            TransactionType.DEPOSIT -> lang.bedrock(
                "bedrock.bank.history.row.deposit",
                "timestamp" to timestamp,
                "player" to actorName,
                "amount" to transaction.amount,
                "description" to description
            )
            TransactionType.WITHDRAWAL -> lang.bedrock(
                "bedrock.bank.history.row.withdrawal",
                "timestamp" to timestamp,
                "player" to actorName,
                "amount" to "-${transaction.amount}",
                "description" to description
            )
            TransactionType.FEE, TransactionType.DEDUCTION -> lang.bedrock(
                "bedrock.bank.history.row.neutral",
                "timestamp" to timestamp,
                "player" to actorName,
                "amount" to "-${transaction.amount}",
                "description" to "${transactionTypeLabel(transaction.type)}: $description"
            )
        }
    }

    private fun transactionTypeLabel(type: TransactionType): String = when (type) {
        TransactionType.DEPOSIT -> lang.bedrock("bedrock.bank.history.filter.type_deposit")
        TransactionType.WITHDRAWAL -> lang.bedrock("bedrock.bank.history.filter.type_withdrawal")
        TransactionType.FEE -> lang.bedrock("bedrock.bank.history.filter.type_fee")
        TransactionType.DEDUCTION -> lang.bedrock("bedrock.bank.history.filter.type_deduction")
    }

    private fun dateRangeLabel(range: HistoryDateRange): String = when (range) {
        HistoryDateRange.ALL -> lang.bedrock("bedrock.bank.history.filter.date_all")
        HistoryDateRange.LAST_24_HOURS -> lang.bedrock("bedrock.bank.history.filter.date_24h")
        HistoryDateRange.LAST_7_DAYS -> lang.bedrock("bedrock.bank.history.filter.date_7d")
        HistoryDateRange.LAST_30_DAYS -> lang.bedrock("bedrock.bank.history.filter.date_30d")
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit

    private enum class HistoryDateRange(val cutoffSeconds: Long?) {
        ALL(null),
        LAST_24_HOURS(24L * 60 * 60),
        LAST_7_DAYS(7L * 24 * 60 * 60),
        LAST_30_DAYS(30L * 24 * 60 * 60)
    }

    companion object {
        private const val HISTORY_LOAD_LIMIT = 500
        private const val PAGE_SIZE = 10
    }
}
