package uz.harfix.app

import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Converts Word / Excel / PowerPoint / txt files (same rules as the Telegram bot's
 * bot/harfix_bot/documents.py). Only the text inside text nodes changes; fonts, styles,
 * images, formulas and every other part of the file are copied as they are.
 */
object Office {
    class Unsupported(what: String, cause: Throwable? = null) : Exception(what, cause)

    val SUPPORTED = setOf("docx", "docm", "xlsx", "xlsm", "pptx", "pptm", "txt")

    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** text element -> the paragraph elements that group its runs */
    private val TEXT_TO_PARA: Map<Pair<String, String>, Set<Pair<String, String>>> = mapOf(
        (W to "t") to setOf(W to "p"),
        (A to "t") to setOf(A to "p"),
        (S to "t") to setOf(S to "si", S to "is"),
    )

    /** Which parts of each format hold user text. */
    private val PARTS = mapOf(
        "docx" to Regex("^word/(document|header\\d*|footer\\d*|footnotes|endnotes|comments|diagrams/data\\d*)\\.xml$"),
        "pptx" to Regex("^ppt/(slides/slide\\d+|notesSlides/notesSlide\\d+|diagrams/data\\d+)\\.xml$"),
        "xlsx" to Regex("^xl/(sharedStrings|worksheets/sheet\\d+)\\.xml$"),
    )
    private val FAMILY = mapOf(
        "docx" to "docx", "docm" to "docx", "pptx" to "pptx", "pptm" to "pptx", "xlsx" to "xlsx", "xlsm" to "xlsx",
    )

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** "Hisobot.docx" -> the same name in the target alphabet, extension kept. */
    fun outputName(name: String, target: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) Converter.convert(name.substring(0, dot), target) + name.substring(dot)
        else Converter.convert(name, target) + ".txt"
    }

    fun convert(data: ByteArray, name: String, target: String): ByteArray {
        val ext = ext(name)
        if (ext !in SUPPORTED) throw Unsupported(ext)
        if (ext == "txt") return convertText(data, target)
        try {
            return convertOffice(data, ext, target)
        } catch (e: Unsupported) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw Unsupported(ext, e)
        } catch (e: org.xml.sax.SAXException) {
            throw Unsupported(ext, e)
        } catch (e: javax.xml.parsers.ParserConfigurationException) {
            throw Unsupported(ext, e)
        } catch (e: javax.xml.transform.TransformerException) {
            throw Unsupported(ext, e)
        }
    }

    private fun convertText(data: ByteArray, target: String): ByteArray {
        val noBom = if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte())
            data.copyOfRange(3, data.size) else data
        val text = decode(Charsets.UTF_8, noBom)
            ?: decode(Charset.forName("windows-1251"), data)
            ?: throw Unsupported("encoding")
        return Converter.convert(text, target).toByteArray(Charsets.UTF_8)
    }

    private fun decode(cs: Charset, data: ByteArray): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun convertOffice(data: ByteArray, ext: String, target: String): ByteArray {
        val parts = PARTS.getValue(FAMILY.getValue(ext))
        val out = ByteArrayOutputStream(data.size + 1024)
        var entries = 0
        ZipInputStream(ByteArrayInputStream(data)).use { zin ->
            ZipOutputStream(out).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    entries++
                    var content = zin.readBytes()
                    if (!e.isDirectory && parts.matches(e.name)) content = convertXml(content, target)
                    val ne = ZipEntry(e.name)
                    if (e.time != -1L) ne.time = e.time
                    if (e.method == ZipEntry.STORED) {
                        ne.method = ZipEntry.STORED
                        ne.size = content.size.toLong()
                        ne.compressedSize = content.size.toLong()
                        ne.crc = CRC32().apply { update(content) }.value
                    }
                    zout.putNextEntry(ne)
                    zout.write(content)
                    zout.closeEntry()
                }
            }
        }
        if (entries == 0) throw Unsupported("$ext: not a zip")
        return out.toByteArray()
    }

    private fun key(n: Node): Pair<String, String>? {
        val ns = n.namespaceURI ?: return null
        val local = n.localName ?: n.nodeName
        return ns to local
    }

    internal fun convertXml(data: ByteArray, target: String): ByteArray {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(data))

        // text elements grouped by their paragraph, in document order
        val groups = LinkedHashMap<Node, MutableList<Node>>()
        val stack = ArrayDeque<Node>()
        stack.addLast(doc.documentElement)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.nodeType == Node.ELEMENT_NODE) {
                val paras = key(n)?.let { TEXT_TO_PARA[it] }
                if (paras != null) {
                    var p: Node? = n.parentNode
                    while (p != null && key(p) !in paras) p = p.parentNode
                    groups.getOrPut(p ?: n) { ArrayList() }.add(n)
                }
            }
            var c = n.lastChild
            while (c != null) {
                stack.addLast(c)
                c = c.previousSibling
            }
        }

        var changed = false
        for (els in groups.values) {
            val old = els.map { it.textContent ?: "" }
            val new = Converter.convertRuns(old, target)
            for (i in els.indices) {
                if (old[i] != new[i]) {
                    els[i].textContent = new[i]
                    changed = true
                }
            }
        }
        if (!changed) return data

        val head = String(data, 0, minOf(200, data.size), Charsets.UTF_8)
        val standalone = head.contains("standalone=\"yes\"")
        if (standalone) doc.xmlStandalone = true
        val tr = TransformerFactory.newInstance().newTransformer()
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        tr.setOutputProperty(OutputKeys.INDENT, "no")
        if (standalone) tr.setOutputProperty(OutputKeys.STANDALONE, "yes")
        val out = ByteArrayOutputStream(data.size + 256)
        tr.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }
}
