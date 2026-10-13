package io.github.developergr3y.greenhousehelper.hud

import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.greenhouse.PlantState
import io.github.developergr3y.greenhousehelper.greenhouse.Scan
import io.github.developergr3y.greenhousehelper.greenhouse.Scanner
import io.github.developergr3y.greenhousehelper.layout.Layout

/**
 * What the layout in the greenhouse you're standing in still needs:
 *
 *   Shopping List · All In Aloe
 *   Magic Jellybean     3 left
 *   Plantboy Advance 2×2    ✔
 *   Zombud              2 left
 *   Planted 87/92 · Rings 3/4
 *   All In Aloe: 2 grown, 1 ready
 *
 * Counts go yellow once some are planted and become a green tick when they're all in.
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

    override fun lines(): List<PanelLine> {
        val greenhouse = Greenhouses.current() ?: return emptyList()
        val layout = greenhouse.layout ?: return emptyList()
        return lines(layout, Scanner.of(greenhouse))
    }

    override fun previewLines() = listOf(
        PanelLine("§2§lShopping List §7All In Aloe"),
        PanelLine.row("  §dMagic Jellybean", "§e3 left"),
        PanelLine.row("  §aPlantboy Advance §82×2", "§a✔"),
        PanelLine.row("  §aZombud", "§f2 left"),
        PanelLine("§7Planted §f87§7/92 · Rings §f3§7/4"),
        PanelLine("§7All In Aloe: §f2§7 grown, §61 ready"),
    )

    private fun lines(layout: Layout, scan: Scan?): List<PanelLine> {
        val lines = mutableListOf(PanelLine("§2§lShopping List §7${layout.shortName()}"))
        val done = scan?.plants?.filterValues { it == PlantState.DONE }?.keys?.groupingBy { it.sameKind() }?.eachCount().orEmpty()
        for ((plant, total) in layout.shoppingList().entries.sortedByDescending { it.value }) {
            val size = if (plant.width > 1 || plant.height > 1) " §8${plant.width}×${plant.height}" else ""
            val name = "  " + colourCode(plant.colour) + plant.label + size
            val have = done[plant] ?: 0
            if (scan == null || !config.countDown) {
                lines += PanelLine.row(name, "§7×$total")
                continue
            }
            val left = total - have
            val count = when {
                left <= 0 -> "§a✔"
                have > 0 -> "§e$left left"
                else -> "§f$left left"
            }
            lines += PanelLine.row(name, count, tooltip = listOf("§f${plant.label}", "§7$have of $total planted"))
        }
        if (scan != null && config.progress) lines += progress(layout, scan)
        return lines
    }

    private fun progress(layout: Layout, scan: Scan): List<PanelLine> {
        val lines = mutableListOf<PanelLine>()
        val rings = scan.spots.filter { it.ringTotal > 0 }
        val ringText = if (rings.isEmpty()) "" else " §7· Rings §f${rings.count { it.ringComplete }}§7/${rings.size}"
        lines += PanelLine("§7Planted §f${scan.planted}§7/${scan.plants.size}$ringText")
        if (scan.spots.isNotEmpty()) {
            val grown = scan.spots.count { it.grown }
            val ready = scan.spots.count { it.ready }
            val name = scan.spots.first().spot.label.ifEmpty { layout.shortName() }
            lines += PanelLine("§7$name: §f$grown§7 grown" + if (ready > 0) ", §6$ready ready" else "")
            scan.spots.filter { it.decaysAt > 0 }.minOfOrNull { it.decaysAt }?.let { at ->
                val left = at - System.currentTimeMillis()
                lines += PanelLine(if (left <= 0) "§cOne may have decayed: harvest them!" else "§7First decays in about §e${duration(left)}")
            }
        }
        if (scan.wrong > 0) lines += PanelLine("§c${scan.wrong} plant${if (scan.wrong == 1) "" else "s"} in the wrong place")
        if (scan.badGroundCount > 0) lines += PanelLine("§c${scan.badGroundCount} cell${if (scan.badGroundCount == 1) " needs" else "s need"} different ground")
        return lines
    }

    private fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes >= 24 * 60 -> "${minutes / (24 * 60)}d ${minutes / 60 % 24}h"
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
    }

    /** The nearest of Minecraft's 16 chat colours to the plant's colour, for its name. */
    fun colourCode(rgb: Int): String {
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
