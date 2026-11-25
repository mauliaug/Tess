package com.psikotes.input

import java.net.HttpURLConnection
import java.net.URL

/** Mengunduh file spreadsheet dari link Google Drive / Sheets (harus dibagikan "Siapa saja yang memiliki link"). */
object Net {

    fun extractId(link: String): String? {
        val s = link.trim()
        Regex("/d/([a-zA-Z0-9_-]{20,})").find(s)?.let { return it.groupValues[1] }
        Regex("[?&]id=([a-zA-Z0-9_-]{20,})").find(s)?.let { return it.groupValues[1] }
        if (Regex("^[a-zA-Z0-9_-]{20,}$").matches(s)) return s
        return null
    }

    fun downloadXlsx(id: String): ByteArray {
        val urls = listOf(
            "https://drive.google.com/uc?export=download&id=$id",               // file .xlsx di Drive
            "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx",    // file Google Sheets
        )
        for (u in urls) {
            val b = try { get(u) } catch (e: java.io.IOException) { null }
            if (b != null && b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()) return b
        }
        throw AppException(
            "File tidak bisa dibaca. Atur berbagi file ke \"Siapa saja yang memiliki link\", " +
                "atau pakai tombol \"Pilih file .xlsx dari HP\"."
        )
    }

    private fun get(start: String): ByteArray? {
        var url = start
        repeat(8) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) InputPsikotes")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) return null
                url = URL(URL(url), loc).toString()
                return@repeat
            }
            if (code !in 200..299) {
                c.disconnect()
                return null
            }
            val bytes = c.inputStream.use { it.readBytes() }
            c.disconnect()
            return bytes
        }
        return null
    }
}
