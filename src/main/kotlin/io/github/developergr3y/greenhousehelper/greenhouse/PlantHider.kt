package io.github.developergr3y.greenhousehelper.greenhouse

import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.config.HidePlants
import io.github.developergr3y.greenhousehelper.layout.Kinds
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

/**
 * Hides the big plant models in the greenhouse you're in (they're armour stands wearing heads, e.g. Magic Jellybean's
 * tower of cubes), so you can see the layout and the ground. Only how they're drawn on your screen: they're still
 * there, and name tags ("Stage 3") stay. Worked out every few ticks; the renderer just checks the list.
 */
object PlantHider {
    private const val EVERY_TICKS = 5
    private var hidden: Set<Int> = emptySet()
    private var ticks = 0

    /** Called by the entity renderer for everything it's about to draw, so it has to be quick. */
    fun isHidden(entity: Entity) = hidden.isNotEmpty() && entity.id in hidden

    fun tick() {
        if (++ticks % EVERY_TICKS != 0) return
        val mode = GreenhouseHelper.config.plants.hide
        val greenhouse = if (mode == HidePlants.OFF) null else Greenhouses.current()
        val level = Minecraft.getInstance().level
        if (greenhouse == null || level == null) {
            hidden = emptySet()
            return
        }
        val keep = if (mode == HidePlants.KEEP_TARGET) greenhouse.layout?.target().orEmpty() else ""
        val box = AABB(
            greenhouse.minX.toDouble(), greenhouse.y - 3.0, greenhouse.minZ.toDouble(),
            greenhouse.maxX + 1.0, greenhouse.y + 12.0, greenhouse.maxZ + 1.0,
        )
        hidden = level.getEntitiesOfClass(ArmorStand::class.java, box).filter { stand ->
            val head = stand.getItemBySlot(EquipmentSlot.HEAD)
            // Only the plant models: a stand showing a name tag is text, not a plant.
            !head.isEmpty && !stand.isCustomNameVisible && (keep.isEmpty() || !Kinds.matches(keep, head.hoverName.string))
        }.mapTo(HashSet()) { it.id }
    }
}
