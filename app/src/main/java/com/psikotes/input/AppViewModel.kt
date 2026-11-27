package com.psikotes.input

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class Screen { LINK, LIST, FORM, STUDENT }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("config", Context.MODE_PRIVATE)

    var link by mutableStateOf(prefs.getString("link", "") ?: "")
    var screen by mutableStateOf(Screen.LINK)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var scan by mutableStateOf<ScanResult?>(null)
        private set
    var current by mutableStateOf<Student?>(null)
        private set
    var query by mutableStateOf("")
    var lastExport by mutableStateOf<Uri?>(null)
        private set

    // Form data siswa tambahan
    var idNama by mutableStateOf("")
    var idJk by mutableStateOf("")
    var idTgl by mutableStateOf("")
    var editingId by mutableStateOf<Long?>(null)
        private set
    private var baseScan: ScanResult? = null

    /** Nilai E..AE per baris (yang tampil & akan diekspor). */
    val values = mutableStateMapOf<Int, List<String>>()
    val form = mutableStateListOf<String>().apply { repeat(FIELD_COUNT) { add("") } }
    val invalid = mutableStateListOf<Int>()

    private var lastFileUri: Uri? = null
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun msg(e: Exception): String = when (e) {
        is AppException -> e.message ?: "Terjadi kesalahan."
        is UnknownHostException -> "Tidak ada koneksi internet."
        is SocketTimeoutException -> "Koneksi timeout, coba lagi."
        else -> e.message ?: e.toString()
    }

    fun filled(row: Int): Int = values[row]?.count { it.isNotBlank() } ?: 0

    // ---------- Scan ----------
    fun onSharedText(text: String) {
        val m = Regex("https://(docs|drive)\\.google\\.com/\\S+").find(text)
        link = m?.value ?: text.trim()
        screen = Screen.LINK
        scanLink()
    }

    fun scanLink(goToList: Boolean = true) {
        if (loading) return
        val l = link.trim()
        val id = Net.extractId(l)
        if (id == null) {
            error = "Link tidak dikenali. Tempel link Google Sheets / Drive."
            return
        }
        prefs.edit().putString("link", l).apply()
        lastFileUri = null
        load(goToList) {
            val bytes = Net.downloadXlsx(id)
            XlsxReader.read(bytes).toScanResult(key = "id:$id", file = "Spreadsheet")
        }
    }

    fun scanFile(uri: Uri, goToList: Boolean = true) {
        if (loading) return
        lastFileUri = uri
        val cr = getApplication<Application>().contentResolver
        load(goToList) {
            var name = "file.xlsx"
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw AppException("File tidak bisa dibuka.")
            XlsxReader.read(bytes).toScanResult(key = "file:$name", file = name)
        }
    }

    fun rescan() {
        val u = lastFileUri
        if (u != null) scanFile(u, goToList = false) else scanLink(goToList = false)
    }

    private fun load(goToList: Boolean, block: () -> ScanResult) {
        error = null
        viewModelScope.launch {
            loading = true
            try {
                val r = withContext(Dispatchers.IO) { block() }
                if (r.students.isEmpty()) throw AppException("Tidak ada nama siswa (data dibaca dari kolom B mulai baris 6).")
                applyScan(r)
                if (goToList) screen = Screen.LIST else _messages.send("Daftar diperbarui")
            } catch (e: Exception) {
                error = msg(e)
            } finally {
                loading = false
            }
        }
    }

    private fun applyScan(r: ScanResult) {
        baseScan = r
        lastExport = null
        rebuild()
    }

    /** Susun ulang daftar (siswa file + siswa tambahan) dan nilai-nilainya. */
    private fun rebuild() {
        val b = baseScan ?: return
        val r = b.copy(students = mergeAdded(b.students, loadAdded(b.key)))
        scan = r
        values.clear()
        r.students.forEach { values[it.row] = it.fileValues.withNormalizedLs() }
        // Isian yang sudah dibuat di HP menimpa nilai dari file
        val saved = loadLocal(r.key)
        val byName = HashMap<String, JSONObject>()
        saved.keys().forEach { k -> saved.optJSONObject(k)?.let { byName[it.optString("n").lowercase()] = it } }
        for (s in r.students) {
            var o = saved.optJSONObject(s.row.toString())
            if (o != null && o.optString("n") != s.nama) o = null
            if (o == null && s.added) o = byName[s.nama.lowercase()]
            val arr = o?.optJSONArray("v") ?: continue
            values[s.row] = (0 until FIELD_COUNT).map { arr.optString(it, "") }.withNormalizedLs()
        }
    }

    // ---------- Siswa tambahan ----------
    private fun loadAdded(key: String): List<NewStudent> = try {
        val arr = JSONArray(prefs.getString("added_$key", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            NewStudent(o.getLong("id"), o.optString("nama"), o.optString("jk"), o.optString("tgl"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveAdded(key: String, list: List<NewStudent>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("nama", it.nama).put("jk", it.jk).put("tgl", it.tgl)) }
        prefs.edit().putString("added_$key", arr.toString()).apply()
    }

    fun startAddStudent() {
        editingId = null
        idNama = ""; idJk = ""; idTgl = ""
        error = null
        screen = Screen.STUDENT
    }

    fun editStudentIdentity(s: Student) {
        if (!s.added) return
        editingId = s.addedId
        idNama = s.nama; idJk = s.jk; idTgl = s.tgl
        error = null
        screen = Screen.STUDENT
    }

    fun saveIdentity() {
        val sc = scan ?: return
        val nama = idNama.trim().replace(Regex("\\s+"), " ")
        val tgl = normalizeDate(idTgl)
        val others = sc.students.filter { !(it.added && it.addedId == editingId) }
        error = when {
            nama.isEmpty() -> "Nama siswa wajib diisi."
            others.any { it.nama.equals(nama, ignoreCase = true) } -> "Nama \"$nama\" sudah ada di daftar."
            idJk.isEmpty() -> "Pilih jenis kelamin."
            tgl == null -> "Tanggal lahir tidak valid. Contoh: 08/07/2010"
            else -> null
        }
        if (error != null || tgl == null) return
        idTgl = tgl

        val list = loadAdded(sc.key).toMutableList()
        val id = editingId
        val newId: Long
        if (id == null) {
            newId = System.currentTimeMillis()
            list.add(NewStudent(newId, nama, idJk, tgl))
        } else {
            newId = id
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return
            val old = list[idx]
            list[idx] = old.copy(nama = nama, jk = idJk, tgl = tgl)
            if (old.nama != nama) renameSaved(sc.key, old.nama, nama)
        }
        saveAdded(sc.key, list)
        rebuild()
        val st = scan?.students?.firstOrNull { it.added && it.addedId == newId } ?: return
        notify(if (id == null) "Siswa ditambahkan di baris ${st.row}" else "Data siswa diperbarui")
        openStudent(st)
    }

    fun deleteAdded() {
        val sc = scan ?: return
        val id = editingId ?: return
        val list = loadAdded(sc.key)
        val target = list.firstOrNull { it.id == id } ?: return
        saveAdded(sc.key, list.filter { it.id != id })
        removeSaved(sc.key, target.nama)
        rebuild()
        editingId = null
        error = null
        screen = Screen.LIST
        notify("${target.nama} dihapus")
    }

    private fun renameSaved(key: String, oldName: String, newName: String) {
        val all = loadLocal(key)
        all.keys().asSequence().toList().forEach { k ->
            val o = all.optJSONObject(k) ?: return@forEach
            if (o.optString("n") == oldName) o.put("n", newName)
        }
        prefs.edit().putString("data_$key", all.toString()).apply()
    }

    private fun removeSaved(key: String, name: String) {
        val all = loadLocal(key)
        all.keys().asSequence().toList().forEach { k ->
            if (all.optJSONObject(k)?.optString("n") == name) all.remove(k)
        }
        prefs.edit().putString("data_$key", all.toString()).apply()
    }

    // ---------- Penyimpanan lokal ----------
    private fun loadLocal(key: String): JSONObject =
        try {
            JSONObject(prefs.getString("data_$key", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    private fun saveLocal(key: String, s: Student, v: List<String>) {
        val all = loadLocal(key)
        all.put(s.row.toString(), JSONObject().put("n", s.nama).put("v", JSONArray(v)))
        prefs.edit().putString("data_$key", all.toString()).apply()
    }

    // ---------- Form ----------
    fun openStudent(s: Student) {
        current = s
        error = null
        invalid.clear()
        val v = values[s.row] ?: s.fileValues
        for (i in 0 until FIELD_COUNT) form[i] = v.getOrElse(i) { "" }
        screen = Screen.FORM
    }

    fun setField(i: Int, v: String) {
        form[i] = v
        invalid.remove(i)
    }

    fun addDisc(letter: String) {
        val cur = form[IDX_DISC].trim().uppercase()
        val parts = if (cur.isEmpty()) mutableListOf() else cur.split("-").toMutableList()
        if (letter in parts || parts.size >= 4) return
        parts.add(letter)
        setField(IDX_DISC, parts.joinToString("-"))
    }

    fun save() {
        val s = current ?: return
        val sc = scan ?: return
        val v = form.map { it.trim() }.toMutableList()
        v[IDX_DISC] = v[IDX_DISC].uppercase().replace(Regex("\\s+"), "")
        form[IDX_DISC] = v[IDX_DISC]

        invalid.clear()
        for (i in v.indices) {
            if (i == IDX_DISC || i == IDX_LS || v[i].isEmpty()) continue
            v[i] = v[i].replace(',', '.')
            val n = v[i].toDoubleOrNull()
            if (n == null || n < 0) invalid.add(i)
        }
        if (v[IDX_DISC].isNotEmpty()) {
            val p = v[IDX_DISC].split("-")
            if (!p.all { it in DISC_LETTERS } || p.toSet().size != p.size) invalid.add(IDX_DISC)
        }
        if (invalid.isNotEmpty()) {
            error = "Ada isian yang tidak valid (ditandai merah)."
            return
        }
        values[s.row] = v.toList()
        saveLocal(sc.key, s, v)
        lastExport = null
        error = null
        screen = Screen.LIST
        viewModelScope.launch { _messages.send("Tersimpan ✓  ${s.nama}") }
    }

    // ---------- Hasil ----------
    /** Nilai E..AE siswa dari file (baris 6 s/d baris terakhir file), dipisah TAB, untuk ditempel di sel E6. */
    fun valuesAsTsv(): String {
        val sc = scan ?: return ""
        val last = sc.fileLastRow
        val sb = StringBuilder()
        for (r in FIRST_ROW..last) {
            val v = values[r] ?: List(FIELD_COUNT) { "" }
            sb.append(v.joinToString("\t"))
            if (r < last) sb.append("\n")
        }
        return sb.toString()
    }

    /** Baris lengkap A..AE untuk siswa tambahan, ditempel di sel A(baris pertama siswa tambahan). */
    fun addedAsTsv(): String {
        val sc = scan ?: return ""
        return sc.addedStudents.joinToString("\n") { s ->
            (listOf(s.no, s.nama, s.jk, s.tgl) + (values[s.row] ?: s.fileValues)).joinToString("\t")
        }
    }

    fun exportFileName(): String {
        val sc = scan ?: return "HASIL_INPUT.xlsx"
        val base = if (sc.file.endsWith(".xlsx", true)) sc.file.dropLast(5) else "Ruang${sc.ruang.ifBlank { "" }}"
        return "${base}_HASIL.xlsx"
    }

    fun exportTo(uri: Uri) {
        val sc = scan ?: return
        val cr = getApplication<Application>().contentResolver
        val snapshot = values.toMap()
        viewModelScope.launch {
            loading = true
            try {
                withContext(Dispatchers.IO) {
                    cr.openOutputStream(uri, "wt")?.use { XlsxWriter.write(it, sc, snapshot) }
                        ?: throw AppException("Tidak bisa menulis file.")
                }
                lastExport = uri
                _messages.send("File hasil tersimpan ✓")
            } catch (e: Exception) {
                _messages.send("Gagal menyimpan: ${msg(e)}")
            } finally {
                loading = false
            }
        }
    }

    fun notify(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }

    // ---------- Navigasi ----------
    fun back() {
        error = null
        screen = when (screen) {
            Screen.FORM -> Screen.LIST
            Screen.STUDENT -> Screen.LIST
            Screen.LIST -> Screen.LINK
            Screen.LINK -> Screen.LINK
        }
    }
}
