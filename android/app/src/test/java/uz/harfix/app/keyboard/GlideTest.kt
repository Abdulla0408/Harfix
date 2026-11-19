package uz.harfix.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GlideTest {
    private val keyWidth = 100f

    /** Key centres of the Latin rows, like the keyboard view (rows 150 px high). */
    private val centers: Map<Char, Glide.P> = HashMap<Char, Glide.P>().apply {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { y, (row, offset) ->
            row.forEachIndexed { x, c -> put(c, Glide.P((x + offset) * keyWidth + keyWidth / 2, y * 150f + 75f)) }
        }
    }

    /** A finger drawing through the keys of [letters], a little off-centre, with points in between. */
    private fun drawn(letters: String, seed: Int = 1): List<Glide.P> {
        val rnd = Random(seed)
        val stops = letters.map { centers.getValue(it) }.map {
            Glide.P(it.x + rnd.nextFloat() * 30f - 15f, it.y + rnd.nextFloat() * 30f - 15f)
        }
        val out = ArrayList<Glide.P>()
        for (i in 1 until stops.size) {
            val a = stops[i - 1]
            val b = stops[i]
            for (s in 0 until 8) {
                val t = s / 8f
                out.add(Glide.P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        out.add(stops.last())
        return out
    }

    private fun decode(letters: String) = Glide.decode(drawn(letters), centers, keyWidth, SuggestTest.dict).map { it.word }

    @Test
    fun findsCommonWords() {
        assertEquals("bilan", decode("bilan").first())
        assertEquals("kitob", decode("kitob").first())
        assertEquals("maktab", decode("maktab").first())
    }

    @Test
    fun plainLettersGiveTheNewOnes() {
        // t-o-s-k-e-n-t on the keys: the dictionary word is «Toşkent»
        assertEquals("Toşkent", decode("toskent").first())
        assertEquals("yaxşi", decode("yaxsi").first())
    }

    @Test
    fun resampleKeepsTheEnds() {
        val pts = listOf(Glide.P(0f, 0f), Glide.P(10f, 0f), Glide.P(10f, 10f))
        val r = Glide.resample(pts, 5)
        assertEquals(5, r.size)
        assertEquals(0f, r.first().x)
        assertEquals(10f, r.last().y, 0.01f)
        assertTrue(Glide.decode(listOf(Glide.P(0f, 0f)), centers, keyWidth, SuggestTest.dict).isEmpty())
    }
}
