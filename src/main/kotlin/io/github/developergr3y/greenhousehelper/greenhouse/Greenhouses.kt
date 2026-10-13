package io.github.developergr3y.greenhousehelper.greenhouse

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.util.Compat
import io.github.developergr3y.greenhousehelper.util.Location
import io.github.developergr3y.greenhousehelper.util.stripFormatting
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import io.github.developergr3y.greenhousehelper.layout.Kinds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One greenhouse's 10×10 crop grid, set up by picking its corners. [corner1] is cell (0, 0) (top-left), [corner2] is
 * cell (9, 9) (bottom-right); both are the ground blocks the plants sit on.
 */
class Greenhouse {
    @Expose var x1 = 0
    @Expose var z1 = 0
    @Expose var x2 = 0
    @Expose var z2 = 0

    /** Height of the ground blocks. */
    @Expose var y = 0

    /** The scoreboard's plot when it was set up, e.g. "Plot 24". */
    @Expose var plot = ""

    /** Quarter turns applied to the layout (0-3). */
    @Expose var rotation = 0
    @Expose var layout: Layout? = null

    /** Progress when last seen, for /greenhouse list: plants in place out of the layout's, and mutations grown. */
    @Expose var planted = -1
    @Expose var plantTotal = 0
    @Expose var grown = 0
    @Expose var spotTotal = 0

    /** When each grown mutation ("x,y") was first seen, for decay warnings. */
    @Expose var grownSince: MutableMap<String, Long> = mutableMapOf()

    /** e.g. "Plot 24 greenhouse", or where it is if the plot wasn't known. */
    fun label() = if (plot.isNotEmpty()) "$plot greenhouse" else "Greenhouse at ${minX}, $y, ${minZ}"

    val minX get() = min(x1, x2)
    val maxX get() = max(x1, x2)
    val minZ get() = min(z1, z2)
    val maxZ get() = max(z1, z2)

    /** Standing on (or just around) this grid. */
    fun contains(pos: Vec3, margin: Double = 2.0) =
        pos.x >= minX - margin && pos.x <= maxX + 1 + margin && pos.z >= minZ - margin && pos.z <= maxZ + 1 + margin &&
            pos.y >= y - 3 && pos.y <= y + 8

    /** The ground block under layout cell (cx, cy), after rotation. */
    fun ground(cx: Int, cy: Int, size: Int): BlockPos {
        val (rx, ry) = rotate(cx, cy, size)
        val dx = if (x2 >= x1) 1 else -1
        val dz = if (z2 >= z1) 1 else -1
        return BlockPos(x1 + rx * dx, y, z1 + ry * dz)
    }

    private fun rotate(cx: Int, cy: Int, size: Int): Pair<Int, Int> = when (rotation and 3) {
        1 -> (size - 1 - cy) to cx
        2 -> (size - 1 - cx) to (size - 1 - cy)
        3 -> cy to (size - 1 - cx)
        else -> cx to cy
    }
}

private class Profile {
    @Expose var greenhouses: MutableList<Greenhouse> = mutableListOf()
}

private class SavedData {
    @Expose var profiles: MutableMap<String, Profile> = mutableMapOf()
    @Expose var lastProfile = ""
}

/**
 * Your greenhouses, per SkyBlock profile (each profile has its own Garden), and the corner-picking setup.
 * Saved in config/greenhousehelper/greenhouses.json.
 */
object Greenhouses {
    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create()
    private val file get() = File(GreenhouseHelper.configDir, "greenhouses.json")
    private var data = SavedData()

    /** "Profile ID: 9edd67d3-..." is sent when you join SkyBlock. */
    private val profileLine = Regex("^Profile ID: ([0-9a-fA-F-]{8,40})")
    private val plotLine = Regex("Plot\\s*-?\\s*(\\d{1,2})")

    private var firstCorner: BlockPos? = null
    var settingUp = false
        private set

    fun register() {
        load()
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) return@register
            profileLine.find(message.string.stripFormatting())?.let {
                if (data.lastProfile != it.groupValues[1]) {
                    data.lastProfile = it.groupValues[1]
                    save()
                }
            }
        }
    }

    private val all: MutableList<Greenhouse>
        get() = data.profiles.getOrPut(data.lastProfile.ifEmpty { "default" }) { Profile() }.greenhouses

    fun list(): List<Greenhouse> = all

    /** The greenhouse you're standing in, if any (only on your Garden). */
    fun current(): Greenhouse? {
        val player = Minecraft.getInstance().player ?: return null
        if (!Location.onGarden) return null
        return all.firstOrNull { it.contains(player.position()) }
    }

    // --- setup ---

    fun startSetup() {
        settingUp = true
        firstCorner = null
        GreenhouseHelper.chat("Look at the §etop-left§r ground block of the 10×10 crop grid, then click §a[Set corner]§r below (or §e/greenhouse corner§r).")
        GreenhouseHelper.cornerButton()
    }

    fun cancelSetup() {
        settingUp = false
        firstCorner = null
    }

    /** The block you're looking at becomes the next corner. */
    fun pickCorner(target: BlockPos?) {
        if (!settingUp) {
            GreenhouseHelper.chat("Start with §e/greenhouse setup§r.")
            return
        }
        if (target == null) {
            GreenhouseHelper.chat("§cLook at a block first.")
            return
        }
        val first = firstCorner
        if (first == null) {
            firstCorner = target
            GreenhouseHelper.chat("Top-left set at §f${target.x}, ${target.y}, ${target.z}§r. Now the §ebottom-right§r ground block.")
            GreenhouseHelper.cornerButton()
            return
        }
        val dx = abs(target.x - first.x) + 1
        val dz = abs(target.z - first.z) + 1
        if (target.y != first.y || dx != 10 || dz != 10) {
            val why = if (target.y != first.y) "it's at a different height" else "that's $dx×$dz, not 10×10"
            GreenhouseHelper.chat("§cThat corner doesn't fit: $why. Pick the opposite corner of the grid.")
            GreenhouseHelper.cornerButton()
            return
        }
        val greenhouse = Greenhouse().also {
            it.x1 = first.x; it.z1 = first.z; it.x2 = target.x; it.z2 = target.z; it.y = first.y
            it.plot = scoreboardPlot().orEmpty()
        }
        add(greenhouse)
        cancelSetup()
        GreenhouseHelper.chat("§aSaved the ${greenhouse.label()}.§r ${nextStep(greenhouse)}")
    }

    /** Replaces any greenhouse it overlaps, keeping that one's layout. */
    private fun add(greenhouse: Greenhouse) {
        all.firstOrNull { overlaps(it, greenhouse) }?.let { old ->
            greenhouse.layout = old.layout
            greenhouse.rotation = old.rotation
        }
        all.removeAll { overlaps(it, greenhouse) }
        all += greenhouse
        save()
    }

    private fun nextStep(greenhouse: Greenhouse) =
        if (greenhouse.layout != null) "It still has its layout, §f${greenhouse.layout!!.shortName()}§r."
        else "Load a layout with §e/greenhouse load <skylayouts link>§r or §e/greenhouse use <name>§r."

    /**
     * Finds the 10×10 grid you're in (or next to) by itself: the patch of plantable ground (farmland, sand, soul sand…)
     * under or nearest you, if it's exactly 10×10. Its north-west corner becomes the top-left. False if it couldn't tell.
     */
    fun autoSetup(): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return false
        val player = mc.player ?: return false
        val grounds = Kinds.GROUND_BLOCKS.values.toSet()
        fun isGround(pos: BlockPos): Boolean {
            if (level.getBlockState(pos).block !in grounds) return false
            val above = pos.above()
            return !level.getBlockState(above).isCollisionShapeFullBlock(level, above)
        }
        val feet = player.blockPosition()
        val seed = sequence {
            yield(feet.below())
            yield(feet.below(2))
            for (r in 1..6) for (dy in 1..2) for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(abs(dx), abs(dz)) == r) yield(feet.offset(dx, -dy, dz))
            }
        }.firstOrNull(::isGround) ?: return false

        val seen = hashSetOf(seed)
        val queue = ArrayDeque(listOf(seed))
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            for (next in listOf(pos.north(), pos.south(), pos.east(), pos.west())) {
                if (next in seen || !isGround(next)) continue
                seen += next
                if (seen.size > 150) return false // spills out of the greenhouse
                queue += next
            }
        }
        val minX = seen.minOf { it.x }
        val maxX = seen.maxOf { it.x }
        val minZ = seen.minOf { it.z }
        val maxZ = seen.maxOf { it.z }
        if (maxX - minX + 1 != 10 || maxZ - minZ + 1 != 10 || seen.size < 90) return false

        val greenhouse = Greenhouse().also {
            it.x1 = minX; it.z1 = minZ; it.x2 = maxX; it.z2 = maxZ; it.y = seed.y
            it.plot = scoreboardPlot().orEmpty()
        }
        add(greenhouse)
        cancelSetup()
        val manual = Component.literal("§e[Pick the corners]").withStyle { it.withClickEvent(ClickEvent.RunCommand("/greenhouse setup manual")) }
        GreenhouseHelper.chat("§aFound the 10×10 grid and saved the ${greenhouse.label()}.§r ${nextStep(greenhouse)}")
        Compat.chat.addClientSystemMessage(
            Component.literal("§2[GreenhouseHelper] §7Top-left is its north-west corner (§e/greenhouse rotate§7 turns the layout). Wrong grid? ").append(manual),
        )
        return true
    }

    private fun overlaps(a: Greenhouse, b: Greenhouse) =
        a.minX <= b.maxX && b.minX <= a.maxX && a.minZ <= b.maxZ && b.minZ <= a.maxZ && abs(a.y - b.y) <= 2

    /** "Plot 24" from the scoreboard's location line, if it shows one. */
    private fun scoreboardPlot(): String? {
        val level = Minecraft.getInstance().level ?: return null
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR) ?: return null
        return scoreboard.listPlayerScores(objective).firstNotNullOfOrNull { entry ->
            val text = entry.display()?.string
                ?: net.minecraft.world.scores.PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).string
            plotLine.find(text.stripFormatting())?.let { "Plot ${it.groupValues[1]}" }
        }
    }

    fun remove(greenhouse: Greenhouse) {
        all.remove(greenhouse)
        save()
    }

    // --- storage ---

    fun save() {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(data))
        }.onFailure { GreenhouseHelper.logger.warn("Couldn't save ${file.path}", it) }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching { data = gson.fromJson(file.readText(), SavedData::class.java) ?: SavedData() }
            .onFailure { GreenhouseHelper.logger.warn("Couldn't read ${file.path}", it) }
    }
}
