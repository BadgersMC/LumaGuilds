package net.lumalyte.lg.di.runtime

/**
 * Shutdown stages in REQ-NEXUS-CUTOVER-3 order. [GATES] precedes service unregistration for
 * irreversible gates (durable invite-block writes) that must close before anything can throw.
 */
internal enum class ShutdownStage { GATES, SERVICES, INTEGRATIONS, TASKS, FLUSH, CONTEXT, STORAGE }

/**
 * Records every resource the runtime acquires and releases them exactly once.
 *
 * Stages run in [ShutdownStage] order; within a stage, the most recently acquired resource is
 * released first. One failing action is reported and never prevents later actions, so storage
 * always closes last. Resources acquired after [close] (for example an optional plugin enabling
 * during shutdown) are released immediately.
 */
internal class RuntimeShutdown(private val onFailure: (String, Throwable) -> Unit) {
    private class Action(val name: String, val run: () -> Unit)

    private val lock = Any()
    private val stages = ShutdownStage.entries.associateWith { ArrayDeque<Action>() }
    private var closed = false

    val isClosed: Boolean get() = synchronized(lock) { closed }

    fun register(stage: ShutdownStage, name: String, action: () -> Unit) {
        val runNow = synchronized(lock) {
            if (!closed) stages.getValue(stage).addLast(Action(name, action))
            closed
        }
        if (runNow) runSafely(Action(name, action))
    }

    /** Returns false when shutdown already ran. */
    fun close(): Boolean {
        val ordered = synchronized(lock) {
            if (closed) return false
            closed = true
            ShutdownStage.entries.flatMap { stage -> stages.getValue(stage).reversed().also { stages.getValue(stage).clear() } }
        }
        ordered.forEach(::runSafely)
        return true
    }

    private fun runSafely(action: Action) {
        try {
            action.run()
        } catch (failure: Exception) {
            onFailure(action.name, failure)
        } catch (failure: LinkageError) {
            // Optional plugin APIs can fail class loading during shutdown.
            onFailure(action.name, failure)
        }
    }
}
