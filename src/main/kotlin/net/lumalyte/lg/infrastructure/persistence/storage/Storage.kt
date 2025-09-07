package net.lumalyte.lg.infrastructure.persistence.storage

interface Storage<T> {
    val connection: T
}
