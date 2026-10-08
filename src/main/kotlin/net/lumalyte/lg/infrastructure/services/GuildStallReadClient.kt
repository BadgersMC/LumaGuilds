package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.GuildStallInfo
import net.lumalyte.lg.application.services.GuildStallMemberInfo
import net.lumalyte.lg.application.services.GuildStallPermission
import net.lumalyte.lg.application.services.GuildStallReadService
import net.lumalyte.lg.application.services.StallReadResult
import org.bukkit.Bukkit
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/** Uses the provider's own classloader contract, with no mandatory Market dependency. */
internal class GuildStallReadClient(
    private val provider: () -> Any? = {
        val services = Bukkit.getServicesManager()
        services.knownServices.firstOrNull { it.name == API_NAME }?.let {
            services.getRegistration(it)?.provider
        }
    },
) : GuildStallReadService {
    override fun read(
        guildId: UUID,
        viewerId: UUID,
    ): CompletableFuture<StallReadResult> {
        return try {
            val service = provider() ?: return unavailable()
            if (service.javaClass.getMethod("apiVersion").invoke(service) != API_VERSION) return unavailable()
            val stage =
                service.javaClass
                    .getMethod("guildStalls", UUID::class.java, UUID::class.java)
                    .invoke(service, guildId, viewerId) as? CompletionStage<*> ?: return unavailable()
            stage.toCompletableFuture().orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).handle<StallReadResult> { value, error ->
                if (error != null) {
                    StallReadResult.Unavailable
                } else {
                    runCatching<StallReadResult> {
                        StallReadResult.Available((value as List<*>).map { decode(requireNotNull(it)) })
                    }.getOrDefault(StallReadResult.Unavailable)
                }
            }
        } catch (_: Exception) {
            unavailable()
        }
    }

    private fun decode(row: Any): GuildStallInfo =
        GuildStallInfo(
            row.text("id"),
            row.text("region"),
            row.text("world"),
            row.text("state"),
            (row.field("rent") as Long).also { require(it >= 0) },
            (row.field("intervalSeconds") as Long).also { require(it > 0) },
            row.optional<Instant>("nextRentAt"),
            row.optional<Instant>("graceEndsAt"),
            row.optional<String>("coordinates"),
            (row.field("members") as List<*>).map { raw ->
                val member = requireNotNull(raw)
                GuildStallMemberInfo(
                    member.field("playerId") as UUID,
                    (member.field("permissions") as Set<*>).map { GuildStallPermission.valueOf(it as String) }.toSet(),
                )
            },
        )

    // A wrong non-null type is an unavailable contract, not silently missing metadata.
    private inline fun <reified T> Any.optional(name: String): T? {
        val value = field(name) ?: return null
        return value as? T ?: error("Invalid nullable companion field: $name")
    }

    private fun Any.field(name: String): Any? = javaClass.getMethod(name).invoke(this)

    private fun Any.text(name: String): String =
        (field(name) as String).also {
            require(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH && it.none(Char::isISOControl))
        }

    private fun unavailable(): CompletableFuture<StallReadResult> = CompletableFuture.completedFuture(StallReadResult.Unavailable)

    private companion object {
        const val API_NAME = "net.enthusia.market.api.guild.GuildStallReadApi"
        const val API_VERSION = 1
        const val TIMEOUT_SECONDS = 10L
        const val MAX_TEXT_LENGTH = 128
    }
}

/** Shared late-result guard for both Java inventories and Bedrock forms. */
internal object GuildStallLoadGuard {
    /** Accept only data for the current connected member and navigation generation. */
    fun accepts(
        expected: Long,
        current: Long,
        online: Boolean,
        member: Boolean,
    ): Boolean = expected == current && online && member
}
