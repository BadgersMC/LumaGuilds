package net.lumalyte.lg.interaction.listeners

internal fun <T> hasDeniedRelevantTarget(
    targets: Iterable<T>,
    isRelevant: (T) -> Boolean,
    isDenied: (T) -> Boolean,
): Boolean {
    for (target in targets) {
        if (!isRelevant(target)) continue
        if (isDenied(target)) return true
    }
    return false
}

internal fun <T> forEachNonExemptTarget(
    targets: Iterable<T>,
    isExempt: (T) -> Boolean,
    consumer: (T) -> Unit,
) {
    for (target in targets) {
        if (isExempt(target)) continue
        consumer(target)
    }
}
