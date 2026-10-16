package io.github.developergr3y.greenhousehelper.layout

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/** What each plant looks like and grows on in a greenhouse. */
object Kinds {
    /** Ground blocks a layout can ask for. */
    val GROUND_BLOCKS: Map<String, Block> = mapOf(
        "FARMLAND" to Blocks.FARMLAND,
        "SAND" to Blocks.SAND,
        "SOUL_SAND" to Blocks.SOUL_SAND,
        "END_STONE" to Blocks.END_STONE,
        "DIRT" to Blocks.DIRT,
        "MYCELIUM" to Blocks.MYCELIUM,
    )

    private val GROUND_COLOURS = mapOf(
        "FARMLAND" to 0x7A5233,
        "SAND" to 0xE3D08A,
        "SOUL_SAND" to 0x6B5440,
        "END_STONE" to 0xDDDBA0,
        "DIRT" to 0x86603E,
        "MYCELIUM" to 0x8A7C8C,
    )

    /** Plants that don't grow on farmland. */
    private val GROUND = mapOf(
        "ALL_IN_ALOE" to "SAND", "BLASTBERRY" to "SAND", "GLASSCORN" to "SAND", "MAGIC_JELLYBEAN" to "SAND",
        "CACTUS" to "SAND", "SUGAR_CANE" to "SAND",
        "ASHWREATH" to "SOUL_SAND", "CINDERSHADE" to "SOUL_SAND", "PHANTOMLEAF" to "SOUL_SAND",
        "WITHERBLOOM" to "SOUL_SAND", "ZOMBUD" to "SOUL_SAND", "NETHER_WART" to "SOUL_SAND",
        "CHORUS_FRUIT" to "END_STONE", "TIMESTALK" to "END_STONE",
        "BROWN_MUSHROOM" to "MYCELIUM", "RED_MUSHROOM" to "MYCELIUM", "VEILSHROOM" to "MYCELIUM",
    )

    /** Ordinary crops show as their block rather than a head. */
    private val CROP_BLOCKS: Map<String, Set<Block>> = mapOf(
        "WHEAT" to setOf(Blocks.WHEAT),
        "CARROT" to setOf(Blocks.CARROTS),
        "POTATO" to setOf(Blocks.POTATOES),
        "SUGAR_CANE" to setOf(Blocks.SUGAR_CANE),
        "CACTUS" to setOf(Blocks.CACTUS),
        "NETHER_WART" to setOf(Blocks.NETHER_WART),
        "MELON" to setOf(Blocks.MELON, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM),
        "PUMPKIN" to setOf(Blocks.PUMPKIN, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM),
        "RED_MUSHROOM" to setOf(Blocks.RED_MUSHROOM),
        "BROWN_MUSHROOM" to setOf(Blocks.BROWN_MUSHROOM),
        "CHORUS_FRUIT" to setOf(Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER),
        "COCOA_BEANS" to setOf(Blocks.COCOA),
    )
    private val ALL_CROP_BLOCKS = CROP_BLOCKS.values.flatten().toSet()

    fun isGround(kind: String) = kind in GROUND_BLOCKS
    fun groundFor(kind: String) = GROUND[kind] ?: "FARMLAND"
    fun groundColour(ground: String) = GROUND_COLOURS[ground] ?: 0xFFFFFF

    /** "SOUL_SAND" -> "Soul Sand". */
    fun title(id: String) = id.lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

    fun cropMatches(kind: String, block: Block) = CROP_BLOCKS[kind]?.contains(block) == true
    fun isCropBlock(block: Block) = block in ALL_CROP_BLOCKS
    fun kindOfCrop(block: Block) = CROP_BLOCKS.entries.firstOrNull { block in it.value }?.key

    /** "Magic Jellybean Seeds" -> "magicjellybeanseeds". */
    fun norm(text: String) = text.lowercase().filter { it in 'a'..'z' }

    /**
     * Hypixel's plants are heads named after the plant, e.g. "magicjellybean1" or "Plantboy4" (for Plantboy Advance),
     * and seeds are named like the plant: both match when one starts with the other.
     */
    fun matches(kind: String, name: String): Boolean {
        val n = norm(name)
        val k = norm(kind)
        return n.length >= 4 && k.isNotEmpty() && (k.startsWith(n) || n.startsWith(k))
    }

    /** The kind a head or item is, from [kinds]: the longest match wins (so "Wheat Seeds" isn't "WHEAT_..."). */
    fun identify(name: String, kinds: Collection<String>): String? =
        kinds.filter { matches(it, name) }.maxByOrNull { norm(it).length }
}
