package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag

/**
 * The finite enum name keys are declared in LocaleContractTest's dynamic-key
 * inventory. Keep these lookups typed rather than introducing unbounded keys.
 */
fun LangService.localizedClaimFlagNames(flags: Iterable<Flag>): List<Component> =
    flags.map { this.msg(it.nameKey) }

fun LangService.localizedClaimPermissionNames(permissions: Iterable<ClaimPermission>): List<Component> =
    permissions.map { this.msg(it.nameKey) }

/** Finite Flag enum keys are already declared in the locale contract. */
fun LangService.localizedClaimFlagPlain(flag: Flag): String = this.plain(flag.nameKey)
