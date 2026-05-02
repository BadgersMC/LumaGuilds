package net.lumalyte.lg.infrastructure.web

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.lumalyte.lg.config.WebApiConfig
import net.lumalyte.lg.infrastructure.web.handlers.GuildLeaderboardHandler
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class WebApiServer(
    private val plugin: JavaPlugin,
    private val config: WebApiConfig,
    private val guildLeaderboardHandler: GuildLeaderboardHandler
) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private var server: HttpServer? = null

    fun start() {
        if (!config.enabled) {
            plugin.logger.info("[WebApi] disabled by config; skipping start.")
            return
        }
        if (server != null) return

        val httpServer = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
        httpServer.createContext("/api/leaderboards/guilds", ::handleGuildLeaderboard)
        httpServer.createContext("/api/health") { exchange ->
            respondJson(exchange, 200, mapOf("status" to "ok"))
        }
        httpServer.executor = Executors.newFixedThreadPool(2, namedThreadFactory("LumaGuilds-WebApi"))
        httpServer.start()
        server = httpServer
        plugin.logger.info("[WebApi] listening on http://${config.host}:${config.port}")
    }

    fun stop() {
        server?.let {
            it.stop(0)
            plugin.logger.info("[WebApi] stopped.")
        }
        server = null
    }

    private fun handleGuildLeaderboard(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") {
                respondJson(exchange, 405, mapOf("error" to "method_not_allowed"))
                return
            }
            if (!isAuthorized(exchange)) {
                respondJson(exchange, 401, mapOf("error" to "unauthorized"))
                return
            }

            val params = parseQuery(exchange.requestURI.rawQuery)

            // Heavy work hits services that may touch the DB. Run on the calling
            // executor thread (already off the main server thread). Services are
            // expected to be thread-safe — they are also called from PAPI / other
            // async paths in this plugin.
            val response = guildLeaderboardHandler.build(
                typeParam = params["type"],
                periodParam = params["period"],
                limitParam = params["limit"]
            )
            respondJson(exchange, 200, response)
        } catch (t: Throwable) {
            plugin.logger.warning("[WebApi] guild leaderboard failed: ${t.message}")
            respondJson(exchange, 500, mapOf("error" to "internal_error"))
        }
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val token = config.bearerToken
        if (token.isBlank()) return true
        val header = exchange.requestHeaders.getFirst("Authorization") ?: return false
        if (!header.startsWith("Bearer ")) return false
        val provided = header.substring("Bearer ".length).trim()
        return constantTimeEquals(provided, token)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: Any) {
        val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else {
                val k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
                val v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                k to v
            }
        }.toMap()
    }

    private fun namedThreadFactory(prefix: String): ThreadFactory {
        val counter = AtomicInteger(1)
        return ThreadFactory { r ->
            Thread(r, "$prefix-${counter.getAndIncrement()}").apply { isDaemon = true }
        }
    }
}
