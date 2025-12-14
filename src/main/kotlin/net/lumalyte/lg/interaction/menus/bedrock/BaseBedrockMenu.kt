package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.config.BedrockConfig
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.geysermc.floodgate.api.FloodgateApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Base class for Bedrock Edition menus using Cumulus forms
 */
abstract class BaseBedrockMenu(
    protected val menuNavigator: MenuNavigator,
    protected val player: Player,
    protected val logger: Logger
) : Menu, BedrockMenu, KoinComponent {

    companion object {
        // Track active forms and their timeout tasks
        private val activeForms = ConcurrentHashMap<String, FormTimeoutTask>()

        /**
         * Registers a form timeout for tracking
         */
        fun registerFormTimeout(playerId: String, menu: BaseBedrockMenu, timeoutSeconds: Int) {
            // Cancel any existing timeout for this player
            activeForms[playerId]?.cancel()

            // Create new timeout task
            val timeoutTask = FormTimeoutTask(playerId, menu, timeoutSeconds)
            activeForms[playerId] = timeoutTask

            // Schedule the timeout
            timeoutTask.runTaskLater(menu.getPlugin(), (timeoutSeconds * 20).toLong()) // 20 ticks per second
        }

        /**
         * Cancels a form timeout for a player
         */
        fun cancelFormTimeout(playerId: String) {
            activeForms.remove(playerId)?.cancel()
        }


    }

    protected val menuFactory: MenuFactory by inject()
    protected val bedrockLocalization: BedrockLocalizationService by inject()
    protected val formCacheService: net.lumalyte.lg.application.services.FormCacheService by inject()
    protected val formValidationService: net.lumalyte.lg.application.services.FormValidationService by inject()

    /**
     * Gets the Bedrock configuration
     */
    protected fun getBedrockConfig(): BedrockConfig {
        return try {
            val configService: net.lumalyte.lg.application.services.ConfigService by inject()
            configService.loadConfig().bedrock
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Could not load Bedrock config, using defaults: ${e.message}")
            BedrockConfig()
        }
    }

    /**
     * Creates a form image based on configuration using Cumulus FormImage API.
     * Prioritizes URL if available, falls back to PATH, or returns null if neither is valid.
     */
    protected fun createFormImage(imageUrl: String, imagePath: String): org.geysermc.cumulus.util.FormImage? {
        val config = getBedrockConfig()
        return BedrockFormUtils.createFormImage(config, imageUrl, imagePath)
    }

    /**
     * Opens the menu by building and sending the form to the Bedrock player
     */
    override fun open() {
        try {
            // Check if Bedrock services are still available before opening
            if (!isBedrockServicesAvailable()) {
                handleBedrockUnavailable()
                return
            }

            val form = getFormCached()

        // Send the form using Floodgate API
        val floodgateApi = FloodgateApi.getInstance()
        // Send the built form with timeout handling
        floodgateApi.sendForm(player.uniqueId, form)

        // Register timeout for this form
        val config = getBedrockConfig()
        registerFormTimeout(player.uniqueId.toString(), config.formTimeoutSeconds)

        // Log successful form opening for debugging
        logger.fine("Opened Bedrock form ${this::class.simpleName} for player ${player.name} (Timeout: ${config.formTimeoutSeconds}s)")

        } catch (e: IllegalStateException) {
            // Handle cases where Floodgate is not properly initialized
            logger.warning("Floodgate not properly initialized for menu ${this::class.simpleName}: ${e.message}")
            handleBedrockUnavailable()

        } catch (e: IllegalArgumentException) {
            // Handle invalid player UUID or form data
            logger.warning("Invalid arguments for Bedrock menu ${this::class.simpleName}: ${e.message}")
            player.sendMessage("§cUnable to open menu due to invalid data. Please contact an administrator.")

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Generic error handling
            logger.warning("Unexpected error opening Bedrock menu ${this::class.simpleName} for player ${player.name}: ${e.message}")
            logger.warning("Stack trace: ${e.stackTraceToString()}")

            // Try fallback to Java menu
            handleBedrockUnavailable()
        }
    }

    /**
     * Default implementation for data passing - can be overridden by subclasses
     */
    override fun passData(data: Any?) {
        // Default implementation does nothing
        // Subclasses can override this to handle data passed from other menus
    }

    /**
     * Called when a form response is received - cancels any pending timeout
     */
    protected fun onFormResponseReceived() {
        val playerId = player.uniqueId.toString()
        cancelFormTimeout(playerId)
        logger.fine("Form response received for ${this::class.simpleName}, cancelled timeout for player ${player.name}")
    }

    /**
     * Registers a form timeout for this menu instance
     */
    private fun registerFormTimeout(playerId: String, timeoutSeconds: Int) {
        BaseBedrockMenu.registerFormTimeout(playerId, this, timeoutSeconds)
    }

    /**
     * Public getter for player (used by FormTimeoutTask)
     */
    fun getPlayerInstance(): Player = player

    /**
     * Public getter for logger (used by FormTimeoutTask)
     */
    fun getLoggerInstance(): Logger = logger

    /**
     * Public getter for menuNavigator (used by FormTimeoutTask)
     */
    fun getMenuNavigatorInstance(): MenuNavigator = menuNavigator

    /**
     * Template method for creating form response handlers
     * This will be called when Cumulus response handling is available
     */
    protected open fun createResponseHandler(): Any? {
        // When Cumulus is available, this will return a response handler
        // For now, return null
        return null
    }

    /**
     * Bedrock-specific navigation helper
     */
    protected val bedrockNavigator = BedrockMenuNavigator(menuNavigator, player, menuFactory)

    /**
     * Helper method to handle navigation back to previous menu
     */
    protected fun navigateBack() {
        menuNavigator.goBack()
    }

    /**
     * Helper method to handle navigation back with data
     */
    protected fun navigateBackWithData(data: Any?) {
        menuNavigator.goBackWithData(data)
    }

    /**
     * Helper method to open a new menu
     */
    protected fun openMenu(menu: Menu) {
        menuNavigator.openMenu(menu)
    }

    /**
     * Helper method to clear the menu navigation stack
     */
    protected fun clearMenuStack() {
        menuNavigator.clearMenuStack()
    }

    // Form State Management

    /**
     * Saves form state for later restoration
     * @param key Unique identifier for the state
     * @param state Data to save
     */
    public fun saveFormState(key: String, state: Map<String, Any?>) {
        val stateKey = "${player.uniqueId}:$key"
        FormStateManager.saveState(stateKey, state)
    }

    /**
     * Restores form state for the current menu
     * @param key Unique identifier for the state
     * @return The saved state or empty map if not found
     */
    protected fun restoreFormState(key: String): Map<String, Any?> {
        val stateKey = "${player.uniqueId}:$key"
        return FormStateManager.restoreState(stateKey) ?: emptyMap()
    }

    /**
     * Clears saved form state
     * @param key Unique identifier for the state to clear
     */
    protected fun clearFormState(key: String) {
        val stateKey = "${player.uniqueId}:$key"
        FormStateManager.clearState(stateKey)
    }

    /**
     * Saves the current form data for multi-step workflows
     * @param stepName Identifier for the current step
     * @param formData The form data to save
     */
    protected fun saveWorkflowStep(stepName: String, formData: Map<String, Any?>) {
        val workflowKey = "${player.uniqueId}:${this::class.simpleName}:workflow"
        val existingSteps = FormStateManager.restoreState(workflowKey) ?: emptyMap()
        val updatedSteps = existingSteps.toMutableMap()
        updatedSteps[stepName] = formData
        FormStateManager.saveState(workflowKey, updatedSteps)
    }

    /**
     * Convenience method to show validation errors and reopen the form
     * @param errors List of validation error messages
     */
    protected fun showFormValidationErrors(errors: List<String>) {
        formValidationService.showValidationErrors(player, errors, { reopen() }, { localize(it) })
    }

    /**
     * Restores workflow data for multi-step forms
     * @return Map of step names to their saved data
     */
    @Suppress("UNCHECKED_CAST")
    protected fun restoreWorkflow(): Map<String, Map<String, Any?>> {
        val workflowKey = "${player.uniqueId}:${this::class.simpleName}:workflow"
        return (FormStateManager.restoreState(workflowKey) as? Map<String, Map<String, Any?>>) ?: emptyMap()
    }

    /**
     * Clears workflow data
     */
    protected fun clearWorkflow() {
        val workflowKey = "${player.uniqueId}:${this::class.simpleName}:workflow"
        FormStateManager.clearState(workflowKey)
    }

    /**
     * Enhanced navigation with state preservation
     */
    protected fun navigateBackWithState() {
        val stateKey = "${this::class.simpleName}:last"
        val lastState = restoreFormState(stateKey)
        if (lastState.isNotEmpty()) {
            navigateBackWithData(lastState)
        } else {
            navigateBack()
        }
    }

    /**
     * Navigates forward to a menu while preserving current state
     */
    protected fun navigateForwardWithState(menu: Menu) {
        val currentState = getCurrentFormState()
        saveFormState("${this::class.simpleName}:forward", currentState)
        openMenu(menu)
    }

    /**
     * Cancels current workflow and clears all state
     */
    protected fun cancelWorkflow() {
        clearWorkflow()
        clearMenuStack()
        player.sendMessage("§eWorkflow cancelled. All progress cleared.")
    }

    /**
     * Gets the current form state for preservation
     */
    public fun getCurrentFormState(): Map<String, Any?> {
        // This should be overridden by subclasses to provide actual form state
        return emptyMap()
    }


    /**
     * Gets a localized string for the current player
     */
    protected fun localize(key: String, vararg args: Any?): String {
        return bedrockLocalization.getBedrockString(player, key, *args)
    }

    /**
     * Determines if this form should be cached
     * Override to enable caching for frequently used forms
     */
    protected open fun shouldCacheForm(): Boolean = false

    /**
     * Creates a cache key for this form
     * Should include all parameters that affect form content
     */
    protected open fun createCacheKey(): String {
        return "${this::class.simpleName}:${player.uniqueId}"
    }

    /**
     * Gets the form, using cache if enabled
     */
    private fun getFormCached(): org.geysermc.cumulus.form.Form {
        return if (shouldCacheForm()) {
            val cacheKey = createCacheKey()
            formCacheService.getOrBuildForm(cacheKey) {
                logger.fine("Building form for cache: $cacheKey")
                getForm()
            }
        } else {
            getForm()
        }
    }

    /**
     * Determines if this form should be built asynchronously
     * Override for complex forms that take time to build
     */
    protected open fun shouldBuildAsync(): Boolean = false

    /**
     * Public method to reopen the menu (used by form response handlers)
     */
    fun reopen() {
        openMenu()
    }

    /**
     * Opens the menu asynchronously if enabled, otherwise synchronously
     */
    private fun openMenu() {
        try {
            // Check if Bedrock services are still available before opening
            if (!isBedrockServicesAvailable()) {
                handleBedrockUnavailable()
                return
            }

            if (shouldBuildAsync()) {
                openAsync()
            } else {
                openSync()
            }

        } catch (e: IllegalStateException) {
            // Handle cases where Floodgate is not properly initialized
            logger.warning("Floodgate not properly initialized for menu ${this::class.simpleName}: ${e.message}")
            handleBedrockUnavailable()

        } catch (e: IllegalArgumentException) {
            // Handle invalid player UUID or form data
            logger.warning("Invalid arguments for Bedrock menu ${this::class.simpleName}: ${e.message}")
            player.sendMessage("§cUnable to open menu due to invalid data. Please contact an administrator.")

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Generic error handling
            logger.warning("Unexpected error opening Bedrock menu ${this::class.simpleName} for player ${player.name}: ${e.message}")
            logger.warning("Stack trace: ${e.stackTraceToString()}")

            // Try fallback to Java menu
            handleBedrockUnavailable()
        }
    }

    /**
     * Opens the menu synchronously
     */
    private fun openSync() {
        val form = getFormCached()

        // Send the form using Floodgate API
        val floodgateApi = FloodgateApi.getInstance()
        // Send the built form with timeout handling
        floodgateApi.sendForm(player.uniqueId, form)

        // Register timeout for this form
        val config = getBedrockConfig()
        registerFormTimeout(player.uniqueId.toString(), config.formTimeoutSeconds)

        // Log successful form opening for debugging
        logger.fine("Opened Bedrock form ${this::class.simpleName} for player ${player.name} (Timeout: ${config.formTimeoutSeconds}s)")
    }

    /**
     * Opens the menu asynchronously
     */
    private fun openAsync() {
        // Show loading message
        player.sendMessage("§eLoading menu...")

        // Build form asynchronously
        val cacheKey = if (shouldCacheForm()) createCacheKey() else null

        val formFuture = if (cacheKey != null) {
            formCacheService.getOrBuildFormAsync(cacheKey) {
                logger.fine("Building form asynchronously for cache: $cacheKey")
                getForm()
            }
        } else {
            formCacheService.buildFormAsync {
                logger.fine("Building form asynchronously: ${this::class.simpleName}")
                getForm()
            }
        }

        // Handle completion
        formFuture.thenAccept { form ->
            try {
                // Send the form using Floodgate API
                val floodgateApi = FloodgateApi.getInstance()
                floodgateApi.sendForm(player.uniqueId, form)

                // Register timeout for this form
                val config = getBedrockConfig()
                registerFormTimeout(player.uniqueId.toString(), config.formTimeoutSeconds)

                // Clear loading message and show success
                player.sendMessage("§aMenu loaded successfully!")
                logger.fine("Opened Bedrock form asynchronously ${this::class.simpleName} for player ${player.name}")

            } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
                logger.warning("Error sending async form ${this::class.simpleName} to player ${player.name}: ${e.message}")
                player.sendMessage("§cFailed to load menu. Please try again.")
            }
        }.exceptionally { throwable ->
            logger.warning("Async form building failed for ${this::class.simpleName}: ${throwable.message}")
            player.sendMessage("§cFailed to load menu. Please try again.")
            null
        }
    }

    /**
     * Gets a localized string for a specific locale
     */
    protected fun localize(locale: java.util.Locale, key: String, vararg args: Any?): String {
        return bedrockLocalization.getBedrockString(locale, key, *args)
    }

    /**
     * Checks if the current player's locale requires RTL text direction
     */
    protected fun isRTL(): Boolean {
        val locale = bedrockLocalization.getBedrockLocale(player)
        return bedrockLocalization.isRTLLocale(locale)
    }

    /**
     * Gets the text direction for the current player's locale
     */
    protected fun getTextDirection(): net.lumalyte.lg.application.services.TextDirection {
        val locale = bedrockLocalization.getBedrockLocale(player)
        return bedrockLocalization.getTextDirection(locale)
    }

    /**
     * Checks if Bedrock services (Floodgate/Cumulus) are available
     * This provides real-time availability checking
     */
    private fun isBedrockServicesAvailable(): Boolean {
        return try {
            // Check Floodgate availability
            val floodgateApi = FloodgateApi.getInstance()
            if (floodgateApi == null) {
                logger.warning("Floodgate API is null")
                return false
            }

            // Check if player is still connected and is Bedrock
            if (!floodgateApi.isFloodgatePlayer(player.uniqueId)) {
                logger.fine("Player ${player.name} is no longer a Bedrock player")
                return false
            }

            // Check if Cumulus classes are still available (use correct package)
            Class.forName("org.geysermc.cumulus.form.Form")

            true
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error checking Bedrock services availability: ${e.message}")
            false
        }
    }

    /**
     * Handles the case when Bedrock services are unavailable
     * Attempts graceful fallback to Java menus
     */
    private fun handleBedrockUnavailable() {
        try {
            // Get configuration to check if fallback is enabled
            val config = getBedrockConfig()

            if (config.fallbackToJavaMenus) {
                logger.info("Attempting fallback to Java menu for player ${player.name}")

                // Try to create equivalent Java menu
                val javaMenu = createFallbackJavaMenu()
                if (javaMenu != null) {
                    player.sendMessage("§eBedrock menu unavailable. Opening Java version...")
                    openMenu(javaMenu)
                    return
                }
            }

            // If no fallback available or disabled, show error message
            player.sendMessage("§cBedrock menus are currently unavailable. Please try again later or contact an administrator.")
            logger.warning("Bedrock menu ${this::class.simpleName} failed for player ${player.name} - no fallback available")

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error during Bedrock fallback handling: ${e.message}")
            player.sendMessage("§cAn error occurred. Please contact an administrator.")
        }
    }


    /**
     * Creates a fallback Java menu equivalent
     * This should be overridden by subclasses to provide appropriate fallbacks
     */
    protected open fun createFallbackJavaMenu(): Menu? {
        // Default implementation returns null - subclasses should override
        logger.warning("No Java fallback menu implemented for ${this::class.simpleName}")
        return null
    }

    /**
     * Gets the plugin instance for scheduling tasks
     * This needs to be injected or accessed through the application
     */
    private fun getPlugin(): org.bukkit.plugin.Plugin {
        // This is a placeholder - in a real implementation, you'd inject the plugin
        // For now, we'll access it through the player/server
        return player.server.pluginManager.getPlugin("LumaGuilds")
            ?: throw IllegalStateException("LumaGuilds plugin not found")
    }
}

/**
 * Task that handles form timeouts
 */
class FormTimeoutTask(
    private val playerId: String,
    private val menu: BaseBedrockMenu,
    private val timeoutSeconds: Int
) : BukkitRunnable() {

    override fun run() {
        try {
            // Check if player is still online
            val player = menu.getPlayerInstance()
            if (!player.isOnline) {
                BaseBedrockMenu.cancelFormTimeout(playerId)
                return
            }

            // Send timeout message
            player.sendMessage("§eMenu timed out after $timeoutSeconds seconds.")
            player.sendMessage("§7You can continue where you left off by reopening the menu.")

            menu.getLoggerInstance().info("Form timeout for player ${player.name} after $timeoutSeconds seconds")

            // Save current state for recovery
            val currentState = menu.getCurrentFormState()
            if (currentState.isNotEmpty()) {
                menu.saveFormState("timeout_recovery", currentState)
            }

            // Try to go back to previous menu
            menu.getMenuNavigatorInstance().goBack()

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            menu.getLoggerInstance().warning("Error handling form timeout: ${e.message}")
        } finally {
            // Always clean up the timeout tracking
            BaseBedrockMenu.cancelFormTimeout(playerId)
        }
    }
}