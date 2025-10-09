package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import com.mojang.brigadier.arguments.StringArgumentType
import net.lumalyte.lg.application.services.ClaimService
import net.lumalyte.lg.application.services.MessageService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Execution handlers for claim-related Brigadier commands.
 * Contains the business logic for all claim command operations.
 */
object ClaimExecutors : KoinComponent {

    private val claimService: ClaimService by inject()
    private val messageService: MessageService by inject()

    // TODO: Implement trust command - add player to claim trust list
    fun trust(context: CommandContext<CommandSourceStack>): Int {
        val player = StringArgumentType.getString(context, "player")
        return LegacyCommandRouter.routeToLegacy(context, "claim trust", arrayOf(player))
    }

    // TODO: Implement untrust command - remove player from claim trust list
    fun untrust(context: CommandContext<CommandSourceStack>): Int {
        val player = StringArgumentType.getString(context, "player")
        return LegacyCommandRouter.routeToLegacy(context, "claim untrust", arrayOf(player))
    }

    // TODO: Implement trustall command - trust all online players
    fun trustAll(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claim trustall")
    }

    // TODO: Implement untrustall command - untrust all players
    fun untrustAll(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claim untrustall")
    }

    // TODO: Implement trustlist command - display trusted players
    fun trustList(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claim trustlist")
    }

    // TODO: Implement addflag command - add flag to claim
    fun addFlag(context: CommandContext<CommandSourceStack>): Int {
        val flag = StringArgumentType.getString(context, "flag")
        return LegacyCommandRouter.routeToLegacy(context, "claim addflag", arrayOf(flag))
    }

    // TODO: Implement removeflag command - remove flag from claim
    fun removeFlag(context: CommandContext<CommandSourceStack>): Int {
        val flag = StringArgumentType.getString(context, "flag")
        return LegacyCommandRouter.routeToLegacy(context, "claim removeflag", arrayOf(flag))
    }

    // TODO: Implement setDescription command - set claim description
    fun setDescription(context: CommandContext<CommandSourceStack>): Int {
        val text = StringArgumentType.getString(context, "text")
        return LegacyCommandRouter.routeToLegacy(context, "claim description", arrayOf(text))
    }

    // TODO: Implement info command - display claim information
    fun info(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claim info")
    }

    // TODO: Implement rename command - rename claim
    fun rename(context: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(context, "name")
        return LegacyCommandRouter.routeToLegacy(context, "claim rename", arrayOf(name))
    }

    // TODO: Implement remove command - delete claim
    fun remove(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claim remove")
    }

    // TODO: Implement partitionsList command - list claim partitions
    fun partitionsList(context: CommandContext<CommandSourceStack>): Int {
        return LegacyCommandRouter.routeToLegacy(context, "claim partitions list")
    }

    // TODO: Implement partitionsAdd command - add partition to claim
    fun partitionsAdd(context: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(context, "name")
        return LegacyCommandRouter.routeToLegacy(context, "claim partitions add", arrayOf(name))
    }

    // TODO: Implement partitionsRemove command - remove partition from claim
    fun partitionsRemove(context: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(context, "name")
        return LegacyCommandRouter.routeToLegacy(context, "claim partitions remove", arrayOf(name))
    }
}
