package uz.harfix.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTest {
    @Test
    fun pairs() {
        assertEquals(Swap(1, "ö"), Live.swap("Men o", '\''))
        assertEquals(Swap(1, "ö"), Live.swap("o", 'ʻ'))
        assertEquals(Swap(1, "Ğ"), Live.swap("G", '\''))
        assertEquals(Swap(1, "ş"), Live.swap("s", 'h'))
        assertEquals(Swap(1, "Ç"), Live.swap("C", 'H'))
        assertNull(Live.swap("a", '\''))
        assertNull(Live.swap("", 'h'))
    }

    @Test
    fun leavesLinksAlone() {
        assertNull(Live.swap("https://bas", 'h'))
        assertNull(Live.swap("www.cas", 'h'))
        assertNull(Live.swap("ali@mos", 'h'))
        assertNull(Live.swap("#tos", 'h'))
        assertNull(Live.swap("my_func", 'h'))
        assertNull(Live.swap("3s", 'h'))
        assertEquals(Swap(1, "ş"), Live.swap("https://a.uz bos", 'h'))
    }

    @Test
    fun undo() {
        val u = Live.Undo()
        u.pending = "sh" to "ş"
        assertEquals(Swap(1, "sh"), u.undo("bo ş"))
        assertNull(u.pending)
        u.pending = "sh" to "ş"
        assertNull(u.undo("something else"))
    }
}
