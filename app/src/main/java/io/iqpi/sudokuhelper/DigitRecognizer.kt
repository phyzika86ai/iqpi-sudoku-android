package io.iqpi.sudokuhelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight, offline recognition of the large, bright glyphs seen in iqpi.io.
 * Feed it the SQUARE GRID ALONE, not the entire camera preview.
 * No OCR services, Internet, screen recording or hidden website data are used.
 * A different font/theme may need new templates or manual correction.
 */
class DigitRecognizer(context: Context) {
    data class Scan(val digits: IntArray, val uncertain: Set<Int>, val averageSimilarity: Double) {
        val found: Int get() = digits.count { it > 0 }
    }

    private val templates: List<BooleanArray> = (1..6).map { digit ->
        context.assets.open("digits/$digit.png").use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream)
                ?: error("Шаблон цифры $digit не найден")
            require(bitmap.width == 40 && bitmap.height == 40)
            val pixels = IntArray(1600)
            bitmap.getPixels(pixels, 0, 40, 0, 0, 40, 40)
            bitmap.recycle()
            BooleanArray(1600) { i -> Color.red(pixels[i]) > 127 }
        }
    }

    fun read(square: Bitmap): Scan {
        // Fixed 120px cells make sampling independent of camera/video resolution.
        val scaled = Bitmap.createScaledBitmap(square, 720, 720, true)
        val pixels = IntArray(720 * 720)
        scaled.getPixels(pixels, 0, 720, 0, 0, 720, 720)
        if (scaled !== square) scaled.recycle()
        val values = IntArray(36)
        val dubious = mutableSetOf<Int>()
        var similarity = 0.0
        var count = 0
        for (row in 0..5) for (col in 0..5) {
            val glyph = isolate(pixels, row, col) ?: continue
            val ranked = templates.mapIndexed { idx, ref ->
                val intersection = glyph.indices.count { glyph[it] && ref[it] }
                val union = glyph.indices.count { glyph[it] || ref[it] }
                Pair(idx + 1, if (union == 0) 0.0 else intersection.toDouble() / union)
            }.sortedByDescending { it.second }
            val index = row * 6 + col
            values[index] = ranked[0].first
            similarity += ranked[0].second
            count++
            if (ranked[0].second < 0.49 || ranked[0].second - ranked[1].second < 0.10)
                dubious.add(index)
        }
        return Scan(values, dubious, if (count > 0) similarity / count else 0.0)
    }

    private fun isolate(frame: IntArray, row: Int, col: Int): BooleanArray? {
        // Exclude cell borders, decorative lines and Killer group totals at cell corners.
        val startX = col * 120 + 20
        val startY = row * 120 + 20
        val size = 80
        val on = BooleanArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val rgb = frame[(startY + y) * 720 + startX + x]
            val r = Color.red(rgb)
            val g = Color.green(rgb)
            val b = Color.blue(rgb)
            on[y * size + x] = min(r, min(g, b)) >= 165 && max(r, max(g, b)) - min(r, min(g, b)) <= 70
        }

        // Find the largest bright connected component; small dots and glares are excluded.
        val labels = IntArray(size * size)
        val queue = IntArray(size * size)
        var label = 0
        var bestLabel = 0
        var bestArea = 0
        var bx0 = 0; var bx1 = 0; var by0 = 0; var by1 = 0
        for (start in on.indices) {
            if (!on[start] || labels[start] != 0) continue
            label++
            labels[start] = label
            var head = 0
            var tail = 1
            queue[0] = start
            var minX = size; var maxX = 0; var minY = size; var maxY = 0
            while (head < tail) {
                val point = queue[head++]
                val x = point % size; val y = point / size
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || xx >= size || yy < 0 || yy >= size) continue
                    val neighbour = yy * size + xx
                    if (on[neighbour] && labels[neighbour] == 0) {
                        labels[neighbour] = label
                        queue[tail++] = neighbour
                    }
                }
            }
            if (tail > bestArea) {
                bestArea = tail
                bestLabel = label
                bx0 = minX; bx1 = maxX; by0 = minY; by1 = maxY
            }
        }
        val glyphWidth = bx1 - bx0 + 1
        val glyphHeight = by1 - by0 + 1
        if (bestArea < 85 || glyphHeight < 18 || glyphWidth < 5) return null
        // Exact same normalization as the included 40x40 digit templates.
        val scale = min(32.0 / glyphWidth, 32.0 / glyphHeight)
        val newW = max(1, (glyphWidth * scale).roundToInt())
        val newH = max(1, (glyphHeight * scale).roundToInt())
        val padX = (40 - newW) / 2; val padY = (40 - newH) / 2
        val output = BooleanArray(1600)
        for (yy in 0 until newH) for (xx in 0 until newW) {
            // OpenCV INTER_NEAREST maps target pixels to source with floor(index / scale).
            val sx = bx0 + min(glyphWidth - 1, (xx / scale).toInt())
            val sy = by0 + min(glyphHeight - 1, (yy / scale).toInt())
            output[(padY + yy) * 40 + (padX + xx)] = labels[sy * size + sx] == bestLabel
        }
        return output
    }
}
