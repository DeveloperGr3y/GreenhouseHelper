package io.github.developergr3y.greenhousehelper.greenhouse

import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.config.HidePlants
import io.github.developergr3y.greenhousehelper.layout.Kinds
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

/**
 * Hides the plants in the greenhouse you're in, so you can see the layout and the ground: the head models
 * (armour stands, e.g. Magic Jellybean's tower of cubes) and the crop blocks under them (sugar cane, stems, wart).
 * Only how they're drawn on your screen: they're still there, you can still break them, and name tags ("Stage 3")
 * stay. While they're hidden, the overlay marks each planted cell instead.
 * Worked out every few ticks; the renderers just check the lists.
 */
object PlantHider {
    private const val EVERY_TICKS = 5

    /** How high above the ground crop blocks are hidden (sugar cane grows 3 tall). */
    private const val CROP_HEIGHT = 4

    private var hidden: Set<Int> = emptySet()

    /** Packed positions of hidden blocks. Read by chunk-building threads, so replaced, never changed. */
    @Volatile
    private var hiddenBlocks: Set<Long> = emptySet()
    private var ticks = 0

    /** Whether plants are being hidden right now (so the overlay should mark planted cells). */
    val active get() = hidden.isNotEmpty() || hiddenBlocks.isNotEmpty()

    /** Called by the entity renderer for everything it's about to draw, so it has to be quick. */
    fun isHidden(entity: Entity) = hidden.isNotEmpty() && entity.id in hidden

    /** The same, by coordinates (Sodium's chunk builder). */
    fun hidesBlock(x: Int, y: Int, z: Int): Boolean {
        val blocks = hiddenBlocks
        return blocks.isNotEmpty() && BlockPos.asLong(x, y, z) in blocks
    }

    /** Called while building chunk meshes, on other threads. */
    fun hidesBlock(pos: BlockPos): Boolean {
        val blocks = hiddenBlocks
        return blocks.isNotEmpty() && pos.asLong() in blocks
    }

    fun tick() {
        if (++ticks % EVERY_TICKS != 0) return
        val mode = GreenhouseHelper.config.plants.hide
        val greenhouse = if (mode == HidePlants.OFF) null else Greenhouses.current()
        val level = Minecraft.getInstance().level
        if (greenhouse == null || level == null) {
            hidden = emptySet()
            setBlocks(emptySet())
            return
        }
        val keep = if (mode == HidePlants.KEEP_TARGET) greenhouse.layout?.target().orEmpty() else ""
        val box = AABB(
            greenhouse.minX.toDouble(), greenhouse.y - 3.0, greenhouse.minZ.toDouble(),
            greenhouse.maxX + 1.0, greenhouse.y + 12.0, greenhouse.maxZ + 1.0,
        )
        val kept = HashSet<Long>()
        hidden = level.getEntitiesOfClass(ArmorStand::class.java, box).filter { stand ->
            val head = stand.getItemBySlot(EquipmentSlot.HEAD)
            // Only the plant models: a stand showing a name tag is text, not a plant.
            if (head.isEmpty || stand.isCustomNameVisible) return@filter false
            if (keep.isNotEmpty() && Kinds.matches(keep, head.hoverName.string)) {
                kept += BlockPos.containing(stand.x, greenhouse.y.toDouble(), stand.z).asLong()
                return@filter false
            }
            true
        }.mapTo(HashSet()) { it.id }

        // The crop blocks above each cell's ground, except under a mutation that's being kept.
        val blocks = HashSet<Long>()
        val pos = BlockPos.MutableBlockPos()
        for (x in greenhouse.minX..greenhouse.maxX) for (z in greenhouse.minZ..greenhouse.maxZ) {
            if (BlockPos.asLong(x, greenhouse.y, z) in kept) continue
            for (dy in 1..CROP_HEIGHT) {
                pos.set(x, greenhouse.y + dy, z)
                val state = level.getBlockState(pos)
                if (state.isAir || state.isCollisionShapeFullBlock(level, pos)) break
                blocks += pos.asLong()
            }
        }
        setBlocks(blocks)
    }

    /** Swaps in the new hidden blocks and redraws the chunks whose blocks changed. */
    private fun setBlocks(blocks: Set<Long>) {
        val old = hiddenBlocks
        if (old == blocks) return
        hiddenBlocks = blocks
        val changed = (old - blocks) + (blocks - old)
        if (changed.isEmpty()) return
        val xs = changed.map(BlockPos::getX)
        val ys = changed.map(BlockPos::getY)
        val zs = changed.map(BlockPos::getZ)
        // Chunk sections are 16 blocks each way: redraw every one the changes touch.
        Minecraft.getInstance().level?.setSectionRangeDirty(
            xs.min() shr 4, ys.min() shr 4, zs.min() shr 4, xs.max() shr 4, ys.max() shr 4, zs.max() shr 4,
        )
    }
}
