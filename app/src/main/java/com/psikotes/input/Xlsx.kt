package com.psikotes.input

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** row (1-based) -> col (0-based) -> teks yang tampil. */
class SheetData(val sheetName: String, val cells: Map<Int, Map<Int, String>>)

/** Pembaca .xlsx sederhana (tanpa library), cukup untuk membaca nilai sel. */
object XlsxReader {

    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun read(bytes: ByteArray, preferredSheet: String = "INPUT"): SheetData {
        val entries = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (!e.isDirectory) entries[e.name.trimStart('/')] = zis.readBytes()
                }
            }
        } catch (e: Exception) {
            throw AppException("File bukan .xlsx yang valid.")
        }
        val wbBytes = entries["xl/workbook.xml"] ?: throw AppException("File bukan .xlsx yang valid.")

        // Daftar sheet + relasi ke file XML-nya
        val wb = parse(wbBytes)
        val sheets = all(wb, "sheet").map { el ->
            var rid = el.getAttributeNS(REL_NS, "id")
            if (rid.isNullOrEmpty()) rid = attrByLocalName(el, "id")
            el.getAttribute("name") to rid
        }
        if (sheets.isEmpty()) throw AppException("Tidak ada sheet di file ini.")
        val rels = HashMap<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { rb ->
            all(parse(rb), "Relationship").forEach { rels[it.getAttribute("Id")] = it.getAttribute("Target") }
        }
        val chosen = sheets.firstOrNull { it.first.equals(preferredSheet, ignoreCase = true) } ?: sheets.first()
        val target = rels[chosen.second] ?: "worksheets/sheet1.xml"
        val path = normalize(if (target.startsWith("/")) target.drop(1) else "xl/$target")
        val sheetBytes = entries[path] ?: throw AppException("Sheet \"${chosen.first}\" tidak ditemukan di file.")

        val shared = entries["xl/sharedStrings.xml"]?.let { sharedStrings(parse(it)) } ?: emptyList()
        val dateStyles = entries["xl/styles.xml"]?.let { dateStyleIndexes(parse(it)) } ?: emptySet()

        val cells = HashMap<Int, HashMap<Int, String>>()
        val sheet = parse(sheetBytes)
        var rowCounter = 0
        for (rowEl in all(sheet, "row")) {
            val r = rowEl.getAttribute("r").toIntOrNull() ?: (rowCounter + 1)
            rowCounter = r
            var colCounter = -1
            for (c in children(rowEl, "c")) {
                val ref = c.getAttribute("r")
                val col = if (ref.isNullOrEmpty()) colCounter + 1 else colIndex(ref)
                colCounter = col
                val t = c.getAttribute("t")
                val v = children(c, "v").firstOrNull()?.textContent
                val text = when (t) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> children(c, "is").firstOrNull()?.let { textOf(it) } ?: ""
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    "str", "e" -> v ?: ""
                    else -> {
                        if (v.isNullOrEmpty()) ""
                        else {
                            val style = c.getAttribute("s").toIntOrNull() ?: 0
                            formatNumber(v, style in dateStyles)
                        }
                    }
                }
                if (text.isNotEmpty()) cells.getOrPut(r) { HashMap() }[col] = text
            }
        }
        return SheetData(chosen.first, cells)
    }

    private fun formatNumber(v: String, isDate: Boolean): String {
        val d = v.toDoubleOrNull() ?: return v
        if (isDate && d > 0 && d < 2958466) {
            return LocalDate.of(1899, 12, 30).plusDays(d.toLong()).format(DATE_FMT)
        }
        return try {
            BigDecimal(v).stripTrailingZeros().toPlainString()
        } catch (e: Exception) {
            v
        }
    }

    private fun sharedStrings(doc: Document): List<String> =
        all(doc, "si").map { textOf(it) }

    /** Gabungkan semua <t> di dalam elemen, kecuali teks fonetik (<rPh>). */
    private fun textOf(el: Element): String {
        val sb = StringBuilder()
        val ts = el.getElementsByTagNameNS("*", "t")
        for (i in 0 until ts.length) {
            val t = ts.item(i)
            if (local(t.parentNode) != "rPh") sb.append(t.textContent)
        }
        return sb.toString()
    }

    private fun dateStyleIndexes(doc: Document): Set<Int> {
        val customDate = HashSet<Int>()
        all(doc, "numFmt").forEach { nf ->
            val id = nf.getAttribute("numFmtId").toIntOrNull() ?: return@forEach
            val code = nf.getAttribute("formatCode")
                .replace(Regex("\"[^\"]*\""), "")
                .replace(Regex("\\[[^\\]]*\\]"), "")
                .lowercase()
            if (code.contains('d') || code.contains('y') || (code.contains('m') && !code.contains("0"))) customDate.add(id)
        }
        val result = HashSet<Int>()
        val cellXfs = all(doc, "cellXfs").firstOrNull() ?: return result
        children(cellXfs, "xf").forEachIndexed { idx, xf ->
            val id = xf.getAttribute("numFmtId").toIntOrNull() ?: 0
            if (id in 14..22 || id in 45..47 || id in customDate) result.add(idx)
        }
        return result
    }

    // ---- helper DOM ----
    private fun parse(bytes: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun local(n: Node?): String? = n?.localName ?: n?.nodeName?.substringAfter(':')

    private fun all(doc: Document, name: String): List<Element> {
        val nl = doc.getElementsByTagNameNS("*", name)
        return (0 until nl.length).map { nl.item(it) as Element }
    }

    private fun children(el: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nl = el.childNodes
        for (i in 0 until nl.length) {
            val n = nl.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && local(n) == name) out.add(n as Element)
        }
        return out
    }

    private fun attrByLocalName(el: Element, name: String): String {
        val attrs = el.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (local(a) == name) return a.nodeValue
        }
        return ""
    }

    private fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (p in path.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }
}

/** Penulis .xlsx dengan susunan sama seperti file asli (data mulai baris 6, kolom A..AE). */
object XlsxWriter {

    private val IST_SUB = listOf("Sub Tes 1", "Sub Tes 2", "Sub Tes 3", "Sub Tes 4", "Sub Tes 5", "Sub Tes 6", "Sub Tes 7", "Sub Tes 8", "Sub Tes 9")

    fun write(out: OutputStream, scan: ScanResult, values: Map<Int, List<String>>) {
        val rows = sortedMapOf<Int, MutableList<String>>() // xml <c> per baris
        fun put(r: Int, c: Int, value: String, style: Int = 0, numeric: Boolean = false) {
            if (value.isEmpty() && style == 0) return
            val ref = colName(c) + r
            val s = if (style != 0) " s=\"$style\"" else ""
            val xml = when {
                value.isEmpty() -> "<c r=\"$ref\"$s/>"
                numeric && value.toDoubleOrNull() != null -> "<c r=\"$ref\"$s><v>$value</v></c>"
                else -> "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>"
            }
            rows.getOrPut(r) { ArrayList() }.add(xml)
        }

        // Info
        put(1, 0, "Sekolah"); put(1, 1, scan.sekolah)
        put(2, 0, "Ruang"); put(2, 1, scan.ruang)
        put(2, 2, "Salin E$FIRST_ROW:AE${scan.lastRow} lalu tempel ke sel E$FIRST_ROW di file asli")
        put(3, 0, "Tgl Tes"); put(3, 1, scan.tglTes)

        // Header baris 4-5
        val h = 1
        put(4, 0, "NO", h); put(4, 1, "NAMA SISWA", h); put(4, 2, "JENIS KELAMIN", h); put(4, 3, "TANGGAL LAHIR", h)
        IST_SUB.forEachIndexed { i, s -> put(4, FIRST_VALUE_COL + i, s, h) }
        put(4, FIRST_VALUE_COL + IDX_DISC, "DISC", h)
        for (i in 0 until 16) put(4, FIRST_VALUE_COL + IDX_CC + i, if (i == 0) "CAREER CLUSTER" else "", h)
        put(4, FIRST_VALUE_COL + IDX_LS, "LEARNING STYLE", h)
        for (c in 0..3) put(5, c, "", h)
        IST_LABELS.forEachIndexed { i, s -> put(5, FIRST_VALUE_COL + i, s, h) }
        put(5, FIRST_VALUE_COL + IDX_DISC, "", h)
        for (i in 0 until 16) put(5, FIRST_VALUE_COL + IDX_CC + i, (i + 1).toString(), h, numeric = true)
        put(5, FIRST_VALUE_COL + IDX_LS, "", h)

        // Data: baris sama persis dengan file asli
        val b = 2
        val mid = 3 // rata tengah, seperti template asli
        for (s in scan.students) {
            put(s.row, 0, s.no, mid, numeric = true)
            put(s.row, 1, s.nama, b)
            put(s.row, 2, s.jk, b)
            put(s.row, 3, s.tgl, b)
            val v = values[s.row] ?: s.fileValues
            for (i in 0 until FIELD_COUNT) {
                val text = v.getOrElse(i) { "" }
                put(s.row, FIRST_VALUE_COL + i, text, mid, numeric = i != IDX_DISC && i != IDX_LS)
            }
        }

        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<sheetViews><sheetView workbookViewId="0"><pane xSplit="2" ySplit="5" topLeftCell="C6" activePane="bottomRight" state="frozen"/></sheetView></sheetViews>""")
            append("""<cols><col min="1" max="1" width="5" customWidth="1"/><col min="2" max="2" width="30" customWidth="1"/>""")
            append("""<col min="3" max="4" width="15" customWidth="1"/><col min="5" max="13" width="8" customWidth="1"/>""")
            append("""<col min="14" max="14" width="9" customWidth="1"/><col min="15" max="30" width="5" customWidth="1"/><col min="31" max="31" width="12" customWidth="1"/></cols>""")
            append("<sheetData>")
            for ((r, cs) in rows) append("<row r=\"$r\">").append(cs.joinToString("")).append("</row>")
            append("</sheetData>")
            append("""<mergeCells count="1"><mergeCell ref="O4:AD4"/></mergeCells>""")
            append("</worksheet>")
        }

        ZipOutputStream(out).use { z ->
            fun entry(name: String, content: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", WORKBOOK)
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry("xl/styles.xml", STYLES)
            entry("xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun esc(s: String) = buildString {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> {}
                else -> append(ch)
            }
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="INPUT" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
