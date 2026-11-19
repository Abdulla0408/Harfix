package uz.harfix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordsLogicTest {
    /** Types [c] at [cursor] in [before], like a keyboard does, and runs the logic. */
    private fun type(before: String, cursor: Int, c: Char, live: Boolean = true, last: WordsLogic.Undo? = null) =
        WordsLogic.onChange(before, before.substring(0, cursor) + c + before.substring(cursor), cursor + 1, 1, 0, live, last)

    private fun backspace(before: String, cursor: Int, last: WordsLogic.Undo?) =
        WordsLogic.onChange(before, before.removeRange(cursor - 1, cursor), cursor - 1, 0, 1, true, last)

    @Test
    fun swapsPairsWhileTyping() {
        val e = type("Tos", 3, 'h')!!
        assertEquals("Toş", e.text)
        assertEquals(3, e.cursor)
        assertEquals("Öz", type("Oz", 1, '\'')!!.text) // capital O
        assertEquals("ğalaba", type("galaba", 1, 'ʻ')!!.text)
    }

    @Test
    fun lateApostropheAfterMovingTheCursorBack() {
        // "toliq" was typed without the mark; the cursor goes back behind the o and ' is typed
        val e = type("toliq", 2, '\'')!!
        assertEquals("töliq", e.text)
        assertEquals(2, e.cursor)
        // same for sh typed later inside a word
        assertEquals("işla", type("isla", 2, 'h')!!.text)
    }

    @Test
    fun backspaceRightAfterBringsTheLettersBack() {
        val e = type("Tos", 3, 'h')!!
        val back = backspace(e.text, e.cursor, e.undo)!!
        assertEquals("Tosh", back.text)
        assertEquals(4, back.cursor)
        assertNull(back.undo)
        // an ordinary Backspace later is left alone
        assertNull(backspace("Toş", 3, null))
    }

    @Test
    fun wordModeConvertsWhenTheWordEnds() {
        val e = type("Men toshkentlik", 15, ' ', live = false)!!
        assertEquals("Men toşkentlik ", e.text)
        assertEquals(15, e.cursor)
        assertNull(type("Men tosh", 7, 'k', live = false)) // still inside the word
        val back = backspace(e.text, e.cursor, e.undo)!!
        assertEquals("Men toshkentlik", back.text)
    }

    @Test
    fun leavesOtherTextAlone() {
        assertNull(type("https://bas", 11, 'h'))
        assertNull(type("ali@mos", 7, 'h'))
        assertNull(type("Тош", 3, ' ', live = false)) // Cyrillic
        assertNull(type("abc", 3, 'x'))
        // pasted or autocorrected text (more than one character) is not typing
        assertNull(WordsLogic.onChange("to", "tosh", 4, 2, 0, true, null))
    }

    @Test
    fun keyboardsThatReplaceTheComposingWord() {
        // Gboard replaces the word being composed ("tos" -> "tosh"): counts say 3 removed,
        // 4 added, but the texts show one letter typed at the cursor
        val e = WordsLogic.onChange("Men tos", "Men tosh", 8, 4, 3, true, null)!!
        assertEquals("Men toş", e.text)
    }
}
