package com.psikotes.input

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxTest {

    private fun zip(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((k, v) in entries) {
                z.putNextEntry(ZipEntry(k)); z.write(v.toByteArray()); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** File mirip template asli: sharedStrings, tanggal berformat, sheet INPUT bukan sheet pertama. */
    private fun sampleXlsx(): ByteArray = zip(
        mapOf(
            "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet2" sheetId="2" r:id="rId2"/><sheet name="INPUT" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="/xl/worksheets/sheet1.xml"/><Relationship Id="rId2" Type="x" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Sekolah</t></si><si><t>SMAN 1 Batipuh</t></si><si><r><t>Adib </t></r><r><t>Zainul Muttaqin</t></r></si><si><t>Laki - Laki</t></si><si><t>C-S-D</t></si><si><t>Reflektor</t></si><si><t>AKBAR DWIYASRI</t></si></sst>""",
            "xl/styles.xml" to """<?xml version="1.0"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/></numFmts><cellXfs count="3"><xf numFmtId="0"/><xf numFmtId="165"/><xf numFmtId="14"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet2.xml" to """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="B2"><v>1</v></c></row>
                <row r="3"><c r="B3" s="2"><v>46349</v></c></row>
                <row r="6"><c r="A6"><v>1</v></c><c r="B6" t="s"><v>2</v></c><c r="C6" t="s"><v>3</v></c><c r="D6" s="1"><v>40367</v></c><c r="E6"><v>3</v></c><c r="F6"><v>5.0</v></c><c r="N6" t="s"><v>4</v></c><c r="O6"><v>7</v></c><c r="AD6"><v>10</v></c><c r="AE6" t="s"><v>5</v></c></row>
                <row r="7"><c r="A7"><v>2</v></c><c r="B7" t="s"><v>6</v></c></row>
                <row r="8"><c r="A8"><v>3</v></c></row>
                </sheetData></worksheet>""",
        )
    )

    @Test
    fun readsTemplate() {
        val r = XlsxReader.read(sampleXlsx()).toScanResult("k", "f.xlsx")
        assertEquals("SMAN 1 Batipuh", r.sekolah)
        assertEquals("1", r.ruang)
        assertEquals("23/11/2026", r.tglTes)
        assertEquals(2, r.students.size)
        val a = r.students[0]
        assertEquals(6, a.row)
        assertEquals("Adib Zainul Muttaqin", a.nama)
        assertEquals("08/07/2010", a.tgl)
        assertEquals("3", a.fileValues[0])
        assertEquals("5", a.fileValues[1])
        assertEquals("C-S-D", a.fileValues[IDX_DISC])
        assertEquals("7", a.fileValues[IDX_CC])
        assertEquals("10", a.fileValues[IDX_CC + 15])
        assertEquals("Reflektor", a.fileValues[IDX_LS])
        assertEquals(7, r.lastRow)
    }

    @Test
    fun writeThenReadRoundTrip() {
        val scan = XlsxReader.read(sampleXlsx()).toScanResult("k", "f.xlsx")
        val newVals = List(FIELD_COUNT) { i -> if (i == IDX_DISC) "D-I" else if (i == IDX_LS) "Teoris" else (i + 1).toString() }
        val out = ByteArrayOutputStream()
        XlsxWriter.write(out, scan, mapOf(7 to newVals))
        val back = XlsxReader.read(out.toByteArray()).toScanResult("k", "f.xlsx")
        assertEquals(listOf(6, 7), back.students.map { it.row })
        assertEquals("C-S-D", back.students[0].fileValues[IDX_DISC]) // tidak diubah -> nilai file
        assertEquals(newVals, back.students[1].fileValues)
        assertEquals("SMAN 1 Batipuh", back.sekolah)
    }

    @Test
    fun columnNames() {
        assertEquals("A", colName(0)); assertEquals("E", colName(4)); assertEquals("AE", colName(30))
        assertEquals(30, colIndex("AE12")); assertEquals(0, colIndex("A1"))
    }

    @Test
    fun learningStyleEnglish() {
        assertEquals("Reflector", normalizeLs("Reflektor"))
        assertEquals("Activist", normalizeLs("aktivis"))
        assertEquals("Theorist", normalizeLs("Teoris"))
        assertEquals("Pragmatist", normalizeLs("Pragmatis"))
        assertEquals("", normalizeLs(""))
    }
}
