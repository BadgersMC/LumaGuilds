package net.lumalyte.lg.application.services

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.bukkit.entity.Player
import java.util.logging.Logger

class FormValidationServiceTest {

    private lateinit var validationService: FormValidationService
    private lateinit var mockLogger: Logger
    private lateinit var mockPlayer: Player

    @BeforeEach
    fun setUp() {
        mockLogger = mock(Logger::class.java)
        mockPlayer = mock(Player::class.java)

        validationService = FormValidationServiceImpl(mockLogger)
    }

    @Test
    fun `should validate required fields successfully`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.REQUIRED))
        )
        val data = mapOf("name" to "Test Name")

        val errors = validationService.validate(data, validators)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should fail validation for missing required field`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.REQUIRED))
        )
        val data = mapOf<String, Any?>("name" to null)

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("name is required"))
    }

    @Test
    fun `should fail validation for empty required field`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.REQUIRED))
        )
        val data = mapOf("name" to "")

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("name cannot be empty"))
    }

    @Test
    fun `should validate length constraints successfully`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.LENGTH, 2, 10))
        )
        val data = mapOf("name" to "Test")

        val errors = validationService.validate(data, validators)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should fail validation for name too short`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.LENGTH, 3, 10))
        )
        val data = mapOf("name" to "Hi")

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be at least 3 characters"))
    }

    @Test
    fun `should fail validation for name too long`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.LENGTH, 2, 5))
        )
        val data = mapOf("name" to "ThisNameIsTooLong")

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be at most 5 characters"))
    }

    @Test
    fun `should validate numeric range successfully`() {
        val validators = mapOf(
            "level" to listOf(validationService.getValidator(ValidatorType.RANGE, 1, 100))
        )
        val data = mapOf("level" to 50)

        val errors = validationService.validate(data, validators)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should fail validation for number too low`() {
        val validators = mapOf(
            "level" to listOf(validationService.getValidator(ValidatorType.RANGE, 10, 100))
        )
        val data = mapOf("level" to 5)

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be at least 10"))
    }

    @Test
    fun `should fail validation for number too high`() {
        val validators = mapOf(
            "level" to listOf(validationService.getValidator(ValidatorType.RANGE, 1, 50))
        )
        val data = mapOf("level" to 100)

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be at most 50"))
    }

    @Test
    fun `should validate regex format successfully`() {
        val validators = mapOf(
            "email" to listOf(validationService.getValidator(ValidatorType.FORMAT, ValidationPatterns.EMAIL))
        )
        val data = mapOf("email" to "test@example.com")

        val errors = validationService.validate(data, validators)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should fail validation for invalid format`() {
        val validators = mapOf(
            "email" to listOf(validationService.getValidator(ValidatorType.FORMAT, ValidationPatterns.EMAIL))
        )
        val data = mapOf("email" to "invalid-email")

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("format is invalid"))
    }

    @Test
    fun `should validate business rules successfully`() {
        val businessRule = { fieldName: String, value: Any? ->
            if (value is String && value.contains("admin")) {
                ValidationResult.invalid("Admin names are not allowed")
            } else {
                ValidationResult.valid()
            }
        }

        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.BUSINESS_RULE, businessRule))
        )
        val data = mapOf("name" to "regularuser")

        val errors = validationService.validate(data, validators)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should fail validation for business rule violation`() {
        val businessRule = { fieldName: String, value: Any? ->
            if (value is String && value.contains("admin")) {
                ValidationResult.invalid("Admin names are not allowed")
            } else {
                ValidationResult.valid()
            }
        }

        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.BUSINESS_RULE, businessRule))
        )
        val data = mapOf("name" to "adminuser")

        val errors = validationService.validate(data, validators)

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Admin names are not allowed"))
    }

    @Test
    fun `should validate multiple fields with multiple validators`() {
        val validators = mapOf(
            "name" to listOf(
                validationService.getValidator(ValidatorType.REQUIRED),
                validationService.getValidator(ValidatorType.LENGTH, 3, 20)
            ),
            "level" to listOf(
                validationService.getValidator(ValidatorType.REQUIRED),
                validationService.getValidator(ValidatorType.RANGE, 1, 100)
            )
        )
        val data = mapOf(
            "name" to "ValidName",
            "level" to 50
        )

        val errors = validationService.validate(data, validators)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should collect multiple validation errors`() {
        val validators = mapOf(
            "name" to listOf(validationService.getValidator(ValidatorType.REQUIRED)),
            "level" to listOf(validationService.getValidator(ValidatorType.RANGE, 10, 100))
        )
        val data = mapOf(
            "name" to "",
            "level" to 5
        )

        val errors = validationService.validate(data, validators)

        assertEquals(2, errors.size)
        assertTrue(errors.any { it.contains("cannot be empty") })
        assertTrue(errors.any { it.contains("must be at least 10") })
    }

    @Test
    fun `should display validation errors to player`() {
        val errors = listOf("Name is required", "Email format is invalid")
        var reopenCalled = false

        validationService.showValidationErrors(mockPlayer, errors, { reopenCalled = true }) { key ->
            when (key) {
                "form.validation.errors.title" -> "Validation Errors:"
                "form.button.retry" -> "Please correct the errors and try again."
                "form.button.cancel" -> "Use /menu to cancel."
                else -> key
            }
        }

        // Verify that error messages were sent to player
        verify(mockPlayer).sendMessage("§c❌ Validation Errors:")
        verify(mockPlayer).sendMessage("§7• Name is required")
        verify(mockPlayer).sendMessage("§7• Email format is invalid")
        verify(mockPlayer).sendMessage("§ePlease correct the errors and try again.")
        verify(mockPlayer).sendMessage("§cUse /menu to cancel.")

        // Verify that reopen function was called
        assertTrue(reopenCalled)
    }

    @Test
    fun `should not display errors when list is empty`() {
        val errors = emptyList<String>()
        var reopenCalled = false

        validationService.showValidationErrors(mockPlayer, errors, { reopenCalled = true }) { key -> key }

        // Verify no messages were sent
        verify(mockPlayer, never()).sendMessage(anyString())
        assertFalse(reopenCalled)
    }
}
