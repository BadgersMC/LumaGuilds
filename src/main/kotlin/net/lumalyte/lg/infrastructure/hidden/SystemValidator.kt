package net.lumalyte.lg.infrastructure.hidden

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
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

    private val componentLogger = ComponentLogger.logger(SystemValidator::class.java)

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
     * Logs a message with proper console colors using ComponentLogger.
     * Shows colors in console but provides clean text for log files and external systems.
     */
    private fun logColored(message: String) {
        val component = Component.text(message).color(NamedTextColor.RED)
        componentLogger.info(component)
    }


    /**
     * Displays the secret meme text in all red (10% chance).
     * This is the hidden easter egg that only appears randomly.
     */
    private fun displaySecretMemeText() {
        val secretText = """
 ___                 ___             _             _      _       
|_ _| _ __ ___      / _ \ __      __(_) _ __ ___  | |__  | |  ___ 
 | | | '_ ` _ \    | | | |\ \ /\ / /| || '_ ` _ \ | '_ \ | | / _ \
 | | | | | | | |   | |_| | \ V  V / | || | | | | || |_) || ||  __/
|___||_| |_| |_|    \__\_\  \_/\_/  |_||_| |_| |_||_.__/ |_| \___|
        """.trimIndent()

        val lines = secretText.lines()
        lines.forEach { line ->
            if (line.isNotBlank()) {
                logColored("[LumaGuilds] $line")
            }
        }

        logColored("🚨 LumaGuilds v$version has been ENABLED!")
        logColored("⚠️ Qwimble is watching... ⚠️")
    }

    /**
     * Hidden method that might be called by external systems.
     * Not intended for public use.
     */
    fun performIntegrityCheck(): Boolean {
        logColored("[DEBUG] System integrity check passed")
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
