package net.lumalyte.lg.infrastructure.persistence.guilds

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import net.lumalyte.lg.domain.entities.DurableWarRecord
import java.time.Duration
import java.time.Instant

/** Versioned format. Invalid persisted state is fatal, never interpreted as an empty war registry. */
internal object WarRecordCodec {
    private val gson = GsonBuilder().serializeNulls()
        .registerTypeAdapter(Instant::class.java, TextAdapter(Instant::parse).nullSafe())
        .registerTypeAdapter(Duration::class.java, TextAdapter(Duration::parse).nullSafe())
        .create()

    fun encode(record: DurableWarRecord): String = gson.toJson(JsonObject().apply {
        addProperty("version", 4)
        add("record", gson.toJsonTree(record))
    })

    fun decode(payload: String): DurableWarRecord = try {
        val root = JsonParser.parseString(payload).asJsonObject
        val version = root.get("version")?.asInt
        check(version != null && version in 1..4) { "Unsupported war record version" }
        val json = root.getAsJsonObject("record").deepCopy()
        check(json.keySet().containsAll(setOf("id", "revision", "fundingCycle", "declaration", "war", "stats", "wager",
            "paymentPhase", "settlementChosen", "settlementWinner", "paymentAttempts"))) { "Incomplete war record" }
        if (version >= 3) {
            check(json.keySet().containsAll(setOf(
                "notificationRecipients",
                "declarationNotificationExpected",
                "acceptanceNotificationExpected",
                "resolutionNotificationExpected",
            ))) { "Incomplete war notification recovery state" }
        } else {
            json.add("notificationRecipients", gson.toJsonTree(net.lumalyte.lg.domain.entities.WarNotificationRecipients()))
            json.addProperty("declarationNotificationExpected", false)
            json.addProperty("acceptanceNotificationExpected", false)
            json.addProperty("resolutionNotificationExpected", false)
        }

        // v1 predates explicit rated-war identity. Existing persisted wars must remain
        // unrated rather than being rejected or silently attached to a current chapter.
        if (version == 1) {
            json.get("declaration")?.takeUnless { it.isJsonNull }?.asJsonObject
                ?.add("ratedChapterId", com.google.gson.JsonNull.INSTANCE)
            json.get("war")?.takeUnless { it.isJsonNull }?.asJsonObject
                ?.add("ratedChapterId", com.google.gson.JsonNull.INSTANCE)
        } else {
            json.get("declaration")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
                check(it.has("ratedChapterId")) { "Incomplete war declaration rating identity" }
            }
            json.get("war")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
                check(it.has("ratedChapterId")) { "Incomplete war rating identity" }
            }
        }

        // v4 persists the exact cooldown deadline chosen when a declaration is
        // created or a winning war ends. v3 was already deployed for durable
        // notification recovery, so v1-v3 must remain readable without cooldown fields.
        if (version < 4) {
            json.get("declaration")?.takeUnless { it.isJsonNull }?.asJsonObject
                ?.add("declarationCooldownUntil", com.google.gson.JsonNull.INSTANCE)
            json.get("war")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
                it.add("farmingCooldownGuildId", com.google.gson.JsonNull.INSTANCE)
                it.add("farmingCooldownUntil", com.google.gson.JsonNull.INSTANCE)
            }
        } else {
            json.get("declaration")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
                check(it.has("declarationCooldownUntil")) { "Incomplete war declaration cooldown identity" }
            }
            json.get("war")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
                check(it.has("farmingCooldownGuildId") && it.has("farmingCooldownUntil")) {
                    "Incomplete war farming cooldown identity"
                }
            }
        }

        val raw = gson.fromJson(json, DurableWarRecord::class.java)
        // Gson can bypass Kotlin constructors. Re-enter constructors and enforce identity invariants.
        val checked = raw.copy(
            declaration = raw.declaration?.let { it.copy(objectives = it.objectives.map { objective -> objective.copy() }.toSet()) },
            war = raw.war?.let { it.copy(objectives = it.objectives.map { objective -> objective.copy() }.toSet()) },
            stats = raw.stats?.copy(), wager = raw.wager?.copy(), paymentAttempts = raw.paymentAttempts.toMap())
        check(gson.toJsonTree(checked) == json) { "Incomplete or noncanonical war record" }
        checked
    } catch (error: Exception) {
        throw IllegalStateException("Cannot decode persisted war record", error)
    }

    private class TextAdapter<T>(private val parse: (String) -> T) : TypeAdapter<T>() {
        override fun write(out: JsonWriter, value: T) { out.value(value.toString()) }
        override fun read(input: JsonReader): T = parse(input.nextString())
    }
}
