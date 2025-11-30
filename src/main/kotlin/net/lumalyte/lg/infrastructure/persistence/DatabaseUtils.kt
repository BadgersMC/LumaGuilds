package net.lumalyte.lg.infrastructure.persistence

import co.aikar.idb.DbRow
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * Helper function to get Instant from a DbRow column that could be either a String (SQLite) or Timestamp (MariaDB)
 */
fun DbRow.getInstant(columnName: String): Instant? {
    val value: Any? = this.get(columnName)
    if (value == null) return null

    try {
        when {
            value is Timestamp -> {
                return Instant.ofEpochMilli((value as java.util.Date).time)
            }
            value is String -> {
                if (value.isBlank()) return null

                // Try parsing as ISO-8601 first
                return try {
                    Instant.parse(value)
                } catch (e: SQLException) {
                    // Try parsing as LocalDateTime (MariaDB DATETIME format: "YYYY-MM-DD HH:MM:SS")
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    val localDateTime = java.time.LocalDateTime.parse(value, formatter)
                    localDateTime.atZone(java.time.ZoneId.of("UTC")).toInstant()
                }
            }
            else -> {
                throw IllegalStateException("Unexpected type for column $columnName: ${value::class.java.name} with value: $value")
            }
        }
    } catch (e: SQLException) {
        throw IllegalStateException("Error parsing column $columnName: ${e.message}", e)
    }
}

/**
 * Helper function to get a non-null Instant from a DbRow column
 */
fun DbRow.getInstantNotNull(columnName: String): Instant {
    return getInstant(columnName) ?: throw IllegalStateException("Column $columnName is null but required")
}
