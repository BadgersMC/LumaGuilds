package net.lumalyte.lg.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.config.WebApiConfig
import net.lumalyte.lg.infrastructure.web.dto.GuildDirectoryPageDto
import net.lumalyte.lg.infrastructure.web.handlers.GuildLeaderboardHandler
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.logging.Logger
import kotlin.test.assertEquals

class GuildDirectoryAccessTest {
    @Test
    fun `directory requires configured bearer and validates methods and paging`() {
        val port = ServerSocket(0).use { it.localPort }
        val plugin = mockk<JavaPlugin>()
        every { plugin.logger } returns Logger.getLogger("GuildDirectoryAccessTest")
        val handler = mockk<GuildLeaderboardHandler>()
        every { handler.buildDirectoryPage(0, 50, null) } returns GuildDirectoryPageDto("2026-01-01T00:00:00Z", "a".repeat(64), 0, 0, null, emptyList())
        val config = WebApiConfig(enabled = true, host = "127.0.0.1", port = port)
        val server = WebApiServer(plugin, config, handler)
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            fun request(path: String = "", token: String? = null, method: String = "GET"): Int {
                val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/community/guild-directory$path"))
                if (token != null) builder.header("Authorization", "Bearer $token")
                builder.method(method, HttpRequest.BodyPublishers.noBody())
                return client.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
            }
            assertEquals(401, request())
            config.bearerToken = "private-test-token"
            assertEquals(401, request(token = "wrong"))
            assertEquals(405, request(token = config.bearerToken, method = "POST"))
            assertEquals(404, request(path = "/other", token = config.bearerToken))
            assertEquals(400, request(path = "?limit=51", token = config.bearerToken))
            assertEquals(200, request(token = config.bearerToken))
        } finally {
            server.stop()
        }
    }
}
