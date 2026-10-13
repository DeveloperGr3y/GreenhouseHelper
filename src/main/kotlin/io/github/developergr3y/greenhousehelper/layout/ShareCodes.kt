package io.github.developergr3y.greenhousehelper.layout

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Share codes: a whole layout in one short line of text, e.g. `GH1:AQpH…`, small enough to paste in chat.
 * The code holds everything (each plant's name, code, colour and size, where it goes, and the mutation spots), so
 * it needs nothing else to load: no website, no server.
 *
 * After "GH1:" it's URL-safe base64 of: one byte saying whether the rest is deflated (whichever is shorter), then
 *   size, interval, mutation id (text, may be empty) and, if there is one: flags, [label], colour, stages, decay days;
 *   kinds of plant: count, then each one's id, flags, [label], short code, colour, width and height;
 *   every cell row by row, bit-packed: 0 = nothing starts here, n = the n-th kind has its top-left here;
 *   the spots as one bit per cell; then cells with their own ground block.
 * Text is a length byte then UTF-8. A label is left out when it's just the id in title case ("MAGIC_JELLYBEAN" ->
 * "Magic Jellybean").
 */
object ShareCodes {
    const val PREFIX = "GH1:"
    val PATTERN = Regex("GH1:[A-Za-z0-9_-]{8,3000}")

    private const val MAX_SIZE = 32
    private const val MAX_BYTES = 16 * 1024
    private const val HAS_LABEL = 4

    /** Ground blocks by number. Fixed here (not taken from elsewhere) so codes keep meaning the same thing. */
    private val GROUNDS = listOf("FARMLAND", "SAND", "SOUL_SAND", "END_STONE", "DIRT", "MYCELIUM")

    private fun title(id: String) = id.lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

    fun encode(layout: Layout): String {
        val out = Writer()
        out.byte(layout.size)
        out.byte(layout.interval.coerceIn(0, 255))
        val target = layout.target()
        out.text(target)
        if (target.isNotEmpty()) {
            val spot = layout.spots.firstOrNull()
            val label = spot?.label ?: title(target)
            out.byte(if (label != title(target)) HAS_LABEL else 0)
            if (label != title(target)) out.text(label)
            out.colour(spot?.colour ?: 0xFFFFFF)
            out.byte(spot?.stages?.coerceIn(0, 255) ?: 0)
            out.byte(spot?.decayDays?.coerceIn(0, 255) ?: 0)
        }

        val kinds = layout.plants.map { it.sameKind() }.distinct()
        out.byte(kinds.size)
        for (k in kinds) {
            val custom = k.label != title(k.kind)
            out.text(k.kind)
            out.byte((if (k.needsWater) 1 else 0) or (if (k.anyGround) 2 else 0) or (if (custom) HAS_LABEL else 0))
            if (custom) out.text(k.label)
            out.text(k.abbr)
            out.colour(k.colour)
            out.byte(((k.width - 1) shl 4) or (k.height - 1))
        }
        val bits = bitsFor(kinds.size)
        val anchors = layout.plants.associateBy { it.x to it.y }
        val grid = Bits()
        for (y in 0 until layout.size) for (x in 0 until layout.size) {
            grid.write(anchors[x to y]?.let { kinds.indexOf(it.sameKind()) + 1 } ?: 0, bits)
        }
        out.bytes(grid.bytes())

        val spots = Bits()
        val spotCells = layout.spots.map { it.x to it.y }.toSet()
        for (y in 0 until layout.size) for (x in 0 until layout.size) spots.write(if ((x to y) in spotCells) 1 else 0, 1)
        out.bytes(spots.bytes())

        val tiles = layout.grounds.filter { it.kind in GROUNDS }
        out.byte(tiles.size)
        for (g in tiles) {
            out.byte(g.x)
            out.byte(g.y)
            out.byte(GROUNDS.indexOf(g.kind))
        }

        val raw = out.bytes()
        val deflated = deflate(raw)
        val payload = if (deflated.size < raw.size) byteArrayOf(1) + deflated else byteArrayOf(0) + raw
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    /** The layout in a code, or null if it isn't a valid one. */
    fun decode(code: String): Layout? = runCatching { read(code.trim()) }.getOrNull()

    private fun read(code: String): Layout? {
        if (!code.startsWith(PREFIX) || code.length > 4000) return null
        val payload = Base64.getUrlDecoder().decode(code.removePrefix(PREFIX))
        if (payload.isEmpty()) return null
        val body = payload.copyOfRange(1, payload.size)
        val r = Reader(
            when (payload[0].toInt()) {
                0 -> body
                1 -> inflate(body) ?: return null
                else -> return null
            },
        )

        val layout = Layout()
        layout.size = r.byte().takeIf { it in 1..MAX_SIZE } ?: return null
        layout.interval = r.byte()
        layout.mutation = r.id() ?: return null
        var spotTemplate: Spot? = null
        if (layout.mutation.isNotEmpty()) {
            val flags = r.byte()
            spotTemplate = Spot().also {
                it.kind = layout.mutation
                it.label = if (flags and HAS_LABEL != 0) r.text() else title(layout.mutation)
                it.colour = r.colour()
                it.stages = r.byte()
                it.decayDays = r.byte()
            }
        }

        val kinds = (0 until r.byte()).map {
            Plant().also { p ->
                p.kind = r.id()?.takeIf { it.isNotEmpty() } ?: return null
                val flags = r.byte()
                p.needsWater = flags and 1 != 0
                p.anyGround = flags and 2 != 0
                p.label = if (flags and HAS_LABEL != 0) r.text() else title(p.kind)
                p.abbr = r.text()
                p.colour = r.colour()
                val wh = r.byte()
                p.width = (wh shr 4) + 1
                p.height = (wh and 15) + 1
            }
        }
        val n = layout.size
        val grid = r.bits((n * n * bitsFor(kinds.size) + 7) / 8)
        for (y in 0 until n) for (x in 0 until n) {
            val k = grid.read(bitsFor(kinds.size))
            if (k == 0) continue
            val kind = kinds.getOrNull(k - 1) ?: return null
            if (x + kind.width > n || y + kind.height > n) return null
            layout.plants += kind.sameKind().also { it.x = x; it.y = y }
        }

        val spots = r.bits((n * n + 7) / 8)
        for (y in 0 until n) for (x in 0 until n) {
            if (spots.read(1) == 0) continue
            val template = spotTemplate ?: return null
            layout.spots += Spot().also {
                it.x = x; it.y = y; it.kind = template.kind; it.label = template.label; it.colour = template.colour
                it.stages = template.stages; it.decayDays = template.decayDays
            }
        }
        repeat(r.byte()) {
            val x = r.byte()
            val y = r.byte()
            val kind = GROUNDS.getOrNull(r.byte()) ?: return null
            if (x >= n || y >= n) return null
            layout.grounds += GroundTile().also { it.x = x; it.y = y; it.kind = kind }
        }
        layout.name = (spotTemplate?.label ?: "Shared layout") + " (shared)"
        layout.source = "shared code"
        return layout
    }

    /** Bits needed to number 0..kinds. */
    private fun bitsFor(kinds: Int) = maxOf(1, 32 - Integer.numberOfLeadingZeros(kinds))

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater(true)
        val out = ByteArrayOutputStream()
        try {
            inflater.setInput(data)
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) return null
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private class Bits(private val data: ByteArray = ByteArray(0)) {
        private val out = ByteArrayOutputStream()
        private var current = 0
        private var used = 0
        private var at = 0

        fun write(value: Int, bits: Int) {
            for (i in bits - 1 downTo 0) {
                current = (current shl 1) or (value shr i and 1)
                if (++used == 8) {
                    out.write(current)
                    current = 0
                    used = 0
                }
            }
        }

        fun bytes(): ByteArray {
            if (used > 0) {
                out.write(current shl (8 - used))
                current = 0
                used = 0
            }
            return out.toByteArray()
        }

        fun read(bits: Int): Int {
            var value = 0
            repeat(bits) {
                val byte = data.getOrNull(at / 8)?.toInt() ?: throw IllegalArgumentException("code ends early")
                value = (value shl 1) or (byte shr (7 - at % 8) and 1)
                at++
            }
            return value
        }
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun byte(v: Int) = out.write(v and 0xFF)
        fun bytes(b: ByteArray) = out.write(b)
        fun colour(rgb: Int) { byte(rgb shr 16); byte(rgb shr 8); byte(rgb) }
        fun text(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8).take(64).toByteArray()
            byte(bytes.size)
            out.write(bytes)
        }
        fun bytes(): ByteArray = out.toByteArray()
    }

    private class Reader(private val data: ByteArray) {
        private var at = 0
        fun byte(): Int {
            if (at >= data.size) throw IllegalArgumentException("code ends early")
            return data[at++].toInt() and 0xFF
        }
        fun colour() = (byte() shl 16) or (byte() shl 8) or byte()
        fun bits(count: Int): Bits {
            if (at + count > data.size) throw IllegalArgumentException("code ends early")
            return Bits(data.copyOfRange(at, at + count)).also { at += count }
        }
        fun text(): String {
            val n = byte()
            if (at + n > data.size) throw IllegalArgumentException("code ends early")
            return String(data, at, n, Charsets.UTF_8).also { at += n }
                .filter { it >= ' ' && it != '§' }
        }
        /** A plant id: capitals, digits and _ only. */
        fun id(): String? = text().takeIf { it.matches(Regex("[A-Z0-9_]{0,40}")) }
    }
}
