package io.github.developergr3y.greenhousehelper.hud

import com.google.gson.annotations.Expose
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.util.Compat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

/** Where a panel sits (GUI pixels) and how big it is. Set in /greenhousehelper gui. */
class HudPosition(
    @Expose @JvmField var x: Int = 5,
    @Expose @JvmField var y: Int = 60,
    @Expose @JvmField var scale: Float = 1f,
)

/** One piece of a line. With [onClick] it's a button while your inventory is open. */
class Cell(val text: String, val onClick: (() -> Unit)? = null)

/**
 * One line of a panel.
 *  - [columns] = true: cells are table columns. The first is left-aligned, the rest are right-aligned, and every
 *    column lines up with the same column on the other lines (name · count · value).
 *  - [columns] = false: cells just follow each other (e.g. a title, or a row of buttons).
 * [tooltip] shows when you hover the line with your inventory open.
 * [paint] draws something other than text across the line (e.g. a progress bar): given x, y and the panel's inner width.
 */
class PanelLine(
    val cells: List<Cell>,
    val columns: Boolean = false,
    val tooltip: List<String>? = null,
    val paint: ((GuiGraphicsExtractor, Int, Int, Int) -> Unit)? = null,
) {
    constructor(text: String, onClick: (() -> Unit)? = null) : this(listOf(Cell(text, onClick)))

    companion object {
        fun row(vararg cells: String, tooltip: List<String>? = null) = PanelLine(cells.map { Cell(it) }, columns = true, tooltip = tooltip)

        fun painted(paint: (GuiGraphicsExtractor, Int, Int, Int) -> Unit) = PanelLine(emptyList(), paint = paint)
    }
}

/**
 * An on-screen panel: a small box of lines at a saved position and scale. Subclasses say what to show; this lays it
 * out (including aligned columns), draws it, and handles hovering and clicking its buttons while your inventory is open.
 */
abstract class Panel(val label: String) {
    abstract var position: HudPosition
    abstract fun defaultPosition(): HudPosition

    /** Should it show right now (on the HUD, and over your inventory)? */
    abstract fun visible(): Boolean
    abstract fun lines(): List<PanelLine>

    /** Example lines for the editor when there's nothing real to show. */
    abstract fun previewLines(): List<PanelLine>

    /** Size as last drawn, unscaled. */
    var width = 0
        private set
    var height = 0
        private set

    /** Where each cell was drawn (unscaled, relative to the panel), for clicks. */
    private class Placed(val line: Int, val x0: Int, val x1: Int, val cell: Cell)
    private var placed: List<Placed> = emptyList()
    private var drawn: List<PanelLine> = emptyList()

    fun draw(graphics: GuiGraphicsExtractor, lines: List<PanelLine>, mouseX: Double = -1.0, mouseY: Double = -1.0, interactive: Boolean = false) {
        if (lines.isEmpty()) return
        val font = Minecraft.getInstance().font
        drawn = lines

        // Column widths across every table line.
        val colWidths = mutableListOf<Int>()
        for (line in lines.filter { it.columns }) {
            line.cells.forEachIndexed { i, c ->
                val w = textWidth(c.text)
                if (i < colWidths.size) colWidths[i] = maxOf(colWidths[i], w) else colWidths += w
            }
        }
        val tableWidth = if (colWidths.isEmpty()) 0 else colWidths.sum() + GAP * (colWidths.size - 1)
        val flowWidth = lines.filter { !it.columns && it.cells.isNotEmpty() }.maxOfOrNull { l -> l.cells.sumOf { textWidth(it.text) } + GAP * (l.cells.size - 1) } ?: 0
        val inner = maxOf(tableWidth, flowWidth)
        width = inner + PADDING * 2
        height = lines.size * LINE_HEIGHT + PADDING * 2 - 1

        // Lay every cell out.
        val cells = mutableListOf<Placed>()
        lines.forEachIndexed { index, line ->
            if (line.columns) {
                var x = PADDING
                line.cells.forEachIndexed { i, c ->
                    val colWidth = colWidths.getOrElse(i) { 0 }
                    val w = textWidth(c.text)
                    // First column left, the rest right-aligned (the last one to the panel's right edge).
                    val right = if (i == line.cells.lastIndex) PADDING + inner else x + colWidth
                    val left = if (i == 0) x else right - w
                    cells += Placed(index, left, left + w, c)
                    x += colWidth + GAP
                }
            } else {
                var x = PADDING
                for (c in line.cells) {
                    val w = textWidth(c.text)
                    cells += Placed(index, x, x + w, c)
                    x += w + GAP
                }
            }
        }
        placed = cells

        val hovered = if (interactive) at(mouseX, mouseY) else null
        val hoveredLine = if (interactive) lineAt(mouseY, mouseX) else -1
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(position.x.toFloat(), position.y.toFloat())
        pose.scale(position.scale)
        if (GreenhouseHelper.config.shopping.background) graphics.fill(0, 0, width, height, 0x80000000.toInt())
        for (p in cells) {
            val y = PADDING + p.line * LINE_HEIGHT
            if (p === hovered && p.cell.onClick != null) graphics.fill(p.x0 - 1, y - 1, p.x1 + 1, y + LINE_HEIGHT - 1, 0x40FFFFFF)
            graphics.text(font, p.cell.text, p.x0, y, -1, true)
        }
        lines.forEachIndexed { index, line -> line.paint?.invoke(graphics, PADDING, PADDING + index * LINE_HEIGHT, inner) }
        pose.popMatrix()

        if (interactive) lines.getOrNull(hoveredLine)?.tooltip?.let { tip ->
            graphics.setComponentTooltipForNextFrame(font, tip.map { Component.literal(it) }, mouseX.toInt(), mouseY.toInt())
            // Container screens have already drawn their tooltips by now, so draw this one straight away.
            graphics.extractDeferredElements(mouseX.toInt(), mouseY.toInt(), 0f)
        }
    }

    fun contains(mouseX: Double, mouseY: Double) =
        mouseX >= position.x && mouseY >= position.y &&
            mouseX <= position.x + width * position.scale && mouseY <= position.y + height * position.scale

    private fun local(mouseX: Double, mouseY: Double) =
        (mouseX - position.x) / position.scale to (mouseY - position.y) / position.scale

    private fun lineAt(mouseY: Double, mouseX: Double): Int {
        if (!contains(mouseX, mouseY)) return -1
        val (_, ly) = local(mouseX, mouseY)
        val index = ((ly - PADDING) / LINE_HEIGHT).toInt()
        return if (ly >= PADDING && index in drawn.indices) index else -1
    }

    private fun at(mouseX: Double, mouseY: Double): Placed? {
        val line = lineAt(mouseY, mouseX)
        if (line < 0) return null
        val (lx, _) = local(mouseX, mouseY)
        return placed.firstOrNull { it.line == line && lx >= it.x0 - 1 && lx <= it.x1 + 1 }
    }

    /** Runs the clicked button. True if a button was clicked. */
    fun click(mouseX: Double, mouseY: Double): Boolean {
        val action = at(mouseX, mouseY)?.cell?.onClick ?: return false
        action()
        return true
    }

    companion object {
        /** Your inventory (or another container) is open, so panel buttons can be clicked. Buttons only show then. */
        val inInventory get() = Compat.screen is AbstractContainerScreen<*>

        /**
         * Width of a "§"-formatted string as drawn. The font's own measurement leaves out bold (each bold character is
         * drawn 1px wider), which made bold titles overlap what came after them.
         */
        fun textWidth(text: String): Int {
            var bold = false
            var extra = 0
            var i = 0
            while (i < text.length) {
                if (text[i] == '§' && i + 1 < text.length) {
                    val code = text[i + 1].lowercaseChar()
                    if (code == 'l') bold = true else if (code == 'r' || code in '0'..'9' || code in 'a'..'f') bold = false
                    i += 2
                    continue
                }
                if (bold) extra++
                i++
            }
            return Minecraft.getInstance().font.width(text) + extra
        }

        const val PADDING = 3
        const val LINE_HEIGHT = 10
        const val GAP = 6
    }
}
