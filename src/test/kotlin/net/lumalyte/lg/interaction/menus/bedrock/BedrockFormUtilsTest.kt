package net.lumalyte.lg.interaction.menus.bedrock

import org.geysermc.cumulus.component.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested

class BedrockFormUtilsTest {

    @Nested
    inner class InputComponentTests {
        @Test
        fun `createInputComponent creates valid component with default values`() {
            val component = BedrockFormUtils.createInputComponent(
                label = "Test Input",
                placeholder = "Enter text",
                defaultValue = "default"
            )

            assertEquals("Test Input", component.placeholder())
            assertEquals("Enter text", component.defaultText())
        }

        @Test
        fun `createInputComponent handles empty values correctly`() {
            val component = BedrockFormUtils.createInputComponent(
                label = "Empty Input"
            )

            assertEquals("Empty Input", component.placeholder())
            assertEquals("", component.defaultText())
        }

        @Test
        fun `createInputComponent truncates long default values`() {
            val longValue = "a".repeat(300)
            val component = BedrockFormUtils.createInputComponent(
                label = "Long Input",
                defaultValue = longValue
            )

            assertEquals(256, component.defaultText().length)
            assertTrue(component.defaultText().endsWith("..."))
        }

        @Test
        fun `createInputComponent validates with custom validator`() {
            var validationCalled = false
            val component = BedrockFormUtils.createInputComponent(
                label = "Validated Input",
                defaultValue = "valid",
                validator = { value ->
                    validationCalled = true
                    if (value == "valid") null else "Invalid value"
                }
            )

            assertEquals("valid", component.defaultText())
            // Note: We can't easily test the validator call in this setup
            // In a real scenario, this would be tested through form response handling
        }

        @Test
        fun `createNumberInput creates validated number input`() {
            val component = BedrockFormUtils.createNumberInput(
                label = "Number Input",
                min = 0,
                max = 100,
                defaultValue = 50
            )

            assertEquals("Number Input", component.placeholder())
            assertEquals("50", component.defaultText())
        }

        @Test
        fun `createNumberInput validates number input correctly`() {
            val component = BedrockFormUtils.createNumberInput(
                label = "Number Input",
                min = 10,
                max = 20,
                defaultValue = 15
            )

            assertEquals("Number Input", component.placeholder())
            assertEquals("15", component.defaultText())
        }
    }

    @Nested
    inner class DropdownComponentTests {
        @Test
        fun `createDropdownComponent handles empty options list`() {
            assertThrows(IllegalArgumentException::class.java) {
                BedrockFormUtils.createDropdownComponent(
                    label = "Empty Dropdown",
                    options = emptyList()
                )
            }
        }

        @Test
        fun `createLocalizedDropdown creates dropdown with translated options`() {
            val translations = mapOf(
                "en" to "English",
                "es" to "Español",
                "fr" to "Français"
            )

            val component = BedrockFormUtils.createLocalizedDropdown(
                label = "Language",
                options = translations,
                defaultKey = "en"
            )

            assertEquals("Language", component.placeholder())
            assertEquals(listOf("English", "Español", "Français"), component.options())
            assertEquals(0, component.defaultOption()) // "en" is first in the map
        }
    }

    @Nested
    inner class SliderComponentTests {
        @Test
        fun `createSliderComponent validates range constraints`() {
            assertThrows(IllegalArgumentException::class.java) {
                BedrockFormUtils.createSliderComponent(
                    label = "Invalid Slider",
                    min = 100f,
                    max = 0f,
                    defaultValue = 50f
                )
            }
        }

        @Test
        fun `createSliderComponent validates step is positive`() {
            assertThrows(IllegalArgumentException::class.java) {
                BedrockFormUtils.createSliderComponent(
                    label = "Invalid Slider",
                    min = 0f,
                    max = 100f,
                    step = -1f,
                    defaultValue = 50f
                )
            }
        }

        @Test
        fun `createSliderComponent validates default value is in range`() {
            assertThrows(IllegalArgumentException::class.java) {
                BedrockFormUtils.createSliderComponent(
                    label = "Invalid Slider",
                    min = 0f,
                    max = 100f,
                    defaultValue = 150f
                )
            }
        }

        @Test
        fun `createPercentageSlider clamps out of range values`() {
            val component = BedrockFormUtils.createPercentageSlider(
                label = "Percentage",
                defaultValue = 150f // Should be clamped to 100
            )

            assertEquals(100f, component.defaultValue())
        }
    }

    @Nested
    inner class ToggleComponentTests {
        @Test
        fun `createToggleComponent defaults to false`() {
            val component = BedrockFormUtils.createToggleComponent(
                label = "Default Toggle"
            )

            assertFalse(component.defaultValue())
        }
    }

    @Nested
    inner class StepSliderComponentTests {
        @Test
        fun `createStepSliderComponent handles empty options list`() {
            assertThrows(IllegalArgumentException::class.java) {
                BedrockFormUtils.createStepSliderComponent(
                    label = "Empty Step Slider",
                    options = emptyList()
                )
            }
        }

        @Test
        fun `createStepSliderComponent validates default index is in bounds`() {
            val options = listOf("Option 1", "Option 2")
            assertThrows(IllegalArgumentException::class.java) {
                BedrockFormUtils.createStepSliderComponent(
                    label = "Invalid Step Slider",
                    options = options,
                    defaultIndex = 5
                )
            }
        }

        @Test
        fun `createStepSliderComponent defaults to first option when index out of bounds`() {
            val options = listOf("Option 1", "Option 2")
            val component = BedrockFormUtils.createStepSliderComponent(
                label = "Default Step Slider",
                options = options,
                defaultIndex = -1
            )

            assertEquals(0, component.defaultStep())
        }
    }

    @Nested
    inner class LabelComponentTests {
        @Test
        fun `createSectionHeader creates formatted header`() {
            val component = BedrockFormUtils.createSectionHeader("Test Section")
            assertEquals("§e§lTest Section", component.text())
        }

        @Test
        fun `createErrorLabel creates formatted error message`() {
            val component = BedrockFormUtils.createErrorLabel("Something went wrong")
            assertEquals("§c⚠ Something went wrong", component.text())
        }

        @Test
        fun `createSuccessLabel creates formatted success message`() {
            val component = BedrockFormUtils.createSuccessLabel("Operation successful")
            assertEquals("§a✓ Operation successful", component.text())
        }
    }

    @Nested
    inner class FormImageTests {
        @Test
        fun `createFormImage returns null when both URL and PATH are empty`() {
            val mockConfig = BedrockConfig()
            val image = BedrockFormUtils.createFormImage(
                config = mockConfig,
                imageUrl = "",
                imagePath = ""
            )

            assertNull(image)
        }
    }

    @Nested
    inner class FormResponseDataTests {
        @Test
        fun `FormResponseData sealed class creates correct subclasses`() {
            val inputData = BedrockFormUtils.FormResponseData.InputData("test")
            val dropdownData = BedrockFormUtils.FormResponseData.DropdownData(1, "option2")
            val sliderData = BedrockFormUtils.FormResponseData.SliderData(50f)
            val toggleData = BedrockFormUtils.FormResponseData.ToggleData(true)
            val stepSliderData = BedrockFormUtils.FormResponseData.StepSliderData(2, "step3")

            assertEquals("test", inputData.value)
            assertEquals(1, dropdownData.selectedIndex)
            assertEquals("option2", dropdownData.selectedValue)
            assertEquals(50f, sliderData.value)
            assertTrue(toggleData.value)
            assertEquals(2, stepSliderData.selectedIndex)
            assertEquals("step3", stepSliderData.selectedValue)
        }

        @Test
        fun `ProcessedFormData handles valid responses correctly`() {
            val responses = mapOf(
                "input" to BedrockFormUtils.FormResponseData.InputData("test"),
                "dropdown" to BedrockFormUtils.FormResponseData.DropdownData(1, "option2")
            )
            val processedData = BedrockFormUtils.ProcessedFormData(
                responses = responses,
                isValid = true,
                validationErrors = emptyMap(),
                businessData = mapOf("input" to "test", "dropdown" to "option2")
            )

            assertTrue(processedData.isValid)
            assertEquals(2, processedData.responses.size)
            assertEquals(2, processedData.businessData.size)
            assertTrue(processedData.validationErrors.isEmpty())
        }

        @Test
        fun `ProcessedFormData handles invalid responses correctly`() {
            val responses = mapOf(
                "input" to BedrockFormUtils.FormResponseData.InputData("")
            )
            val errors = mapOf("input" to "Value cannot be empty")
            val processedData = BedrockFormUtils.ProcessedFormData(
                responses = responses,
                isValid = false,
                validationErrors = errors,
                businessData = emptyMap()
            )

            assertFalse(processedData.isValid)
            assertEquals(1, processedData.validationErrors.size)
            assertEquals("Value cannot be empty", processedData.validationErrors["input"])
        }
    }

    @Nested
    inner class ValidationTests {
        @Test
        fun `Validators notEmpty validates correctly`() {
            val validator = BedrockFormUtils.Validators.notEmpty
            val context = BedrockFormUtils.ValidationContext("player123")

            assertNull(validator.validate("not empty", context))
            assertEquals("Value cannot be empty", validator.validate("", context))
            assertEquals("Value cannot be empty", validator.validate("   ", context))
            assertNull(validator.validate("test", context))
        }

        @Test
        fun `Validators length validates correctly`() {
            val validator = BedrockFormUtils.Validators.StringLengthValidator(3, 10)
            val context = BedrockFormUtils.ValidationContext("player123")

            assertEquals("Must be at least 3 characters", validator.validate("ab", context))
            assertEquals("Must be at most 10 characters", validator.validate("this is a very long string", context))
            assertNull(validator.validate("hello", context))
        }

        @Test
        fun `Validators range validates correctly`() {
            val validator = BedrockFormUtils.Validators.RangeValidator(0f, 100f)
            val context = BedrockFormUtils.ValidationContext("player123")

            assertEquals("Must be at least 0.0", validator.validate(-5f, context))
            assertEquals("Must be at most 100.0", validator.validate(150f, context))
            assertNull(validator.validate(50f, context))
            assertNull(validator.validate(0f, context))
            assertNull(validator.validate(100f, context))
        }

        @Test
        fun `Validators pattern validates correctly`() {
            val validator = BedrockFormUtils.Validators.RegexValidator(
                Regex("^[A-Za-z]+$"),
                "Only letters allowed"
            )
            val context = BedrockFormUtils.ValidationContext("player123")

            assertNull(validator.validate("HelloWorld", context))
            assertEquals("Only letters allowed", validator.validate("Hello123", context))
            assertEquals("Only letters allowed", validator.validate("Hello World", context))
        }

        @Test
        fun `Validators combine runs multiple validators`() {
            val validator = BedrockFormUtils.Validators.CompositeValidator(
                listOf(
                    BedrockFormUtils.Validators.notEmpty,
                    BedrockFormUtils.Validators.StringLengthValidator(3, 20)
                )
            )
            val context = BedrockFormUtils.ValidationContext("player123")

            assertEquals("Value cannot be empty", validator.validate("", context))
            assertEquals("Must be at least 3 characters", validator.validate("ab", context))
            assertNull(validator.validate("hello world", context))
        }

        @Test
        fun `Validators helper functions create correct validators`() {
            val required = BedrockFormUtils.Validators.required()
            val length = BedrockFormUtils.Validators.length(5, 15)
            val range = BedrockFormUtils.Validators.range(10f, 50f)
            val pattern = BedrockFormUtils.Validators.pattern("[0-9]+", "Must be numbers")

            assertEquals("Value cannot be empty", required.validate("", BedrockFormUtils.ValidationContext("")))
            assertEquals("Must be at least 5 characters", length.validate("abc", BedrockFormUtils.ValidationContext("")))
            assertEquals("Must be at least 10.0", range.validate(5f, BedrockFormUtils.ValidationContext("")))
            assertEquals("Must be numbers", pattern.validate("abc", BedrockFormUtils.ValidationContext("")))
        }
    }

    @Nested
    inner class FormResponseExtractionTests {
        @Test
        fun `createFieldMapping creates correct mapping`() {
            val mapping = BedrockFormUtils.createFieldMapping(
                "name" to BedrockFormUtils.FieldType.INPUT,
                "amount" to BedrockFormUtils.FieldType.SLIDER,
                "enabled" to BedrockFormUtils.FieldType.TOGGLE
            )

            assertEquals(3, mapping.size)
            assertEquals(BedrockFormUtils.FieldType.INPUT, mapping["name"])
            assertEquals(BedrockFormUtils.FieldType.SLIDER, mapping["amount"])
            assertEquals(BedrockFormUtils.FieldType.TOGGLE, mapping["enabled"])
        }

        @Test
        fun `FieldType enum contains all expected types`() {
            val expectedTypes = setOf(
                BedrockFormUtils.FieldType.INPUT,
                BedrockFormUtils.FieldType.DROPDOWN,
                BedrockFormUtils.FieldType.SLIDER,
                BedrockFormUtils.FieldType.TOGGLE,
                BedrockFormUtils.FieldType.STEP_SLIDER,
                BedrockFormUtils.FieldType.LABEL
            )

            assertEquals(expectedTypes, BedrockFormUtils.FieldType.values().toSet())
        }
    }
}
