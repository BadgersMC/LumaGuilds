package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ValidationResult
import net.lumalyte.lg.application.services.ValidatorType
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
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

    override fun getForm(): Form {
        val currentTag = guildService.getTag(guild.id)
        val config = getBedrockConfig()
        val tagIcon = BedrockFormUtils.createFormImage(config, config.guildTagIconUrl, config.guildTagIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "form.title.tag.editor")} - ${guild.name}")
            .apply { tagIcon?.let { icon(it) } }
            .label("""
                |§7${bedrockLocalization.getBedrockString(player, "guild.tag.editor.description")}
                |
                |§6§l━━━ BEDROCK COLOR CODES ━━━
                |§7Use § followed by a code for colors:
                |§7• §c§c (red) §7- Use §c in your tag
                |§7• §a§a (green) §7- Use §a in your tag
                |§7• §e§e (yellow) §7- Use §e in your tag
                |§7• §b§b (aqua) §7- Use §b in your tag
                |§7• §d§d (pink) §7- Use §d in your tag
                |§7• §l§l§r§7 (bold) §7- Use §l for bold
                |§7• §o§o§r§7 (italic) §7- Use §o for italic
                |
                |§7Example: §c§lRED§r§7 = §c§lRED
                |
                |§e§l━━━ ADVANCED TEXT GENERATOR ━━━
                |§7Want gradients, hex colors, or fancy effects?
                |§7Use the toggle below to get a QR code map!
                |§7Scan it with your phone to access Birdflop!
            """.trimMargin())
            .addLocalizedInput(
                player, bedrockLocalization,
                "guild.tag.input.label",
                "guild.tag.input.placeholder",
                currentTag ?: ""
            )
            .label(bedrockLocalization.getBedrockString(player, "guild.tag.validation.too.long", 32))
            .toggle("§e📱 Get QR Code Map for Birdflop Text Generator", false)
            .addLocalizedToggle(
                player, bedrockLocalization,
                "guild.tag.clear.toggle",
                false
            )
            .validResultHandler { response ->
                try {
                    val tagInput = response.next() as? String ?: ""
                    val getQRCodeMap = response.next() as? Boolean ?: false
                    val clearTag = response.next() as? Boolean ?: false

                    // Handle QR code map request
                    if (getQRCodeMap) {
                        giveQRCodeMap("https://birdflop.com/resources/rgb/")
                        player.sendMessage("§a✓ QR Code map added to your inventory!")
                        player.sendMessage("§7Scan it with your phone to open Birdflop!")
                        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        player.sendMessage("§6Birdflop Text Generator:")
                        player.sendMessage("§bhttps://birdflop.com/resources/rgb/")
                        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        // Reopen the menu so they can continue editing
                        bedrockNavigator.openMenu(BedrockTagEditorMenu(menuNavigator, player, guild, logger))
                        return@validResultHandler
                    }

                    // Handle clear tag toggle - requires confirmation
                    if (clearTag) {
                        showConfirmationDialog(
                            localize("guild.tag.confirm.clear.title"),
                            localize("guild.tag.confirm.clear.message"),
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
                        player.sendMessage("§7${localize("guild.tag.no.changes")}")
                        bedrockNavigator.goBack()
                        return@validResultHandler
                    }

                    // Show confirmation dialog before saving
                    showConfirmationDialog(
                        localize("guild.tag.confirm.change.title"),
                        localize("guild.tag.confirm.change.message", renderFormattedTag(tagInput)),
                        { saveTagChange(tagInput) }
                    )

                } catch (e: Exception) {
                    // Menu operation - catching all exceptions to prevent UI failure
                    logger.warning("Error processing tag editor form: ${e.message}")
                    player.sendMessage("§c[ERROR] ${localize("form.error.processing")}")
                    bedrockNavigator.goBack()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage("§7Tag editing cancelled.")
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
                            return@getValidator ValidationResult.invalid(localize("guild.tag.validation.too.long", visibleChars, 32))
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
                                ValidationResult.invalid(localize("guild.tag.validation.invalid.format", e.message ?: "Unknown error"))
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
            player.sendMessage("§a[SUCCESS] ${localize("guild.tag.cleared")}")
        } else {
            player.sendMessage("§c[ERROR] ${localize("guild.tag.save.failed")}")
        }
        bedrockNavigator.goBack()
    }

    private fun saveTagChange(tagInput: String) {
        val success = guildService.setTag(guild.id, tagInput, player.uniqueId)
        if (success) {
            player.sendMessage("§a[SUCCESS] ${localize("guild.tag.updated.success")}")
            if (tagInput.isNotEmpty()) {
                val formattedTag = renderFormattedTag(tagInput)
                player.sendMessage("§7${localize("guild.tag.new.preview", formattedTag)}")
                player.sendMessage("§7${localize("guild.tag.chat.preview", player.name, formattedTag)}")
            }
            bedrockNavigator.goBack()
        } else {
            player.sendMessage("§c[ERROR] ${localize("guild.tag.save.failed")}")
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
            val mapItem = org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? org.bukkit.inventory.meta.MapMeta
            mapMeta?.mapView = mapView
            mapMeta?.displayName(net.kyori.adventure.text.Component.text("Birdflop Text Generator")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
            mapMeta?.lore(listOf(
                net.kyori.adventure.text.Component.text("Scan this QR code to open:").color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                net.kyori.adventure.text.Component.text(url).color(net.kyori.adventure.text.format.NamedTextColor.AQUA),
                net.kyori.adventure.text.Component.text("Use your phone's camera!").color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
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


