package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.TextDirection
import net.lumalyte.lg.config.BedrockConfig
import net.lumalyte.lg.config.ImageSource
import net.lumalyte.lg.infrastructure.services.BedrockLocalizationServiceFloodgate
import org.bukkit.entity.Player
import org.geysermc.cumulus.component.*
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.response.CustomFormResponse
import org.geysermc.cumulus.util.FormImage
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Adds a button with image to a SimpleForm using Cumulus FormImage API
 * Creates image with fallback options if primary image fails
 */
fun org.geysermc.cumulus.form.SimpleForm.Builder.addButtonWithImage(
    config: BedrockConfig,
    text: String,
    imageUrl: String,
    imagePath: String,
    fallbackUrl: String? = null,
    fallbackPath: String? = null
): org.geysermc.cumulus.form.SimpleForm.Builder {
    // Try to create image with primary options
    val primaryImage = BedrockFormUtils.createFormImage(config, imageUrl, imagePath)

    // If primary image fails and fallback is provided, try fallback
    val finalImage = if (primaryImage == null && (fallbackUrl != null || fallbackPath != null)) {
        BedrockFormUtils.createFormImage(config, fallbackUrl ?: "", fallbackPath ?: "")
    } else {
        primaryImage
    }

    // Add button with or without image
    return if (finalImage != null) {
        this.button(text, finalImage)
    } else {
        this.button(text)
    }
}

/**
 * Utility class for managing Bedrock form components and images
 */
object BedrockFormUtils {

    /**
     * Creates a form image based on configuration using Cumulus FormImage API
     * Prioritizes URL if available, falls back to PATH, or returns null if neither is valid
     */
    fun createFormImage(config: BedrockConfig, imageUrl: String, imagePath: String): FormImage? {
        // Use URL if provided and not empty
        if (!imageUrl.isBlank()) {
            return try {
                FormImage.of(FormImage.Type.URL, imageUrl)
            } catch (e: Exception) {
                // Log error if URL is invalid, but continue to PATH fallback
                println("Invalid URL for FormImage: $imageUrl - ${e.message}")
                null
            }
        }

        // Fallback to PATH if URL is not available
        if (!imagePath.isBlank()) {
            return try {
                FormImage.of(FormImage.Type.PATH, imagePath)
            } catch (e: Exception) {
                // Log error if PATH is invalid
                println("Invalid PATH for FormImage: $imagePath - ${e.message}")
                null
            }
        }

        // Return null if neither URL nor PATH is valid
        // Note: config parameter is reserved for future validation rules (e.g., URL whitelisting)
        return null
    }

    /**
     * Creates a form image with fallback to default
     */
    @Suppress("unused")
    fun createFormImageWithFallback(
        _config: BedrockConfig, // Reserved for future validation rules
        _imageUrl: String, // Reserved for future implementation
        _imagePath: String, // Reserved for future implementation
        _fallbackUrl: String? = null, // Reserved for future implementation
        _fallbackPath: String? = null // Reserved for future implementation
    ): Any? {
        return null // Placeholder for future implementation
    }

    // Enhanced Component Builders with Validation and Convenience Features

    /**
     * Creates an InputComponent with validation and enhanced features
     */
    fun createInputComponent(
        label: String,
        placeholder: String = "",
        defaultValue: String = "",
        maxLength: Int = 256,
        validator: ((String) -> String?)? = null
    ): InputComponent {
        // Validate input length
        val finalDefaultValue = if (defaultValue.length > maxLength) {
            println("Warning: InputComponent default value truncated from ${defaultValue.length} to $maxLength characters")
            defaultValue.take(maxLength)
        } else {
            defaultValue
        }

        // Validate placeholder length
        val finalPlaceholder = if (placeholder.length > maxLength) {
            println("Warning: InputComponent placeholder truncated from ${placeholder.length} to $maxLength characters")
            placeholder.take(maxLength)
        } else {
            placeholder
        }

        // Apply custom validation if provided
        if (validator != null && finalDefaultValue.isNotBlank()) {
            val error = validator(finalDefaultValue)
            if (error != null) {
                println("Warning: InputComponent default value validation failed: $error")
            }
        }

        return InputComponent.of(label, finalPlaceholder, finalDefaultValue)
    }

    /**
     * Creates a DropdownComponent with current value selection
     */
    fun createDropdownComponent(
        label: String,
        options: List<String>,
        defaultValue: String? = null,
        selectByValue: Boolean = true
    ): DropdownComponent {
        require(options.isNotEmpty()) { "DropdownComponent requires at least one option" }

        val defaultIndex = when {
            defaultValue == null -> 0
            selectByValue -> options.indexOf(defaultValue).takeIf { it >= 0 } ?: 0
            else -> defaultValue.toIntOrNull()?.takeIf { it in options.indices } ?: 0
        }

        return DropdownComponent.of(label, options, defaultIndex)
    }

    /**
     * Creates a DropdownComponent using builder pattern for complex option sets
     */
    fun createDropdownComponent(
        label: String,
        builder: DropdownComponent.Builder.() -> Unit
    ): DropdownComponent {
        val dropdownBuilder = DropdownComponent.builder(label)
        builder(dropdownBuilder)
        return dropdownBuilder.build()
    }

    /**
     * Creates a SliderComponent with range validation
     */
    fun createSliderComponent(
        label: String,
        min: Float,
        max: Float,
        step: Float = 1.0f,
        defaultValue: Float = min,
        validator: ((Float) -> String?)? = null
    ): SliderComponent {
        require(min <= max) { "SliderComponent min ($min) must be <= max ($max)" }
        require(step > 0) { "SliderComponent step must be positive" }
        require(min <= defaultValue && defaultValue <= max) {
            "SliderComponent defaultValue ($defaultValue) must be between min ($min) and max ($max)"
        }

        // Validate against custom validator
        if (validator != null) {
            val error = validator(defaultValue)
            if (error != null) {
                println("Warning: SliderComponent default value validation failed: $error")
            }
        }

        return SliderComponent.of(label, min, max, step, defaultValue)
    }

    /**
     * Creates a ToggleComponent with enhanced state management
     */
    @Suppress("unused")
    fun createToggleComponent(
        label: String,
        defaultValue: Boolean = false,
        _trueLabel: String? = null, // Reserved for future toggle text customization
        _falseLabel: String? = null // Reserved for future toggle text customization
    ): ToggleComponent {
        return ToggleComponent.of(label, defaultValue)
    }

    /**
     * Creates a StepSliderComponent with option building
     */
    fun createStepSliderComponent(
        label: String,
        options: List<String>,
        defaultIndex: Int = 0
    ): StepSliderComponent {
        require(options.isNotEmpty()) { "StepSliderComponent requires at least one option" }
        require(defaultIndex in options.indices) {
            "StepSliderComponent defaultIndex ($defaultIndex) must be valid for options list of size ${options.size}"
        }

        return StepSliderComponent.of(label, options, defaultIndex)
    }

    /**
     * Creates a StepSliderComponent using builder pattern
     */
    fun createStepSliderComponent(
        label: String,
        builder: StepSliderComponent.Builder.() -> Unit
    ): StepSliderComponent {
        val stepSliderBuilder = StepSliderComponent.builder(label)
        builder(stepSliderBuilder)
        return stepSliderBuilder.build()
    }


    /**
     * Gets guild-specific icon based on type
     */
    fun getGuildIcon(config: BedrockConfig, iconType: GuildIconType): Pair<String, String> {
        return when (iconType) {
            GuildIconType.MEMBERS -> Pair(config.guildMembersIconUrl, config.guildMembersIconPath)
            GuildIconType.SETTINGS -> Pair(config.guildSettingsIconUrl, config.guildSettingsIconPath)
            GuildIconType.BANK -> Pair(config.guildBankIconUrl, config.guildBankIconPath)
            GuildIconType.WARS -> Pair(config.guildWarsIconUrl, config.guildWarsIconPath)
            GuildIconType.HOME -> Pair(config.guildHomeIconUrl, config.guildHomeIconPath)
            GuildIconType.TAG -> Pair(config.guildTagIconUrl, config.guildTagIconPath)
        }
    }

    /**
     * Gets action-specific icon based on type
     */
    fun getActionIcon(config: BedrockConfig, iconType: ActionIconType): Pair<String, String> {
        return when (iconType) {
            ActionIconType.CONFIRM -> Pair(config.confirmIconUrl, config.confirmIconPath)
            ActionIconType.CANCEL -> Pair(config.cancelIconUrl, config.cancelIconPath)
            ActionIconType.BACK -> Pair(config.backIconUrl, config.backIconPath)
            ActionIconType.CLOSE -> Pair(config.closeIconUrl, config.closeIconPath)
            ActionIconType.EDIT -> Pair(config.editIconUrl, config.editIconPath)
            ActionIconType.DELETE -> Pair(config.deleteIconUrl, config.deleteIconPath)
        }
    }

    // Legacy component creation methods (maintained for backward compatibility)
    // These use the enhanced component builders internally for consistency

    /**
     * Creates a dropdown component for CustomForm
     * @deprecated Use createDropdownComponent() for enhanced features
     */
    @Deprecated("Use createDropdownComponent() for enhanced features", ReplaceWith("createDropdownComponent"))
    fun createDropdown(
        label: String,
        defaultOption: String,
        vararg options: String
    ): CustomForm.Builder.() -> CustomForm.Builder = {
        this.dropdown(label, defaultOption, *options)
    }

    /**
     * Creates an input component for CustomForm
     * @deprecated Use createInputComponent() for enhanced features and validation
     */
    @Deprecated("Use createInputComponent() for enhanced features and validation", ReplaceWith("createInputComponent"))
    fun createInput(
        label: String,
        placeholder: String,
        defaultValue: String = ""
    ): CustomForm.Builder.() -> CustomForm.Builder = {
        this.input(label, placeholder, defaultValue)
    }

    /**
     * Creates a toggle component for CustomForm
     * @deprecated Use createToggleComponent() for enhanced features
     */
    @Deprecated("Use createToggleComponent() for enhanced features", ReplaceWith("createToggleComponent"))
    fun createToggle(
        label: String,
        defaultValue: Boolean = false
    ): CustomForm.Builder.() -> CustomForm.Builder = {
        this.toggle(label, defaultValue)
    }

    /**
     * Creates a slider component for CustomForm
     * @deprecated Use createSliderComponent() for enhanced features and validation
     */
    @Deprecated("Use createSliderComponent() for enhanced features and validation", ReplaceWith("createSliderComponent"))
    fun createSlider(
        label: String,
        min: Float,
        max: Float,
        step: Float,
        defaultValue: Float
    ): CustomForm.Builder.() -> CustomForm.Builder = {
        this.slider(label, min, max, step, defaultValue)
    }

    /**
     * Creates a step slider component for CustomForm
     * @deprecated Use createStepSliderComponent() for enhanced features and validation
     */
    @Deprecated("Use createStepSliderComponent() for enhanced features and validation", ReplaceWith("createStepSliderComponent"))
    fun createStepSlider(
        label: String,
        vararg options: String,
        defaultOptionIndex: Int = 0
    ): CustomForm.Builder.() -> CustomForm.Builder = {
        this.stepSlider(label, defaultOptionIndex, *options)
    }

    /**
     * Creates a label component for CustomForm
     */
    fun createLabel(text: String): CustomForm.Builder.() -> CustomForm.Builder = {
        this.label(text)
    }

    // Utility methods for common component patterns

    /**
     * Creates a validated input component for numbers
     */
    fun createNumberInput(
        label: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        defaultValue: Int = 0
    ): InputComponent {
        return createInputComponent(
            label = label,
            defaultValue = defaultValue.toString(),
            validator = { value ->
                val number = value.toIntOrNull()
                when {
                    number == null -> "Must be a valid number"
                    number < min -> "Must be at least $min"
                    number > max -> "Must be at most $max"
                    else -> null
                }
            }
        )
    }

    /**
     * Creates a dropdown component with translated options
     */
    fun createLocalizedDropdown(
        label: String,
        options: Map<String, String>, // key -> display text
        defaultKey: String? = options.keys.firstOrNull()
    ): DropdownComponent {
        val optionList = options.values.toList()
        val defaultIndex = defaultKey?.let { key -> options.keys.indexOf(key) } ?: 0

        return DropdownComponent.of(label, optionList, defaultIndex)
    }

    /**
     * Creates a slider component for percentage values (0-100)
     */
    fun createPercentageSlider(
        label: String,
        defaultValue: Float = 0f,
        step: Float = 5f
    ): SliderComponent {
        return createSliderComponent(
            label = label,
            min = 0f,
            max = 100f,
            step = step,
            defaultValue = defaultValue.coerceIn(0f, 100f)
        )
    }

    /**
     * Creates a section header with consistent formatting
     */
    fun createSectionHeader(title: String): LabelComponent {
        return LabelComponent.of("<yellow><bold>$title")
    }

    /**
     * Creates an error message label
     */
    fun createErrorLabel(message: String): LabelComponent {
        return LabelComponent.of("<red>[ERROR] $message")
    }

    /**
     * Creates a success message label
     */
    fun createSuccessLabel(message: String): LabelComponent {
        return LabelComponent.of("<green>[SUCCESS] $message")
    }

    fun createInfoLabel(message: String): LabelComponent {
        return LabelComponent.of("<aqua>[i] $message")
    }

    /**
     * Placeholder for future image functionality
     */
    fun getPlaceholderIcon(): String {
        return "https://via.placeholder.com/64x64/4CAF50/FFFFFF?text=ICON"
    }

    // Form Response Data Extraction Utilities

    /**
     * Sealed class hierarchy for typed form response data
     */
    sealed class FormResponseData {
        data class InputData(val value: String) : FormResponseData()
        data class DropdownData(val selectedIndex: Int, val selectedValue: String) : FormResponseData()
        data class SliderData(val value: Float) : FormResponseData()
        data class ToggleData(val value: Boolean) : FormResponseData()
        data class StepSliderData(val selectedIndex: Int, val selectedValue: String) : FormResponseData()
    }

    /**
     * Data class for processed form response with validation results
     */
    data class ProcessedFormData(
        val responses: Map<String, FormResponseData>,
        val isValid: Boolean,
        val validationErrors: Map<String, String>,
        val businessData: Map<String, Any?>
    )

    /**
     * Interface for form response validators
     */
    interface FormResponseValidator {
        fun validate(value: Any?, context: ValidationContext): String?
    }

    /**
     * Context information for validation
     */
    data class ValidationContext(
        val playerId: String,
        val formData: Map<String, FormResponseData> = emptyMap(),
        val config: BedrockConfig? = null
    )

    /**
     * Built-in validators for common validation scenarios
     */
    object Validators {

        /**
         * Validates that a string is not empty
         */
        val notEmpty: FormResponseValidator = object : FormResponseValidator {
            override fun validate(value: Any?, context: ValidationContext): String? {
                return if (value is String && value.isBlank()) "Value cannot be empty" else null
            }
        }

        /**
         * Validates string length
         */
        class StringLengthValidator(
            private val min: Int,
            private val max: Int
        ) : FormResponseValidator {
            override fun validate(value: Any?, context: ValidationContext): String? {
                if (value !is String) return "Value must be a string"
                return when {
                    value.length < min -> "Must be at least $min characters"
                    value.length > max -> "Must be at most $max characters"
                    else -> null
                }
            }
        }

        /**
         * Validates numeric range
         */
        class RangeValidator(
            private val min: Float,
            private val max: Float
        ) : FormResponseValidator {
            override fun validate(value: Any?, context: ValidationContext): String? {
                if (value !is Number) return "Value must be a number"
                val floatValue = value.toFloat()
                return when {
                    floatValue < min -> "Must be at least $min"
                    floatValue > max -> "Must be at most $max"
                    else -> null
                }
            }
        }

        /**
         * Validates regex pattern
         */
        class RegexValidator(
            private val pattern: Regex,
            private val errorMessage: String = "Invalid format"
        ) : FormResponseValidator {
            override fun validate(value: Any?, context: ValidationContext): String? {
                if (value !is String) return "Value must be a string"
                return if (!pattern.matches(value)) errorMessage else null
            }
        }

        /**
         * Composite validator that runs multiple validators
         */
        class CompositeValidator(
            private val validators: List<FormResponseValidator>
        ) : FormResponseValidator {
            override fun validate(value: Any?, context: ValidationContext): String? {
                return validators.firstOrNull { it.validate(value, context) != null }?.validate(value, context)
            }
        }

        /**
         * Creates a validator that checks if a value is required
         */
        fun required(): FormResponseValidator = notEmpty

        /**
         * Creates a validator for string length
         */
        fun length(min: Int, max: Int): FormResponseValidator = StringLengthValidator(min, max)

        /**
         * Creates a validator for numeric range
         */
        fun range(min: Float, max: Float): FormResponseValidator = RangeValidator(min, max)

        /**
         * Creates a validator for regex patterns
         */
        fun pattern(pattern: String, errorMessage: String = "Invalid format"): FormResponseValidator =
            RegexValidator(Regex(pattern), errorMessage)

        /**
         * Combines multiple validators
         */
        fun combine(vararg validators: FormResponseValidator): FormResponseValidator =
            CompositeValidator(validators.toList())
    }

    /**
     * Extracts typed data from a CustomFormResponse
     */
    fun extractFormResponseData(
        response: CustomFormResponse,
        fieldMapping: Map<String, FieldType>,
        validators: Map<String, FormResponseValidator> = emptyMap(),
        validationContext: ValidationContext = ValidationContext("")
    ): ProcessedFormData {
        val extractedData = mutableMapOf<String, FormResponseData>()
        val errors = mutableMapOf<String, String>()

        // Reset response iterator to start
        response.reset()
        response.includeLabels(false)

        // Process each field according to the mapping
        for ((fieldName, fieldType) in fieldMapping) {
            try {
                when (fieldType) {
                    FieldType.INPUT -> {
                        val value = response.asInput()
                        extractedData[fieldName] = FormResponseData.InputData(value ?: "")

                        // Apply validation
                        validators[fieldName]?.let { validator ->
                            validator.validate(value, validationContext)?.let { error ->
                                errors[fieldName] = error
                            }
                        }
                    }
                    FieldType.DROPDOWN -> {
                        val selectedIndex = response.asDropdown()
                        val selectedValue = response.asDropdown().toString() // This is simplified

                        extractedData[fieldName] = FormResponseData.DropdownData(selectedIndex, selectedValue)

                        // Apply validation
                        validators[fieldName]?.let { validator ->
                            validator.validate(selectedIndex, validationContext)?.let { error ->
                                errors[fieldName] = error
                            }
                        }
                    }
                    FieldType.SLIDER -> {
                        val value = response.asSlider()
                        extractedData[fieldName] = FormResponseData.SliderData(value)

                        // Apply validation
                        validators[fieldName]?.let { validator ->
                            validator.validate(value, validationContext)?.let { error ->
                                errors[fieldName] = error
                            }
                        }
                    }
                    FieldType.TOGGLE -> {
                        val value = response.asToggle()
                        extractedData[fieldName] = FormResponseData.ToggleData(value)

                        // Apply validation
                        validators[fieldName]?.let { validator ->
                            validator.validate(value, validationContext)?.let { error ->
                                errors[fieldName] = error
                            }
                        }
                    }
                    FieldType.STEP_SLIDER -> {
                        val selectedIndex = response.asStepSlider()
                        val selectedValue = response.asStepSlider().toString() // This is simplified

                        extractedData[fieldName] = FormResponseData.StepSliderData(selectedIndex, selectedValue)

                        // Apply validation
                        validators[fieldName]?.let { validator ->
                            validator.validate(selectedIndex, validationContext)?.let { error ->
                                errors[fieldName] = error
                            }
                        }
                    }
                    FieldType.LABEL -> {
                        // Labels don't have values, skip
                        response.skip()
                    }
                }
            } catch (e: Exception) {
                errors[fieldName] = "Error processing field: ${e.message}"
            }
        }

        return ProcessedFormData(
            responses = extractedData,
            isValid = errors.isEmpty(),
            validationErrors = errors,
            businessData = extractedData.mapValues { it.value.toBusinessValue() }
        )
    }

    /**
     * Extension function to extract data from CustomFormResponse with simplified API
     */
    fun CustomFormResponse.extractData(
        vararg fields: Pair<String, FieldType>,
        validators: Map<String, FormResponseValidator> = emptyMap(),
        validationContext: ValidationContext = ValidationContext("")
    ): ProcessedFormData {
        val fieldMapping = fields.toMap()
        return extractFormResponseData(this, fieldMapping, validators, validationContext)
    }

    /**
     * Helper method to get all response values as a simple map
     */
    fun CustomFormResponse.getAllValues(): Map<Int, Any?> {
        val values = mutableMapOf<Int, Any?>()

        reset()
        includeLabels(false)

        var index = 0
        while (hasNext()) {
            try {
                val value = next() as Any?
                values[index] = value
            } catch (e: Exception) {
                values[index] = null
            }
            index++
        }

        return values
    }

    /**
     * Helper method to get response value at specific index
     */
    fun CustomFormResponse.getValueAt(index: Int): Any? {
        return valueAt(index)
    }

    // Localization-Aware Component Builders

    /**
     * Creates a localized InputComponent with validation and enhanced features
     */
    fun createLocalizedInputComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        placeholderKey: String? = null,
        defaultValue: String = "",
        maxLength: Int = 256,
        validator: ((String) -> String?)? = null
    ): InputComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)
        val placeholder = placeholderKey?.let { bedrockLocalization.getBedrockString(player, it) } ?: ""

        return createInputComponent(label, placeholder, defaultValue, maxLength, validator)
    }

    /**
     * Creates a localized DropdownComponent with current value selection
     */
    fun createLocalizedDropdownComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        optionKeys: List<String>,
        defaultValue: String? = null,
        selectByValue: Boolean = true
    ): DropdownComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)
        val options = optionKeys.map { bedrockLocalization.getBedrockString(player, it) }

        return createDropdownComponent(label, options, defaultValue, selectByValue)
    }

    /**
     * Creates a localized DropdownComponent using builder pattern for complex option sets
     */
    fun createLocalizedDropdownComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        builder: (DropdownComponent.Builder, BedrockLocalizationService, Player) -> Unit
    ): DropdownComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)
        val dropdownBuilder = DropdownComponent.builder(label)
        builder(dropdownBuilder, bedrockLocalization, player)
        return dropdownBuilder.build()
    }

    /**
     * Creates a localized SliderComponent with range validation
     */
    fun createLocalizedSliderComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        min: Float,
        max: Float,
        step: Float = 1.0f,
        defaultValue: Float = min,
        validator: ((Float) -> String?)? = null
    ): SliderComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)

        return createSliderComponent(label, min, max, step, defaultValue, validator)
    }

    /**
     * Creates a localized ToggleComponent with enhanced state management
     */
    fun createLocalizedToggleComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        defaultValue: Boolean = false
    ): ToggleComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)

        return createToggleComponent(label, defaultValue)
    }

    /**
     * Creates a localized StepSliderComponent with option building
     */
    fun createLocalizedStepSliderComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        optionKeys: List<String>,
        defaultValue: String? = null
    ): StepSliderComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)
        val options = optionKeys.map { bedrockLocalization.getBedrockString(player, it) }

        val defaultIndex = defaultValue?.let { dv ->
            val translatedValue = bedrockLocalization.getBedrockString(player, dv)
            options.indexOf(translatedValue).takeIf { it >= 0 } ?: 0
        } ?: 0

        return createStepSliderComponent(label, options, defaultIndex)
    }

    /**
     * Creates a localized StepSliderComponent using builder pattern for complex option sets
     */
    fun createLocalizedStepSliderComponent(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        builder: (StepSliderComponent.Builder, BedrockLocalizationService, Player) -> Unit
    ): StepSliderComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)
        val stepSliderBuilder = StepSliderComponent.builder(label)
        builder(stepSliderBuilder, bedrockLocalization, player)
        return stepSliderBuilder.build()
    }

    /**
     * Creates a localized LabelComponent for section headers
     */
    fun createLocalizedSectionHeader(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        titleKey: String
    ): LabelComponent {
        val title = bedrockLocalization.getBedrockString(player, titleKey)
        return createSectionHeader(title)
    }

    /**
     * Creates a localized LabelComponent for error messages
     */
    fun createLocalizedErrorLabel(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        messageKey: String,
        vararg args: Any?
    ): LabelComponent {
        val message = bedrockLocalization.getBedrockString(player, messageKey, *args)
        return createErrorLabel(message)
    }

    /**
     * Creates a localized LabelComponent for success messages
     */
    fun createLocalizedSuccessLabel(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        messageKey: String,
        vararg args: Any?
    ): LabelComponent {
        val message = bedrockLocalization.getBedrockString(player, messageKey, *args)
        return createSuccessLabel(message)
    }

    /**
     * Creates a localized LabelComponent for info messages
     */
    fun createLocalizedInfoLabel(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        messageKey: String,
        vararg args: Any?
    ): LabelComponent {
        val message = bedrockLocalization.getBedrockString(player, messageKey, *args)
        return createInfoLabel(message)
    }

    /**
     * Creates a localized LabelComponent for general text
     */
    fun createLocalizedLabel(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        textKey: String,
        vararg args: Any?
    ): LabelComponent {
        val text = bedrockLocalization.getBedrockString(player, textKey, *args)
        return LabelComponent.of(text)
    }

    // RTL-Aware Form Utilities

    /**
     * Creates a section header with RTL-aware formatting
     */
    fun createLocalizedSectionHeaderWithRTL(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        titleKey: String
    ): LabelComponent {
        val title = bedrockLocalization.getBedrockString(player, titleKey)
        val direction = bedrockLocalization.getTextDirection(bedrockLocalization.getBedrockLocale(player))

        return when (direction) {
            TextDirection.RTL -> {
                // For RTL, add RTL marker and use right-aligned formatting
                LabelComponent.of("<yellow><bold>${BedrockLocalizationServiceFloodgate.RTL_MARKER}$title")
            }
            TextDirection.LTR -> {
                // For LTR, use standard formatting
                LabelComponent.of("<yellow><bold>$title")
            }
            else -> {
                // Default to LTR
                LabelComponent.of("<yellow><bold>$title")
            }
        }
    }

    /**
     * Creates RTL-aware label with proper text direction markers
     */
    fun createLocalizedLabelWithRTL(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        textKey: String,
        vararg args: Any?
    ): LabelComponent {
        val text = bedrockLocalization.getBedrockString(player, textKey, *args)
        val direction = bedrockLocalization.getTextDirection(bedrockLocalization.getBedrockLocale(player))

        return when (direction) {
            TextDirection.RTL -> {
                LabelComponent.of("${BedrockLocalizationServiceFloodgate.RTL_MARKER}$text${BedrockLocalizationServiceFloodgate.LTR_MARKER}")
            }
            TextDirection.LTR -> {
                LabelComponent.of(text)
            }
            else -> {
                LabelComponent.of(text)
            }
        }
    }

    /**
     * Creates RTL-aware input component with proper placeholder direction
     */
    fun createLocalizedInputComponentWithRTL(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        labelKey: String,
        placeholderKey: String? = null,
        defaultValue: String = "",
        maxLength: Int = 256,
        validator: ((String) -> String?)? = null
    ): InputComponent {
        val label = bedrockLocalization.getBedrockString(player, labelKey)
        val placeholder = placeholderKey?.let { bedrockLocalization.getBedrockString(player, it) } ?: ""
        val direction = bedrockLocalization.getTextDirection(bedrockLocalization.getBedrockLocale(player))

        // For RTL languages, add direction markers to placeholder
        val adjustedPlaceholder = when (direction) {
            TextDirection.RTL -> {
                "${BedrockLocalizationServiceFloodgate.RTL_MARKER}$placeholder${BedrockLocalizationServiceFloodgate.LTR_MARKER}"
            }
            TextDirection.LTR -> placeholder
            else -> placeholder
        }

        return createInputComponent(label, adjustedPlaceholder, defaultValue, maxLength, validator)
    }

    /**
     * Gets RTL-adjusted text alignment for form layouts
     * Note: Cumulus forms don't directly support text alignment, but this can be used
     * for future enhancements or custom implementations
     */
    fun getRTLTextAlignment(
        bedrockLocalization: BedrockLocalizationService,
        locale: java.util.Locale
    ): TextAlignment {
        return when (bedrockLocalization.getTextDirection(locale)) {
            TextDirection.RTL -> TextAlignment.RIGHT
            TextDirection.LTR -> TextAlignment.LEFT
            else -> TextAlignment.LEFT
        }
    }

    /**
     * Text alignment enum for future form layout enhancements
     */
    enum class TextAlignment {
        LEFT, RIGHT, CENTER
    }

    /**
     * Creates a form with RTL-aware component ordering
     * This is a placeholder for future form-level RTL adjustments
     */
    fun adjustFormForRTL(
        components: List<org.geysermc.cumulus.component.Component>,
        bedrockLocalization: BedrockLocalizationService,
        locale: java.util.Locale
    ): List<org.geysermc.cumulus.component.Component> {
        return when (bedrockLocalization.getTextDirection(locale)) {
            TextDirection.RTL -> {
                // For RTL, reverse the order of certain components if needed
                // Currently, just return as-is since Cumulus handles component ordering
                components
            }
            TextDirection.LTR -> components
            else -> components
        }
    }

    // Localized Validation Utilities

    /**
     * Creates a validation function that returns localized error messages
     */
    fun createLocalizedValidator(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        validationKey: String,
        vararg args: Any?
    ): (Any?) -> String? {
        return { value ->
            // This is a simple validator that always returns the localized message
            // In a real implementation, you'd check the value and return null if valid
            bedrockLocalization.getBedrockString(player, validationKey, *args)
        }
    }

    /**
     * Creates a length validator with localized error messages
     */
    fun createLocalizedLengthValidator(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        minLength: Int? = null,
        maxLength: Int? = null
    ): (String) -> String? {
        return { value ->
            when {
                minLength != null && value.length < minLength -> {
                    bedrockLocalization.getBedrockString(player, "validation.too.short", minLength)
                }
                maxLength != null && value.length > maxLength -> {
                    bedrockLocalization.getBedrockString(player, "validation.too.long", maxLength)
                }
                else -> null // Valid
            }
        }
    }

    /**
     * Creates a numeric range validator with localized error messages
     */
    fun createLocalizedRangeValidator(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        min: Number? = null,
        max: Number? = null
    ): (Number) -> String? {
        return { value ->
            val doubleValue = value.toDouble()
            when {
                min != null && doubleValue < min.toDouble() -> {
                    bedrockLocalization.getBedrockString(player, "validation.number.too.small", min)
                }
                max != null && doubleValue > max.toDouble() -> {
                    bedrockLocalization.getBedrockString(player, "validation.number.too.large", max)
                }
                else -> null // Valid
            }
        }
    }

    /**
     * Creates a regex pattern validator with localized error messages
     */
    fun createLocalizedPatternValidator(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        pattern: Regex,
        errorKey: String = "validation.invalid.format"
    ): (String) -> String? {
        return { value ->
            if (pattern.matches(value)) null
            else bedrockLocalization.getBedrockString(player, errorKey)
        }
    }

    /**
     * Creates a required field validator with localized error messages
     */
    fun createLocalizedRequiredValidator(
        player: Player,
        bedrockLocalization: BedrockLocalizationService
    ): (String) -> String? {
        return { value ->
            if (value.isBlank()) {
                bedrockLocalization.getBedrockString(player, "validation.required")
            } else null
        }
    }

    /**
     * Creates a composite validator that combines multiple validation rules
     */
    fun createCompositeValidator(vararg validators: (Any?) -> String?): (Any?) -> String? {
        return { value ->
            validators.firstNotNullOfOrNull { validator -> validator(value) }
        }
    }

    /**
     * Creates validation error components for form redisplay
     */
    fun createValidationErrorComponents(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        validationErrors: Map<String, String>
    ): List<LabelComponent> {
        if (validationErrors.isEmpty()) return emptyList()

        val components = mutableListOf<LabelComponent>()

        // Add error header
        components.add(createLocalizedErrorLabel(
            player,
            bedrockLocalization,
            "form.validation.errors.title"
        ))

        // Add individual error messages
        validationErrors.values.forEach { errorMessage ->
            components.add(createErrorLabel(errorMessage))
        }

        // Add retry instruction
        components.add(createLocalizedInfoLabel(
            player,
            bedrockLocalization,
            "form.button.retry"
        ))

        return components
    }

    /**
     * Validates form data and returns localized error messages
     */
    fun validateFormData(
        player: Player,
        bedrockLocalization: BedrockLocalizationService,
        formData: Map<String, Any?>,
        validationRules: Map<String, (Any?) -> String?>
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        formData.forEach { (fieldName, value) ->
            val validator = validationRules[fieldName]
            if (validator != null) {
                val error = validator(value)
                if (error != null) {
                    errors[fieldName] = error
                }
            }
        }

        return errors
    }

    /**
     * Converts FormResponseData to business-friendly values
     */
    private fun FormResponseData.toBusinessValue(): Any? {
        return when (this) {
            is FormResponseData.InputData -> value
            is FormResponseData.DropdownData -> selectedValue
            is FormResponseData.SliderData -> value
            is FormResponseData.ToggleData -> value
            is FormResponseData.StepSliderData -> selectedValue
        }
    }

    /**
     * Enum for field types in form response mapping
     */
    enum class FieldType {
        INPUT, DROPDOWN, SLIDER, TOGGLE, STEP_SLIDER, LABEL
    }

    /**
     * Helper function to create field mappings for common patterns
     */
    fun createFieldMapping(vararg fields: Pair<String, FieldType>): Map<String, FieldType> {
        return fields.toMap()
    }
}

enum class GuildIconType {
    MEMBERS, SETTINGS, BANK, WARS, HOME, TAG
}

enum class ActionIconType {
    CONFIRM, CANCEL, BACK, CLOSE, EDIT, DELETE
}

// Extension methods for CustomForm.Builder to support localized components

/**
 * Adds a localized input component to a CustomForm
 */
fun org.geysermc.cumulus.form.CustomForm.Builder.addLocalizedInput(
    player: Player,
    bedrockLocalization: BedrockLocalizationService,
    labelKey: String,
    placeholderKey: String? = null,
    defaultValue: String = "",
    maxLength: Int = 256,
    validator: ((String) -> String?)? = null
): org.geysermc.cumulus.form.CustomForm.Builder {
    val label = bedrockLocalization.getBedrockString(player, labelKey)
    val placeholder = placeholderKey?.let { bedrockLocalization.getBedrockString(player, it) } ?: ""

    return input(label, placeholder, defaultValue)
}

/**
 * Adds a localized dropdown component to a CustomForm
 */
fun org.geysermc.cumulus.form.CustomForm.Builder.addLocalizedDropdown(
    player: Player,
    bedrockLocalization: BedrockLocalizationService,
    labelKey: String,
    optionKeys: List<String>,
    defaultValue: String? = null,
    selectByValue: Boolean = true
): org.geysermc.cumulus.form.CustomForm.Builder {
    val label = bedrockLocalization.getBedrockString(player, labelKey)
    val options = optionKeys.map { bedrockLocalization.getBedrockString(player, it) }

    val defaultIndex = when {
        defaultValue == null -> 0
        selectByValue -> options.indexOf(defaultValue).takeIf { it >= 0 } ?: 0
        else -> defaultValue.toIntOrNull()?.takeIf { it in options.indices } ?: 0
    }

    return dropdown(label, options, defaultIndex)
}

/**
 * Adds a localized toggle component to a CustomForm
 */
fun org.geysermc.cumulus.form.CustomForm.Builder.addLocalizedToggle(
    player: Player,
    bedrockLocalization: BedrockLocalizationService,
    labelKey: String,
    defaultValue: Boolean = false
): org.geysermc.cumulus.form.CustomForm.Builder {
    val label = bedrockLocalization.getBedrockString(player, labelKey)
    return toggle(label, defaultValue)
}

/**
 * Adds a localized slider component to a CustomForm
 */
fun org.geysermc.cumulus.form.CustomForm.Builder.addLocalizedSlider(
    player: Player,
    bedrockLocalization: BedrockLocalizationService,
    labelKey: String,
    min: Float,
    max: Float,
    step: Float = 1.0f,
    defaultValue: Float = min
): org.geysermc.cumulus.form.CustomForm.Builder {
    val label = bedrockLocalization.getBedrockString(player, labelKey)
    return slider(label, min, max, step, defaultValue)
}

/**
 * Adds a localized step slider component to a CustomForm
 */
fun org.geysermc.cumulus.form.CustomForm.Builder.addLocalizedStepSlider(
    player: Player,
    bedrockLocalization: BedrockLocalizationService,
    labelKey: String,
    optionKeys: List<String>,
    defaultValue: String? = null
): org.geysermc.cumulus.form.CustomForm.Builder {
    val label = bedrockLocalization.getBedrockString(player, labelKey)
    val options = optionKeys.map { bedrockLocalization.getBedrockString(player, it) }

    val defaultIndex = defaultValue?.let { dv ->
        options.indexOf(dv).takeIf { it >= 0 } ?: 0
    } ?: 0

    return stepSlider(label, options, defaultIndex)
}

/**
 * Adds a localized label component to a CustomForm
 */
fun org.geysermc.cumulus.form.CustomForm.Builder.addLocalizedLabel(
    player: Player,
    bedrockLocalization: BedrockLocalizationService,
    textKey: String,
    vararg args: Any?
): org.geysermc.cumulus.form.CustomForm.Builder {
    val text = bedrockLocalization.getBedrockString(player, textKey, *args)
    return label(text)
}
