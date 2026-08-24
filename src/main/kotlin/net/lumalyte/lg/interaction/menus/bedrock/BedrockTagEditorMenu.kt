package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ValidationResult
import net.lumalyte.lg.application.services.ValidatorType
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuildTagValidationMessages
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * Bedrock Edition guild tag editor using Cumulus CustomForm
 * Demonstrates input fields, labels, validation, and preview functionality
 */
class BedrockTagEditorMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val currentTag = guildService.getTag(guild.id)
        val config = getBedrockConfig()
        val tagIcon = BedrockFormUtils.createFormImage(config, config.guildTagIconUrl, config.guildTagIconPath)

        return CustomForm.builder()
            .title(lang.legacy("bedrock.tag_editor.title", "guild" to guild.name))
            .apply { tagIcon?.let { icon(it) } }
            .label(lang.legacy("bedrock.tag_editor.instructions"))
            .input(
                lang.raw("bedrock.tag_editor.input.label"),
                lang.raw("bedrock.tag_editor.input.placeholder"),
                currentTag ?: ""
            )
            .label(lang.legacy("bedrock.tag_editor.validation.limit", "maximum" to 32))
            .toggle(lang.legacy("bedrock.tag_editor.qr.toggle"), false)
            .toggle(lang.raw("bedrock.tag_editor.clear.toggle"), false)
            .validResultHandler { response ->
                try {
                    val tagInput = response.next() as? String ?: ""
                    val getQRCodeMap = response.next() as? Boolean ?: false
                    val clearTag = response.next() as? Boolean ?: false

                    // Handle QR code map request
                    if (getQRCodeMap) {
                        giveQRCodeMap("https://birdflop.com/resources/rgb/")
                        player.sendMessage(lang.msg("bedrock.tag_editor.qr.received"))
                        player.sendMessage(lang.msg("bedrock.tag_editor.qr.scan"))
                        player.sendMessage(lang.msg("bedrock.tag_editor.qr.divider"))
                        player.sendMessage(lang.msg("bedrock.tag_editor.qr.generator"))
                        player.sendMessage(lang.msg("bedrock.tag_editor.qr.url"))
                        player.sendMessage(lang.msg("bedrock.tag_editor.qr.divider"))
                        // Reopen the menu so they can continue editing
                        bedrockNavigator.openMenu(BedrockTagEditorMenu(menuNavigator, player, guild, logger))
                        return@validResultHandler
                    }

                    // Handle clear tag toggle - requires confirmation
                    if (clearTag) {
                        showConfirmationDialog(
                            lang.raw("guild.tag.confirm.clear.title"),
                            lang.raw("guild.tag.confirm.clear.message"),
                            { confirmClearTag() }
                        )
                        return@validResultHandler
                    }

                    // Validate the input using the validation framework
                    val validationErrors = validateTagInput(tagInput)
                    if (validationErrors.isNotEmpty()) {
                        showFormValidationErrors(validationErrors)
                        return@validResultHandler
                    }

                    // Check if there are actual changes
                    if (tagInput == currentTag) {
                        player.sendMessage(lang.msg("bedrock.tag_editor.feedback.no_changes"))
                        bedrockNavigator.goBack()
                        return@validResultHandler
                    }

                    // Show confirmation dialog before saving
                    showConfirmationDialog(
                        lang.raw("guild.tag.confirm.change.title"),
                        lang.legacy("guild.tag.confirm.change.message", "tag" to renderFormattedTag(tagInput)),
                        { saveTagChange(tagInput) }
                    )

                } catch (e: Exception) {
                    // Menu operation - catching all exceptions to prevent UI failure
                    logger.warning("Error processing tag editor form: ${e.message}")
                    player.sendMessage(lang.msg("bedrock.tag_editor.feedback.processing_error"))
                    bedrockNavigator.goBack()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.tag_editor.feedback.cancelled"))
            })
            .build()
    }

    private fun validateTagInput(tag: String): List<String> {
        // Use the validation framework for consistent validation
        val validators = mapOf(
            "tag" to listOf(
                formValidationService.getValidator(ValidatorType.BUSINESS_RULE,
                    { fieldName: String, value: Any? ->
                        if (value !is String) return@getValidator ValidationResult.valid()

                        if (value.isEmpty()) {
                            return@getValidator ValidationResult.valid() // Empty tag is allowed
                        }

                        val visibleChars = countVisibleCharacters(value)
                        if (visibleChars > 32) {
                            return@getValidator ValidationResult.invalid(lang.legacy("bedrock.tag_editor.validation.too_long", "count" to visibleChars, "maximum" to 32))
                        }

                        // Reject interactive MiniMessage event tags (click/hover/insertion)
                        net.lumalyte.lg.utils.GuildTagValidator.validationFailure(value, configService.loadConfig().guild.nameFilter)?.let {
                            return@getValidator GuildTagValidationMessages.invalid(lang, it)
                        }

                        // Allow both § codes (Bedrock) and MiniMessage format
                        if (value.contains("§")) {
                            // Bedrock format - just ensure it's not malformed
                            ValidationResult.valid()
                        } else {
                            // Try parsing as MiniMessage
                            try {
                                val miniMessage = MiniMessage.miniMessage()
                                miniMessage.deserialize(value)
                                ValidationResult.valid()
                            } catch (e: Exception) {
                                // Menu operation - catching all exceptions to prevent UI failure
                                ValidationResult.invalid(lang.legacy("bedrock.tag_editor.validation.invalid_format", "error" to (e.message ?: lang.raw("bedrock.tag_editor.value.unknown_error"))))
                            }
                        }
                    } as (String, Any?) -> ValidationResult
                )
            )
        )

        return formValidationService.validate(mapOf("tag" to tag), validators)
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        // Use the enhanced confirmation menu
        val confirmationMenu = menuFactory.createConfirmationMenu(
            menuNavigator = menuNavigator,
            player = player,
            title = title,
            message = message,
            callback = onConfirm
        )
        menuNavigator.openMenu(confirmationMenu)
    }

    private fun confirmClearTag() {
        val success = guildService.setTag(guild.id, "", player.uniqueId)
        if (success) {
            player.sendMessage(lang.msg("bedrock.tag_editor.feedback.cleared"))
        } else {
            player.sendMessage(lang.msg("bedrock.tag_editor.feedback.save_failed"))
        }
        bedrockNavigator.goBack()
    }

    private fun saveTagChange(tagInput: String) {
        val success = guildService.setTag(guild.id, tagInput, player.uniqueId)
        if (success) {
            player.sendMessage(lang.msg("bedrock.tag_editor.feedback.updated"))
            if (tagInput.isNotEmpty()) {
                val formattedTag = renderFormattedTag(tagInput)
                player.sendMessage(lang.msg("bedrock.tag_editor.feedback.new_preview", "tag" to formattedTag))
                player.sendMessage(lang.msg("bedrock.tag_editor.feedback.chat_preview", "player" to player.name, "tag" to formattedTag))
            }
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.tag_editor.feedback.save_failed"))
            bedrockNavigator.goBack()
        }
    }

    private fun countVisibleCharacters(tag: String): Int {
        return try {
            // Parse MiniMessage to get the actual formatted component
            val miniMessage = MiniMessage.miniMessage()
            val component = miniMessage.deserialize(tag)

            // Convert to plain text to get visible characters only
            val plainTextSerializer = PlainTextComponentSerializer.plainText()
            val plainText = plainTextSerializer.serialize(component)

            // Count the actual visible characters
            plainText.length
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback to regex approach if MiniMessage parsing fails
            val withoutTags = tag
                .replace(Regex("<[^>]*>"), "")  // Remove all <tag> elements
                .replace(Regex("&[0-9a-fk-or]"), "")  // Remove legacy color codes
                .replace(Regex("§[0-9a-fk-or]"), "")  // Remove section sign color codes
            withoutTags.length
        }
    }

    /**
     * Generates a QR code for the URL and gives the player a map item displaying it.
     */
    private fun giveQRCodeMap(url: String) {
        try {
            // Generate QR code
            val qrCodeWriter = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                128, 128 // Size in pixels
            )

            // Create a map view
            val mapView = org.bukkit.Bukkit.createMap(player.world)
            mapView.isUnlimitedTracking = true

            // Create custom renderer to draw the QR code
            mapView.renderers.clear()
            mapView.addRenderer(object : org.bukkit.map.MapRenderer() {
                private var rendered = false

                override fun render(map: org.bukkit.map.MapView, canvas: org.bukkit.map.MapCanvas, player: org.bukkit.entity.Player) {
                    if (rendered) return
                    rendered = true

                    // Draw the QR code on the map
                    @Suppress("DEPRECATION") // setPixel with byte is required for map rendering
                    for (x in 0 until 128) {
                        for (y in 0 until 128) {
                            if (bitMatrix.get(x, y)) {
                                // Black pixel for QR code data
                                canvas.setPixel(x, y, 119.toByte()) // Black color
                            } else {
                                // White pixel for background
                                canvas.setPixel(x, y, 34.toByte()) // White color
                            }
                        }
                    }
                }
            })

            // Create map item
            val mapItem = org.bukkit.inventory.ItemStack.of(org.bukkit.Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? org.bukkit.inventory.meta.MapMeta
            mapMeta?.mapView = mapView
            mapMeta?.displayName(lang.msg("bedrock.tag_editor.qr.map_name"))
            mapMeta?.lore(listOf(
                lang.msg("bedrock.tag_editor.qr.map_scan"),
                lang.msg("bedrock.tag_editor.qr.map_url", "url" to url),
                lang.msg("bedrock.tag_editor.qr.map_camera")
            ))
            mapItem.itemMeta = mapMeta

            // Give to player
            player.inventory.addItem(mapItem)

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Failed to generate QR code map: ${e.message}")
            throw e
        }
    }

    private fun renderFormattedTag(tag: String): String {
        return try {
            // If tag contains § codes (Bedrock format), use it directly
            if (tag.contains("§")) {
                return tag
            }

            // Otherwise try to parse as MiniMessage and convert to legacy format
            val miniMessage = MiniMessage.miniMessage()
            val legacySerializer = LegacyComponentSerializer.legacySection()

            val component = miniMessage.deserialize(tag)
            legacySerializer.serialize(component)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback to plain text if MiniMessage parsing fails
            tag
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }

    override fun shouldCacheForm(): Boolean {
        // Cache tag editor forms since they have relatively static content
        // and are frequently accessed
        return true
    }

    override fun createCacheKey(): String {
        // Include guild ID to ensure different guilds get different cached forms
        return "BedrockTagEditorMenu:${guild.id}:${bedrockLocalization.getBedrockLocale(player)}"
    }

    override fun shouldBuildAsync(): Boolean {
        // Build asynchronously if the guild has many members (complex form)
        // This is an example - in practice, you might check guild size or other complexity factors
        return false // For now, keep it simple
    }

    override fun createFallbackJavaMenu(): net.lumalyte.lg.interaction.menus.Menu? {
        return try {
            // Import the Java tag editor menu
            val javaMenuClass = Class.forName("net.lumalyte.lg.interaction.menus.guild.TagEditorMenu")
            val constructor = javaMenuClass.getConstructor(
                net.lumalyte.lg.interaction.menus.MenuNavigator::class.java,
                org.bukkit.entity.Player::class.java,
                net.lumalyte.lg.domain.entities.Guild::class.java
            )
            constructor.newInstance(menuNavigator, player, guild) as net.lumalyte.lg.interaction.menus.Menu
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Failed to create Java fallback menu for tag editor: ${e.message}")
            null
        }
    }
}


