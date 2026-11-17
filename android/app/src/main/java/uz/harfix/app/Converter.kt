package uz.harfix.app

import java.util.Locale
import java.util.regex.Pattern

/**
 * Harfix converter: Kotlin port of the Mini App's harfix.js (the same rules as
 * core/yangi_alifbo/converter.py). Targets: "new" (new Latin, O'RQ-1184),
 * "old" (oʻ gʻ sh ch) and "cyr" (Cyrillic). Must give the same output as harfix.js;
 * ConverterTest checks it against core/tests/cases.json and golden outputs of harfix.js.
 */
object Converter {
    const val TUTUQ = "ʼ" // U+02BC
    const val OKINA = "ʻ" // U+02BB
    const val APOSTROPHES = "'`‘’ʻʼ´"
    val TARGETS = listOf("new", "old", "cyr")

    private const val OG = "['`‘’ʻ´]" // the oʻ/gʻ mark: every apostrophe except U+02BC
    private const val AP = "['`‘’ʻʼ´]"
    private const val W = "[\\p{L}\\p{N}_]"

    private val LATIN_RULES = listOf(
        Regex("O$OG") to "Ö",
        Regex("o$OG") to "ö",
        Regex("G$OG") to "Ğ",
        Regex("g$OG") to "ğ",
        Regex("S[Hh]") to "Ş",
        Regex("s[Hh]") to "ş",
        Regex("C[Hh]") to "Ç",
        Regex("c[Hh]") to "ç",
        // remaining apostrophes between letters are the tutuq: maʼno, sheʼr
        Regex("(?<=$W)$AP(?=$W)") to TUTUQ,
    )

    private val CYR = mapOf(
        "а" to "a", "б" to "b", "в" to "v", "г" to "g", "д" to "d", "ё" to "yo", "ж" to "j",
        "з" to "z", "и" to "i", "й" to "y", "к" to "k", "л" to "l", "м" to "m", "н" to "n",
        "о" to "o", "п" to "p", "р" to "r", "с" to "s", "т" to "t", "у" to "u", "ф" to "f",
        "х" to "x", "ч" to "ç", "ш" to "ş", "щ" to "şç", "ъ" to TUTUQ, "ы" to "i", "ь" to "",
        "э" to "e", "ю" to "yu", "я" to "ya", "ў" to "ö", "қ" to "q", "ғ" to "ğ", "ҳ" to "h",
    )
    private const val CYR_VOWELS = "аеёиоуэюяўАЕЁИОУЭЮЯЎ"
    private val CYR_RE = Regex("[Ѐ-ӿ]")
    private val LAT_RE = Regex("[A-Za-zÖöĞğŞşÇç]")

    // URLs, e-mails, @mentions, #tags and `code` are never touched.
    private const val NS = "[^ \\t\\n\\u000B\\f\\r\\u001C-\\u001F\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]"
    private val SKIP_RE = Pattern.compile(
        "(https?://$NS+|www\\.$NS+|$NS+@$NS+\\.$NS+|[@#][\\p{L}\\p{N}_.]+|`[^`]*`)",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )

    /** Converts [text] to [target] ("new", "old" or "cyr"). */
    fun convert(text: String, target: String = "new"): String {
        val fn: (String) -> String = when (target) {
            "new" -> ::toNew
            "old" -> ::toOld
            "cyr" -> ::toCyr
            else -> throw IllegalArgumentException("unknown target $target")
        }
        val out = StringBuilder()
        var pos = 0
        val m = SKIP_RE.matcher(text)
        while (m.find()) {
            out.append(fn(text.substring(pos, m.start())))
            out.append(m.group())
            pos = m.end()
        }
        out.append(fn(text.substring(pos)))
        return out.toString()
    }

    /** A saved target setting, or "new" if it is missing or unknown. */
    fun target(value: String?): String = if (value != null && value in TARGETS) value else "new"

    // --- helpers that behave like the JS ones on single UTF-16 units ----------------------

    private fun lower(c: Char): String = c.toString().lowercase(Locale.ROOT)
    private fun isAlpha(c: Char?): Boolean = c != null && Character.isLetter(c)
    private fun isUpper(c: Char?): Boolean {
        if (c == null) return false
        val s = c.toString()
        return s != s.lowercase(Locale.ROOT) && s == s.uppercase(Locale.ROOT)
    }
    private fun has(set: String, c: String?): Boolean = !c.isNullOrEmpty() && set.contains(c)
    private fun has(set: String, c: Char?): Boolean = c != null && set.indexOf(c) >= 0
    private fun at(text: String, i: Int): Char? = if (i >= 0 && i < text.length) text[i] else null

    private fun caseLike(out: String, text: String, i: Int): String {
        if (out.length == 1) return out.uppercase(Locale.ROOT)
        val prev = at(text, i - 1)
        val nxt = at(text, i + 1)
        val allCaps = (isAlpha(nxt) && isUpper(nxt)) || (isAlpha(prev) && isUpper(prev))
        return if (allCaps) out.uppercase(Locale.ROOT)
        else out.substring(0, 1).uppercase(Locale.ROOT) + out.substring(1)
    }

    // --- Cyrillic -> new Latin -------------------------------------------------------------

    private fun cyrChar(text: String, i: Int): String {
        val ch = text[i]
        val low = lower(ch)
        val prev = at(text, i - 1)
        val wordStart = !isAlpha(prev)
        val out: String = when {
            low == "е" ->
                if (wordStart || has(CYR_VOWELS, prev) || has("ъь", prev?.let { lower(it) })) "ye" else "e"
            low == "ц" -> if (has(CYR_VOWELS, prev)) "ts" else "s"
            low == "с" && i + 1 < text.length && lower(text[i + 1]) == "ҳ" -> "s$TUTUQ"
            CYR.containsKey(low) -> CYR.getValue(low)
            else -> return ch.toString()
        }
        if (ch.toString() == low || out.isEmpty()) return out
        return caseLike(out, text, i)
    }

    private fun fromCyrillic(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (i in text.indices) sb.append(cyrChar(text, i))
        return sb.toString()
    }

    private fun toNew(input: String): String {
        var text = input
        if (CYR_RE.containsMatchIn(text)) text = fromCyrillic(text)
        for ((re, rep) in LATIN_RULES) text = re.replace(text, rep)
        return text
    }

    // --- new -> old Latin ------------------------------------------------------------------

    private val OLD = mapOf("ö" to "o$OKINA", "ğ" to "g$OKINA", "ş" to "sh", "ç" to "ch")
    private val SH_CH = Regex("([sScC])([hH])")

    private fun newToOld(input: String): String {
        val text = SH_CH.replace(input, "$1$TUTUQ$2")
        val sb = StringBuilder(text.length + 8)
        for (i in text.indices) {
            val ch = text[i]
            val low = lower(ch)
            val rep = OLD[low]
            when {
                rep == null -> sb.append(ch)
                ch.toString() == low -> sb.append(rep)
                else -> sb.append(caseLike(rep, text, i))
            }
        }
        return sb.toString()
    }

    // --- new -> Cyrillic -------------------------------------------------------------------

    private val TO_CYR = mapOf(
        "a" to "а", "b" to "б", "c" to "с", "d" to "д", "e" to "е", "f" to "ф", "g" to "г",
        "h" to "ҳ", "i" to "и", "j" to "ж", "k" to "к", "l" to "л", "m" to "м", "n" to "н",
        "o" to "о", "p" to "п", "q" to "қ", "r" to "р", "s" to "с", "t" to "т", "u" to "у",
        "v" to "в", "w" to "в", "x" to "х", "y" to "й", "z" to "з",
        "ö" to "ў", "ğ" to "ғ", "ş" to "ш", "ç" to "ч",
    )
    private val Y_PAIRS = mapOf("a" to "я", "o" to "ё", "u" to "ю", "e" to "е")
    private const val LAT_VOWELS = "aeiouö"
    private val TS_CYR = Pattern.compile("ts(?=i(?:ya|on))", Pattern.CASE_INSENSITIVE)

    private fun up(rep: String, ch: Char): String = if (isUpper(ch)) rep.uppercase(Locale.ROOT) else rep

    private fun newToCyr(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            val low = lower(ch)
            val prev = at(text, i - 1)
            val nxt = at(text, i + 1)
            val nxtLow = nxt?.let { lower(it) } ?: ""
            if (low == "y" && Y_PAIRS.containsKey(nxtLow)) {
                out.append(up(Y_PAIRS.getValue(nxtLow), ch))
                i += 2
                continue
            }
            if (low == "t" && nxtLow == "s" && TS_CYR.matcher(text).region(i, n).lookingAt()) {
                out.append(if (isUpper(ch)) "Ц" else "ц")
                i += 2
                continue
            }
            if (ch.toString() == TUTUQ) {
                if (prev != null && has("sc", lower(prev)) && nxtLow == "h") {
                    i += 1
                    continue
                }
                out.append(if (isAlpha(prev) && isAlpha(nxt)) "ъ" else ch.toString())
                i += 1
                continue
            }
            if (low == "e") {
                val rep = if (!isAlpha(prev) || has(LAT_VOWELS, prev?.let { lower(it) })) "э" else "е"
                out.append(up(rep, ch))
                i += 1
                continue
            }
            val r = TO_CYR[low]
            out.append(if (r == null) ch.toString() else up(r, ch))
            i += 1
        }
        return out.toString()
    }

    private fun toOld(text: String): String = newToOld(toNew(text))

    private fun toCyr(text: String): String {
        if (CYR_RE.containsMatchIn(text) && !LAT_RE.containsMatchIn(text)) return text
        return newToCyr(toNew(text))
    }

    // --- formatted runs (Word / PowerPoint / Excel paragraphs) ----------------------------

    /** Letter pairs that must stay together; for Cyrillic also ya/yo/yu/ye/ts (я ё ю е ц). */
    private val PAIRS: Set<String> = buildSet {
        add("sh"); add("ch")
        for (c in "og") for (a in APOSTROPHES) add("$c$a")
    }
    private val PAIRS_CYR: Set<String> = PAIRS + setOf("ya", "yo", "yu", "ye", "ts")

    /**
     * Converts a paragraph split into formatted runs, keeping the same number of runs.
     * A letter pair split across runs ("s" | "h") is moved into the first run, and each run
     * is converted with the previous run's last character as context.
     */
    fun convertRuns(runs: List<String>, target: String): List<String> {
        val r = runs.toMutableList()
        val pairs = if (target == "cyr") PAIRS_CYR else PAIRS
        for (i in 0 until r.size - 1) {
            val a = r[i]
            val b = r[i + 1]
            if (a.isNotEmpty() && b.isNotEmpty() &&
                ("" + a.last() + b[0]).lowercase(Locale.ROOT) in pairs
            ) {
                r[i] = a + b[0]
                r[i + 1] = b.substring(1)
            }
        }
        val out = ArrayList<String>(r.size)
        var prev = ""
        for (s in r) {
            out.add(if (s.isEmpty()) s else convertSegment(s, prev, target))
            if (s.isNotEmpty()) prev = s
        }
        return out
    }

    /** Converts [text] that follows [prev], so rules that look back still work across runs. */
    fun convertSegment(text: String, prev: String, target: String): String {
        if (prev.isEmpty()) return convert(text, target)
        val ctx = prev.last().toString()
        val full = convert(ctx + text, target)
        val pre = convert(ctx, target)
        return if (full.startsWith(pre)) full.substring(pre.length) else convert(text, target)
    }
}

/** UI texts are written in the current Latin alphabet and shown in the new one. */
fun t(text: String): String = Converter.convert(text, "new")
