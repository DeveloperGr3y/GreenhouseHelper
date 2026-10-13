package io.github.developergr3y.greenhousehelper.hud

import io.github.developergr3y.greenhousehelper.util.Compat
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

/**
 * Draws the panels. Normally on the HUD; while your inventory (or any container) is open they're drawn over it instead,
 * so their buttons (e.g. the profit panel's price switch) can be clicked.
 */
object Panels {
    val all: List<Panel> = listOf(ShoppingPanel)

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            ScreenEvents.afterExtract(screen).register { _, graphics, mouseX, mouseY, _ ->
                for (panel in all) if (panel.visible()) panel.draw(graphics, panel.lines(), mouseX.toDouble(), mouseY.toDouble(), interactive = true)
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                if (event.button() != 0) return@register true
                !all.any { it.visible() && it.click(event.x(), event.y()) } // false stops the click reaching the inventory
            }
        }
    }

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val screen = Compat.screen
        if (screen is AbstractContainerScreen<*> || screen is HudEditScreen) return
        for (panel in all) if (panel.visible()) panel.draw(graphics, panel.lines())
    }
}
