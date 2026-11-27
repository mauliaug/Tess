package com.psikotes.input

import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val FIRST_ROW = 6          // data siswa mulai baris 6
const val FIRST_VALUE_COL = 4    // kolom E (0-based)
const val FIELD_COUNT = 27       // E..AE
const val IDX_DISC = 9           // kolom N
const val IDX_CC = 10            // kolom O (cluster 1)
const val IDX_LS = 26            // kolom AE

val IST_LABELS = listOf("SE", "WA", "AN", "GE", "RA", "ZR", "FA", "WU", "ME")

/** Urutan input di layar, mengikuti lembar hasil IST (SE WA AN GE ME RA ZR FA WU).
 *  Isinya indeks kolom di file (E..M), jadi susunan .xlsx tidak berubah. */
val IST_INPUT_ORDER = listOf(0, 1, 2, 3, 8, 4, 5, 6, 7)
val DISC_LETTERS = listOf("D", "I", "S", "C")
val LS_OPTIONS = listOf("Activist", "Reflector", "Theorist", "Pragmatist")

/** Nama lama (Indonesia) -> nama baru (Inggris). */
private val LS_ALIASES = mapOf(
    "aktivis" to "Activist", "activist" to "Activist",
    "reflektor" to "Reflector", "reflector" to "Reflector",
    "teoris" to "Theorist", "theorist" to "Theorist",
    "pragmatis" to "Pragmatist", "pragmatist" to "Pragmatist",
)

fun normalizeLs(v: String): String = LS_ALIASES[v.trim().lowercase()] ?: v

fun List<String>.withNormalizedLs(): List<String> =
    mapIndexed { i, x -> if (i == IDX_LS) normalizeLs(x) else x }

class AppException(msg: String) : Exception(msg)

data class Student(
    val row: Int,
    val no: String,
    val nama: String,
    val jk: String,
    val tgl: String,
    val fileValues: List<String>,
    /** true = siswa tambahan yang dibuat di aplikasi (belum ada di file). */
    val added: Boolean = false,
    val addedId: Long = 0,
)

/** Siswa tambahan yang disimpan di HP. */
data class NewStudent(val id: Long, val nama: String, val jk: String, val tgl: String)

val JK_OPTIONS = listOf("Laki - Laki", "Perempuan")

data class ScanResult(
    val key: String,
    val file: String,
    val sekolah: String,
    val ruang: String,
    val tglTes: String,
    val students: List<Student>,
) {
    val lastRow: Int get() = students.maxOfOrNull { it.row } ?: (FIRST_ROW - 1)
    /** Baris terakhir yang berasal dari file asli (tanpa siswa tambahan). */
    val fileLastRow: Int get() = students.filter { !it.added }.maxOfOrNull { it.row } ?: (FIRST_ROW - 1)
    val addedStudents: List<Student> get() = students.filter { it.added }
}

/** Ubah isi sheet (hasil XlsxReader) menjadi daftar siswa sesuai template. */
fun SheetData.toScanResult(key: String, file: String): ScanResult {
    fun cell(r: Int, c: Int) = cells[r]?.get(c)?.trim() ?: ""
    val students = cells.keys.filter { it >= FIRST_ROW }.sorted().mapNotNull { r ->
        val nama = cell(r, 1)
        if (nama.isEmpty()) null
        else Student(
            row = r,
            no = cell(r, 0),
            nama = nama,
            jk = cell(r, 2),
            tgl = cell(r, 3),
            fileValues = (0 until FIELD_COUNT).map { cell(r, FIRST_VALUE_COL + it) },
        )
    }
    return ScanResult(key, file, cell(1, 1), cell(2, 1), cell(3, 1), students)
}

/** Nama kolom Excel dari indeks 0-based: 0 -> A, 30 -> AE. */
fun colName(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        val m = (n - 1) % 26
        sb.insert(0, ('A' + m))
        n = (n - 1) / 26
    }
    return sb.toString()
}

/** Indeks 0-based dari nama kolom / referensi sel: "AE12" -> 30. */
fun colIndex(ref: String): Int {
    var n = 0
    for (ch in ref) {
        if (ch !in 'A'..'Z') break
        n = n * 26 + (ch - 'A' + 1)
    }
    return n - 1
}

/**
 * Gabungkan siswa dari file dengan siswa tambahan.
 * Siswa tambahan ditaruh setelah baris terakhir file, NO melanjutkan nomor terakhir.
 * Siswa tambahan yang namanya sudah ada di file (misalnya sudah ditempel ke file asli) dilewati.
 */
fun mergeAdded(fileStudents: List<Student>, added: List<NewStudent>): List<Student> {
    val names = fileStudents.map { it.nama.trim().lowercase() }.toSet()
    var row = (fileStudents.maxOfOrNull { it.row } ?: (FIRST_ROW - 1)) + 1
    var no = fileStudents.mapNotNull { it.no.toIntOrNull() }.maxOrNull() ?: fileStudents.size
    val extra = added.sortedBy { it.id }
        .filter { it.nama.trim().lowercase() !in names }
        .map { a ->
            no += 1
            Student(
                row = row++, no = no.toString(), nama = a.nama.trim(), jk = a.jk, tgl = a.tgl,
                fileValues = List(FIELD_COUNT) { "" }, added = true, addedId = a.id,
            )
        }
    return fileStudents + extra
}

/** Terima 8/7/2010, 08-07-2010, 08.07.2010 atau 08072010 -> "08/07/2010". Kosong -> "". Tidak valid -> null. */
fun normalizeDate(input: String): String? {
    val s = input.trim()
    if (s.isEmpty()) return ""
    val parts = s.split('/', '-', '.', ' ').filter { it.isNotEmpty() }
    val digits = s.filter { it.isDigit() }
    val dmy: Triple<Int?, Int?, Int?> = when {
        parts.size == 3 -> Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].toIntOrNull())
        parts.size == 1 && digits.length == 8 ->
            Triple(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), digits.substring(4).toInt())
        else -> return null
    }
    val d = dmy.first ?: return null
    val m = dmy.second ?: return null
    val y0 = dmy.third ?: return null
    val y = if (y0 < 100) 2000 + y0 else y0
    return try {
        LocalDate.of(y, m, d).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (e: Exception) {
        null
    }
}
