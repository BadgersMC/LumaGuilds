package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.RejectedExecutionException
import java.util.logging.Logger

class FormCacheServiceGuavaTest {
    @Test
    fun `shutdown releases the async form executor`() {
        val service = FormCacheServiceGuava(logger = Logger.getLogger("FormCacheServiceGuavaTest"))

        service.shutdown()

        assertThrows<RejectedExecutionException> {
            service.buildFormAsync { error("form builder should not run after shutdown") }
        }
    }
}