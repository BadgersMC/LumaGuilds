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
    private val guildLeaderboardHandler: GuildLeaderboardHandler,
    /** REQ-301: the Hall of Prestige feed; null leaves the route unavailable. */
    private val prestigeFeed: net.lumalyte.lg.infrastructure.web.handlers.PrestigeHallFeed? = null,
    private val playerGuildSnapshot: ((List<java.util.UUID>) -> Map<String, Any?>)? = null,
    private val guildQuestSnapshot: (() -> Map<String, Any?>?)? = null,
) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val questGson: Gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private var server: HttpServer? = null

    fun start() {
        if (!config.enabled) {
            plugin.logger.info("[WebApi] disabled by config; skipping start.")
            return
        }
        if (server != null) return

        val httpServer = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
        httpServer.createContext("/api/leaderboards/guilds", ::handleGuildLeaderboard)
        httpServer.createContext("/api/community/guild-directory", ::handleGuildDirectory)
        httpServer.createContext("/api/community/prestige", ::handlePrestige)
        httpServer.createContext("/api/community/guildquests", ::handleGuildQuests)
        httpServer.createContext("/api/community/player-guilds", ::handlePlayerGuilds)
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

    private fun handleGuildDirectory(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != "/api/community/guild-directory") {
                respondJson(exchange, 404, mapOf("error" to "not_found")); return
            }
            if (exchange.requestMethod != "GET") {
                respondJson(exchange, 405, mapOf("error" to "method_not_allowed")); return
            }
            // The legacy leaderboard may be public when the token is blank; the directory must not be.
            if (config.bearerToken.isBlank() || !isAuthorized(exchange)) {
                respondJson(exchange, 401, mapOf("error" to "unauthorized")); return
            }
            val params = parseQuery(exchange.requestURI.rawQuery)
            val offset = params["offset"]?.toIntOrNull() ?: if (params.containsKey("offset")) -1 else 0
            val limit = params["limit"]?.toIntOrNull() ?: if (params.containsKey("limit")) -1 else 50
            if (offset < 0 || offset > 1000 || limit !in 1..50) {
                respondJson(exchange, 400, mapOf("error" to "invalid_page")); return
            }
            respondJson(exchange, 200, guildLeaderboardHandler.buildDirectoryPage(offset, limit, params["revision"]))
        } catch (_: Exception) {
            respondJson(exchange, 503, mapOf("error" to "directory_unavailable"))
        }
    }

    // REQ-301: like the directory, this feed is never public, even when the leaderboard token is blank.
    private fun handlePrestige(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != "/api/community/prestige") {
                respondJson(exchange, 404, mapOf("error" to "not_found")); return
            }
            if (exchange.requestMethod != "GET") {
                respondJson(exchange, 405, mapOf("error" to "method_not_allowed")); return
            }
            if (config.bearerToken.isBlank() || !isAuthorized(exchange)) {
                respondJson(exchange, 401, mapOf("error" to "unauthorized")); return
            }
            val feed = prestigeFeed ?: run { respondJson(exchange, 503, mapOf("error" to "prestige_unavailable")); return }
            val bytes = net.lumalyte.lg.infrastructure.web.handlers.PrestigeHallFeed.GSON.toJson(feed.build())
                .toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (t: Exception) {
            plugin.logger.warning("[WebApi] prestige feed failed: ${t.message}")
            respondJson(exchange, 503, mapOf("error" to "prestige_unavailable"))
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

    @Volatile private var questCache: Pair<Long, Map<String, Any?>>? = null
    private val questCacheLock = Any()
    private fun cachedQuestSnapshot(): Map<String, Any?>? = synchronized(questCacheLock) {
        val cached = questCache
        if (cached != null && System.currentTimeMillis() - cached.first < 60_000L) return@synchronized cached.second
        val data = guildQuestSnapshot?.invoke() ?: return@synchronized null
        if (questGson.toJson(data).toByteArray(StandardCharsets.UTF_8).size > 150_000) return@synchronized null
        questCache = System.currentTimeMillis() to data
        data
    }
    private fun handleGuildQuests(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != "/api/community/guildquests") {
                respondJson(exchange, 404, mapOf("error" to "not_found")); return
            }
            if (exchange.requestMethod != "GET") {
                respondJson(exchange, 405, mapOf("error" to "method_not_allowed")); return
            }
            if (config.bearerToken.isBlank() || !isAuthorized(exchange)) {
                respondJson(exchange, 401, mapOf("error" to "unauthorized")); return
            }
            val data = cachedQuestSnapshot()
            respondJson(exchange, if (data == null) 503 else 200,
                data ?: mapOf("error" to "quest_export_unavailable"), questGson)
        } catch (_: Exception) {
            plugin.logger.warning("[WebApi] quest export failed")
            respondJson(exchange, 503, mapOf("error" to "quest_export_unavailable"))
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun handlePlayerGuilds(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != "/api/community/player-guilds") {
                respondJson(exchange, 404, mapOf("error" to "not_found")); return
            }
            if (exchange.requestMethod != "GET") {
                respondJson(exchange, 405, mapOf("error" to "method_not_allowed")); return
            }
            if (config.bearerToken.isBlank() || !isAuthorized(exchange)) {
                respondJson(exchange, 401, mapOf("error" to "unauthorized")); return
            }
            val query = parseQuery(exchange.requestURI.rawQuery)
            val raw = query["players"]?.split(',').orEmpty()
            if (query.keys != setOf("players") || raw.size !in 1..50 || raw.map { it.lowercase() }.distinct().size != raw.size ||
                raw.any { !it.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) }) {
                respondJson(exchange, 400, mapOf("error" to "invalid_players")); return
            }
            val data = playerGuildSnapshot?.invoke(raw.map(java.util.UUID::fromString))
            if (data == null) { respondJson(exchange, 503, mapOf("error" to "guild_lookup_unavailable")); return }
            if (questGson.toJson(data).toByteArray(StandardCharsets.UTF_8).size > 150_000) {
                respondJson(exchange, 503, mapOf("error" to "guild_lookup_too_large")); return
            }
            respondJson(exchange, 200, data, questGson)
        } catch (_: Exception) { respondJson(exchange, 503, mapOf("error" to "guild_lookup_unavailable")) }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: Any, serializer: Gson = gson) {
        val bytes = serializer.toJson(body).toByteArray(StandardCharsets.UTF_8)
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
