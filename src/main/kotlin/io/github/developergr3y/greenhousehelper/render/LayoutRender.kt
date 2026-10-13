package io.github.developergr3y.greenhousehelper.render

import com.mojang.blaze3d.vertex.PoseStack
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.config.Layer
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouse
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.util.Compat
import io.github.developergr3y.greenhousehelper.util.Projection
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Draws the layout of the greenhouse you're standing in, in two layers like a schematic:
 *  1. Floor: a tile on top of each cell's ground block. Blue where the plant needs watered ground, tan where it
 *     doesn't, and the target mutation's colour on the cells it should appear on.
 *  2. Crops: a box over each plant, its full size (2×2 plants are one box), in the plant's colour.
 * Both are drawn in the world, so the game's depth test hides them behind walls. Plant codes (e.g. "MJLB") float
 * above the crop boxes on the HUD.
 */
object LayoutRender {
    private const val WET = 0x4A90E2
    private const val DRY = 0xC9B48C
    private const val LIFT = 0.01
    private const val CROP_HEIGHT = 0.9

    private val config get() = GreenhouseHelper.config.general

    fun renderWorld(poseStack: PoseStack, collector: SubmitNodeCollector) {
        if (!config.enabled) return
        val greenhouse = Greenhouses.current() ?: return
        val layout = greenhouse.layout ?: return
        val camera = Compat.camera.position()
        val floorAlpha = alpha(config.floorOpacity)
        val cropAlpha = alpha(config.cropOpacity)
        val layer = config.layer

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, buffer ->
            fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, argb: Int) {
                for (p in arrayOf(a, b, c, d)) {
                    buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat()).setColor(argb)
                }
            }
            if (layer != Layer.CROPS) floor(greenhouse, layout, floorAlpha, ::quad)
            if (layer != Layer.FLOOR) crops(greenhouse, layout, cropAlpha, ::quad)
        }
    }

    private fun floor(greenhouse: Greenhouse, layout: Layout, alpha: Int, quad: (Vec3, Vec3, Vec3, Vec3, Int) -> Unit) {
        val spots = layout.spots.associateBy { it.x to it.y }
        for (cy in 0 until layout.size) for (cx in 0 until layout.size) {
            val spot = spots[cx to cy]
            val plant = layout.plants.firstOrNull { it.covers(cx, cy) }
            val rgb = when {
                spot != null -> spot.colour
                plant == null -> continue
                plant.needsWater -> WET
                else -> DRY
            }
            val pos = greenhouse.ground(cx, cy, layout.size)
            val top = pos.y + 1 + LIFT
            quad(
                Vec3(pos.x.toDouble(), top, pos.z.toDouble()), Vec3(pos.x.toDouble(), top, pos.z + 1.0),
                Vec3(pos.x + 1.0, top, pos.z + 1.0), Vec3(pos.x + 1.0, top, pos.z.toDouble()),
                (alpha shl 24) or rgb,
            )
        }
    }

    private fun crops(greenhouse: Greenhouse, layout: Layout, alpha: Int, quad: (Vec3, Vec3, Vec3, Vec3, Int) -> Unit) {
        for (plant in layout.plants) {
            val box = box(greenhouse, layout, plant.x, plant.y, plant.width, plant.height)
            faces(box, (alpha shl 24) or plant.colour, quad)
        }
    }

    /** The world box over cells (x, y) to (x + w - 1, y + h - 1), whichever way the layout is turned. */
    private fun box(greenhouse: Greenhouse, layout: Layout, x: Int, y: Int, w: Int, h: Int): AABB {
        val a = greenhouse.ground(x, y, layout.size)
        val b = greenhouse.ground(x + w - 1, y + h - 1, layout.size)
        val inset = 0.04
        return AABB(
            minOf(a.x, b.x) + inset, a.y + 1.0 + LIFT, minOf(a.z, b.z) + inset,
            maxOf(a.x, b.x) + 1 - inset, a.y + 1.0 + CROP_HEIGHT, maxOf(a.z, b.z) + 1 - inset,
        )
    }

    private fun faces(b: AABB, argb: Int, quad: (Vec3, Vec3, Vec3, Vec3, Int) -> Unit) {
        val (x0, y0, z0) = Triple(b.minX, b.minY, b.minZ)
        val (x1, y1, z1) = Triple(b.maxX, b.maxY, b.maxZ)
        quad(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), argb) // top
        quad(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x0, y1, z0), argb) // north
        quad(Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y0, z1), argb) // south
        quad(Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x0, y0, z1), argb) // west
        quad(Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), argb) // east
    }

    /** Plant codes above the crop boxes, nearest few metres only. */
    fun renderLabels(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        if (!config.enabled || !config.labels || config.layer == Layer.FLOOR || Compat.screen != null) return
        val greenhouse = Greenhouses.current() ?: return
        val layout = greenhouse.layout ?: return
        val mc = Minecraft.getInstance()
        val eye = mc.player?.getEyePosition(1f) ?: return
        val font = mc.font
        for (plant in layout.plants) {
            val box = box(greenhouse, layout, plant.x, plant.y, plant.width, plant.height)
            val at = Vec3((box.minX + box.maxX) / 2, box.maxY + 0.35, (box.minZ + box.maxZ) / 2)
            if (at.distanceTo(eye) > config.labelDistance) continue
            val (x, y) = Projection.toScreen(at) ?: continue
            val w = font.width(plant.abbr)
            graphics.text(font, plant.abbr, x - w / 2, y - 4, (0xFF shl 24) or plant.colour, true)
        }
    }

    private fun alpha(percent: Float) = (percent / 100f * 255).toInt().coerceIn(0, 255)
}
