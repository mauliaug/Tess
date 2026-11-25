# Input Psikotes (Android, Kotlin)

Aplikasi Android untuk mengisi nilai psikotes siswa: IST, DISC, Career Cluster, dan Learning Style. Aplikasi ini tidak memerlukan server atau Apps Script.

## Alur
1. **Scan:** tempel link Google Sheets/Drive, lalu tekan **Scan**. Cara lain: dari aplikasi Sheets/Drive pilih **Bagikan → Input Psikotes**, atau tekan **Pilih file .xlsx dari HP**.
   Agar bisa di-scan lewat link, file harus dibagikan dengan akses **"Siapa saja yang memiliki link" (Viewer)**.
2. **Daftar siswa:** tekan nama siswa, isi nilainya, lalu tekan **Selesai**. Isian tersimpan di HP, dan kamu bisa mengeditnya kapan saja.
3. **Pindahkan ke file asli:**
   - **Salin nilai**: menyalin kolom E–AE dari baris 6 sampai baris terakhir. Buka file asli, tekan sel **E6**, lalu pilih **Tempel**.
   - **Buat .xlsx**: membuat file hasil dengan susunan yang sama persis dengan file asli (baris dan kolomnya sama). Buka file itu, salin range `E6:AE…`, lalu tempel di sel **E6** file asli.

## Susunan kolom (template SMAN1BATIPUH_RUANGx)
- Sheet `INPUT`. Data siswa dimulai dari baris 6. Informasi sekolah ada di B1 (Sekolah), B2 (Ruang), dan B3 (Tgl Tes).

| Kolom | Isi |
|---|---|
| A–D | No, Nama, Jenis Kelamin, Tanggal Lahir (hanya dibaca) |
| E–M | IST: SE, WA, AN, GE, RA, ZR, FA, WU, ME |
| N | DISC (contoh `C-S-D`) |
| O–AD | Career Cluster 1–16 |
| AE | Learning Style |

## Build
Setiap push ke repo ini otomatis menjalankan GitHub Actions (`.github/workflows/build-apk.yml`), yaitu menjalankan tes lalu membuat APK. File APK bisa diunduh di tab **Actions → run terakhir → Artifacts**.
