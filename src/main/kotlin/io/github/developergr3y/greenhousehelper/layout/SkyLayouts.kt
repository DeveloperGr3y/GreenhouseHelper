package io.github.developergr3y.greenhousehelper.layout

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/**
 * Loads one layout from skylayouts.io, from a link like
 * `https://skylayouts.io/layout.html?m=ALL_IN_ALOE&i=7&p=1` (m = mutation, i = interval, p = plot 1-3).
 *
 * The site keeps its layouts in public JSON files: /data/latest.json names the current data version, catalog.json
 * describes every plant (name, colour, size, whether it needs water) and boards.json holds the layouts, keyed like
 * "all_in_aloe_s7_plot1". Only the one layout you asked for is kept.
 */
object SkyLayouts {
    private const val SITE = "https://skylayouts.io"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** The mutation, interval and plot a link points at, or null if it isn't a skylayouts.io layout link. */
    data class Link(val mutation: String, val interval: Int, val plot: Int)

    fun parse(url: String): Link? {
        val uri = runCatching { URI.create(url.trim()) }.getOrNull() ?: return null
        if (uri.host?.lowercase()?.removePrefix("www.") != "skylayouts.io") return null
        val query = (uri.rawQuery ?: return null).split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) null else part.substring(0, eq) to URLDecoder.decode(part.substring(eq + 1), Charsets.UTF_8)
        }.toMap()
        val mutation = query["m"]?.takeIf { it.matches(Regex("[A-Za-z_]{1,40}")) } ?: return null
        val interval = query["i"]?.toIntOrNull() ?: 7
        val plot = (query["p"]?.toIntOrNull() ?: 1).coerceIn(1, 3)
        return Link(mutation.uppercase(), interval, plot)
    }

    fun load(link: Link, source: String): CompletableFuture<Layout> =
        getJson("$SITE/data/latest.json").thenCompose { latest ->
            val version = latest.get("version").asString
            require(version.matches(Regex("[A-Za-z0-9._-]{1,40}"))) { "unexpected data version" }
            getJson("$SITE/data/$version/catalog.json").thenCombine(getJson("$SITE/data/$version/boards.json")) { catalog, boards ->
                build(link, source, catalog, boards)
            }
        }

    private fun build(link: Link, source: String, catalog: JsonObject, boards: JsonObject): Layout {
        val prefix = link.mutation.lowercase() + "_s"
        val suffix = "_plot${link.plot}"
        // The intervals this mutation has a layout for; use yours, or the nearest one.
        val intervals = boards.keySet().filter { it.startsWith(prefix) && it.endsWith(suffix) }
            .mapNotNull { it.removePrefix(prefix).removeSuffix(suffix).toIntOrNull() }
        require(intervals.isNotEmpty()) { "skylayouts.io has no layout ${link.plot} for ${link.mutation}" }
        val interval = intervals.minBy { abs(it - link.interval) * 2 + (if (it > link.interval) 1 else 0) }
        val board = boards.getAsJsonObject("$prefix$interval$suffix")

        val pieces = catalog.getAsJsonObject("pieces")
        val specs = catalog.getAsJsonObject("specs")
        fun label(kind: String) = pieces.getAsJsonObject(kind)?.get("label")?.asString ?: kind

        val layout = Layout()
        // The site plans up to three greenhouse plots per mutation; "p" picks one. Nothing to do with Garden plot numbers.
        layout.name = "${label(link.mutation)} (interval $interval, layout ${link.plot} of 3)"
        layout.source = source
        layout.size = board.get("size")?.asInt ?: 10
        for (element in board.getAsJsonArray("tiles")) {
            val tile = element.asJsonObject
            val x = tile.get("x").asInt
            val y = tile.get("y").asInt
            // Cells of a bigger plant other than its top-left one point back to it ("ax", "ay"): skip them.
            if ((tile.get("ax")?.asInt ?: x) != x || (tile.get("ay")?.asInt ?: y) != y) continue
            val kind = tile.get("kind").asString
            val piece = pieces.getAsJsonObject(kind)
            val size = piece?.getAsJsonArray("size")
            layout.plants += Plant().also {
                it.x = x
                it.y = y
                it.width = size?.get(0)?.asInt ?: 1
                it.height = size?.get(1)?.asInt ?: 1
                it.kind = kind
                it.label = label(kind)
                it.abbr = piece?.get("abbr")?.asString ?: kind.take(4)
                it.colour = colour(piece?.get("color")?.asString)
                it.needsWater = specs.getAsJsonObject(kind)?.get("needs_water")?.asBoolean ?: false
            }
        }
        val target = pieces.getAsJsonObject(link.mutation)
        for (element in board.getAsJsonArray("spots") ?: return layout) {
            val spot = element.asJsonObject
            layout.spots += Spot().also {
                it.x = spot.get("x").asInt
                it.y = spot.get("y").asInt
                it.label = label(link.mutation)
                it.colour = colour(target?.get("color")?.asString)
            }
        }
        return layout
    }

    private fun colour(hex: String?) = hex?.removePrefix("#")?.toIntOrNull(16) ?: 0xFFFFFF

    private fun getJson(url: String): CompletableFuture<JsonObject> {
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
