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
        val dict: Dictionary by lazy {
            File("src/main/assets/dict/uz.txt").inputStream().use { main ->
                File("src/main/assets/dict/uz-extra.txt").inputStream().use { extra -> Dictionary.load(main, extra) }
            }
        }
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
        assertTrue(Typing.twins('s').containsAll(listOf('s', 'ş')))
    }

    @Test
    fun autocorrectsTyposItIsSureAbout() {
        assertEquals("uçun", Suggest.forWord(dict, null, "ucun").autocorrect)
        assertEquals("boşqa", Suggest.forWord(dict, null, "bosqa").autocorrect)
        assertEquals("yaxşi", Suggest.forWord(dict, null, "yaxsi").autocorrect)
        assertEquals("qişloq", Suggest.forWord(dict, null, "qisloq").autocorrect)
        assertEquals("Yaxşi", Suggest.forWord(dict, null, "Yaxsi").autocorrect)
        assertEquals("UÇUN", Suggest.forWord(dict, null, "UCUN").autocorrect)
        assertEquals("maktab", Suggest.forWord(dict, null, "maktap").autocorrect) // missing voicing
        assertEquals("odamlar", Suggest.forWord(dict, null, "odamalr").autocorrect) // swapped letters
        assertEquals("muhabbat", Suggest.forWord(dict, null, "muhabat").autocorrect) // missing letter
        assertEquals("bilan", Suggest.forWord(dict, null, "bilna").autocorrect)
        assertEquals("kitob", Suggest.forWord(dict, null, "kitab").autocorrect) // only a town is called Kitab
        assertEquals("Özbekiston", Suggest.forWord(dict, null, "ozbekiston").autocorrect) // names when very close
        assertEquals("Toşkent", Suggest.forWord(dict, null, "toskent").autocorrect)
    }

    @Test
    fun everydayWordsAndEndingsAreNotChanged() {
        // not in Wikipedia, but real: everyday words and verb forms
        for (w in listOf("sen", "salom", "rahmat", "yöq", "qalaysiz", "keçirasiz", "ketyapman", "tuşundim",
            "kelyapman", "yozyapman", "telefonim", "toşkentda", "bormaymiz")) {
            assertNull(w, Suggest.forWord(dict, null, w).autocorrect)
        }
    }

    @Test
    fun fastEnoughForTyping() {
        val words = listOf("ucun", "maktap", "odamalr", "ketyapman", "bilna", "kitoblr", "assalomu", "qişloq",
            "a", "bi", "bil", "bila", "toşke", "özbekistonda", "yozayotgan", "qanaqa")
        Suggest.forWord(dict, null, "warmup")
        val start = System.nanoTime()
        repeat(10) { for (w in words) Suggest.forWord(dict, null, w) }
        val perWord = (System.nanoTime() - start) / 1e6 / (10 * words.size)
        assertTrue("${perWord}ms per word", perWord < 15.0)
    }

    @Test
    fun leavesKnownAndUnsureWordsAlone() {
        val known = Suggest.forWord(dict, null, "bilan")
        assertNull(known.autocorrect)
        assertEquals("bilan", known.words.first())
        assertFalse(known.words.any { it.startsWith(Suggest.ADD) }) // nothing to add
        assertNull(Suggest.forWord(dict, null, "xyzqw").autocorrect) // nothing close
        assertNull(Suggest.forWord(dict, null, "a").autocorrect) // too short to judge
        assertEquals(Suggest.EMPTY, Suggest.forWord(dict, null, "abc123"))
        // a lower-case word is not turned into a name unless it is very close
        assertNull(Suggest.forWord(dict, null, "sen").autocorrect)
    }

    @Test
    fun showsTheTypedWordNextToTheCorrection() {
        val r = Suggest.forWord(dict, null, "ucun")
        assertEquals("uçun", r.words.first())
        assertEquals(Suggest.ADD + "ucun", r.words[1]) // keep it and add it to the dictionary
        assertTrue(r.words.size <= 3)
        assertTrue(Suggest.forWord(dict, null, "xyzqw").words.contains(Suggest.ADD + "xyzqw"))
    }

    @Test
    fun usersOwnDictionary() {
        val words = Files.createTempFile("words", ".txt").toFile()
        val own = Files.createTempFile("dict", ".txt").toFile()
        val user = UserWords(words, own)
        assertNotNull(Suggest.forWord(dict, user, "ucun").autocorrect)
        assertTrue(user.add("ucun"))
        assertTrue(user.add("Harfix"))
        assertFalse(user.add("abc123")) // not a word
        assertFalse(user.add("x"))
        assertTrue(user.knows("ucun")) // known at once, no need to type it twice
        assertNull(Suggest.forWord(dict, user, "ucun").autocorrect)
        assertFalse(Suggest.forWord(dict, user, "ucun").words.any { it.startsWith(Suggest.ADD) })
        assertTrue(Suggest.forWord(dict, user, "harf").words.contains("Harfix")) // as written
        val again = UserWords(words, own)
        again.loadDictionary()
        assertEquals(listOf("ucun", "Harfix"), again.dictionary())
        again.remove("UCUN")
        assertEquals(listOf("Harfix"), again.dictionary())
        val third = UserWords(words, own)
        third.loadDictionary()
        assertFalse(third.knows("ucun"))
        assertTrue(third.knows("harfix"))
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
