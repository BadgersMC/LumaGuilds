package net.lumalyte.lg.di.runtime

/**
 * Platform operations driven by [GuildRuntimeLifecycle], in the order it calls them.
 *
 * Every step that acquires a Bukkit registration, schedule or flushable resource records its
 * release on the supplied [RuntimeShutdown]. Storage and context release are owned by the
 * lifecycle so they always run last, in that order.
 */
internal interface GuildRuntimeSteps<S : Any, C : Any> {
    /** Static gate; fails before any storage or context exists when command parity is unproven. */
    fun verifyCommandParity()
    fun openStorage(): S
    fun migrate(storage: S)
    fun closeStorage(storage: S)
    fun activate(storage: S): C
    fun closeContext(context: C)
    /** Reconciles startup invariants, then publishes external Bukkit service contracts. */
    fun publishServices(context: C, shutdown: RuntimeShutdown)
    /** Optional integrations, including PlaceholderAPI, once the graph is active. */
    fun registerIntegrations(context: C, shutdown: RuntimeShutdown)
    fun registerListeners(context: C, shutdown: RuntimeShutdown)
    fun registerCommands(context: C, shutdown: RuntimeShutdown)
    fun startSchedules(context: C, shutdown: RuntimeShutdown)
}

/**
 * The single Nexus runtime owner for LumaGuilds (REQ-NEXUS-CUTOVER-2/3).
 *
 * Startup order: command-parity gate, storage, migrations, one Nexus context, external services,
 * optional integrations, listeners, commands, then schedules. Any failure releases exactly the
 * resources acquired so far and rethrows; [stop] is idempotent and safe after a failed start.
 */
internal class GuildRuntimeLifecycle<S : Any, C : Any>(
    private val steps: GuildRuntimeSteps<S, C>,
    private val onFailure: (String, Throwable) -> Unit,
) {
    private val shutdown = RuntimeShutdown(onFailure)
    private var started = false

    @Volatile
    var context: C? = null
        private set

    fun start(): C {
        check(!started) { "LumaGuilds runtime was already started; create a new lifecycle per enable" }
        started = true
        try {
            steps.verifyCommandParity()
            val storage = steps.openStorage()
            shutdown.register(ShutdownStage.STORAGE, "storage") { steps.closeStorage(storage) }
            steps.migrate(storage)

            val active = steps.activate(storage)
            shutdown.register(ShutdownStage.CONTEXT, "Nexus context") {
                context = null
                steps.closeContext(active)
            }
            context = active

            steps.publishServices(active, shutdown)
            steps.registerIntegrations(active, shutdown)
            steps.registerListeners(active, shutdown)
            steps.registerCommands(active, shutdown)
            steps.startSchedules(active, shutdown)
            return active
        } catch (failure: Throwable) {
            shutdown.close()
            context = null
            throw failure
        }
    }

    /** Releases the runtime; returns false when it was already released. */
    fun stop(): Boolean {
        val released = shutdown.close()
        context = null
        return released
    }
}
