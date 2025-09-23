package net.lumalyte.lg.application.services

import net.lumalyte.lg.interaction.menus.bedrock.BaseBedrockMenu
import org.bukkit.entity.Player
import java.util.logging.Logger

/**
 * Service for handling form validation with reusable validators and error display
 */
interface FormValidationService {

    /**
     * Validates form data and returns validation errors
     * @param data Map of field names to values
     * @param validators Map of field names to validation rules
     * @return List of validation error messages, empty if valid
     */
    fun validate(data: Map<String, Any?>, validators: Map<String, List<Validator>>): List<String>

    /**
     * Validates a single field
     * @param fieldName Name of the field being validated
     * @param value Value to validate
     * @param validators List of validators to apply
     * @return List of validation error messages, empty if valid
     */
    fun validateField(fieldName: String, value: Any?, validators: List<Validator>): List<String>

    /**
     * Displays validation errors to the player and redisplays the form
     * @param player The player to show errors to
     * @param errors List of validation error messages
     * @param reopenForm Function to reopen the form for retry
     * @param localize Function to localize messages
     */
    fun showValidationErrors(player: org.bukkit.entity.Player, errors: List<String>, reopenForm: () -> Unit, localize: (String) -> String)

    /**
     * Gets a validator by type
     * @param type The validator type
     * @param params Parameters for the validator
     * @return The validator instance
     */
    fun getValidator(type: ValidatorType, vararg params: Any): Validator
}

/**
 * Represents a validation rule
 */
interface Validator {
    /**
     * Validates a value
     * @param fieldName Name of the field being validated
     * @param value Value to validate
     * @return ValidationResult indicating success or failure with message
     */
    fun validate(fieldName: String, value: Any?): ValidationResult
}

/**
 * Result of a validation operation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        fun valid() = ValidationResult(true)
        fun invalid(message: String) = ValidationResult(false, message)
    }
}

/**
 * Types of built-in validators
 */
enum class ValidatorType {
    REQUIRED,
    LENGTH,
    RANGE,
    FORMAT,
    BUSINESS_RULE
}

/**
 * Common validation patterns
 */
object ValidationPatterns {
    val GUILD_NAME = Regex("^[a-zA-Z0-9 _-]{1,24}$")
    val PLAYER_NAME = Regex("^[a-zA-Z0-9_]{3,16}$")
    val EMAIL = Regex("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$")
    val URL = Regex("^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$")
}
