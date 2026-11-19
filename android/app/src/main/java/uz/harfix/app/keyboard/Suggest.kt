package uz.harfix.app.keyboard

import java.io.File
import java.util.Locale

/**
 * Suggestions for the word being typed, and the autocorrection to make when it ends.
 *
 * A word is corrected when it is unknown, a common word is close (a neighbour key, a forgotten
 * new letter, swapped or missing letters) and clearly better than the next candidate. Uzbek words
 * take many endings that Wikipedia rarely shows (ketyapman, tuşundim); a word that is a known
 * stem plus such an ending is left alone, unless only a new letter is missing (bosqa → boşqa).
 */
object Suggest {
    /** [words]: up to three to show (the first in the middle); [autocorrect]: what to change the word to. */
    class Result(val words: List<String>, val autocorrect: String?)

    val EMPTY = Result(emptyList(), null)

    /** A strip entry starting with this adds the word after it to the user's dictionary. */
    const val ADD = "＋ "

    /** How far a word of this length may be changed without asking. */
    private fun sureCost(len: Int) = if (len <= 3) 0.6f else if (len <= 7) 1f else 1.3f

    /** Common endings (person, tense, case, plural), longest first. */
    private val ENDINGS = (
        "yaptilar yapsizmi yaptimi yapmiz yapsiz yapman yapsan yapti ganman gansiz ganmiz moqçiman moqçi " +
            "moqda madim maganman maymiz dingiz dilar ding dim dik di gan larning lardan larni larga larda lar " +
            "ning dagi dan ni ga da ka qa ingiz imiz ing im si aman asan adi amiz asiz adilar ay ib iş sang sa " +
            "misiz mi mas may man san miz siz dek day lik li çi çilar ça"
        ).split(' ').filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }

    /** A known stem with a common ending: a real word the dictionary does not list. */
    fun inflected(dict: Dictionary, key: String): Boolean {
        for (end in ENDINGS) {
            if (!key.endsWith(end)) continue
            val stem = key.substring(0, key.length - end.length)
            val e = dict.lookup(stem)
            if (stem.length >= 3 && (e != null || dict.hasPrefix(stem))) return true
            if (stem.length == 2 && e != null && e.weight >= 60) return true
        }
        return false
    }

    fun forWord(dict: Dictionary, user: UserWords?, typed: String): Result {
        val key = Dictionary.normalize(typed)
        if (key.isEmpty() || key.any { !(it.isLetter() || it == '\'') } || key.length > 24) return EMPTY
        val capital = typed[0].isUpperCase()
        val entry = dict.lookup(key)
        // a lower-case word that only matches a name ("kitab" → the town Kitab) is not known
        val known = (entry != null && (!entry.isName || capital)) || user?.knows(key) == true
        val completions = dict.completions(key, 6).filter { it.key != key }

        var corrections = emptyList<Scored>()
        var fix: String? = null
        if (!known && key.length >= 2) {
            val max = sureCost(key.length)
            corrections = dict.corrections(key, Typing.twins(key[0]), 6, max)
            if (corrections.isEmpty()) corrections = dict.corrections(key, Typing.near(key[0]), 6, max)
            // names only when very close: ozbekiston → Özbekiston, but never sen → Şen
            if (!capital) corrections = corrections.filter { !it.entry.isName || (it.cost <= 0.6f && it.entry.weight >= 120) }
            val best = corrections.firstOrNull()
            val second = corrections.getOrNull(1)
            if (best != null && (best.cost <= 0.3f || !inflected(dict, key)) &&
                (second == null || second.score - best.score >= 0.15f)
            ) {
                fix = matchCase(best.entry.word, typed)
            }
        }

        val out = LinkedHashSet<String>()
        when {
            fix != null -> out.add(fix)
            known -> out.add(typed)
        }
        user?.completions(key, 2)?.forEach { out.add(matchCase(it, typed)) }
        corrections.forEach { out.add(matchCase(it.entry.word, typed)) }
        completions.forEach { out.add(matchCase(it.word, typed)) }
        val words = out.filter { it != typed || known }.toMutableList()
        // an unknown word: one tap keeps it and adds it to the user's dictionary
        if (!known && key.length >= 2) words.add(minOf(1, words.size), ADD + typed)
        return Result(words.take(3), fix)
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
class UserWords(private val file: File, private val dictFile: File? = null) {
    private val counts = HashMap<String, Int>()
    private var dirty = false
    /** Words the user added on purpose (the app's form or the ＋ chip), as written; kept when learning is off. */
    private val added = LinkedHashMap<String, String>()

    /** Reads the user's dictionary (the app can change it too). */
    @Synchronized fun loadDictionary() {
        added.clear()
        val f = dictFile ?: return
        if (!f.exists()) return
        f.forEachLine(Charsets.UTF_8) { line ->
            val w = line.trim()
            if (w.isNotEmpty()) added[Dictionary.normalize(w)] = w
        }
    }

    /** Adds [word] to the user's dictionary; false if it is not a word. */
    @Synchronized fun add(word: String): Boolean {
        val w = word.trim()
        val key = Dictionary.normalize(w)
        if (!ANY_WORD.matches(key)) return false
        if (added.put(key, w) == null) {
            try {
                dictFile?.appendText(w + "\n", Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        return true
    }

    @Synchronized fun isAdded(key: String): Boolean = added.containsKey(key)

    /** The user's dictionary, as written, in the order added. */
    @Synchronized fun dictionary(): List<String> = added.values.toList()

    /** Removes [word] from the user's dictionary (and forgets it was learned). */
    @Synchronized fun remove(word: String) {
        val key = Dictionary.normalize(word.trim())
        if (added.remove(key) == null) return
        try {
            dictFile?.writeText(added.values.joinToString("") { it + "\n" }, Charsets.UTF_8)
        } catch (_: Exception) {
        }
        if (counts.remove(key) != null) {
            dirty = true
            save()
        }
    }

    @Synchronized fun load() {
        counts.clear()
        if (!file.exists()) return
        file.forEachLine(Charsets.UTF_8) { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) line.substring(tab + 1).toIntOrNull()?.let { counts[line.substring(0, tab)] = it }
        }
    }

    @Synchronized fun knows(key: String): Boolean = added.containsKey(key) || (counts[key] ?: 0) >= 2

    @Synchronized fun learn(word: String, by: Int = 1) {
        val key = Dictionary.normalize(word)
        if (!VALID.matches(key)) return
        counts[key] = (counts[key] ?: 0) + by
        dirty = true
        if (counts.size > MAX) {
            counts.entries.sortedBy { it.value }.take(counts.size - MAX).forEach { counts.remove(it.key) }
        }
    }

    @Synchronized fun completions(prefix: String, limit: Int): List<String> {
        val own = added.entries.filter { it.key != prefix && it.key.startsWith(prefix) }.map { it.value }
        val learned = counts.entries.filter { it.value >= 2 && it.key != prefix && it.key.startsWith(prefix) }
            .sortedByDescending { it.value }.map { it.key }
        return (own + learned).distinct().take(limit)
    }

    @Synchronized fun save() {
        if (!dirty) return
        dirty = false
        try {
            file.writeText(counts.entries.joinToString("") { "${it.key}\t${it.value}\n" }, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    @Synchronized fun clear() {
        counts.clear()
        dirty = false
        file.delete()
    }

    companion object {
        const val FILE = "user_words.txt"
        /** The user's own dictionary: one word per line, edited by the keyboard and the app. */
        const val DICT_FILE = "user_dict.txt"
        private val ANY_WORD = Regex("[\\p{L}]{2,30}(?:'[\\p{L}]+)*")
        private const val MAX = 3000
        private val VALID = Regex("[a-zöğşçñ]{2,24}(?:'[a-zöğşçñ]+)?")
    }
}
