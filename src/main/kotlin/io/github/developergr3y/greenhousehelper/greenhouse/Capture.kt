package io.github.developergr3y.greenhousehelper.greenhouse

import io.github.developergr3y.greenhousehelper.layout.Board
import io.github.developergr3y.greenhousehelper.layout.Catalog
import io.github.developergr3y.greenhousehelper.layout.Kinds
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.layout.SkyLayouts

/**
 * `/greenhouse capture <name>`: turns what's planted in a greenhouse into a layout.
 * Mutations that have grown inside their own ring are the result, not part of the layout, so they become its spots
 * (along with any empty cell that has the same ring around it).
 */
object Capture {
    fun build(cells: Map<Pair<Int, Int>, CellContents>, catalog: Catalog.Data, size: Int): Layout {
        val kinds = catalog.pieces.keys.filterNot(Kinds::isGround)
        val found = HashMap<Pair<Int, Int>, String>()
        for ((cell, contents) in cells) {
            val kind = contents.heads.firstNotNullOfOrNull { Kinds.identify(it, kinds) } ?: Kinds.kindOfCrop(contents.above.block)
            if (kind != null) found[cell] = kind
        }
        val everything = SkyLayouts.fromCells(Board(size, found), catalog, null)

        // A plant whose whole recipe is around it grew there: that's the mutation this layout is for.
        val grown = everything.plants.filter { plant ->
            val piece = catalog.piece(plant.kind)
            piece.ring && piece.recipe.isNotEmpty() && plant.width == 1 && plant.height == 1 &&
                everything.cellsAround(plant.x, plant.y).let { around -> piece.recipe.all { (k, n) -> (around[k] ?: 0) >= n } }
        }
        val target = grown.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }?.key?.let(catalog::piece)
        val plants = found.filterKeys { cell -> grown.none { it.kind == target?.kind && it.x == cell.first && it.y == cell.second } }
        val layout = SkyLayouts.fromCells(Board(size, plants), catalog, target)
        layout.interval = 7
        return layout
    }
}
