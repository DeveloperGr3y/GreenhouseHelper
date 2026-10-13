package io.github.developergr3y.greenhousehelper.hud

import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.Locale

private const val GLFW_KEY_R = 82

/** Drag a panel to move it, scroll over it to resize, R to reset them all. Saved as you go. */
class HudEditScreen : Screen(Component.literal("Move GreenhouseHelper displays")) {
    private var dragging: Panel? = null
    private var grabX = 0.0
    private var grabY = 0.0

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val hovered = panelAt(mouseX.toDouble(), mouseY.toDouble())
        for (panel in Panels.all) {
            // Show the real thing if there is one, otherwise an example so it can still be placed.
            val lines = if (panel.visible()) panel.lines() else panel.previewLines()
            panel.draw(graphics, lines)
            val pos = panel.position
            val w = (panel.width * pos.scale).toInt()
            val h = (panel.height * pos.scale).toInt()
            graphics.outline(pos.x - 1, pos.y - 1, w + 2, h + 2, if (panel == hovered || panel == dragging) -1 else 0xFF888888.toInt())
            if (panel == hovered) {
                val scale = String.format(Locale.ROOT, "%.1f", pos.scale)
                graphics.text(font, "${panel.label} (x$scale)", pos.x, pos.y + h + 3, 0xFFAAAAAA.toInt(), true)
            }
        }
        graphics.centeredText(font, "Drag to move · Scroll to resize · R to reset · Esc to save", width / 2, 10, -1)
    }

    private fun panelAt(x: Double, y: Double) = Panels.all.lastOrNull { it.contains(x, y) }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val panel = panelAt(event.x(), event.y())
        if (event.button() == 0 && panel != null) {
            dragging = panel
            grabX = event.x() - panel.position.x
            grabY = event.y() - panel.position.y
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val panel = dragging ?: return super.mouseDragged(event, dragX, dragY)
        val pos = panel.position
        val maxX = width - (panel.width * pos.scale).toInt()
        val maxY = height - (panel.height * pos.scale).toInt()
        pos.x = (event.x() - grabX).toInt().coerceIn(0, maxOf(0, maxX))
        pos.y = (event.y() - grabY).toInt().coerceIn(0, maxOf(0, maxY))
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null) GreenhouseHelper.saveConfig()
        dragging = null
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val pos = panelAt(mouseX, mouseY)?.position ?: return false
        pos.scale = (pos.scale + scrollY.toFloat() * 0.1f).coerceIn(0.5f, 3.0f)
        GreenhouseHelper.saveConfig()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW_KEY_R) {
            GreenhouseHelper.resetPanels()
            return true
        }
        return super.keyPressed(event)
    }

    override fun isPauseScreen() = false

    override fun removed() {
        GreenhouseHelper.saveConfig()
        super.removed()
    }
}
