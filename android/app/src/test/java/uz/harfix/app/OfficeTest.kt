package uz.harfix.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class OfficeTest {
    private val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>S</w:t></w:r><w:r><w:t xml:space="preserve">hahar va oʻzbek </w:t></w:r><w:r><w:t>tili</w:t></w:r></w:p>
<w:p><w:r><w:t>Ўзбекистон</w:t></w:r></w:p>
<w:p><w:r><w:t>https://o'zbek.uz</w:t></w:r></w:p>
</w:body></w:document>"""

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun unzip(data: ByteArray): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(data)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                m[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        return m
    }

    private fun texts(xml: String): List<String> =
        Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(xml).map { it.groupValues[1] }.toList()

    @Test
    fun docxKeepsRunsAndFormatting() {
        val src = zip("[Content_Types].xml" to "<Types/>", "word/document.xml" to docXml, "word/styles.xml" to "<w:t>shahar</w:t>")
        val out = unzip(Office.convert(src, "Hisobot.docx", "new"))
        assertEquals(listOf("[Content_Types].xml", "word/document.xml", "word/styles.xml"), out.keys.toList())
        val doc = out.getValue("word/document.xml")
        assertEquals(listOf("Ş", "ahar va özbek ", "tili", "Özbekiston", "https://o'zbek.uz"), texts(doc))
        assertTrue(doc.contains("<w:b/>"))
        assertTrue(doc.contains("standalone=\"yes\""))
        // parts that hold no user text are copied as they are
        assertEquals("<w:t>shahar</w:t>", out.getValue("word/styles.xml"))
    }

    @Test
    fun unchangedXmlIsCopiedByteForByte() {
        val xml = docXml.replace("S</w:t>", "X</w:t>").replace("oʻzbek", "uzbek").replace("Ўзбекистон", "Uzbek")
            .replace("hahar", "abc")
        val src = zip("word/document.xml" to xml)
        assertEquals(xml, unzip(Office.convert(src, "a.docx", "new")).getValue("word/document.xml"))
    }

    @Test
    fun excelSharedStringsToCyrillic() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><r><t>y</t></r><r><t>axshi</t></r></si><si><t>shahar</t></si></sst>"""
        val out = unzip(Office.convert(zip("xl/sharedStrings.xml" to xml), "J.xlsx", "cyr")).getValue("xl/sharedStrings.xml")
        val t = Regex("<t>([^<]*)</t>").findAll(out).map { it.groupValues[1] }.toList()
        assertEquals(listOf("я", "хши", "шаҳар"), t)
    }

    @Test
    fun textFiles() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "oʻzbek".toByteArray()
        assertArrayEquals("özbek".toByteArray(), Office.convert(bom, "a.txt", "new"))
        val cp1251 = "Ўзбек".toByteArray(Charset.forName("windows-1251"))
        assertEquals("Özbek", String(Office.convert(cp1251, "a.TXT", "new"), Charsets.UTF_8))
    }

    @Test(expected = Office.Unsupported::class)
    fun unsupported() {
        Office.convert(ByteArray(3), "rasm.jpg", "new")
    }

    @Test(expected = Office.Unsupported::class)
    fun notAZip() {
        Office.convert("hello".toByteArray(), "a.docx", "new")
    }

    @Test
    fun names() {
        assertEquals("Özbek tili.docx", Office.outputName("Oʻzbek tili.docx", "new"))
        assertEquals("Шаҳар.xlsx", Office.outputName("Shahar.xlsx", "cyr"))
        assertEquals("docx", Office.ext("A.DOCX"))
        assertEquals("", Office.ext("README"))
    }
}
