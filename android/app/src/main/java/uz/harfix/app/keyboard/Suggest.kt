package uz.harfix.app.keyboard

import java.io.File
import java.util.Locale

/**
 * Suggestions for the word being typed, and the autocorrection to make when it ends. Corrects only
 * when it is fairly sure: the word is unknown, the best candidate is close, common and clearly
 * better than the next one.
 */
object Suggest {
    /** [words]: up to three to show (the first in the middle); [autocorrect]: what to change the word to. */
    class Result(val words: List<String>, val autocorrect: String?)

    val EMPTY = Result(emptyList(), null)

    private fun maxCost(len: Int) = if (len <= 3) 1f else if (len <= 6) 1.5f else 2f

    /** How far a word of this length may be changed without asking. */
    private fun sureCost(len: Int) = if (len <= 4) 0.7f else if (len <= 7) 1f else 1.5f

    fun forWord(dict: Dictionary, user: UserWords?, typed: String): Result {
        val key = Dictionary.normalize(typed)
        if (key.isEmpty() || key.any { !(it.isLetter() || it == '\'') } || key.length > 24) return EMPTY
        val known = dict.lookup(key) != null || user?.knows(key) == true
        val completions = dict.completions(key, 6).filter { it.key != key }
        val corrections = if (key.length >= 2) dict.corrections(key, 6, maxCost(key.length)) else emptyList()

        var fix: String? = null
        if (!known && corrections.isNotEmpty()) {
            val best = corrections[0]
            val second = corrections.getOrNull(1)
            val nameOk = best.entry.word[0].isLowerCase() || typed[0].isUpperCase()
            val sure = best.cost > 0f && best.cost <= sureCost(key.length) && best.entry.weight >= 40 &&
                (second == null || second.score - best.score >= 0.25f)
            if (sure && nameOk) fix = matchCase(best.entry.word, typed)
        }

        val out = LinkedHashSet<String>()
        when {
            fix != null -> out.add(fix)
            known -> out.add(typed)
        }
        user?.completions(key, 2)?.forEach { out.add(matchCase(it, typed)) }
        completions.forEach { out.add(matchCase(it.word, typed)) }
        corrections.forEach { if (it.entry.key != key) out.add(matchCase(it.entry.word, typed)) }
        if (fix != null) out.add(typed) // keep what was typed, one tap away
        return Result(out.take(3), fix)
    }

    /** [word] written the way [typed] is: CAPS, Capitalised, or as the dictionary has it. */
    fun matchCase(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(Locale.ROOT)
        typed.isNotEmpty() && typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    /** The word right before the cursor (letters and apostrophes), or "". */
    fun wordBefore(text: CharSequence): String {
        var start = text.length
        while (start > 0 && (text[start - 1].isLetter() || Dictionary.normalize(text[start - 1].toString()) == "'")) start--
        while (start < text.length && !text[start].isLetter()) start++ // no leading apostrophes
        return text.subSequence(start, text.length).toString()
    }
}

/**
 * Words the user types that the dictionary does not have. Kept only on the phone, in the
 * keyboard's own file; nothing is sent. A word counts as known after it was typed twice, or
 * at once when the user undoes an autocorrection of it. "So‘zlarni o‘rganish" off clears it.
 */
class UserWords(private val file: File) {
    private val counts = HashMap<String, Int>()
    private var dirty = false

    fun load() {
        counts.clear()
        if (!file.exists()) return
        file.forEachLine(Charsets.UTF_8) { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) line.substring(tab + 1).toIntOrNull()?.let { counts[line.substring(0, tab)] = it }
        }
    }

    fun knows(key: String): Boolean = (counts[key] ?: 0) >= 2

    fun learn(word: String, by: Int = 1) {
        val key = Dictionary.normalize(word)
        if (!VALID.matches(key)) return
        counts[key] = (counts[key] ?: 0) + by
        dirty = true
        if (counts.size > MAX) {
            counts.entries.sortedBy { it.value }.take(counts.size - MAX).forEach { counts.remove(it.key) }
        }
    }

    fun completions(prefix: String, limit: Int): List<String> =
        counts.entries.filter { it.value >= 2 && it.key != prefix && it.key.startsWith(prefix) }
            .sortedByDescending { it.value }.take(limit).map { it.key }

    fun save() {
        if (!dirty) return
        dirty = false
        try {
            file.writeText(counts.entries.joinToString("") { "${it.key}\t${it.value}\n" }, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun clear() {
        counts.clear()
        dirty = false
        file.delete()
    }

    companion object {
        const val FILE = "user_words.txt"
        private const val MAX = 3000
        private val VALID = Regex("[a-zöğşçñ]{2,24}(?:'[a-zöğşçñ]+)?")
    }
}
