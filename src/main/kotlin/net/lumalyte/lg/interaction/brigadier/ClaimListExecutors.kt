package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.lumalyte.lg.application.services.ClaimService
import net.lumalyte.lg.application.services.MessageService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Execution handlers for claimlist Brigadier command.
 * Displays paginated list of claims.
 */
object ClaimListExecutors : KoinComponent {

    private val claimService: ClaimService by inject()
    private val messageService: MessageService by inject()

    private const val CLAIMS_PER_PAGE = 10

    /**
     * Lists claims for the first page (default behavior).
     */
    fun list(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claimlist")
    }

    /**
     * Lists claims for a specific page.
     */
    fun listPage(context: CommandContext<CommandSourceStack>): Int {
        val page = IntegerArgumentType.getInteger(context, "page")
        return listPage(context, page)
    }

    private fun listPage(context: CommandContext<CommandSourceStack>, page: Int): Int {
        // TODO: Implement claim listing with pagination
        // This should query all visible claims and display them in pages
        return LegacyCommandRouter.routeToLegacy(context, "claimlist", arrayOf(page.toString()))
    }
}
