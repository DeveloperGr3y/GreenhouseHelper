package io.github.developergr3y.greenhousehelper.hud

import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouse
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.layout.Kinds
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.layout.SavedLayouts
import io.github.developergr3y.greenhousehelper.layout.SkyLayouts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * `/greenhouse layouts`: the layouts you have, i.e. the ones on your greenhouses and the ones you've saved (nothing is
 * fetched from skylayouts.io here). Pick one to see it, then use it on the greenhouse you're in, save it under a name,
 * rename it, turn it, copy a share link, or delete it.
 */
class LayoutsScreen : Screen(Component.literal("Greenhouse layouts")) {
    /** One layout in the list: on a greenhouse, or saved under [savedName]. */
    private class Entry(val title: String, val subtitle: String, val layout: Layout, val greenhouse: Greenhouse?, val savedName: String?)

    private sealed interface Row
    private class Header(val text: String) : Row
    private class Item(val entry: Entry) : Row

    private val here = Greenhouses.current()
    private var entries = listOf<Entry>()
    private var selected: Entry? = null
    private var scroll = 0
    private var status = ""
    private var confirmDelete = false

    private lateinit var search: EditBox
    private lateinit var nameBox: EditBox
    private lateinit var useButton: Button
    private lateinit var saveButton: Button
    private lateinit var renameButton: Button
    private lateinit var rotateButton: Button
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button

    private val listX = 12
    private val listTop = 52
    private val listWidth get() = minOf(170, width * 2 / 5)
    private val rightX get() = listX + listWidth + 14
    private val previewTop = listTop
    private val cell get() = ((minOf(width - rightX - 12, height - 150)) / previewSize()).coerceIn(6, 14)
    private val buttonsTop get() = previewTop + previewSize() * cell + 10

    private fun previewSize() = selected?.layout?.size ?: 10

    override fun init() {
        reload()
        search = EditBox(font, listX, 30, listWidth, 16, Component.literal("Search"))
        search.setHint(Component.literal("Search…"))
        search.setResponder { scroll = 0 }
        addRenderableWidget(search)

        val y = buttonsTop
        nameBox = EditBox(font, rightX, y, 150, 18, Component.literal("Name"))
        nameBox.setMaxLength(40)
        nameBox.setHint(Component.literal("Name to save as"))
        addRenderableWidget(nameBox)
        useButton = button("Use", rightX, y + 24, 150) { use() }
        saveButton = button("Save as", rightX + 154, y, 64) { saveAs() }
        renameButton = button("Rename", rightX + 222, y, 64) { rename() }
        rotateButton = button("Rotate", rightX + 154, y + 24, 64) { rotate() }
        shareButton = button("Share link", rightX + 222, y + 24, 64) { share() }
        deleteButton = button("Delete", rightX, y + 48, 150) { delete() }
        select(entries.firstOrNull { it.title == selected?.title && it.savedName == selected?.savedName } ?: entries.firstOrNull())
    }

    private fun button(text: String, x: Int, y: Int, w: Int, action: () -> Unit): Button =
        addRenderableWidget(Button.builder(Component.literal(text)) { action() }.bounds(x, y, w, 20).build())

    private fun reload() {
        val list = mutableListOf<Entry>()
        for (g in Greenhouses.list()) {
            val layout = g.layout ?: continue
            list += Entry(g.label(), layout.shortName(), layout, g, null)
        }
        for (name in SavedLayouts.names()) {
            val layout = SavedLayouts.load(name) ?: continue
            list += Entry(name, describe(layout), layout, null, name)
        }
        entries = list
    }

    /** "All In Aloe · loaded from skylayouts.io": what it's for, and where it first came from. */
    private fun describe(layout: Layout): String {
        val origin = when {
            layout.source.contains("skylayouts.io") -> "loaded from skylayouts.io"
            layout.source.startsWith("captured") -> layout.source
            else -> "your own"
        }
        val target = layout.spots.firstOrNull()?.label
        return if (target != null) "$target · $origin" else origin
    }

    private fun rows(): List<Row> {
        val query = if (::search.isInitialized) search.value.trim().lowercase() else ""
        val shown = entries.filter { query.isEmpty() || it.title.lowercase().contains(query) || it.subtitle.lowercase().contains(query) }
        val rows = mutableListOf<Row>()
        val onGreenhouses = shown.filter { it.greenhouse != null }
        val saved = shown.filter { it.savedName != null }
        if (onGreenhouses.isNotEmpty()) {
            rows += Header("On your greenhouses")
            onGreenhouses.forEach { rows += Item(it) }
        }
        if (saved.isNotEmpty()) {
            rows += Header("Saved")
            saved.forEach { rows += Item(it) }
        }
        return rows
    }

    private fun rowHeight(row: Row) = if (row is Header) 14 else 24

    private fun select(entry: Entry?) {
        selected = entry
        confirmDelete = false
        if (::nameBox.isInitialized) nameBox.value = entry?.savedName ?: entry?.layout?.shortName().orEmpty().filter { it.isLetterOrDigit() || it in " _.-" }.take(40)
        refreshButtons()
    }

    private fun refreshButtons() {
        if (!::deleteButton.isInitialized) return
        val entry = selected
        val has = entry != null
        useButton.active = has && here != null && entry?.greenhouse !== here
        useButton.message = Component.literal(if (here == null) "Not in a greenhouse" else "Use on ${here.label()}")
        saveButton.active = has
        renameButton.active = entry?.savedName != null
        rotateButton.active = has
        shareButton.active = has
        deleteButton.active = has
        deleteButton.message = Component.literal(
            when {
                confirmDelete -> "§cClick again to confirm"
                entry?.greenhouse != null -> "Clear from greenhouse"
                else -> "Delete"
            },
        )
    }

    // --- actions ---

    private fun use() {
        val entry = selected ?: return
        val greenhouse = here ?: return
        greenhouse.layout = entry.layout.copy()
        Greenhouses.save()
        status = "§aNow using ${entry.title} on the ${greenhouse.label()}."
        refresh(greenhouse.label(), null)
    }

    private fun validName(): String? {
        val name = nameBox.value.trim()
        if (!SavedLayouts.isValidName(name)) {
            status = "§cNames can use letters, numbers, spaces and _ . - (up to 40)."
            return null
        }
        return name
    }

    private fun saveAs() {
        val entry = selected ?: return
        val name = validName() ?: return
        val existed = name in SavedLayouts.names()
        SavedLayouts.save(name, entry.layout)
        status = if (existed) "§aReplaced $name." else "§aSaved as $name."
        refresh(name, name)
    }

    private fun rename() {
        val entry = selected ?: return
        val old = entry.savedName ?: return
        val name = validName() ?: return
        if (name == old) return
        if (name in SavedLayouts.names()) {
            status = "§cThere's already a layout called $name."
            return
        }
        SavedLayouts.save(name, entry.layout)
        SavedLayouts.delete(old)
        status = "§aRenamed to $name."
        refresh(name, name)
    }

    private fun rotate() {
        val entry = selected ?: return
        val greenhouse = entry.greenhouse
        if (greenhouse != null) {
            greenhouse.rotation = (greenhouse.rotation + 1) and 3
            Greenhouses.save()
        } else {
            SavedLayouts.save(entry.savedName ?: return, entry.layout.rotated())
        }
        status = "§aTurned a quarter turn."
        refresh(entry.title, entry.savedName)
    }

    private fun share() {
        val entry = selected ?: return
        val link = SkyLayouts.shareLink(entry.layout)
        if (link == null) {
            status = "§cThis layout has a plant skylayouts.io can't put in a link."
            return
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(link)
        status = "§aCopied a skylayouts.io link to your clipboard."
    }

    private fun delete() {
        val entry = selected ?: return
        if (!confirmDelete) {
            confirmDelete = true
            refreshButtons()
            return
        }
        val greenhouse = entry.greenhouse
        if (greenhouse != null) {
            greenhouse.layout = null
            Greenhouses.save()
            status = "§aCleared the ${greenhouse.label()}'s layout."
        } else {
            SavedLayouts.delete(entry.savedName ?: return)
            status = "§aDeleted ${entry.title}."
        }
        refresh(null, null)
    }

    /** Re-reads the list and selects the entry with this title (or saved name). */
    private fun refresh(title: String?, savedName: String?) {
        reload()
        select(entries.firstOrNull { (savedName != null && it.savedName == savedName) || (savedName == null && it.title == title && it.greenhouse != null) } ?: entries.firstOrNull())
    }

    // --- drawing and input ---

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.text(font, "§lGreenhouse layouts", listX, 10, -1, true)
        graphics.text(font, "§7The ones on your greenhouses and the ones you've saved. Add more with /greenhouse load.", listX, 19, -1, false)

        // The list.
        val bottom = height - 12
        graphics.fill(listX - 2, listTop - 2, listX + listWidth + 2, bottom, 0x70000000)
        var y = listTop - scroll
        val rows = rows()
        if (rows.isEmpty()) graphics.text(font, "§7Nothing here yet.", listX + 4, listTop + 4, -1, false)
        for (row in rows) {
            val h = rowHeight(row)
            if (y + h > listTop - 2 && y < bottom) when (row) {
                is Header -> if (y >= listTop - 2) graphics.text(font, "§8§l${row.text.uppercase()}", listX + 4, y + 3, -1, false)
                is Item -> if (y >= listTop - 2 && y + h <= bottom) {
                    val hovered = mouseX in listX..(listX + listWidth) && mouseY in y until y + h
                    if (row.entry === selected) graphics.fill(listX, y, listX + listWidth, y + h - 2, 0x5040A060)
                    else if (hovered) graphics.fill(listX, y, listX + listWidth, y + h - 2, 0x30FFFFFF)
                    graphics.text(font, fit("§f${row.entry.title}", listWidth - 8), listX + 4, y + 3, -1, false)
                    graphics.text(font, fit("§7${row.entry.subtitle}", listWidth - 8), listX + 4, y + 13, -1, false)
                }
            }
            y += h
        }

        val entry = selected ?: return
        drawPreview(graphics, entry, mouseX, mouseY)
        drawDetails(graphics, entry)
        if (status.isNotEmpty()) graphics.text(font, status, rightX, buttonsTop + 74, -1, true)
    }

    /** Cuts text to fit, with "…". */
    private fun fit(text: String, width: Int): String {
        if (font.width(text) <= width) return text
        var cut = text
        while (cut.length > 2 && font.width("$cut…") > width) cut = cut.dropLast(1)
        return "$cut…"
    }

    /** The layout as the greenhouse will have it (turned like the greenhouse is). */
    private fun shown(entry: Entry): Layout {
        var layout = entry.layout
        repeat(entry.greenhouse?.rotation ?: 0) { layout = layout.rotated() }
        return layout
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor, entry: Entry, mouseX: Int, mouseY: Int) {
        val layout = shown(entry)
        val c = cell
        val x0 = rightX
        val y0 = previewTop
        val n = layout.size
        graphics.fill(x0 - 1, y0 - 1, x0 + n * c + 1, y0 + n * c + 1, 0xFF202020.toInt())
        for (cy in 0 until n) for (cx in 0 until n) {
            val ground = layout.groundAt(cx, cy)
            val rgb = if (ground != null) Kinds.groundColour(ground) else 0x303030
            graphics.fill(x0 + cx * c, y0 + cy * c, x0 + (cx + 1) * c - 1, y0 + (cy + 1) * c - 1, (0x70 shl 24) or rgb)
        }
        for (p in layout.plants) {
            graphics.fill(x0 + p.x * c + 1, y0 + p.y * c + 1, x0 + (p.x + p.width) * c - 2, y0 + (p.y + p.height) * c - 2, (0xFF shl 24) or p.colour)
        }
        for (s in layout.spots) {
            val sx = x0 + s.x * c
            val sy = y0 + s.y * c
            graphics.outline(sx, sy, c - 1, c - 1, (0xFF shl 24) or s.colour)
            graphics.fill(sx + c / 2 - 1, sy + c / 2 - 1, sx + c / 2 + 1, sy + c / 2 + 1, (0xFF shl 24) or s.colour)
        }
        val hx = (mouseX - x0).floorDiv(c)
        val hy = (mouseY - y0).floorDiv(c)
        if (mouseX >= x0 && mouseY >= y0 && hx in 0 until n && hy in 0 until n) {
            val lines = mutableListOf<Component>()
            layout.plantAt(hx, hy)?.let { lines += Component.literal(it.label + if (it.width > 1 || it.height > 1) " §7${it.width}×${it.height}" else "") }
            layout.spotAt(hx, hy)?.let { lines += Component.literal("§f${it.label} §7spot") }
            layout.groundAt(hx, hy)?.let { lines += Component.literal("§7On ${Kinds.title(it)}") }
            if (lines.isNotEmpty()) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY)
        }
    }

    private fun drawDetails(graphics: GuiGraphicsExtractor, entry: Entry) {
        val layout = entry.layout
        val x = rightX + layout.size * cell + 14
        val w = width - x - 8
        if (w < 110) return
        var y = previewTop
        fun line(text: String) {
            graphics.text(font, fit(text, w), x, y, -1, false)
            y += 10
        }
        line("§f§l${entry.title}")
        line("§7${layout.name}")
        if (entry.greenhouse != null && entry.greenhouse.rotation != 0) line("§7Turned ${entry.greenhouse.rotation * 90}°")
        y += 4
        if (layout.spots.isNotEmpty()) line("§7Target: §f${layout.spots.first().label} §7×${layout.spots.size} spots")
        line("§7Needs:")
        for ((plant, n) in layout.shoppingList().entries.sortedByDescending { it.value }) {
            val size = if (plant.width > 1 || plant.height > 1) " §8${plant.width}×${plant.height}" else ""
            line("  ${ShoppingPanel.colourCode(plant.colour)}${plant.label}$size §7×$n")
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != 0 || event.x() < listX || event.x() > listX + listWidth || event.y() < listTop) return false
        var y = listTop - scroll
        for (row in rows()) {
            val h = rowHeight(row)
            if (row is Item && event.y() >= y && event.y() < y + h) {
                select(row.entry)
                status = ""
                return true
            }
            y += h
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX < listX || mouseX > listX + listWidth) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val total = rows().sumOf { rowHeight(it) }
        val max = maxOf(0, total - (height - 12 - listTop))
        scroll = (scroll - (scrollY * 16).toInt()).coerceIn(0, max)
        return true
    }

    override fun isPauseScreen() = false
}
