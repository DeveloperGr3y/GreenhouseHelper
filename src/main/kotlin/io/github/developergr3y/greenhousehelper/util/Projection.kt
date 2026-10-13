package io.github.developergr3y.greenhousehelper.util

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.tan

/** Where a world point appears on screen, worked out from the camera (no per-version render code needed). */
object Projection {
    /** A point relative to the camera: [right], [up], and [depth] (distance in front; negative = behind). */
    class CameraPoint(val right: Double, val up: Double, val depth: Double)

    private const val NEAR = 0.1

    fun toCamera(world: Vec3): CameraPoint {
        val camera = Compat.camera
        val d = world.subtract(camera.position())
        val forward = camera.forwardVector()
        val up = camera.upVector()
        val left = camera.leftVector()
        return CameraPoint(
            right = -(d.x * left.x() + d.y * left.y() + d.z * left.z()),
            up = d.x * up.x() + d.y * up.y() + d.z * up.z(),
            depth = d.x * forward.x() + d.y * forward.y() + d.z * forward.z(),
        )
    }

    /** Screen position (GUI pixels, may be off screen) of a point in front of the camera; null if behind. */
    fun toScreen(p: CameraPoint): Pair<Double, Double>? {
        if (p.depth < NEAR) return null
        val mc = Minecraft.getInstance()
        val width = mc.window.guiScaledWidth
        val height = mc.window.guiScaledHeight
        val fov = Compat.camera.fov.takeIf { it > 1f } ?: mc.options.fov().get().toFloat()
        val scale = 1.0 / tan(Math.toRadians(fov / 2.0))
        val ndcX = p.right / p.depth * scale / (width.toDouble() / height)
        val ndcY = p.up / p.depth * scale
        return (ndcX + 1) / 2 * width to (1 - ndcY) / 2 * height
    }

    /** Screen position in GUI pixels, or null if the point is behind you or more than [margin] off screen. */
    fun toScreen(world: Vec3, margin: Double = 1.2): Pair<Int, Int>? {
        val (x, y) = toScreen(toCamera(world)) ?: return null
        val mc = Minecraft.getInstance()
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        val nx = x / w * 2 - 1
        val ny = 1 - y / h * 2
        if (nx !in -margin..margin || ny !in -margin..margin) return null
        return x.toInt() to y.toInt()
    }

    /** On screen right now (not just in front of you). */
    fun onScreen(world: Vec3) = toScreen(world, margin = 0.95) != null

    /** The on-screen part of the line between two world points (cut at the camera if one end is behind you). */
    fun segment(a: Vec3, b: Vec3): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
        var pa = toCamera(a)
        var pb = toCamera(b)
        if (pa.depth < NEAR && pb.depth < NEAR) return null
        if (pa.depth < NEAR) pa = clip(pb, pa)
        if (pb.depth < NEAR) pb = clip(pa, pb)
        val sa = toScreen(pa) ?: return null
        val sb = toScreen(pb) ?: return null
        return sa to sb
    }

    /** The point on the line from [inFront] to [behind] just in front of the camera. */
    private fun clip(inFront: CameraPoint, behind: CameraPoint): CameraPoint {
        val t = (inFront.depth - NEAR) / (inFront.depth - behind.depth)
        return CameraPoint(
            inFront.right + (behind.right - inFront.right) * t,
            inFront.up + (behind.up - inFront.up) * t,
            NEAR,
        )
    }
}
