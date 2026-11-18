package uz.harfix.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SuggestTest {
    companion object {
        /** The real dictionary from assets (the tests run in android/app). */
        val dict: Dictionary by lazy { File("src/main/assets/dict/uz.txt").inputStream().use { Dictionary.load(it) } }
    }

    @Test
    fun dictionaryLoads() {
        assertTrue(dict.size > 50_000)
        assertNotNull(dict.lookup("bilan"))
        assertEquals("Toşkent", dict.lookup("toşkent")?.word) // names keep their capital
        assertNotNull(dict.lookup("TOŞKENT"))
        assertTrue(dict.completions("toşk", 5).any { it.word == "Toşkent" })
        assertTrue(dict.completions("bil", 5).first().key.startsWith("bil"))
    }

    @Test
    fun typingDistance() {
        assertEquals(0f, Typing.distance("bilan", "bilan", 2f))
        assertEquals(0.25f, Typing.distance("ucun", "uçun", 2f)) // forgot the new letter
        assertEquals(0.6f, Typing.distance("bilan", "bilam", 2f)) // n and m are neighbours
        assertEquals(1f, Typing.distance("bilan", "bilaq", 2f))
        assertEquals(0.6f, Typing.distance("bilna", "bilan", 2f)) // swapped letters
        assertTrue(Typing.near('s').containsAll(listOf('ş', 'a', 'd')))
    }

    @Test
    fun autocorrectsTyposItIsSureAbout() {
        assertEquals("uçun", Suggest.forWord(dict, null, "ucun").autocorrect)
        assertEquals("boşqa", Suggest.forWord(dict, null, "bosqa").autocorrect)
        assertEquals("yaxşi", Suggest.forWord(dict, null, "yaxsi").autocorrect)
        assertEquals("qişloq", Suggest.forWord(dict, null, "qisloq").autocorrect)
        assertEquals("Yaxşi", Suggest.forWord(dict, null, "Yaxsi").autocorrect)
        assertEquals("UÇUN", Suggest.forWord(dict, null, "UCUN").autocorrect)
    }

    @Test
    fun leavesKnownAndUnsureWordsAlone() {
        val known = Suggest.forWord(dict, null, "bilan")
        assertNull(known.autocorrect)
        assertEquals("bilan", known.words.first())
        assertNull(Suggest.forWord(dict, null, "xyzqw").autocorrect) // nothing close
        assertNull(Suggest.forWord(dict, null, "a").autocorrect) // too short to judge
        assertEquals(Suggest.EMPTY, Suggest.forWord(dict, null, "abc123"))
        // a lower-case word is not turned into a name
        assertNull(Suggest.forWord(dict, null, "toşkemt").autocorrect)
    }

    @Test
    fun showsTheTypedWordNextToTheCorrection() {
        val r = Suggest.forWord(dict, null, "ucun")
        assertEquals("uçun", r.words.first())
        assertTrue(r.words.size <= 3)
    }

    @Test
    fun learnedWordsAreNotCorrected() {
        val file = Files.createTempFile("words", ".txt").toFile()
        val user = UserWords(file)
        assertNotNull(Suggest.forWord(dict, user, "ucun").autocorrect)
        user.learn("ucun")
        assertFalse(user.knows("ucun"))
        user.learn("ucun")
        assertTrue(user.knows("ucun"))
        assertNull(Suggest.forWord(dict, user, "ucun").autocorrect)
        user.save()
        val again = UserWords(file)
        again.load()
        assertTrue(again.knows("ucun"))
        again.clear()
        assertFalse(file.exists())
    }

    @Test
    fun wordBeforeTheCursor() {
        assertEquals("dun", Suggest.wordBefore("salom dun"))
        assertEquals("ma'no", Suggest.wordBefore("bu ma'no"))
        assertEquals("", Suggest.wordBefore("salom "))
        assertEquals("yaxşi", Suggest.wordBefore("«yaxşi"))
    }
}
