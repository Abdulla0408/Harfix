package uz.harfix.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConverterTest {

    /** The shared test set every port must pass (core/tests/cases.json in the Harfix repo). */
    @Test
    fun sharedCases() {
        val f = listOf(File("../../core/tests/cases.json"), File("../core/tests/cases.json")).first { it.exists() }
        val cases = JSONArray(f.readText())
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONArray(i)
            assertEquals("case ${c.getString(0)}", c.getString(1), Converter.convert(c.getString(0), "new"))
        }
    }

    /** Same output as harfix.js (golden.json is made by node from assets/ui/harfix.js). */
    @Test
    fun sameAsHarfixJs() {
        val text = javaClass.classLoader!!.getResource("golden.json").readText()
        val cases = JSONArray(text)
        assertTrue(cases.length() > 3000)
        var failed = 0
        val report = StringBuilder()
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val input = c.getString("in")
            for (target in Converter.TARGETS) {
                val got = Converter.convert(input, target)
                val exp = c.getString(target)
                if (got != exp) {
                    failed++
                    if (failed <= 20) report.append("[$target] ${input.debug()} expected ${exp.debug()} got ${got.debug()}\n")
                }
            }
        }
        assertEquals(report.toString(), 0, failed)
    }

    @Test
    fun runsKeepPairsTogether() {
        assertEquals(listOf("ş", "ahar"), Converter.convertRuns(listOf("s", "hahar"), "new"))
        assertEquals(listOf("Ö", "zbekiston"), Converter.convertRuns(listOf("O", "ʻzbekiston"), "new"))
        assertEquals(listOf("я", "хши"), Converter.convertRuns(listOf("y", "axshi"), "cyr"))
        assertEquals(listOf("Ye", "r"), Converter.convertRuns(listOf("Е", "р"), "new"))
        assertEquals(listOf("", "abc"), Converter.convertRuns(listOf("", "abc"), "new"))
    }

    @Test
    fun uiText() {
        assertEquals("Harfix eʼlonlari", t("Harfix eʼlonlari"))
        assertEquals("«Odatiy klaviaturada» öçib qolsa", t("«Odatiy klaviaturada» oʻchib qolsa"))
    }

    private fun String.debug() = buildString {
        append('"')
        for (ch in this@debug) if (ch.code in 32..126) append(ch) else append("\\u%04x".format(ch.code))
        append('"')
    }
}
