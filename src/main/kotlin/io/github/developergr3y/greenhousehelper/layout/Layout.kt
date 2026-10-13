package io.github.developergr3y.greenhousehelper.layout

import com.google.gson.annotations.Expose

/**
 * A greenhouse layout: what goes in each cell of the 10×10 grid.
 * Cell (0, 0) is the top-left corner you pick in setup; x runs towards the bottom-right corner's column, y towards its
 * row. Plants bigger than one cell (e.g. 2×2) are stored once, at their top-left cell.
 */
class Layout {
    /** e.g. "All In Aloe (interval 7, plot 1)", or the name you saved it under. */
    @Expose var name = ""

    /** Where it came from, e.g. a skylayouts.io link; empty for your own. */
    @Expose var source = ""

    @Expose var size = 10
    @Expose var plants: MutableList<Plant> = mutableListOf()

    /** Cells where the target mutation should appear. */
    @Expose var spots: MutableList<Spot> = mutableListOf()

    /** Plants to buy, by label, with how many (big plants count once). */
    fun shoppingList(): Map<Plant, Int> = plants.groupingBy { it.sameKind() }.eachCount()
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

    fun covers(cx: Int, cy: Int) = cx in x until x + width && cy in y until y + height

    /** A copy without its position, for counting the same kind together. */
    fun sameKind() = Plant().also {
        it.kind = kind; it.label = label; it.abbr = abbr; it.colour = colour; it.width = width; it.height = height
        it.needsWater = needsWater
    }

    override fun equals(other: Any?) = other is Plant && other.kind == kind && other.x == x && other.y == y
    override fun hashCode() = kind.hashCode() * 31 + x * 17 + y
}

class Spot {
    @Expose var x = 0
    @Expose var y = 0

    /** What appears here, e.g. "All In Aloe". */
    @Expose var label = ""
    @Expose var colour = 0xFFFFFF
}
