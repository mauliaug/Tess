package com.psikotes.input

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val FIELD_COUNT = 27
const val IDX_DISC = 9
const val IDX_CC = 10
const val IDX_LS = 26
val IST_LABELS = listOf("SE", "WA", "AN", "GE", "RA", "ZR", "FA", "WU", "ME")
val DISC_LETTERS = listOf("D", "I", "S", "C")
val LS_OPTIONS = listOf("Aktivis", "Reflektor", "Teoris", "Pragmatis")

class ApiException(msg: String) : Exception(msg)

data class Student(
    val row: Int,
    val no: String,
    val nama: String,
    val jk: String,
    val tgl: String,
    val filled: Int,
    val total: Int,
)

data class ScanResult(
    val sheetId: String,
    val file: String,
    val sekolah: String,
    val ruang: String,
    val tglTes: String,
    val converted: Boolean,
    val students: List<Student>,
)

data class StudentDetail(
    val row: Int,
    val no: String,
    val nama: String,
    val jk: String,
    val tgl: String,
    val values: List<String>,
)

/** Klien untuk Web App Apps Script (backend/Code.gs). Panggil dari Dispatchers.IO. */
class Api(private val baseUrl: String, private val token: String) {

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 60_000
        }

    private fun call(body: JSONObject): JSONObject {
        body.put("token", token)
        val bytes = body.toString().toByteArray(Charsets.UTF_8)

        var conn = open(baseUrl)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        conn.outputStream.use { it.write(bytes) }
        var code = conn.responseCode

        // Apps Script menjawab POST dengan redirect 302 → hasilnya diambil dengan GET.
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val loc = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = open(loc)
            code = conn.responseCode
            hops++
        }

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        conn.disconnect()

        if (code !in 200..299) throw ApiException("Server error ($code). Cek URL Web App.")
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException("Balasan server tidak valid. Pastikan URL berakhiran /exec dan akses Web App 'Siapa saja'.")
        }
        if (!json.optBoolean("ok")) throw ApiException(json.optString("error", "Terjadi kesalahan."))
        return json
    }

    fun ping() {
        call(JSONObject().put("action", "ping"))
    }

    fun scan(link: String): ScanResult {
        val j = call(JSONObject().put("action", "scan").put("link", link))
        val arr = j.getJSONArray("students")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Student(
                row = o.getInt("row"),
                no = o.optString("no"),
                nama = o.optString("nama"),
                jk = o.optString("jk"),
                tgl = o.optString("tgl"),
                filled = o.optInt("filled"),
                total = o.optInt("total", FIELD_COUNT),
            )
        }
        return ScanResult(
            sheetId = j.getString("sheetId"),
            file = j.optString("file"),
            sekolah = j.optString("sekolah"),
            ruang = j.optString("ruang"),
            tglTes = j.optString("tglTes"),
            converted = j.optBoolean("converted"),
            students = list,
        )
    }

    fun get(sheetId: String, row: Int): StudentDetail {
        val j = call(JSONObject().put("action", "get").put("sheetId", sheetId).put("row", row))
        val arr = j.getJSONArray("values")
        return StudentDetail(
            row = j.getInt("row"),
            no = j.optString("no"),
            nama = j.optString("nama"),
            jk = j.optString("jk"),
            tgl = j.optString("tgl"),
            values = (0 until arr.length()).map { arr.optString(it) },
        )
    }

    fun save(sheetId: String, row: Int, nama: String, values: List<String>) {
        call(
            JSONObject()
                .put("action", "save")
                .put("sheetId", sheetId)
                .put("row", row)
                .put("nama", nama)
                .put("values", JSONArray(values))
        )
    }
}
