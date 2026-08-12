package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Rank-permission DB parsing — self-healing against enum/DB drift.
 *
 * Regression: `EXPORT_BANK_DATA` was removed from [RankPermission] in #90 (CSV export
 * removal) but remained in live rank rows, crashing plugin enable via
 * `RankPermission.valueOf` in preload. The parser must skip unknown names instead.
 */
class RankRepositorySQLiteParseTest {

    @Test
    fun `parses known permissions`() {
        assertEquals(
            setOf(RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS),
            RankRepositorySQLite.parseRankPermissions("MANAGE_RANKS, MANAGE_MEMBERS")
        )
    }

    @Test
    fun `skips unknown permissions instead of crashing`() {
        // EXPORT_BANK_DATA no longer exists in the enum — must be dropped, not fatal.
        assertEquals(
            setOf(RankPermission.MANAGE_RANKS),
            RankRepositorySQLite.parseRankPermissions("MANAGE_RANKS,EXPORT_BANK_DATA")
        )
    }

    @Test
    fun `handles null blank and whitespace-only values`() {
        assertTrue(RankRepositorySQLite.parseRankPermissions(null).isEmpty())
        assertTrue(RankRepositorySQLite.parseRankPermissions("").isEmpty())
        assertTrue(RankRepositorySQLite.parseRankPermissions("   ,, ").isEmpty())
    }

    @Test
    fun `deduplicates repeated permissions`() {
        assertEquals(
            setOf(RankPermission.MANAGE_RANKS),
            RankRepositorySQLite.parseRankPermissions("MANAGE_RANKS,MANAGE_RANKS")
        )
    }
}
