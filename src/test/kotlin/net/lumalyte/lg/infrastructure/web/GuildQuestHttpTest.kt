package net.lumalyte.lg.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.config.WebApiConfig
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.logging.Logger

class GuildQuestHttpTest {
    @Test fun `empty rotation retains explicit null and endpoint enforces authorization and methods`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.logger } returns Logger.getLogger("quest-http-test")
        val port = ServerSocket(0).use { it.localPort }
        val server = WebApiServer(plugin, WebApiConfig(true, "127.0.0.1", port, "test-only-token"), mockk()) {
            mapOf("schemaVersion" to 1, "generatedAt" to "2026-10-12T20:00:00Z",
                "currentWeekId" to null, "items" to emptyList<Any>())
        }
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            fun request(path: String = "/api/community/guildquests", auth: Boolean = true, method: String = "GET"): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                if (auth) builder.header("Authorization", "Bearer test-only-token")
                return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            val response = request()
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"currentWeekId\":null"))
            assertEquals(401, request(auth = false).statusCode())
            assertEquals(405, request(method = "POST").statusCode())
            assertEquals(404, request(path = "/api/community/guildquests/private").statusCode())
        } finally { server.stop() }
    }
}
