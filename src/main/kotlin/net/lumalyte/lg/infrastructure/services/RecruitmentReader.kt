package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Native shells keep their client appearance; text and inventory pages retain guild context. */
internal class RecruitmentReader(
    private val player: Player,
    private val content: RecruitmentBookContent,
    private val valid: () -> Boolean,
    private val back: () -> Unit,
) : KoinComponent {
    private val lang: LangService by inject()
    private val epoch = GuildMenuLifecycle.generation(player)
    private val token = epoch.get()
    private var presentation: Long? = null
    private fun isCurrent(): Boolean = player.isOnline && epoch.get() == token && valid() &&
        (presentation == null || presentation == GuildDialogTransitions.revision(player))
    private val state = RecruitmentReaderState { isCurrent() }
    private val pages = recruitmentReaderPages(content.lines)
    private var bedrock = false
    private var dialog = false

    fun open(
        isBedrock: Boolean,
        supportsDialog: Boolean,
    ) {
        bedrock = isBedrock
        dialog = supportsDialog
        show(0)
    }

    private fun show(page: Int) {
        presentation = null
        val revision = state.beginPage()
        val actions = pageActions(page, revision)
        val current = RecruitmentReaderPage(pages[page], page + 1, pages.size)
        when {
            bedrock -> showBedrock(page, actions)
            dialog -> RecruitmentReaderDialog(player, content, current, actions).show()
            else ->
                RecruitmentReaderInventory.show(
                    player,
                    content,
                    current,
                    actions,
                )
        }
    }

    private fun pageActions(
        page: Int,
        revision: Int,
    ): RecruitmentReaderActions {
        return RecruitmentReaderActions(
            previousLabel = lang.raw("recruitment.previous"),
            nextLabel = lang.raw("recruitment.next"),
            previous = pageAction(page - 1, revision),
            next = pageAction(page + 1, revision),
            back = { returnToMenu(revision, false) },
            dismissed = { returnToMenu(revision, true) },
            valid = ::isCurrent,
            shown = { presentation = it },
        )
    }

    private fun pageAction(
        page: Int,
        revision: Int,
    ): (() -> Unit)? {
        if (page !in pages.indices) return null
        return { dispatch(revision) { show(page) } }
    }

    private fun returnToMenu(
        revision: Int,
        dismissed: Boolean,
    ) {
        dispatch(revision) {
            state.close()
            val top: org.bukkit.inventory.Inventory? = player.openInventory.topInventory
            if (!dismissed || top == null || top.type == org.bukkit.event.inventory.InventoryType.CRAFTING) back()
        }
    }

    @Suppress("TooGenericExceptionCaught") // UI boundary: adapter failures must not reveal data or reopen menus.
    private fun dispatch(revision: Int, action: () -> Unit) {
        Bukkit.getScheduler().runTask(
            net.lumalyte.lg.common.PluginKeys.getPlugin(),
            Runnable {
                try {
                    if (state.consume(revision)) {
                        if (dialog) GuildDialogTransitions.dispatch(player, action) else action()
                    }
                } catch (error: Exception) {
                    state.close()
                    if (dialog) player.closeDialog()
                    player.closeInventory()
                    org.slf4j.LoggerFactory.getLogger(javaClass)
                        .warn("Recruitment reader failed for {}", player.uniqueId, error)
                    player.sendMessage(lang.msg("recruitment.feedback.failed"))
                }
            },
        )
    }

    private fun showBedrock(
        page: Int,
        actions: RecruitmentReaderActions,
    ) {
        val callbacks = mutableListOf(actions.back)
        val text =
            listOfNotNull(
                content.guild?.name,
                content.context.takeIf(String::isNotBlank),
                pages[page],
            ).joinToString("\n")
        val form =
            org.geysermc.cumulus.form.SimpleForm.builder().title("${content.title} ${page + 1}/${pages.size}")
                .content(text)
                .button(content.returnLabel)
        actions.previous?.let {
            form.button(actions.previousLabel)
            callbacks += it
        }
        actions.next?.let {
            form.button(actions.nextLabel)
            callbacks += it
        }
        form.validResultHandler { response -> (callbacks.getOrNull(response.clickedButtonId()) ?: actions.back)() }
            .closedOrInvalidResultHandler { _, _ -> actions.back() }
        val sent = org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.uniqueId, form.build())
        if (!sent) actions.back()
    }
}

internal data class RecruitmentReaderActions(
    val previousLabel: String,
    val nextLabel: String,
    val previous: (() -> Unit)?,
    val next: (() -> Unit)?,
    val back: () -> Unit,
    val dismissed: () -> Unit,
    val valid: () -> Boolean = { true },
    val shown: (Long) -> Unit = {},
)

internal data class RecruitmentReaderPage(val text: String, val number: Int, val count: Int)
