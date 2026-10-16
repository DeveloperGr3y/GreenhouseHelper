package io.github.developergr3y.greenhousehelper.render

import com.mojang.blaze3d.vertex.PoseStack
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.config.HidePlants
import io.github.developergr3y.greenhousehelper.config.Layer
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouse
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.greenhouse.PlantHider
import io.github.developergr3y.greenhousehelper.greenhouse.PlantState
import io.github.developergr3y.greenhousehelper.greenhouse.Scan
import io.github.developergr3y.greenhousehelper.greenhouse.Scanner
import io.github.developergr3y.greenhousehelper.layout.Kinds
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

private typealias Quad = (Vec3, Vec3, Vec3, Vec3, Int) -> Unit

/**
 * Draws the layout of the greenhouse you're standing in, in two layers like a schematic:
 *  1. Floor: a tile on each cell in the colour of the ground block it needs (sand, farmland, soul sand…), and the
 *     target mutation's colour on the cells it should appear on.
 *  2. Crops: a box over each plant, its full size (2×2 plants are one box), in the plant's colour.
 * Once the greenhouse has been looked at ([Scanner]) only what's left is drawn: wrong plants in red, wrong ground
 * outlined in red, and posts on mutation spots (green: ring complete, gold: ready to harvest).
 * Both are drawn in the world, so the game's depth test hides them behind walls. Plant codes (e.g. "MJLB") float
 * above the crop boxes on the HUD.
 */
object LayoutRender {
    private const val WRONG = 0xE53935
    private const val RING_DONE = 0x55FF55
    private const val READY = 0xFFC107
    private const val LIFT = 0.01
    private const val CROP_HEIGHT = 0.9

    /** How tall the solid stand-ins for hidden plants are. */
    private const val STAND_IN_HEIGHT = 0.5

    private val overlay get() = GreenhouseHelper.config.overlay
    private val labels get() = GreenhouseHelper.config.labels
    private val progress get() = GreenhouseHelper.config.progress

    fun renderWorld(poseStack: PoseStack, collector: SubmitNodeCollector) {
        if (!overlay.enabled) return
        val greenhouse = Greenhouses.current() ?: return
        val layout = greenhouse.layout ?: return
        val scan = Scanner.of(greenhouse)
        val held = if (progress.highlightHeld) Scanner.heldKind(layout) else null
        val camera = Compat.camera.position()
        val floorAlpha = alpha(overlay.floorOpacity)
        val cropAlpha = alpha(overlay.cropOpacity)
        val layer = overlay.layer

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, buffer ->
            fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, argb: Int) {
                for (p in arrayOf(a, b, c, d)) {
                    buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat()).setColor(argb)
                }
            }
            if (layer != Layer.CROPS) floor(greenhouse, layout, scan, floorAlpha, ::quad)
            if (layer != Layer.FLOOR) crops(greenhouse, layout, scan, held, cropAlpha, ::quad)
            if (scan != null) posts(greenhouse, layout, scan, ::quad)
        }
    }

    private fun floor(greenhouse: Greenhouse, layout: Layout, scan: Scan?, alpha: Int, quad: Quad) {
        val hide = progress.hideDone && scan != null
        for (cy in 0 until layout.size) for (cx in 0 until layout.size) {
            val pos = greenhouse.ground(cx, cy, layout.size)
            val x0 = pos.x.toDouble()
            val z0 = pos.z.toDouble()
            val top = pos.y + 1 + LIFT
            val spot = layout.spotAt(cx, cy)
            val bad = scan?.badGround(cx, cy) == true
            val rgb = if (spot != null) {
                if (hide && scan.spot(spot)?.grown == true) continue
                spot.colour
            } else {
                val need = layout.groundAt(cx, cy) ?: continue
                if (hide && !bad) continue
                Kinds.groundColour(need)
            }
            quad(Vec3(x0, top, z0), Vec3(x0, top, z0 + 1), Vec3(x0 + 1, top, z0 + 1), Vec3(x0 + 1, top, z0), (alpha shl 24) or rgb)
            if (bad && progress.markWrong) outline(x0, top + LIFT, z0, quad)
        }
    }

    /** A red frame round a cell whose ground is the wrong block. */
    private fun outline(x: Double, y: Double, z: Double, quad: Quad) {
        val w = 0.08
        val argb = (0xE0 shl 24) or WRONG
        quad(Vec3(x, y, z), Vec3(x, y, z + w), Vec3(x + 1, y, z + w), Vec3(x + 1, y, z), argb)
        quad(Vec3(x, y, z + 1 - w), Vec3(x, y, z + 1), Vec3(x + 1, y, z + 1), Vec3(x + 1, y, z + 1 - w), argb)
        quad(Vec3(x, y, z), Vec3(x, y, z + 1), Vec3(x + w, y, z + 1), Vec3(x + w, y, z), argb)
        quad(Vec3(x + 1 - w, y, z), Vec3(x + 1 - w, y, z + 1), Vec3(x + 1, y, z + 1), Vec3(x + 1, y, z), argb)
    }

    private fun crops(greenhouse: Greenhouse, layout: Layout, scan: Scan?, held: String?, alpha: Int, quad: Quad) {
        for (plant in layout.plants) {
            val state = scan?.state(plant)
            if (state == PlantState.DONE && PlantHider.active) {
                // The plant is hidden: stand in for it with a solid block in its colour.
                faces(box(greenhouse, layout, plant.x, plant.y, plant.width, plant.height, STAND_IN_HEIGHT), (0xB0 shl 24) or plant.colour, quad)
                continue
            }
            if (state == PlantState.DONE && progress.hideDone) continue
            val argb = when {
                state == PlantState.WRONG && progress.markWrong -> (maxOf(alpha, 0x99) shl 24) or WRONG
                held == plant.kind -> (minOf(alpha + 100, 0xE6) shl 24) or plant.colour
                held != null -> ((alpha / 3) shl 24) or plant.colour
                else -> (alpha shl 24) or plant.colour
            }
            faces(box(greenhouse, layout, plant.x, plant.y, plant.width, plant.height), argb, quad)
        }
    }

    /** Posts on mutation spots: gold when ready to harvest, green when the ring around an empty one is complete. */
    private fun posts(greenhouse: Greenhouse, layout: Layout, scan: Scan, quad: Quad) {
        val hidingAll = PlantHider.active && GreenhouseHelper.config.plants.hide == HidePlants.ALL
        for (state in scan.spots) {
            if (hidingAll && state.grown) {
                val pos = greenhouse.ground(state.spot.x, state.spot.y, layout.size)
                faces(AABB(pos.x + 0.04, pos.y + 1.0 + LIFT, pos.z + 0.04, pos.x + 0.96, pos.y + 1.0 + STAND_IN_HEIGHT, pos.z + 0.96), (0xB0 shl 24) or state.spot.colour, quad)
            }
            val rgb = when {
                progress.growth && state.ready -> READY
                progress.ringStatus && !state.grown && state.ringComplete -> RING_DONE
                else -> continue
            }
            val pos = greenhouse.ground(state.spot.x, state.spot.y, layout.size)
            val base = pos.y + 1.0
            faces(AABB(pos.x + 0.4, base, pos.z + 0.4, pos.x + 0.6, base + 2.2, pos.z + 0.6), (0xC0 shl 24) or rgb, quad)
        }
    }

    /** The world box over cells (x, y) to (x + w - 1, y + h - 1), whichever way the layout is turned. */
    private fun box(greenhouse: Greenhouse, layout: Layout, x: Int, y: Int, w: Int, h: Int, height: Double = CROP_HEIGHT): AABB {
        val a = greenhouse.ground(x, y, layout.size)
        val b = greenhouse.ground(x + w - 1, y + h - 1, layout.size)
        val inset = 0.04
        return AABB(
            minOf(a.x, b.x) + inset, a.y + 1.0 + LIFT, minOf(a.z, b.z) + inset,
            maxOf(a.x, b.x) + 1 - inset, a.y + 1.0 + height, maxOf(a.z, b.z) + 1 - inset,
        )
    }

    private fun faces(b: AABB, argb: Int, quad: Quad) {
        val (x0, y0, z0) = Triple(b.minX, b.minY, b.minZ)
        val (x1, y1, z1) = Triple(b.maxX, b.maxY, b.maxZ)
        quad(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), argb) // top
        quad(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x0, y1, z0), argb) // north
        quad(Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y0, z1), argb) // south
        quad(Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x0, y0, z1), argb) // west
        quad(Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), argb) // east
    }

    /** Plant codes above the crop boxes, wrong ground's block name, and spot details; nearest few metres only. */
    fun renderLabels(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        if (!overlay.enabled || !labels.enabled || Compat.screen != null) return
        val greenhouse = Greenhouses.current() ?: return
        val layout = greenhouse.layout ?: return
        val scan = Scanner.of(greenhouse)
        val mc = Minecraft.getInstance()
        val eye = mc.player?.getEyePosition(1f) ?: return
        val font = mc.font

        fun label(at: Vec3, text: String, rgb: Int) {
            if (at.distanceTo(eye) > labels.distance) return
            val (x, y) = Projection.toScreen(at) ?: return
            val pose = graphics.pose()
            pose.pushMatrix()
            pose.translate(x.toFloat(), y.toFloat())
            pose.scale(labels.size)
            graphics.text(font, text, -font.width(text) / 2, -4, (0xFF shl 24) or rgb, true)
            pose.popMatrix()
        }

        if (overlay.layer != Layer.FLOOR) for (plant in layout.plants) {
            val state = scan?.state(plant)
            if (state == PlantState.DONE && progress.hideDone && !PlantHider.active) continue
            val box = box(greenhouse, layout, plant.x, plant.y, plant.width, plant.height)
            val at = Vec3((box.minX + box.maxX) / 2, box.maxY + 0.35, (box.minZ + box.maxZ) / 2)
            if (state == PlantState.WRONG && progress.markWrong) label(at, "✕ ${plant.abbr}", WRONG) else label(at, plant.abbr, plant.colour)
        }

        if (scan == null) return
        if (overlay.layer != Layer.CROPS && progress.markWrong) for (cy in 0 until layout.size) for (cx in 0 until layout.size) {
            if (!scan.badGround(cx, cy)) continue
            val need = layout.groundAt(cx, cy) ?: continue
            // Under a plant's box the code is already there; put the block's name lower down.
            val pos = greenhouse.ground(cx, cy, layout.size)
            label(Vec3(pos.x + 0.5, pos.y + 1.15, pos.z + 0.5), Kinds.title(need), 0xFF8A80)
        }
        for (state in scan.spots) {
            val pos = greenhouse.ground(state.spot.x, state.spot.y, layout.size)
            val at = Vec3(pos.x + 0.5, pos.y + 1.6, pos.z + 0.5)
            when {
                state.grown && progress.growth -> when {
                    state.ready -> label(at.add(0.0, 1.0, 0.0), "Ready", READY)
                    state.stage > 0 -> label(at.add(0.0, 1.0, 0.0), if (state.spot.stages > 0) "Stage ${state.stage}/${state.spot.stages}" else "Stage ${state.stage}", 0xFFFFFF)
                }
                !state.grown && progress.ringStatus && state.ringTotal > 0 ->
                    label(at, "${state.ringDone}/${state.ringTotal}", if (state.ringComplete) RING_DONE else 0xAAAAAA)
            }
        }
    }

    private fun alpha(percent: Float) = (percent / 100f * 255).toInt().coerceIn(0, 255)
}
