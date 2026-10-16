package io.github.developergr3y.greenhousehelper.layout

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose

/**
 * A greenhouse layout: what goes in each cell of the 10×10 grid.
 * Cell (0, 0) is the top-left corner you pick in setup; x runs towards the bottom-right corner's column, y towards its
 * row. Plants bigger than one cell (e.g. 2×2) are stored once, at their top-left cell.
 */
class Layout {
    /** e.g. "All In Aloe (interval 7, layout 1 of 3)", or the name you saved it under. */
    @Expose var name = ""

    /** Where it came from, e.g. a skylayouts.io link; empty for your own. */
    @Expose var source = ""

    /** The mutation it's for, e.g. "ALL_IN_ALOE" (empty if none), and its interval: used for share links. */
    @Expose var mutation = ""
    @Expose var interval = 7

    @Expose var size = 10
    @Expose var plants: MutableList<Plant> = mutableListOf()

    /** Cells where the target mutation should appear. */
    @Expose var spots: MutableList<Spot> = mutableListOf()

    /** Cells with no plant that still need a certain ground block (e.g. sand left bare). */
    @Expose var grounds: MutableList<GroundTile> = mutableListOf()

    /** The name without its details: "All In Aloe (interval 7, layout 1 of 3)" -> "All In Aloe". */
    fun shortName() = name.substringBefore(" (")

    /** Plants to buy, by label, with how many (big plants count once). */
    fun shoppingList(): Map<Plant, Int> = plants.groupingBy { it.sameKind() }.eachCount()

    fun plantAt(cx: Int, cy: Int) = plants.firstOrNull { it.covers(cx, cy) }

    fun spotAt(cx: Int, cy: Int) = spots.firstOrNull { it.x == cx && it.y == cy }

    /** The ground block cell (cx, cy) needs, e.g. "SAND", or null if it doesn't matter. */
    fun groundAt(cx: Int, cy: Int): String? {
        grounds.firstOrNull { it.x == cx && it.y == cy }?.let { return it.kind }
        plantAt(cx, cy)?.let { return if (it.anyGround) null else Kinds.groundFor(it.kind) }
        spotAt(cx, cy)?.let { return Kinds.groundFor(it.kindId()) }
        return null
    }

    /** The plants touching cell (cx, cy) (the 8 around it), each once. */
    fun around(cx: Int, cy: Int): List<Plant> {
        val found = LinkedHashSet<Plant>()
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            plantAt(cx + dx, cy + dy)?.let { found += it }
        }
        return found.toList()
    }

    /**
     * How many of the 8 cells around (cx, cy) each kind covers. This is what a mutation's recipe counts: a 2×2 plant
     * touching it along a side counts twice.
     */
    fun cellsAround(cx: Int, cy: Int): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            plantAt(cx + dx, cy + dy)?.let { counts.merge(it.kind, 1, Int::plus) }
        }
        return counts
    }

    /** The mutation's id, from [mutation] or (for older saves) its spots. */
    fun target() = mutation.ifEmpty { spots.firstOrNull()?.kindId().orEmpty() }

    fun copy(): Layout = gson.fromJson(gson.toJson(this), Layout::class.java)

    /** A copy turned a quarter turn (the same way as /greenhouse rotate). */
    fun rotated(): Layout {
        val turned = copy()
        val n = size
        for (p in turned.plants) {
            val (x, y, w, h) = listOf(p.x, p.y, p.width, p.height)
            p.x = n - y - h
            p.y = x
            p.width = h
            p.height = w
        }
        for (s in turned.spots) s.x = (n - 1 - s.y).also { s.y = s.x }
        for (g in turned.grounds) g.x = (n - 1 - g.y).also { g.y = g.x }
        return turned
    }

    companion object {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create()!!
    }
}

class Plant {
    @Expose var x = 0
    @Expose var y = 0
    @Expose var width = 1
    @Expose var height = 1

    /** Id, e.g. "MAGIC_JELLYBEAN". */
    @Expose var kind = ""
    @Expose var label = ""

    /** Short code drawn above it, e.g. "MJLB". */
    @Expose var abbr = ""

    /** RGB, e.g. 0xB57CE8. */
    @Expose var colour = 0xFFFFFF

    /** Has to sit on watered ground. */
    @Expose var needsWater = false

    /** Not really a plant (e.g. fire): any ground will do. */
    @Expose var anyGround = false

    fun covers(cx: Int, cy: Int) = cx in x until x + width && cy in y until y + height

    /** A copy without its position, for counting the same kind together. */
    fun sameKind() = Plant().also {
        it.kind = kind; it.label = label; it.abbr = abbr; it.colour = colour; it.width = width; it.height = height
        it.needsWater = needsWater; it.anyGround = anyGround
    }

    override fun equals(other: Any?) = other is Plant && other.kind == kind && other.x == x && other.y == y
    override fun hashCode() = kind.hashCode() * 31 + x * 17 + y
}

class Spot {
    @Expose var x = 0
    @Expose var y = 0

    /** What appears here, e.g. "ALL_IN_ALOE" and "All In Aloe". */
    @Expose var kind = ""
    @Expose var label = ""
    @Expose var colour = 0xFFFFFF

    /** Growth stages until it's ready to harvest (0 if unknown). */
    @Expose var stages = 0

    /** Days before a grown one decays (0 if it doesn't). */
    @Expose var decayDays = 0

    /** Older saves only have the label: "All In Aloe" -> "ALL_IN_ALOE". */
    fun kindId() = kind.ifEmpty { label.uppercase().replace(' ', '_') }
}

/** A cell that needs a ground block but has no plant, e.g. "SAND". */
class GroundTile {
    @Expose var x = 0
    @Expose var y = 0
    @Expose var kind = ""
}
