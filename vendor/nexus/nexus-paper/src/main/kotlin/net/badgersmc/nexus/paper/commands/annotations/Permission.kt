package net.badgersmc.nexus.paper.commands.annotations

/** Specifies the permission node required to execute this subcommand (e.g. "plugin.command.sub"). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Permission(val value: String)
