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
        addProperty("version", 1)
        add("record", gson.toJsonTree(record))
    })

    fun decode(payload: String): DurableWarRecord = try {
        val root = JsonParser.parseString(payload).asJsonObject
        check(root.get("version")?.asString == "1") { "Unsupported war record version" }
        val json = root.getAsJsonObject("record")
        check(json.keySet().containsAll(setOf("id", "revision", "fundingCycle", "declaration", "war", "stats", "wager",
            "paymentPhase", "settlementChosen", "settlementWinner", "paymentAttempts"))) { "Incomplete war record" }
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
