package io.github.developergr3y.greenhousehelper.layout

import java.math.BigInteger
import java.util.zip.Inflater

/** A board straight from a link: what's in each cell, before it's grouped into plants. */
class Board(val size: Int, val cells: Map<Pair<Int, Int>, String>)

/** What a skylayouts.io short link (/l/<code>) holds. */
class ShortLink(val mutation: String?, val interval: Int, val plot: Int, val boards: Map<Int, Board>)

/**
 * skylayouts.io's ways of putting a board in a link, so layouts you've edited there (and shared) load too:
 *  - in the hash, `#b=` (or `#b1=`..`#b3=` for each plot): "3…" (packed), "2~…" (run-length) or "KIND=xyxy|…";
 *  - a short link, `/l/<code>`: the mutation, interval and plot, then optionally the boards.
 * Kinds are numbered by their place in [KINDS].
 */
object BoardCodes {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val MAX_LENGTH = 4096
    private const val MAX_SIZE = 32

    val KINDS = listOf(
        "ALL_IN_ALOE", "ASHWREATH", "BLASTBERRY", "BROWN_MUSHROOM", "CACTUS", "CARROT", "CHEESEBITE", "CHLORONITE",
        "CHOCOBERRY", "CHOCONUT", "CHORUS_FRUIT", "CINDERSHADE", "COALROOT", "COCOA_BEANS", "CREAMBLOOM", "DEAD_PLANT",
        "DEVOURER", "DO_NOT_EAT_SHROOM", "DUSKBLOOM", "DUSTGRAIN", "FERMENTO", "FIRE", "FLESHTRAP", "GLASSCORN",
        "GLOOMGOURD", "GODSEED", "LONELILY", "MAGIC_JELLYBEAN", "MELON", "MOONFLOWER", "NETHER_WART", "NOCTILUME",
        "PHANTOMLEAF", "PLANTBOY_ADVANCE", "POTATO", "PUFFERCLOUD", "PUMPKIN", "RED_MUSHROOM", "SCOURROOT", "SHADEVINE",
        "SHELLFRUIT", "SNOOZLING", "SOGGYBUD", "STARTLEVINE", "STOPLIGHT_PETAL", "SUGAR_CANE", "SUNFLOWER",
        "THORNSHADE", "THUNDERLING", "TIMESTALK", "TURTLELLINI", "VEILSHROOM", "WHEAT", "WILD_ROSE", "WITHERBLOOM",
        "ZOMBUD", "FARMLAND", "SAND", "SOUL_SAND", "END_STONE", "DIRT", "MYCELIUM",
    )

    /** Variants the site lists separately but that are the same plant. */
    val VARIANTS = mapOf("BLASTBERRY_PLAIN" to "BLASTBERRY", "SHELLFRUIT_NOTECH" to "SHELLFRUIT", "MUSHROOM" to "RED_MUSHROOM")

    private fun digit(c: Char) = ALPHABET.indexOf(c)
    private fun kind(name: String) = (VARIANTS[name] ?: name).takeIf { it in KINDS }

    fun decode(code: String, defaultSize: Int = 10): Board? {
        if (code.isEmpty() || code.length > MAX_LENGTH) return null
        return when {
            code[0] == '3' -> packed(code)
            code.startsWith("2~") -> runLength(code, defaultSize)
            else -> sparse(code, defaultSize)
        }
    }

    /** "3" + size + kind count + kinds, then every cell as one big number in base (kinds + 1). */
    private fun packed(code: String): Board? {
        if (code.length < 3) return null
        val size = digit(code[1])
        val n = digit(code[2])
        if (size !in 1..MAX_SIZE || n < 0 || code.length < 3 + n) return null
        val kinds = (0 until n).map { KINDS.getOrNull(digit(code[3 + it])) ?: return null }
        if (n == 0) return Board(size, emptyMap())
        var number = BigInteger.ZERO
        for (c in code.substring(3 + n)) {
            val d = digit(c)
            if (d < 0) return null
            number = number.shiftLeft(6).or(BigInteger.valueOf(d.toLong()))
        }
        val base = BigInteger.valueOf(n + 1L)
        val digits = ArrayList<Int>()
        while (number > BigInteger.ONE) {
            if (digits.size > size * size) return null
            val (q, r) = number.divideAndRemainder(base)
            digits += r.toInt() - 1
            number = q
        }
        digits.reverse()
        if (digits.size != size * size) return null
        val cells = HashMap<Pair<Int, Int>, String>()
        digits.forEachIndexed { i, d -> if (d >= 0) cells[i % size to i / size] = kinds[d] }
        return Board(size, cells)
    }

    /** "2~<size>~<kind,kind>~<cells>": one base-36 kind index per cell, "." + count skips empty cells. */
    private fun runLength(code: String, defaultSize: Int): Board? {
        val parts = code.split('~')
        val size = parts.getOrNull(1)?.toIntOrNull(36) ?: defaultSize
        if (size !in 1..MAX_SIZE) return null
        val kinds = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.split(',')?.map { kind(it) }.orEmpty()
        val body = parts.getOrNull(3).orEmpty()
        val cells = HashMap<Pair<Int, Int>, String>()
        var cell = 0
        var i = 0
        while (i < body.length && cell < size * size) {
            if (body[i] == '.') {
                cell += body.getOrNull(i + 1)?.digitToIntOrNull(36) ?: 0
                i += 2
                continue
            }
            val k = body[i].digitToIntOrNull(36)?.let { kinds.getOrNull(it) }
            if (k != null) cells[cell % size to cell / size] = k
            cell++
            i++
        }
        return Board(size, cells)
    }

    /** "KIND=xyxy…|KIND=…" with base-36 coordinates. */
    private fun sparse(code: String, size: Int): Board? {
        val cells = HashMap<Pair<Int, Int>, String>()
        for (part in code.split('|')) {
            val eq = part.indexOf('=')
            if (eq < 0) continue
            val k = kind(part.substring(0, eq)) ?: continue
            val xy = part.substring(eq + 1)
            var i = 0
            while (i + 1 < xy.length) {
                val x = xy[i].digitToIntOrNull(36) ?: -1
                val y = xy[i + 1].digitToIntOrNull(36) ?: -1
                if (x in 0 until size && y in 0 until size) cells[x to y] = k
                i += 2
            }
        }
        return if (cells.isEmpty()) null else Board(size, cells)
    }

    /** The "3…" code for a board, or null if it has a kind the site doesn't number. */
    fun encode(board: Board): String? {
        val size = board.size
        if (size !in 1..MAX_SIZE) return null
        val kinds = ArrayList<String>()
        val values = ArrayList<Int>()
        for (y in 0 until size) for (x in 0 until size) {
            val k = board.cells[x to y]
            if (k == null) {
                values += -1
                continue
            }
            var index = kinds.indexOf(k)
            if (index < 0) {
                index = kinds.size
                kinds += k
            }
            values += index
        }
        if (kinds.size > 63) return null
        val header = StringBuilder("3").append(ALPHABET[size]).append(ALPHABET[kinds.size])
        for (k in kinds) {
            val index = KINDS.indexOf(k)
            if (index !in 0..63) return null
            header.append(ALPHABET[index])
        }
        if (kinds.isEmpty()) return header.toString()
        val base = BigInteger.valueOf(kinds.size + 1L)
        var number = BigInteger.ONE
        for (v in values) number = number.multiply(base).add(BigInteger.valueOf(v + 1L))
        val digits = StringBuilder()
        val mask = BigInteger.valueOf(63)
        while (number.signum() > 0) {
            digits.append(ALPHABET[number.and(mask).toInt()])
            number = number.shiftRight(6)
        }
        return header.append(digits.reverse()).toString()
    }

    /**
     * A short link's code: "1" + mutation (0 = none, else its place in [KINDS] + 1) + interval + flags ("2"/"3" =
     * plot), then optionally "~p" + boards ("~"-separated, "1"/"2" = same as that plot) or "~z" + a deflated grid.
     */
    fun decodeShort(code: String): ShortLink? {
        if (code.isEmpty() || code.length > MAX_LENGTH) return null
        val tilde = code.indexOf('~')
        val head = if (tilde < 0) code else code.substring(0, tilde)
        val tail = if (tilde < 0) "" else code.substring(tilde + 1)
        if (head.length < 3 || head[0] != '1') return null
        val m = digit(head[1])
        val interval = digit(head[2])
        if (m < 0 || interval < 0) return null
        var mutation = if (m == 0) null else KINDS.getOrNull(m - 1) ?: return null
        var plot = 1
        for (flag in head.substring(3)) when (flag) {
            '2', '3' -> plot = flag.digitToInt()
            's', 'c', 'x', 'n' -> {}
            'B' -> mutation = "BLASTBERRY"
            'S' -> mutation = "SHELLFRUIT"
            else -> return null
        }
        val boards = HashMap<Int, Board>()
        when {
            tail.isEmpty() -> {}
            tail[0] == 'p' -> tail.substring(1).split('~').forEachIndexed { i, part ->
                val p = i + 1
                if (p > 3 || part.isEmpty()) return@forEachIndexed
                if (part == "1" || part == "2") boards[part.toInt()]?.let { boards[p] = it }
                else if (part[0] == '3') packed(part)?.takeIf { it.cells.isNotEmpty() }?.let { boards[p] = it }
            }
            tail[0] == 'z' -> boards += deflated(tail) ?: return null
            else -> return null
        }
        return ShortLink(mutation, interval, plot, boards)
    }

    /** "z" + size + which plots (bits) + kind count + kinds + base64 of a raw-deflated byte per cell (0 = empty). */
    private fun deflated(tail: String): Map<Int, Board>? {
        if (tail.length < 4) return null
        val size = digit(tail[1])
        val mask = digit(tail[2])
        val n = digit(tail[3])
        if (size !in 1..MAX_SIZE || mask !in 1..7 || n < 0 || tail.length < 4 + n) return null
        val kinds = (0 until n).map { KINDS.getOrNull(digit(tail[4 + it])) ?: return null }
        val plots = (1..3).filter { mask and (1 shl (it - 1)) != 0 }
        val total = plots.size * size * size
        if (total > 3 * 1024) return null

        val packed = ArrayList<Byte>()
        var bits = 0
        var count = 0
        for (c in tail.substring(4 + n)) {
            val d = digit(c)
            if (d < 0) return null
            bits = (bits shl 6) or d
            count += 6
            if (count >= 8) {
                count -= 8
                packed += (bits shr count and 0xFF).toByte()
                bits = bits and ((1 shl count) - 1)
            }
        }
        val out = ByteArray(total)
        val inflater = Inflater(true)
        var read = 0
        try {
            inflater.setInput(packed.toByteArray())
            while (read < total && !inflater.finished()) {
                val r = inflater.inflate(out, read, total - read)
                if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                read += r
            }
        } catch (_: Exception) {
            return null
        } finally {
            inflater.end()
        }
        if (read != total) return null
        return plots.withIndex().associate { (i, p) ->
            val cells = HashMap<Pair<Int, Int>, String>()
            for (c in 0 until size * size) {
                val v = out[i * size * size + c].toInt() and 0xFF
                kinds.getOrNull(v - 1)?.let { cells[c % size to c / size] = it }
            }
            p to Board(size, cells)
        }
    }
}
