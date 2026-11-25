package com.psikotes.input

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class Screen { SETUP, LINK, LIST, FORM }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("config", Context.MODE_PRIVATE)

    var apiUrl by mutableStateOf(prefs.getString("apiUrl", "") ?: "")
        private set
    var token by mutableStateOf(prefs.getString("token", "") ?: "")
        private set
    var link by mutableStateOf(prefs.getString("link", "") ?: "")
    var screen by mutableStateOf(if (apiUrl.isBlank()) Screen.SETUP else Screen.LINK)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var scan by mutableStateOf<ScanResult?>(null)
        private set
    var detail by mutableStateOf<StudentDetail?>(null)
        private set
    var query by mutableStateOf("")

    val form = mutableStateListOf<String>().apply { repeat(FIELD_COUNT) { add("") } }
    val invalid = mutableStateListOf<Int>()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun api() = Api(apiUrl, token)

    private fun msg(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "Terjadi kesalahan."
        is UnknownHostException -> "Tidak ada koneksi internet."
        is SocketTimeoutException -> "Koneksi timeout, coba lagi."
        else -> e.message ?: e.toString()
    }

    // ---------- Pengaturan ----------
    fun testAndSaveConfig(url: String, tok: String) {
        if (loading) return
        val u = url.trim()
        val t = tok.trim()
        error = null
        viewModelScope.launch {
            loading = true
            try {
                withContext(Dispatchers.IO) { Api(u, t).ping() }
                apiUrl = u
                token = t
                prefs.edit().putString("apiUrl", u).putString("token", t).apply()
                _messages.send("Terhubung ✓")
                screen = Screen.LINK
                if (link.isNotBlank() && pendingAutoScan) {
                    pendingAutoScan = false
                    doScan()
                }
            } catch (e: Exception) {
                error = msg(e)
            } finally {
                loading = false
            }
        }
    }

    // ---------- Link dari menu Bagikan ----------
    private var pendingAutoScan = false

    fun onSharedText(text: String) {
        val m = Regex("https://(docs|drive)\\.google\\.com/\\S+").find(text)
        link = m?.value ?: text.trim()
        if (apiUrl.isBlank()) {
            pendingAutoScan = true
            screen = Screen.SETUP
        } else {
            screen = Screen.LINK
            doScan()
        }
    }

    // ---------- Scan ----------
    fun doScan(goToList: Boolean = true) {
        if (loading) return
        val l = link.trim()
        if (l.isEmpty()) return
        error = null
        prefs.edit().putString("link", l).apply()
        viewModelScope.launch {
            loading = true
            try {
                val r = withContext(Dispatchers.IO) { api().scan(l) }
                scan = r
                if (r.converted && goToList) {
                    _messages.send("File .xlsx → data disimpan di salinan Google Sheets: ${r.file}")
                }
                if (goToList) screen = Screen.LIST
            } catch (e: Exception) {
                error = msg(e)
            } finally {
                loading = false
            }
        }
    }

    // ---------- Form ----------
    fun openStudent(s: Student) {
        val sid = scan?.sheetId ?: return
        detail = null
        error = null
        invalid.clear()
        for (i in 0 until FIELD_COUNT) form[i] = ""
        screen = Screen.FORM
        viewModelScope.launch {
            loading = true
            try {
                val d = withContext(Dispatchers.IO) { api().get(sid, s.row) }
                d.values.forEachIndexed { i, v -> if (i < FIELD_COUNT) form[i] = v }
                detail = d
            } catch (e: Exception) {
                error = msg(e)
            } finally {
                loading = false
            }
        }
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
        if (loading) return
        val d = detail ?: return
        val sid = scan?.sheetId ?: return

        val values = form.map { it.trim() }.toMutableList()
        values[IDX_DISC] = values[IDX_DISC].uppercase().replace(Regex("\\s+"), "")
        form[IDX_DISC] = values[IDX_DISC]

        invalid.clear()
        for (i in values.indices) {
            if (i == IDX_DISC || i == IDX_LS || values[i].isEmpty()) continue
            values[i] = values[i].replace(',', '.')
            val n = values[i].toDoubleOrNull()
            if (n == null || n < 0) invalid.add(i)
        }
        if (values[IDX_DISC].isNotEmpty()) {
            val p = values[IDX_DISC].split("-")
            if (!p.all { it in DISC_LETTERS } || p.toSet().size != p.size) invalid.add(IDX_DISC)
        }
        if (invalid.isNotEmpty()) {
            error = "Ada isian yang tidak valid (ditandai merah)."
            return
        }

        error = null
        viewModelScope.launch {
            loading = true
            try {
                withContext(Dispatchers.IO) { api().save(sid, d.row, d.nama, values) }
                val filled = values.count { it.isNotEmpty() }
                scan = scan?.let { sc ->
                    sc.copy(students = sc.students.map { if (it.row == d.row) it.copy(filled = filled) else it })
                }
                _messages.send("Tersimpan ke Sheets ✓  ${d.nama}")
                screen = Screen.LIST
            } catch (e: Exception) {
                error = msg(e)
            } finally {
                loading = false
            }
        }
    }

    // ---------- Navigasi ----------
    fun back() {
        error = null
        screen = when (screen) {
            Screen.FORM -> Screen.LIST
            Screen.LIST -> Screen.LINK
            Screen.SETUP -> if (apiUrl.isBlank()) Screen.SETUP else Screen.LINK
            Screen.LINK -> Screen.LINK
        }
    }
}
