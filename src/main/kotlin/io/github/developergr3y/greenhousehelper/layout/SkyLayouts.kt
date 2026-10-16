package io.github.developergr3y.greenhousehelper.layout

import com.google.gson.JsonObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/**
 * Loads one layout from a skylayouts.io link:
 *  - `https://skylayouts.io/layout?m=ALL_IN_ALOE&i=7&p=1` (m = mutation, i = interval, p = plot 1-3): one of the site's
 *    own layouts, from its boards.json (keyed like "all_in_aloe_s7_plot1");
 *  - the same with `#b=…` on the end, or a short link `https://skylayouts.io/l/…`: a layout someone edited and shared,
 *    carried in the link itself (see [BoardCodes]).
 * Only the one layout you asked for is kept.
 */
object SkyLayouts {
    /** What a link points at. [boards] are carried in the link itself, by plot. */
    class Link(val mutation: String?, val interval: Int, val plot: Int, val boards: Map<Int, Board> = emptyMap())

    fun parse(url: String): Link? {
        var text = url.trim()
        if (!text.contains("://")) text = "https://$text"
        val uri = runCatching { URI.create(text) }.getOrNull() ?: return null
        if (uri.host?.lowercase()?.removePrefix("www.") != "skylayouts.io") return null

        val path = uri.rawPath.orEmpty()
        if (path.startsWith("/l/")) {
            val code = URLDecoder.decode(path.removePrefix("/l/"), Charsets.UTF_8)
            val short = BoardCodes.decodeShort(code) ?: return null
            return Link(short.mutation, short.interval, short.plot, short.boards)
        }

        val query = params(uri.rawQuery)
        val hash = params(uri.rawFragment)
        val m = query["m"]?.takeIf { it.matches(Regex("[A-Za-z_]{1,40}")) }?.uppercase()
        val mutation = m?.takeIf { it != "BLANK" }
        val interval = query["i"]?.toIntOrNull() ?: 7
        val plot = (query["p"]?.toIntOrNull() ?: 1).coerceIn(1, 3)
        val boards = HashMap<Int, Board>()
        hash["b"]?.let { BoardCodes.decode(it) }?.let { boards[plot] = it }
        for (p in 1..3) hash["b$p"]?.let { BoardCodes.decode(it) }?.let { boards[p] = it }
        if (mutation == null && boards.isEmpty()) return null
        return Link(mutation, interval, plot, boards)
    }

    private fun params(raw: String?): Map<String, String> = raw.orEmpty().split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) null else part.substring(0, eq) to URLDecoder.decode(part.substring(eq + 1), Charsets.UTF_8)
    }.toMap()

    fun load(link: Link, source: String): CompletableFuture<Layout> = Catalog.get().thenCompose { catalog ->
        val plot = if (link.plot in link.boards) link.plot else link.boards.keys.minOrNull()
        if (plot != null) {
            val target = link.mutation?.let { BoardCodes.VARIANTS[it] ?: it }
            val layout = fromCells(link.boards.getValue(plot), catalog, target?.let(catalog::piece))
            layout.name = "${target?.let { catalog.piece(it).label } ?: "Custom layout"} (edited, layout $plot of 3)"
            layout.source = source
            layout.interval = link.interval
            CompletableFuture.completedFuture(layout)
        } else {
            require(link.mutation != null) { "that link has no layout in it" }
            Catalog.boards(catalog.version).thenApply { fromBoards(link, source, catalog, it) }
        }
    }

    /** One of the site's own layouts. */
    private fun fromBoards(link: Link, source: String, catalog: Catalog.Data, boards: JsonObject): Layout {
        val mutation = link.mutation!!
        val prefix = mutation.lowercase() + "_s"
        val suffix = "_plot${link.plot}"
        // The intervals this mutation has a layout for; use yours, or the nearest one.
        val intervals = boards.keySet().filter { it.startsWith(prefix) && it.endsWith(suffix) }
            .mapNotNull { it.removePrefix(prefix).removeSuffix(suffix).toIntOrNull() }
        require(intervals.isNotEmpty()) { "skylayouts.io has no layout ${link.plot} for $mutation" }
        val interval = intervals.minBy { abs(it - link.interval) * 2 + (if (it > link.interval) 1 else 0) }
        val board = boards.getAsJsonObject("$prefix$interval$suffix")
        val target = catalog.piece(BoardCodes.VARIANTS[mutation] ?: mutation)

        val layout = Layout()
        // The site plans up to three greenhouse plots per mutation; "p" picks one. Nothing to do with Garden plot numbers.
        layout.name = "${target.label} (interval $interval, layout ${link.plot} of 3)"
        layout.source = source
        layout.mutation = target.kind
        layout.interval = interval
        layout.size = board.get("size")?.asInt ?: 10
        for (element in board.getAsJsonArray("tiles")) {
            val tile = element.asJsonObject
            val x = tile.get("x").asInt
            val y = tile.get("y").asInt
            val kind = tile.get("kind").asString
            if (Kinds.isGround(kind)) {
                layout.grounds += GroundTile().also { it.x = x; it.y = y; it.kind = kind }
                continue
            }
            // Cells of a bigger plant other than its top-left one point back to it ("ax", "ay"): skip them.
            if ((tile.get("ax")?.asInt ?: x) != x || (tile.get("ay")?.asInt ?: y) != y) continue
            layout.plants += catalog.piece(kind).plant(x, y)
        }
        for (element in board.getAsJsonArray("spots") ?: return layout) {
            val spot = element.asJsonObject
            layout.spots += target.spot(spot.get("x").asInt, spot.get("y").asInt)
        }
        return layout
    }

    /**
     * A layout from what's in each cell: ground blocks, then plants (cells of a 2×2 plant grouped back into one), then
     * the [target]'s spots: the empty cells with its whole recipe around them.
     */
    fun fromCells(board: Board, catalog: Catalog.Data, target: Piece?): Layout {
        val layout = Layout()
        layout.size = board.size
        layout.mutation = target?.kind.orEmpty()
        val plants = HashMap<Pair<Int, Int>, String>()
        for ((cell, kind) in board.cells) {
            if (Kinds.isGround(kind)) layout.grounds += GroundTile().also { it.x = cell.first; it.y = cell.second; it.kind = kind }
            else plants[cell] = kind
        }
        val used = HashSet<Pair<Int, Int>>()
        // Bigger plants first, top-left cells first, the way the site groups them.
        for ((cell, kind) in plants.entries.sortedWith(compareBy({ -catalog.piece(it.value).width * catalog.piece(it.value).height }, { "${it.key.first},${it.key.second}" }))) {
            if (cell in used) continue
            val piece = catalog.piece(kind)
            val cells = (0 until piece.width).flatMap { dx -> (0 until piece.height).map { dy -> cell.first + dx to cell.second + dy } }
            if (cells.all { plants[it] == kind && it !in used }) {
                used += cells
                layout.plants += piece.plant(cell.first, cell.second)
            }
        }
        // Leftover cells of a bigger plant that didn't fit: still show them.
        for ((cell, kind) in plants) if (cell !in used) layout.plants += catalog.piece(kind).plant(cell.first, cell.second).also { it.width = 1; it.height = 1 }
        if (target != null) addSpots(layout, target)
        return layout
    }

    /** Marks every empty cell with [target]'s whole recipe around it. */
    fun addSpots(layout: Layout, target: Piece) {
        if (!target.ring || target.recipe.isEmpty() || target.width != 1 || target.height != 1) return
        for (y in 0 until layout.size) for (x in 0 until layout.size) {
            if (layout.plantAt(x, y) != null || layout.spotAt(x, y) != null) continue
            val around = layout.cellsAround(x, y)
            if (target.recipe.all { (kind, n) -> (around[kind] ?: 0) >= n }) layout.spots += target.spot(x, y)
        }
    }

    /** A skylayouts.io link that opens [layout] on the site (and loads back into this mod), or null if it can't. */
    fun shareLink(layout: Layout): String? {
        val cells = HashMap<Pair<Int, Int>, String>()
        for (g in layout.grounds) cells[g.x to g.y] = g.kind
        for (p in layout.plants) for (dx in 0 until p.width) for (dy in 0 until p.height) cells[p.x + dx to p.y + dy] = p.kind
        val code = BoardCodes.encode(Board(layout.size, cells)) ?: return null
        val m = layout.target().ifEmpty { "blank" }
        return "${Catalog.SITE}/layout?m=${URLEncoder.encode(m, Charsets.UTF_8)}&i=${layout.interval}#b=$code"
    }
}
