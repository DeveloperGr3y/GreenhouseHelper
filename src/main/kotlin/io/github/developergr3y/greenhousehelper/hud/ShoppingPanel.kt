package io.github.developergr3y.greenhousehelper.hud

import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.layout.Layout

/**
 * What the layout in the greenhouse you're standing in needs:
 *
 *   Shopping List · All In Aloe
 *   Magic Jellybean     ×54
 *   Plantboy Advance     ×6
 *   Zombud               ×2
 *
 * For now these are the layout's full totals; once plants are recognised it'll count down what's still missing.
 */
object ShoppingPanel : Panel("Shopping List") {
    private val config get() = GreenhouseHelper.config.shopping

    override var position: HudPosition
        get() = config.position
        set(value) {
            config.position = value
        }

    override fun defaultPosition() = HudPosition(x = 5, y = 80)

    override fun visible() = config.enabled && Greenhouses.current()?.layout != null

    override fun lines(): List<PanelLine> = lines(Greenhouses.current()?.layout)

    override fun previewLines() = listOf(
        PanelLine("§2§lShopping List §7All In Aloe"),
        PanelLine.row("  §dMagic Jellybean", "§7×54"),
        PanelLine.row("  §aPlantboy Advance", "§7×6"),
        PanelLine.row("  §aZombud", "§7×2"),
    )

    private fun lines(layout: Layout?): List<PanelLine> {
        layout ?: return emptyList()
        val lines = mutableListOf(PanelLine("§2§lShopping List §7${layout.shortName()}"))
        for ((plant, count) in layout.shoppingList().entries.sortedByDescending { it.value }) {
            val size = if (plant.width > 1 || plant.height > 1) " §8${plant.width}×${plant.height}" else ""
            lines += PanelLine.row("  " + colourCode(plant.colour) + plant.label + size, "§7×$count")
        }
        return lines
    }

    /** The nearest of Minecraft's 16 chat colours to the plant's colour, for its name. */
    private fun colourCode(rgb: Int): String {
        val palette = mapOf(
            0x55FF55 to 'a', 0x55FFFF to 'b', 0xFF5555 to 'c', 0xFF55FF to 'd', 0xFFFF55 to 'e', 0xFFFFFF to 'f',
            0x00AA00 to '2', 0x00AAAA to '3', 0xAA00AA to '5', 0xFFAA00 to '6', 0xAAAAAA to '7', 0x5555FF to '9',
        )
        fun dist(a: Int, b: Int): Int {
            val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
            val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return dr * dr + dg * dg + db * db
        }
        return "§" + palette.minBy { dist(it.key, rgb) }.value
    }
}
