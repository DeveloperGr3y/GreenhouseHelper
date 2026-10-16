package io.github.developergr3y.greenhousehelper.layout

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** One plant (or mutation) as skylayouts.io describes it. */
class Piece(
    val kind: String,
    val label: String,
    val abbr: String,
    val colour: Int,
    val width: Int,
    val height: Int,
    val needsWater: Boolean,
    val nonPlant: Boolean,
    /** Growth stages until it's ready (0 if unknown). */
    val stages: Int,
    val decayDays: Int,
    /** What has to be around it for it to appear, e.g. {MAGIC_JELLYBEAN: 6, PLANTBOY_ADVANCE: 2}. */
    val recipe: Map<String, Int>,
    /** Appears in a cell surrounded by its [recipe]. */
    val ring: Boolean,
) {
    fun plant(x: Int, y: Int) = Plant().also {
        it.x = x; it.y = y; it.width = width; it.height = height
        it.kind = kind; it.label = label; it.abbr = abbr; it.colour = colour
        it.needsWater = needsWater; it.anyGround = nonPlant
    }

    fun spot(x: Int, y: Int) = Spot().also {
        it.x = x; it.y = y; it.kind = kind; it.label = label; it.colour = colour
        it.stages = stages; it.decayDays = decayDays
    }
}

/**
 * skylayouts.io's public data: /data/latest.json names the current version, /data/<version>/catalog.json describes every
 * plant and /data/<version>/boards.json holds its layouts. The catalogue is fetched once per game.
 */
object Catalog {
    const val SITE = "https://skylayouts.io"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    class Data(val version: String, val pieces: Map<String, Piece>) {
        /** The piece, or a plain stand-in for one the catalogue doesn't know. */
        fun piece(kind: String) = pieces[kind] ?: Piece(
            kind, Kinds.title(kind), kind.take(4), 0xFFFFFF, 1, 1, needsWater = false, nonPlant = false,
            stages = 0, decayDays = 0, recipe = emptyMap(), ring = false,
        )
    }

    private var cached: CompletableFuture<Data>? = null

    @Synchronized
    fun get(): CompletableFuture<Data> {
        cached?.takeIf { !it.isCompletedExceptionally }?.let { return it }
        return json("$SITE/data/latest.json").thenCompose { latest ->
            val version = latest.get("version").asString
            require(version.matches(Regex("[A-Za-z0-9._-]{1,40}"))) { "unexpected data version" }
            json("$SITE/data/$version/catalog.json").thenApply { Data(version, parse(it)) }
        }.also { cached = it }
    }

    fun boards(version: String) = json("$SITE/data/$version/boards.json")

    private fun parse(catalog: JsonObject): Map<String, Piece> {
        val pieces = catalog.getAsJsonObject("pieces")
        val specs = catalog.getAsJsonObject("specs") ?: JsonObject()
        return pieces.keySet().associateWith { kind ->
            val piece = pieces.getAsJsonObject(kind)
            val spec = specs.getAsJsonObject(kind)
            val size = piece.getAsJsonArray("size")
            fun int(o: JsonObject?, key: String) = o?.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            Piece(
                kind = kind,
                label = piece.get("label")?.asString ?: Kinds.title(kind),
                abbr = piece.get("abbr")?.asString ?: kind.take(4),
                colour = piece.get("color")?.asString?.removePrefix("#")?.toIntOrNull(16) ?: 0xFFFFFF,
                width = size?.get(0)?.asInt ?: 1,
                height = size?.get(1)?.asInt ?: 1,
                needsWater = spec?.get("needs_water")?.asBoolean ?: false,
                nonPlant = piece.get("non_plant")?.asBoolean ?: false,
                stages = int(spec, "maturity_stages"),
                decayDays = int(piece, "decay_days"),
                recipe = spec?.getAsJsonObject("recipe")?.entrySet()?.associate { it.key to it.value.asInt }.orEmpty(),
                ring = spec?.get("spawn_rule")?.asString == "ring",
            )
        }
    }

    private fun json(url: String): CompletableFuture<JsonObject> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "GreenhouseHelper/${GreenhouseHelper.version}")
            .timeout(Duration.ofSeconds(30))
            .build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { response ->
            check(response.statusCode() == 200) { "skylayouts.io said ${response.statusCode()}" }
            JsonParser.parseString(response.body()).asJsonObject
        }
    }
}
