package com.psikotes.input

const val FIRST_ROW = 6          // data siswa mulai baris 6
const val FIRST_VALUE_COL = 4    // kolom E (0-based)
const val FIELD_COUNT = 27       // E..AE
const val IDX_DISC = 9           // kolom N
const val IDX_CC = 10            // kolom O (cluster 1)
const val IDX_LS = 26            // kolom AE

val IST_LABELS = listOf("SE", "WA", "AN", "GE", "RA", "ZR", "FA", "WU", "ME")
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
)

data class ScanResult(
    val key: String,
    val file: String,
    val sekolah: String,
    val ruang: String,
    val tglTes: String,
    val students: List<Student>,
) {
    val lastRow: Int get() = students.maxOfOrNull { it.row } ?: (FIRST_ROW - 1)
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
