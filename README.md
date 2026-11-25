# Input Psikotes (Android, Kotlin)

Aplikasi Android untuk mengisi nilai psikotes (IST, DISC, Career Cluster, dan Learning Style) langsung ke Google Sheets.

Alurnya: **Link → Scan → Daftar siswa → Input → Selesai → kembali ke daftar** (untuk mengedit, pilih lagi nama siswanya).

```
Aplikasi Android (Kotlin)  ──HTTPS──►  Apps Script Web App (backend/Code.gs)  ──►  Google Sheets
```

Aplikasi Android tidak bisa langsung masuk ke akun Google kamu. Karena itu, Apps Script dipakai sebagai "jembatan". Kalau langsung pakai Sheets API, kamu harus membuat Google Cloud project, OAuth, dan SHA-1, yang jauh lebih ribet.

## A. Pasang backend (sekali saja)
1. Buka https://script.google.com → **Proyek baru**.
2. Tempel isi `backend/Code.gs`, lalu ganti `GANTI_KODE_RAHASIA` dengan kode rahasia kamu sendiri.
3. Di panel kiri, klik **Services (+) → Drive API → Add**. Ini dibutuhkan agar link .xlsx bisa dikonversi otomatis.
4. Klik **Deploy → New deployment → Web app**, lalu isi:
   - Execute as: **Me**
   - Who has access: **Anyone**
5. Beri izin akses, lalu salin **Web app URL** (berakhiran `/exec`).

> Kalau Code.gs diubah, deploy ulang lewat **Deploy → Manage deployments → Edit → New version**. Dengan cara ini URL-nya tidak berubah.

## B. Build aplikasi
1. Buka folder ini di **Android Studio** (versi Koala atau yang lebih baru), lalu tunggu proses Gradle sync selesai.
2. Sambungkan HP (aktifkan USB debugging), lalu tekan **Run ▶**.
   Kalau mau membuat file APK: **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.

## C. Pakai
1. Saat pertama kali dibuka, isi **URL Web App** dan **kode rahasia**, lalu tekan Simpan & Tes Koneksi.
2. Masukkan link spreadsheet dengan salah satu cara berikut:
   - Tempel link, lalu tekan **Scan**, atau
   - Dari aplikasi Google Sheets/Drive, tekan **Bagikan → Input Psikotes**. Link akan dipindai otomatis.
3. Pilih nama siswa, isi nilainya, lalu tekan **Selesai**. Data langsung tertulis ke sheet.

### Tentang link .xlsx
Link yang mengandung `rtpof=true` adalah file **.xlsx**, dan file seperti ini tidak bisa ditulis oleh API. Untuk itu, backend otomatis membuat salinan **"<nama file> [Sheets]"** di folder yang sama, lalu semua input disimpan di salinan tersebut. Kalau link yang sama di-scan lagi, backend memakai salinan yang sudah ada.
Setelah selesai, kalau butuh file Excel, buka salinan itu lalu pilih **File → Download → .xlsx**.

## Susunan kolom yang dibaca
- Sheet: `INPUT`. Kalau tidak ada, yang dipakai adalah sheet pertama.
- Data siswa mulai **baris 6**. B1 = Sekolah, B2 = Ruang, B3 = Tgl Tes.

| Kolom | Isi |
|---|---|
| A–D | No, Nama, Jenis Kelamin, Tanggal Lahir (hanya dibaca) |
| E–M | IST: SE, WA, AN, GE, RA, ZR, FA, WU, ME |
| N | DISC (contoh `C-S-D`) |
| O–AD | Career Cluster 1–16 |
| AE | Learning Style (Aktivis / Reflektor / Teoris / Pragmatis) |

Kalau template berubah, sesuaikan konstanta di bagian atas `Code.gs`.
