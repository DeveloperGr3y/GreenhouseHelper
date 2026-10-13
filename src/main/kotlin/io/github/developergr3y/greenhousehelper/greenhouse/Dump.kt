package io.github.developergr3y.greenhousehelper.greenhouse

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `/greenhouse dump`: writes what's in each of the greenhouse's 100 cells to
 * config/greenhousehelper/dumps/<time>.json, to learn what each plant looks like in the world (they're Hypixel's own
 * plants, so it isn't obvious): the ground block and the few above it, any head's texture, and any entities standing
 * in the cell (armour stands, displays) with their names and items. Next to each cell is what the layout expects.
 */
object Dump {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private const val HEIGHT = 4

    fun write(greenhouse: Greenhouse): File {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: error("not in a world")
        val layout = greenhouse.layout
        val size = layout?.size ?: 10
        val root = JsonObject()
        root.addProperty("plot", greenhouse.plot)
        root.addProperty("layout", layout?.name ?: "")
        root.addProperty("rotation", greenhouse.rotation)
        val cells = JsonArray()
        for (cy in 0 until size) for (cx in 0 until size) {
            val ground = greenhouse.ground(cx, cy, size)
            val cell = JsonObject()
            cell.addProperty("cell", "$cx,$cy")
            cell.addProperty("ground", "${ground.x},${ground.y},${ground.z}")
            layout?.plants?.firstOrNull { it.covers(cx, cy) }?.let { cell.addProperty("expected", it.kind) }
            layout?.spots?.firstOrNull { it.x == cx && it.y == cy }?.let { cell.addProperty("spot", it.label) }

            val blocks = JsonArray()
            for (dy in 0 until HEIGHT) {
                val pos = ground.above(dy)
                val state = level.getBlockState(pos)
                val block = JsonObject()
                block.addProperty("dy", dy)
                block.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.block).toString())
                block.addProperty("state", state.toString())
                (level.getBlockEntity(pos) as? SkullBlockEntity)?.let { skull ->
                    block.addProperty("head", skull.ownerProfile?.toString() ?: "")
                }
                blocks.add(block)
            }
            cell.add("blocks", blocks)

            val box = AABB(ground.x.toDouble(), ground.y.toDouble(), ground.z.toDouble(), ground.x + 1.0, ground.y + HEIGHT + 1.0, ground.z + 1.0)
            val entities = JsonArray()
            for (entity in level.getEntities(null, box)) {
                if (entity == mc.player) continue
                val e = JsonObject()
                e.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
                e.addProperty("pos", "%.2f,%.2f,%.2f".format(entity.x, entity.y, entity.z))
                entity.customName?.let { e.addProperty("name", it.string) }
                if (entity is ArmorStand) e.addProperty("invisible", entity.isInvisible)
                if (entity is LivingEntity) {
                    val items = JsonObject()
                    for (slot in listOf(EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST)) {
                        val stack = entity.getItemBySlot(slot)
                        if (!stack.isEmpty) items.add(slot.name.lowercase(), item(stack))
                    }
                    if (items.size() > 0) e.add("items", items)
                }
                entities.add(e)
            }
            if (entities.size() > 0) cell.add("entities", entities)
            cells.add(cell)
        }
        root.add("cells", cells)

        val folder = File(GreenhouseHelper.configDir, "dumps").also { it.mkdirs() }
        val file = File(folder, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json")
        file.writeText(gson.toJson(root))
        return file
    }

    private fun item(stack: ItemStack) = JsonObject().also { o ->
        o.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.item).toString())
        o.addProperty("name", stack.hoverName.string)
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "")?.takeIf { it.isNotEmpty() }?.let { o.addProperty("skyblockId", it) }
        stack.get(DataComponents.PROFILE)?.let { o.addProperty("profile", it.toString()) }
        stack.get(DataComponents.ITEM_MODEL)?.let { o.addProperty("model", it.toString()) }
    }
}
