package io.github.developergr3y.greenhousehelper.greenhouse

import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.layout.BoardCodes
import io.github.developergr3y.greenhousehelper.layout.Kinds
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.layout.Plant
import io.github.developergr3y.greenhousehelper.layout.Spot
import io.github.developergr3y.greenhousehelper.util.stripFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import kotlin.math.floor

enum class PlantState {
    /** The right plant is there. */
    DONE,

    /** Something else is planted there. */
    WRONG,

    /** Nothing planted yet. */
    EMPTY,

    /** Something's there the mod doesn't recognise. */
    UNKNOWN,
}

/** A mutation spot: whether it's grown (and how far), and how much of the ring around it is planted. */
class SpotState(val spot: Spot, val grown: Boolean, val stage: Int, val ringDone: Int, val ringTotal: Int, val decaysAt: Long) {
    val ready get() = grown && spot.stages > 0 && stage >= spot.stages
    val ringComplete get() = ringTotal > 0 && ringDone == ringTotal
}

/** What one cell holds: the plant heads and name tags standing in it, its ground block and the block above. */
class CellContents(val ground: BlockState, val above: BlockState) {
    val heads = ArrayList<String>()
    val names = ArrayList<String>()
}

/** The greenhouse you're in, compared with its layout. */
class Scan(val greenhouse: Greenhouse, val layout: Layout, val plants: Map<Plant, PlantState>, val spots: List<SpotState>, private val badGround: Set<Pair<Int, Int>>) {
    fun state(plant: Plant) = plants[plant] ?: PlantState.UNKNOWN
    fun spot(spot: Spot) = spots.firstOrNull { it.spot === spot }
    fun badGround(cx: Int, cy: Int) = (cx to cy) in badGround

    val planted get() = plants.values.count { it == PlantState.DONE }
    val wrong get() = plants.values.count { it == PlantState.WRONG }
    val badGroundCount get() = badGround.size
}

/**
 * Looks at the greenhouse you're standing in twice a second and works out what's planted where.
 *
 * Hypixel's greenhouse plants are invisible armour stands wearing a head named after the plant (e.g. "magicjellybean1",
 * "Plantboy4", "zombud0"), usually over a vanilla crop block. A grown mutation is the same, with name tags like
 * "Stage 3" above it. Ordinary crops (wheat, carrots…) are just their block.
 */
object Scanner {
    private const val EVERY_TICKS = 10
    private val stageTag = Regex("Stage (\\d+)")

    var scan: Scan? = null
        private set
    private var ticks = 0

    fun tick() {
        if (++ticks % EVERY_TICKS != 0) return
        val greenhouse = Greenhouses.current()
        val layout = greenhouse?.layout
        scan = if (greenhouse == null || layout == null) null else runCatching { scan(greenhouse, layout) }
            .onFailure { GreenhouseHelper.logger.warn("Greenhouse scan failed", it) }.getOrNull()
    }

    /** The latest scan, if it's of this greenhouse and layout. */
    fun of(greenhouse: Greenhouse): Scan? = scan?.takeIf { it.greenhouse === greenhouse && it.layout === greenhouse.layout }

    /** What's in each cell (by layout cell) of the greenhouse. */
    fun read(greenhouse: Greenhouse, size: Int): Map<Pair<Int, Int>, CellContents> {
        val level = Minecraft.getInstance().level ?: return emptyMap()
        val byBlock = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        val cells = HashMap<Pair<Int, Int>, CellContents>()
        for (cy in 0 until size) for (cx in 0 until size) {
            val ground = greenhouse.ground(cx, cy, size)
            byBlock[ground.x to ground.z] = cx to cy
            cells[cx to cy] = CellContents(level.getBlockState(ground), level.getBlockState(ground.above()))
        }
        val box = AABB(
            greenhouse.minX.toDouble(), greenhouse.y - 3.0, greenhouse.minZ.toDouble(),
            greenhouse.maxX + 1.0, greenhouse.y + 7.0, greenhouse.maxZ + 1.0,
        )
        for (stand in level.getEntitiesOfClass(ArmorStand::class.java, box)) {
            val head = stand.getItemBySlot(EquipmentSlot.HEAD).takeIf { !it.isEmpty }?.hoverName?.string?.takeIf { it.isNotBlank() }
            val name = stand.customName?.string?.stripFormatting()?.takeIf { it.isNotBlank() }
            if (head == null && name == null) continue
            // A stand counts for every cell it stands over (a 2×2 plant's stands sit across its cells).
            val b = stand.boundingBox
            for (x in floor(b.minX).toInt()..floor(b.maxX - 1e-6).toInt()) for (z in floor(b.minZ).toInt()..floor(b.maxZ - 1e-6).toInt()) {
                val cell = byBlock[x to z]?.let(cells::get) ?: continue
                head?.let { cell.heads += it }
                name?.let { cell.names += it }
            }
        }
        return cells
    }

    private fun scan(greenhouse: Greenhouse, layout: Layout): Scan {
        val cells = read(greenhouse, layout.size)
        val plants = layout.plants.associateWith { judge(it, cells) }

        val now = System.currentTimeMillis()
        var changed = false
        val spots = layout.spots.map { spot ->
            val cell = cells[spot.x to spot.y]
            val stage = cell?.names?.firstNotNullOfOrNull { stageTag.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            val grown = cell != null && (stage > 0 || cell.heads.any { Kinds.matches(spot.kindId(), it) })
            val ring = layout.around(spot.x, spot.y)
            val key = "${spot.x},${spot.y}"
            var decaysAt = 0L
            if (grown) {
                val since = greenhouse.grownSince[key] ?: now.also { greenhouse.grownSince[key] = it; changed = true }
                if (spot.decayDays > 0) decaysAt = since + spot.decayDays * 86_400_000L
            } else if (greenhouse.grownSince.remove(key) != null) {
                changed = true
            }
            SpotState(spot, grown, stage, ring.count { plants[it] == PlantState.DONE }, ring.size, decaysAt)
        }

        val bad = HashSet<Pair<Int, Int>>()
        for ((cell, contents) in cells) {
            val need = layout.groundAt(cell.first, cell.second)?.let { Kinds.GROUND_BLOCKS[it] } ?: continue
            if (contents.ground.block != need) bad += cell
        }

        val scan = Scan(greenhouse, layout, plants, spots, bad)
        val planted = scan.planted
        val grown = spots.count { it.grown }
        if (changed || greenhouse.planted != planted || greenhouse.plantTotal != plants.size || greenhouse.grown != grown || greenhouse.spotTotal != spots.size) {
            greenhouse.planted = planted
            greenhouse.plantTotal = plants.size
            greenhouse.grown = grown
            greenhouse.spotTotal = spots.size
            Greenhouses.save()
        }
        return scan
    }

    private fun judge(plant: Plant, cells: Map<Pair<Int, Int>, CellContents>): PlantState {
        var wrong = false
        var unknown = false
        for (cx in plant.x until plant.x + plant.width) for (cy in plant.y until plant.y + plant.height) {
            val cell = cells[cx to cy] ?: continue
            if (cell.heads.any { Kinds.matches(plant.kind, it) }) return PlantState.DONE
            if (cell.heads.isNotEmpty()) {
                if (cell.heads.any { Kinds.identify(it, BoardCodes.KINDS) != null }) wrong = true else unknown = true
                continue
            }
            val above = cell.above.block
            if (Kinds.cropMatches(plant.kind, above)) return PlantState.DONE
            if (cell.above.isAir) continue
            if (Kinds.isCropBlock(above)) wrong = true else unknown = true
        }
        return when {
            wrong -> PlantState.WRONG
            unknown -> PlantState.UNKNOWN
            else -> PlantState.EMPTY
        }
    }

    private var heldStack: ItemStack? = null
    private var heldLayout: Layout? = null
    private var heldKind: String? = null

    /** The layout's plant you're holding the seeds (or crop) of, if any. */
    fun heldKind(layout: Layout): String? {
        val stack = Minecraft.getInstance().player?.mainHandItem ?: return null
        if (stack !== heldStack || layout !== heldLayout) {
            heldStack = stack
            heldLayout = layout
            heldKind = if (stack.isEmpty) null else Kinds.identify(stack.hoverName.string, layout.plants.map { it.kind }.toSet())
        }
        return heldKind
    }
}
