package io.github.developergr3y.greenhousehelper.greenhouse

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.util.Location
import io.github.developergr3y.greenhousehelper.util.stripFormatting
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
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
        all.removeAll { overlaps(it, greenhouse) }
        all += greenhouse
        save()
        cancelSetup()
        val where = greenhouse.plot.ifEmpty { "this greenhouse" }
        GreenhouseHelper.chat("§aSaved $where.§r Load a layout with §e/greenhouse load <skylayouts link>§r or §e/greenhouse use <name>§r.")
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
