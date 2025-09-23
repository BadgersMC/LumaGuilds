package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.*
import org.bukkit.entity.Player
import java.util.logging.Logger

/**
 * Implementation of FormValidationService with common validators
 */
class FormValidationServiceImpl(
    private val logger: Logger
) : FormValidationService {

    override fun validate(data: Map<String, Any?>, validators: Map<String, List<Validator>>): List<String> {
        val errors = mutableListOf<String>()

        for ((fieldName, fieldValidators) in validators) {
            val value = data[fieldName]
            val fieldErrors = validateField(fieldName, value, fieldValidators)
            errors.addAll(fieldErrors)
        }

        return errors
    }

    override fun validateField(fieldName: String, value: Any?, validators: List<Validator>): List<String> {
        val errors = mutableListOf<String>()

        for (validator in validators) {
            val result = validator.validate(fieldName, value)
            if (!result.isValid && result.errorMessage != null) {
                errors.add(result.errorMessage)
            }
        }

        return errors
    }

    override fun showValidationErrors(player: Player, errors: List<String>, reopenForm: () -> Unit, localize: (String) -> String) {
        if (errors.isEmpty()) return

        val errorMessage = errors.joinToString("\n") { "• $it" }

        // Send error message
        player.sendMessage("§c❌ ${localize("form.validation.errors.title")}")
        player.sendMessage("§7$errorMessage")
        player.sendMessage("§e${localize("form.button.retry")}")
        player.sendMessage("§c${localize("form.button.cancel")}")

        // Reopen the form for retry
        reopenForm()
    }

    override fun getValidator(type: ValidatorType, vararg params: Any): Validator {
        return when (type) {
            ValidatorType.REQUIRED -> RequiredValidator()
            ValidatorType.LENGTH -> {
                require(params.size >= 2) { "Length validator requires min and max parameters" }
                LengthValidator(params[0] as Int, params[1] as Int)
            }
            ValidatorType.RANGE -> {
                require(params.size >= 2) { "Range validator requires min and max parameters" }
                RangeValidator(params[0] as Number, params[1] as Number)
            }
            ValidatorType.FORMAT -> {
                require(params.size >= 1) { "Format validator requires regex parameter" }
                FormatValidator(params[0] as Regex)
            }
            ValidatorType.BUSINESS_RULE -> {
                require(params.size >= 1) { "Business rule validator requires function parameter" }
                @Suppress("UNCHECKED_CAST")
                BusinessRuleValidator(params[0] as (String, Any?) -> ValidationResult)
            }
        }
    }
}

/**
 * Validator for required fields
 */
class RequiredValidator : Validator {
    override fun validate(fieldName: String, value: Any?): ValidationResult {
        return when (value) {
            null -> ValidationResult.invalid("$fieldName is required")
            is String -> if (value.trim().isEmpty()) ValidationResult.invalid("$fieldName cannot be empty") else ValidationResult.valid()
            else -> ValidationResult.valid()
        }
    }
}

/**
 * Validator for string length
 */
class LengthValidator(private val minLength: Int, private val maxLength: Int) : Validator {
    override fun validate(fieldName: String, value: Any?): ValidationResult {
        if (value !is String) return ValidationResult.valid()

        return when {
            value.length < minLength -> ValidationResult.invalid("$fieldName must be at least $minLength characters")
            value.length > maxLength -> ValidationResult.invalid("$fieldName must be at most $maxLength characters")
            else -> ValidationResult.valid()
        }
    }
}

/**
 * Validator for numeric range
 */
class RangeValidator(private val min: Number, private val max: Number) : Validator {
    override fun validate(fieldName: String, value: Any?): ValidationResult {
        if (value !is Number) return ValidationResult.valid()

        val doubleValue = value.toDouble()
        return when {
            doubleValue < min.toDouble() -> ValidationResult.invalid("$fieldName must be at least ${min}")
            doubleValue > max.toDouble() -> ValidationResult.invalid("$fieldName must be at most ${max}")
            else -> ValidationResult.valid()
        }
    }
}

/**
 * Validator for regex format
 */
class FormatValidator(private val pattern: Regex) : Validator {
    override fun validate(fieldName: String, value: Any?): ValidationResult {
        if (value !is String) return ValidationResult.valid()

        return if (pattern.matches(value)) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid("$fieldName format is invalid")
        }
    }
}

/**
 * Validator for custom business rules
 */
class BusinessRuleValidator(private val rule: (String, Any?) -> ValidationResult) : Validator {
    override fun validate(fieldName: String, value: Any?): ValidationResult {
        return rule(fieldName, value)
    }
}
