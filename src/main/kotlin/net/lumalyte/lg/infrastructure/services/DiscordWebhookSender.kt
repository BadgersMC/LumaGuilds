package net.lumalyte.lg.infrastructure.services

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** REQ-188: fire-and-forget webhook POST off the main thread, 10 second limit. */
class DiscordWebhookSender(
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
) : (String, String) -> Unit {
    private val logger = LoggerFactory.getLogger(DiscordWebhookSender::class.java)

    override fun invoke(url: String, json: String) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("User-Agent", "LumaGuilds")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete { response, error ->
            when {
                error != null -> logger.warn("Discord prestige post failed: ${error.message}")
                response.statusCode() !in 200..299 -> logger.warn("Discord prestige post returned ${response.statusCode()}")
            }
        }
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
