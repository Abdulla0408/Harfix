package uz.harfix.app.keyboard

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Glide typing: the finger draws a line over the letters and the word whose ideal line (through
 * the key centres) fits best is chosen. Plain letters stand for the new ones (s for ş, o for ö),
 * so gliding over t-o-s-k-e-n-t gives «toşkent». Plain logic, tested without a phone.
 */
object Glide {
    class P(val x: Float, val y: Float)

    /** Points per line when two lines are compared. */
    const val N = 32

    fun dist(a: P, b: P): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    fun length(points: List<P>): Float {
        var total = 0f
        for (i in 1 until points.size) total += dist(points[i - 1], points[i])
        return total
    }

    /** [n] points evenly spaced along the line. */
    fun resample(points: List<P>, n: Int = N): List<P> {
        val total = length(points)
        if (points.size < 2 || total == 0f) return List(n) { points.first() }
        val step = total / (n - 1)
        val out = ArrayList<P>(n)
        out.add(points[0])
        var acc = 0f
        var prev = points[0]
        var i = 1
        while (i < points.size && out.size < n) {
            val cur = points[i]
            val d = dist(prev, cur)
            if (d > 0f && acc + d >= step) {
                val t = (step - acc) / d
                val q = P(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out.add(q)
                prev = q
                acc = 0f
            } else {
                acc += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out.add(points.last())
        return out
    }

    /** The same line moved to (0, 0) and scaled to size 1: only its shape is left. */
    private fun shape(points: List<P>): List<P> {
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        val w = points.maxOf { it.x } - points.minOf { it.x }
        val h = points.maxOf { it.y } - points.minOf { it.y }
        val s = maxOf(w, h, 1f)
        return points.map { P((it.x - cx) / s, (it.y - cy) / s) }
    }

    private fun meanDist(a: List<P>, b: List<P>): Float {
        var total = 0f
        for (i in a.indices) total += dist(a[i], b[i])
        return total / a.size
    }

    /** Keys within [radius] of [p], nearest first (at most 3), or else the nearest one. */
    fun nearest(centers: Map<Char, P>, p: P, radius: Float): List<Char> {
        val byDistance = centers.entries.sortedBy { dist(it.value, p) }
        val close = byDistance.filter { dist(it.value, p) <= radius }.take(3).map { it.key }
        return close.ifEmpty { byDistance.take(1).map { it.key } }
    }

    /** The ideal line of [key] through the key centres, or null if a letter has no key. */
    fun template(key: String, centers: Map<Char, P>): List<P>? {
        val out = ArrayList<P>(key.length)
        var prev = ' '
        for (c in key) {
            if (c == '\'') continue
            val b = Typing.base(c)
            if (b == prev) continue // a double letter is one stop on the key
            out.add(centers[b] ?: return null)
            prev = b
        }
        return out
    }

    /** The words that fit the drawn [path] best, best first. */
    fun decode(path: List<P>, centers: Map<Char, P>, keyWidth: Float, dict: Dictionary, limit: Int = 4): List<Entry> {
        if (path.size < 2 || centers.isEmpty() || keyWidth <= 0f) return emptyList()
        val input = resample(path)
        val inputShape = shape(input)
        val inputLength = length(path)
        val firsts = nearest(centers, path.first(), keyWidth * 1.3f)
        val lasts = nearest(centers, path.last(), keyWidth * 1.3f).toSet()

        val seen = HashSet<Char>()
        val scored = ArrayList<Pair<Entry, Float>>()
        for (f in firsts) {
            // words that start with f, or with its new letter (s → s..., ş...)
            val starts = listOf(f) + Typing.TWINS.filterValues { it == f }.keys
            for (s in starts) {
                if (!seen.add(s)) continue
                for (e in dict.startingWith(s)) {
                    val k = e.key
                    if (k.length < 2 || Typing.base(k.last()) !in lasts) continue
                    val tpl = template(k, centers) ?: continue
                    if (tpl.size < 2) continue
                    val tplLength = length(tpl)
                    val ratio = (tplLength + keyWidth) / (inputLength + keyWidth)
                    if (ratio < 0.45f || ratio > 2.2f) continue
                    val t = resample(tpl)
                    val location = meanDist(input, t) / keyWidth
                    if (location > 1.5f) continue
                    val form = meanDist(inputShape, shape(t))
                    val score = location + 2f * form + 0.4f * abs(ln(ratio)) - 0.06f * ln(e.weight + 1f)
                    scored.add(e to score)
                }
            }
        }
        scored.sortBy { it.second }
        return scored.take(limit).map { it.first }
    }
}
