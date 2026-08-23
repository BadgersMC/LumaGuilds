package net.lumalyte.lg.utils

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ValidationResult

/** Renders typed guild-tag validation failures at string-only UI boundaries. */
object GuildTagValidationMessages {
    fun legacy(lang: LangService, failure: GuildTagValidator.Failure): String = when (failure) {
        is GuildTagValidator.Failure.InteractiveTag ->
            lang.legacy("command.guild.tag.validation.interactive", "tag" to failure.tagName)
        GuildTagValidator.Failure.InappropriateContent ->
            lang.legacy("command.guild.tag.validation.inappropriate")
    }

    fun invalid(lang: LangService, failure: GuildTagValidator.Failure): ValidationResult =
        ValidationResult.invalid(legacy(lang, failure))
}
