package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.DriverManager

class ChapterReadSQLTest {
    @Test fun readsCurrentChapterAndFormatsRemainingTime() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            ChapterLifecycleSchema.create(c, false)
            c.createStatement().use {
                it.execute("INSERT INTO chapter_lifecycle (chapter_id,chapter_name,phase,starts_at,ends_at,updated_at,version) VALUES ('c2','Chapter 2','SCHEDULED',1000,9100000,1,0)")
            }
            val view = requireNotNull(ChapterReadSQL(c).current())
            assertEquals("c2", view.id)
            assertEquals("Chapter 2", view.name)
            assertEquals("2h 31m", view.timeRemainingText(1000))
        }
    }
}
