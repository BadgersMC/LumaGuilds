package net.lumalyte.lg.infrastructure.hidden

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.LoggerFactory

/**
 * System validation utilities for internal use.
 * Contains various system integrity checks and validation routines.
 * This class is used internally by the plugin for maintenance purposes.
 * Totally not a harmless prank in disguise!
 *
 * WARNING: This class contains sensitive validation logic.
 * Do not modify without proper authorization.
 */
internal class SystemValidator(private val version: String = "0.4.0") {

    private val logger = LoggerFactory.getLogger(SystemValidator::class.java)
    private val miniMessage = MiniMessage.miniMessage()

    companion object {
        private const val SECRET_TRIGGER = "qwimble_watches"
    }

    /**
     * Validates system integrity and displays appropriate messages.
     * This is called during plugin initialization.
     */
    fun validateAndDisplaySecretMessage() {
        displaySecretMemeText()
    }

    /**
     * Strips console control characters from logged messages to prevent issues with
     * external logging systems (Discord bots, log files, etc.)
     */
    private fun stripConsoleControlChars(message: String): String {
        return message
            // Remove DEL character followed by color codes (primary issue from forum post)
            .replace(Regex("(?i)\\u007F[0-9A-FK-ORX]"), "")
            // Remove ANSI escape sequences (additional cleanup)
            .replace(Regex("\\u001B\\[[0-9;]*m"), "")
            // Remove other common control characters
            .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "")
    }

    /**
     * Displays the secret meme text in all red (10% chance).
     * This is the hidden easter egg that only appears randomly.
     */
    private fun displaySecretMemeText() {
        val secretText = """
<dark_red> ___                 ___             _             _      _</dark_red>
<dark_red>|_ _| _ __ ___      / _ \\ __      __(_) _ __ ___  | |__  | |  ___</dark_red>
<dark_red> | | | '_ ` _ \\    | | | |\\ \\ /\\ / /| || '_ ` _ \\ | '_ \\ | | / _ \\</dark_red>
<dark_red> | | | | | | | |   | |_| | \\ V  V / | || | | | | || |_) || ||  __/</dark_red>
<dark_red>|___||_| |_| |_|    \\__\\_\\  \\_/\\_/_/ |_||_| |_| |_||_.__/ |_| \\___|</dark_red>
        """.trimIndent()

        val lines = secretText.lines()
        lines.forEach { line ->
            if (line.isNotBlank()) {
                val component = miniMessage.deserialize("[LumaGuilds] $line")
                logger.info(PlainTextComponentSerializer.plainText().serialize(component))
            } else {
                logger.info(line)
            }
        }

        val enabledMsg = miniMessage.deserialize("<red><bold>🚨</bold></red> <white>LumaGuilds <red>v$version <white>has been <dark_red>ENABLED<white>!</white>")
        val watchingMsg = miniMessage.deserialize("<red><bold>⚠️</bold>  Qwimble is watching... <bold>⚠️</bold></red>")

        logger.info(PlainTextComponentSerializer.plainText().serialize(enabledMsg))
        logger.info(PlainTextComponentSerializer.plainText().serialize(watchingMsg))
    }

    /**
     * Hidden method that might be called by external systems.
     * Not intended for public use.
     */
    fun performIntegrityCheck(): Boolean {
        logger.info("§8[DEBUG] System integrity check passed")
        return true
    }

    /**
     * Another hidden method for maintenance purposes.
     */
    private fun maintenanceRoutine() {
        // This method intentionally left mostly empty
        // It's here for future maintenance operations
    }
}
