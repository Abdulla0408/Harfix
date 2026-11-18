package uz.harfix.app.keyboard

import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/** One word: [word] as written (names keep their capital), [key] its search form, [weight] 1..255. */
class Entry(val word: String, val key: String, val weight: Int)

/** A correction candidate; [cost] is how far the typed word is from it (0 = the same). */
class Scored(val entry: Entry, val cost: Float) {
    /** Lower is better: close to what was typed, and a common word. */
    val score: Float get() = cost - ln(entry.weight + 1f) * 0.15f
}

/**
 * The keyboard dictionary (assets/dict/uz.txt, built by android/tools/build_dictionary.py from
 * the Uzbek Wikipedia, CC BY-SA 4.0): new-alphabet words with a 1..255 frequency weight.
 */
class Dictionary(list: List<Entry>) {
    private val byKey = HashMap<String, Entry>(list.size * 2)
    private val sorted: Array<Entry>
    private val byFirst = HashMap<Char, ArrayList<Entry>>()

    init {
        for (e in list) if (!byKey.containsKey(e.key)) byKey[e.key] = e
        sorted = byKey.values.sortedBy { it.key }.toTypedArray()
        for (e in byKey.values.sortedByDescending { it.weight }) byFirst.getOrPut(e.key[0]) { ArrayList() }.add(e)
    }

    val size: Int get() = byKey.size

    fun lookup(word: String): Entry? = byKey[normalize(word)]

    /** Words whose search form starts with [c], most frequent first. */
    fun startingWith(c: Char): List<Entry> = byFirst[c] ?: emptyList()

    /** Words that start with [prefix] (a search form), most frequent first. */
    fun completions(prefix: String, limit: Int): List<Entry> {
        if (prefix.isEmpty()) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].key < prefix) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<Entry>()
        var i = lo
        while (i < sorted.size && sorted[i].key.startsWith(prefix)) out.add(sorted[i++])
        out.sortByDescending { it.weight }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    /** Words close to [key] (a search form) as typed on the keyboard, best first. */
    fun corrections(key: String, limit: Int, maxCost: Float): List<Scored> {
        if (key.isEmpty()) return emptyList()
        val firsts = LinkedHashSet<Char>()
        firsts.add(key[0])
        firsts.addAll(Typing.near(key[0]))
        val out = ArrayList<Scored>()
        for (f in firsts) {
            for (e in startingWith(f)) {
                if (abs(e.key.length - key.length) > 2) continue
                val cost = Typing.distance(key, e.key, maxCost)
                if (cost <= maxCost) out.add(Scored(e, cost))
            }
        }
        out.sortBy { it.score }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    companion object {
        private const val APOSTROPHES = "'`‘’ʻʼ´"

        /** The search form: lower case, every apostrophe written as '. */
        fun normalize(s: String): String {
            val b = StringBuilder(s.length)
            for (ch in s.lowercase(Locale.ROOT)) b.append(if (APOSTROPHES.indexOf(ch) >= 0) '\'' else ch)
            return b.toString()
        }

        fun parse(lines: Sequence<String>): Dictionary {
            val list = ArrayList<Entry>(80_000)
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val weight = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                val key = normalize(word)
                list.add(Entry(word, if (key == word) word else key, weight))
            }
            return Dictionary(list)
        }

        fun load(input: InputStream): Dictionary = input.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
    }
}

/**
 * How easy a typo is on the Latin layout: neighbouring keys and the new letters (ö for o, ş for s)
 * are cheap mistakes, other letters are not.
 */
object Typing {
    private val ROWS = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
    private val POS = HashMap<Char, FloatArray>().apply {
        ROWS.forEachIndexed { y, (row, offset) ->
            row.forEachIndexed { x, c -> put(c, floatArrayOf(x + offset, y.toFloat())) }
        }
    }

    /** A new letter and the plain letter people type for it. */
    val TWINS = mapOf('ö' to 'o', 'ğ' to 'g', 'ş' to 's', 'ç' to 'c', 'ñ' to 'n')

    fun base(c: Char): Char = TWINS[c] ?: c

    private fun close(a: Char, b: Char): Boolean {
        val pa = POS[base(a)] ?: return false
        val pb = POS[base(b)] ?: return false
        val dx = pa[0] - pb[0]
        val dy = pa[1] - pb[1]
        return dx * dx + dy * dy <= 1.45f
    }

    /** Letters a finger aiming at [c] may hit instead. */
    fun near(c: Char): List<Char> {
        val out = ArrayList<Char>()
        for (k in POS.keys) if (k != c && close(c, k)) out.add(k)
        for ((twin, plain) in TWINS) {
            if (plain == base(c) && twin != c) out.add(twin)
            if (twin == c && plain != c) out.add(plain)
        }
        return out
    }

    fun substitution(a: Char, b: Char): Float = when {
        a == b -> 0f
        base(a) == base(b) -> 0.25f
        close(a, b) -> 0.6f
        else -> 1f
    }

    /** Damerau-Levenshtein distance with the costs above; stops early above [max]. */
    fun distance(a: String, b: String, max: Float): Float {
        val m = b.length
        var prev2 = FloatArray(m + 1)
        var prev = FloatArray(m + 1) { it.toFloat() }
        var cur = FloatArray(m + 1)
        for (i in 1..a.length) {
            cur[0] = i.toFloat()
            var rowMin = cur[0]
            for (j in 1..m) {
                var v = minOf(prev[j] + 1f, cur[j - 1] + 1f, prev[j - 1] + substitution(a[i - 1], b[j - 1]))
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 0.6f)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return rowMin
            val t = prev2
            prev2 = prev
            prev = cur
            cur = t
        }
        return prev[m]
    }
}
