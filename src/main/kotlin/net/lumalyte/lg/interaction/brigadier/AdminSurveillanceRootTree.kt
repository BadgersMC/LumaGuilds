package net.lumalyte.lg.interaction.brigadier

/* TEMPORARILY DISABLED - REFERENCES STUB CODE
package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command tree for admin surveillance functionality.
 * Provides comprehensive surveillance and monitoring capabilities.
 */
object AdminSurveillanceRootTree {

    /**
     * Registers the adminsurveillance command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("adminsurveillance")
                .requires(PermissionGuard.requires("lumaguilds.command.adminsurveillance"))
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("open")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("admin")
                        .requires(PermissionGuard.requiresOp())
                        .executes(LumaGuildsAdminExecutors::adminSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("dashboard")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("guild")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("player")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("export")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("alerts")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("audit")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("compliance")
                        .requires(PermissionGuard.requires("lumaguilds.admin.surveillance"))
                        .executes(LumaGuildsAdminExecutors::openSurveillance)
                )
        )
    }
}

*/