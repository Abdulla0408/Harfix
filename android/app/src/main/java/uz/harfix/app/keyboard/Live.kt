package uz.harfix.app.keyboard

import uz.harfix.app.Converter
import java.util.Locale

/** Replace the last [delete] characters before the cursor with [text]. */
data class Swap(val delete: Int, val text: String)

/**
 * Real-time conversion while typing: when the second letter of oʻ gʻ sh ch is typed, the
 * pair becomes ö ğ ş ç. Backspace right after it brings the two letters back ([Undo]).
 */
object Live {
    private val PAIRS = mapOf(
        ('o' to '\'') to "ö",
        ('g' to '\'') to "ğ",
        ('s' to 'h') to "ş",
        ('c' to 'h') to "ç",
    )

    /** Words that look like links, e-mails, tags, paths or code are left alone. */
    private val SKIP = listOf("@", "#", "/", "\\", ":", "www.", "=", "_")

    fun isTrigger(c: Char) = c == 'h' || c == 'H' || Converter.APOSTROPHES.indexOf(c) >= 0

    /** What to do when [typed] is typed after [before] (the text before the cursor), or null. */
    fun swap(before: CharSequence, typed: Char): Swap? {
        val last = before.lastOrNull() ?: return null
        val second = if (Converter.APOSTROPHES.indexOf(typed) >= 0) '\'' else typed.lowercaseChar()
        var rep = PAIRS[last.lowercaseChar() to second] ?: return null
        if (isLinkish(lastWord(before))) return null
        if (last.isUpperCase()) rep = rep.uppercase(Locale.ROOT)
        return Swap(1, rep)
    }

    /** The word that ends at the end of [before] (back to the last whitespace). */
    fun lastWord(before: CharSequence): String {
        var start = before.length
        while (start > 0 && !before[start - 1].isWhitespace()) start--
        return before.subSequence(start, before.length).toString()
    }

    /** A link, e-mail, @name, #tag, path or code-like word, or one that starts with a digit. */
    fun isLinkish(word: String): Boolean {
        val w = word.lowercase(Locale.ROOT)
        return SKIP.any { w.contains(it) } || (w.length > 1 && w[0].isDigit())
    }

    /** The last automatic swap, so Backspace can undo it. */
    class Undo {
        /** (what was typed, what it became), e.g. ("sh", "ş") */
        var pending: Pair<String, String>? = null

        /** Backspace: if the text still ends with the swap, put the typed letters back. */
        fun undo(before: CharSequence): Swap? {
            val (typed, became) = pending ?: return null
            pending = null
            return if (before.endsWith(became)) Swap(became.length, typed) else null
        }
    }
}
