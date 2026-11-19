package uz.harfix.app.keyboard

import java.io.InputStream
import java.util.Locale
import kotlin.math.ln

/** One word: [word] as written (names keep their capital), [key] its search form, [weight] 1..255. */
class Entry(val word: String, val key: String, val weight: Int) {
    /** Mostly written with a capital letter: a name. */
    val isName: Boolean get() = word[0].isUpperCase()
}

/** A correction candidate; [cost] is how far the typed word is from it (0 = the same). */
class Scored(val entry: Entry, val cost: Float) {
    /** Lower is better: close to what was typed, and a common word. */
    val score: Float = cost - ln(entry.weight + 1f) * 0.15f
}

/**
 * The keyboard dictionary: assets/dict/uz.txt (built by android/tools/build_dictionary.py from the
 * Uzbek Wikipedia, CC BY-SA 4.0) plus assets/dict/uz-extra.txt (everyday words Wikipedia lacks).
 * New-alphabet words with a 1..255 frequency weight. Built once in the background; read-only after.
 */
class Dictionary(list: List<Entry>) {
    private val byKey = HashMap<String, Entry>(list.size * 2)
    private val sorted: Array<Entry>
    /** (first letter, length) → words, most frequent first: corrections look only where they can match. */
    private val buckets = HashMap<Int, Array<Entry>>()
    private val byFirst = HashMap<Char, Array<Entry>>()

    init {
        for (e in list) {
            val old = byKey[e.key]
            if (old == null || e.weight > old.weight) byKey[e.key] = e
        }
        val all = byKey.values.toTypedArray()
        all.sortWith(compareByDescending { it.weight })
        val tmpBuckets = HashMap<Int, ArrayList<Entry>>()
        val tmpFirst = HashMap<Char, ArrayList<Entry>>()
        for (e in all) {
            tmpBuckets.getOrPut(bucket(e.key[0], e.key.length)) { ArrayList() }.add(e)
            tmpFirst.getOrPut(e.key[0]) { ArrayList() }.add(e)
        }
        for ((k, v) in tmpBuckets) buckets[k] = v.toTypedArray()
        for ((k, v) in tmpFirst) byFirst[k] = v.toTypedArray()
        sorted = all.copyOf()
        sorted.sortWith(compareBy { it.key })
    }

    private fun bucket(first: Char, length: Int) = first.code * 64 + minOf(length, 63)

    val size: Int get() = byKey.size

    fun lookup(word: String): Entry? = byKey[normalize(word)]

    /** Words whose search form starts with [c], most frequent first. */
    fun startingWith(c: Char): Array<Entry> = byFirst[c] ?: EMPTY

    /** Index of the first word whose key is >= [prefix]. */
    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].key < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Are there at least [n] words that start with [prefix]? */
    fun hasPrefix(prefix: String, n: Int = 2): Boolean {
        var i = lowerBound(prefix)
        var found = 0
        while (i < sorted.size && sorted[i].key.startsWith(prefix)) {
            if (++found >= n) return true
            i++
        }
        return false
    }

    /** The [limit] most frequent words that start with [prefix] (a search form). */
    fun completions(prefix: String, limit: Int): List<Entry> {
        if (prefix.isEmpty()) return emptyList()
        if (prefix.length == 1) return startingWith(prefix[0]).take(limit)
        val top = ArrayList<Entry>(limit + 1)
        var i = lowerBound(prefix)
        while (i < sorted.size && sorted[i].key.startsWith(prefix)) {
            val e = sorted[i++]
            if (top.size < limit || e.weight > top.last().weight) {
                var at = top.size
                while (at > 0 && top[at - 1].weight < e.weight) at--
                top.add(at, e)
                if (top.size > limit) top.removeAt(top.size - 1)
            }
        }
        return top
    }

    /**
     * Words close to [key] (a search form) as typed on the keyboard, best first. Looks at words that
     * start with [firsts] and are about as long; rare words (weight below [minWeight]) are skipped.
     */
    fun corrections(key: String, firsts: Collection<Char>, limit: Int, maxCost: Float, minWeight: Int = 20): List<Scored> {
        if (key.isEmpty()) return emptyList()
        val span = if (key.length >= 6) 2 else 1
        val out = ArrayList<Scored>()
        for (f in firsts) {
            for (len in maxOf(1, key.length - span)..key.length + span) {
                val words = buckets[bucket(f, len)] ?: continue
                for (e in words) {
                    if (e.weight < minWeight) break // most frequent first: the rest are rarer
                    if (e.key == key) continue
                    val cost = Typing.distance(key, e.key, maxCost)
                    if (cost <= maxCost) out.add(Scored(e, cost))
                }
            }
        }
        out.sortBy { it.score }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    companion object {
        private val EMPTY = emptyArray<Entry>()
        private const val APOSTROPHES = "'`‘’ʻʼ´"

        /** The search form: lower case, every apostrophe written as '. */
        fun normalize(s: String): String {
            var plain = true
            for (ch in s) if (ch.isUpperCase() || (ch != '\'' && APOSTROPHES.indexOf(ch) >= 0)) {
                plain = false
                break
            }
            if (plain) return s
            val b = StringBuilder(s.length)
            for (ch in s.lowercase(Locale.ROOT)) b.append(if (APOSTROPHES.indexOf(ch) >= 0) '\'' else ch)
            return b.toString()
        }

        fun parse(lines: Sequence<String>, into: ArrayList<Entry> = ArrayList(80_000)): ArrayList<Entry> {
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val weight = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                into.add(Entry(word, normalize(word), weight))
            }
            return into
        }

        /** The main word list and, if given, the everyday words. */
        fun load(main: InputStream, extra: InputStream? = null): Dictionary {
            val list = main.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
            extra?.bufferedReader(Charsets.UTF_8)?.useLines { parse(it, list) }
            return Dictionary(list)
        }
    }
}

/**
 * How easy a typo is on the Latin layout: neighbouring keys and the new letters (ö for o, ş for s)
 * are cheap mistakes, other letters are not. Costs come from a table built once.
 */
object Typing {
    private val ROWS = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)

    /** A new letter and the plain letter people type for it. */
    val TWINS = mapOf('ö' to 'o', 'ğ' to 'g', 'ş' to 's', 'ç' to 'c', 'ñ' to 'n')

    private const val LETTERS = "abcdefghijklmnopqrstuvwxyzöğşçñ'"
    private val INDEX = IntArray(0x180) { -1 }.also { idx -> LETTERS.forEachIndexed { i, c -> idx[c.code] = i } }
    private val N = LETTERS.length
    private val COST = FloatArray(N * N)
    private val NEAR = HashMap<Char, List<Char>>()

    fun base(c: Char): Char = TWINS[c] ?: c

    init {
        val pos = HashMap<Char, FloatArray>()
        ROWS.forEachIndexed { y, (row, offset) -> row.forEachIndexed { x, c -> pos[c] = floatArrayOf(x + offset, y.toFloat()) } }
        fun close(a: Char, b: Char): Boolean {
            val pa = pos[base(a)] ?: return false
            val pb = pos[base(b)] ?: return false
            val dx = pa[0] - pb[0]
            val dy = pa[1] - pb[1]
            return dx * dx + dy * dy <= 1.45f
        }
        for (i in 0 until N) {
            for (j in 0 until N) {
                val a = LETTERS[i]
                val b = LETTERS[j]
                COST[i * N + j] = when {
                    a == b -> 0f
                    base(a) == base(b) -> 0.25f
                    close(a, b) -> 0.6f
                    else -> 1f
                }
            }
        }
        for (c in LETTERS) {
            NEAR[c] = LETTERS.filter { it != c && it != '\'' && COST[INDEX[c.code] * N + INDEX[it.code]] < 1f }.toList()
        }
    }

    private fun index(c: Char) = if (c.code < INDEX.size) INDEX[c.code] else -1

    /** Letters a finger aiming at [c] may hit instead (neighbours and the twin letter). */
    fun near(c: Char): List<Char> = NEAR[c] ?: emptyList()

    /** The letter and its twin (s and ş): where a word that starts like this can start. */
    fun twins(c: Char): List<Char> {
        val b = base(c)
        val out = arrayListOf(c)
        if (b != c) out.add(b)
        for ((twin, plain) in TWINS) if (plain == b && twin != c) out.add(twin)
        return out
    }

    fun substitution(a: Char, b: Char): Float {
        if (a == b) return 0f
        val i = index(a)
        val j = index(b)
        return if (i < 0 || j < 0) 1f else COST[i * N + j]
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
            val ca = a[i - 1]
            for (j in 1..m) {
                val cb = b[j - 1]
                var v = prev[j - 1] + substitution(ca, cb)
                val del = prev[j] + 1f
                if (del < v) v = del
                val ins = cur[j - 1] + 1f
                if (ins < v) v = ins
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                    val t = prev2[j - 2] + 0.6f
                    if (t < v) v = t
                }
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
