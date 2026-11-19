package uz.harfix.app

import uz.harfix.app.keyboard.Live

/**
 * What "Odatiy klaviaturada" does with one change of a text field, as plain logic (tested
 * without Android). The service (WordsService, play flavor) reads the field, asks [onChange],
 * and writes back the [Edit] it gets.
 *
 * - Live ("Darhol" on): when the second letter of oʻ gʻ sh ch is typed, the pair becomes
 *   ö ğ ş ç. It looks at the text before the cursor, so an apostrophe typed later, after
 *   moving the cursor back behind the o, is converted too.
 * - Word mode ("Darhol" off): when a word ends (space or punctuation), the whole word is
 *   converted to the new alphabet.
 * - Backspace right after either change brings back what was typed.
 *
 * Cyrillic words, links, e-mails, @names, #tags and code-like words are left alone.
 */
object WordsLogic {
    /** The field gets [text], with the cursor at [cursor]. */
    data class Edit(val text: String, val cursor: Int, val undo: Undo?)

    /**
     * The last change, for Backspace: [became] was written at [at]; when Backspace removes its
     * last character, the rest of it is replaced by [restore].
     */
    data class Undo(val at: Int, val became: String, val restore: String)

    /** Bigger fields (documents) are left alone: the whole text is rewritten on every change. */
    const val MAX_LENGTH = 10_000

    /**
     * [before] is the field before the change (null if unknown), [text] after it, with the
     * cursor at [cursor] and no selection. [added] and [removed] are the event's counts, used
     * when [before] is unknown. [last] is the previous change this logic made in the field.
     */
    fun onChange(
        before: String?,
        text: String,
        cursor: Int,
        added: Int,
        removed: Int,
        live: Boolean,
        last: Undo?,
    ): Edit? {
        if (cursor < 0 || cursor > text.length || text.length > MAX_LENGTH) return null
        val typedOne = if (before != null) insertedOne(before, text, cursor) else added == 1 && removed == 0
        val deletedOne = if (before != null) deletedOne(before, text, cursor) else added == 0 && removed == 1

        if (deletedOne && last != null) return undo(text, cursor, last)
        if (!typedOne || cursor < 1) return null

        val typed = text[cursor - 1]
        val head = text.substring(0, cursor - 1)
        return if (live) swapPair(text, cursor, head, typed) else wordEnd(text, cursor, head, typed)
    }

    private fun swapPair(text: String, cursor: Int, head: String, typed: Char): Edit? {
        if (!Live.isTrigger(typed)) return null
        val swap = Live.swap(head, typed) ?: return null
        val at = head.length - swap.delete
        val out = text.substring(0, at) + swap.text + text.substring(cursor)
        return Edit(out, at + swap.text.length, Undo(at, swap.text, text.substring(at, cursor)))
    }

    private fun wordEnd(text: String, cursor: Int, head: String, typed: Char): Edit? {
        if (typed.isLetterOrDigit() || Converter.APOSTROPHES.indexOf(typed) >= 0 || typed == '-') return null
        val word = Live.lastWord(head)
        if (word.isEmpty() || Live.isLinkish(word) || word.any { it in 'Ѐ'..'ӿ' }) return null
        val converted = Converter.convert(word, "new")
        if (converted == word) return null
        val at = head.length - word.length
        val out = text.substring(0, at) + converted + text.substring(cursor - 1)
        return Edit(out, at + converted.length + 1, Undo(at, converted + typed, word))
    }

    /** Backspace removed the last character of [last]: put back what was typed. */
    private fun undo(text: String, cursor: Int, last: Undo): Edit? {
        val kept = last.became.dropLast(1)
        if (cursor != last.at + kept.length || !text.regionMatches(last.at, kept, 0, kept.length)) return null
        val out = text.substring(0, last.at) + last.restore + text.substring(cursor)
        return Edit(out, last.at + last.restore.length, null)
    }

    /** [text] is [before] with one character inserted just before [cursor]. */
    private fun insertedOne(before: String, text: String, cursor: Int): Boolean =
        text.length == before.length + 1 && cursor >= 1 &&
            text.regionMatches(0, before, 0, cursor - 1) &&
            text.regionMatches(cursor, before, cursor - 1, before.length - cursor + 1)

    /** [text] is [before] with the character at [cursor] removed (Backspace). */
    private fun deletedOne(before: String, text: String, cursor: Int): Boolean =
        text.length == before.length - 1 && cursor <= text.length &&
            text.regionMatches(0, before, 0, cursor) &&
            text.regionMatches(cursor, before, cursor + 1, text.length - cursor)
}
