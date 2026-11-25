/**
 * BACKEND API untuk aplikasi Android "Input Psikotes".
 * Tempel di script.google.com, lalu Deploy sebagai Web App:
 *   - Jalankan sebagai : Saya (akun pemilik file)
 *   - Yang memiliki akses : Siapa saja
 * Aktifkan juga: Services (+) → Drive API (dibutuhkan untuk konversi .xlsx otomatis).
 *
 * Struktur sheet (sesuai template SMAN1BATIPUH_RUANG1):
 *   B1 Sekolah, B2 Ruang, B3 Tgl Tes; data siswa mulai baris 6
 *   A NO, B NAMA, C JK, D TGL LAHIR
 *   E..M IST (SE WA AN GE RA ZR FA WU ME), N DISC, O..AD Cluster 1-16, AE Learning Style
 */

// GANTI dengan kode rahasia kamu sendiri, lalu isi kode yang sama di aplikasi.
const TOKEN = 'GANTI_KODE_RAHASIA';

const SHEET_NAME = 'INPUT';
const FIRST_ROW = 6;
const COL_NAMA = 2;     // B
const COL_START = 5;    // E
const LAST_COL = 31;    // AE
const NUM_FIELDS = 27;  // E..AE
const IDX_DISC = 9;
const IDX_LS = 26;

function doGet() {
  return json_({ ok: true, msg: 'API Input Psikotes aktif.' });
}

function doPost(e) {
  try {
    const req = JSON.parse(e.postData.contents);
    if (TOKEN === 'GANTI_KODE_RAHASIA') throw new Error('TOKEN di Code.gs belum diganti.');
    if (req.token !== TOKEN) throw new Error('Kode rahasia salah.');
    let res;
    switch (req.action) {
      case 'ping': res = {}; break;
      case 'scan': res = scan_(req.link); break;
      case 'get':  res = getStudent_(req.sheetId, Number(req.row)); break;
      case 'save': res = save_(req.sheetId, Number(req.row), req.nama, req.values); break;
      default: throw new Error('Aksi tidak dikenal.');
    }
    res.ok = true;
    return json_(res);
  } catch (err) {
    return json_({ ok: false, error: String((err && err.message) || err) });
  }
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

/** Ambil ID file dari link apa pun: /spreadsheets/d/ID/..., /file/d/ID/..., ?id=ID, atau ID saja. */
function extractId_(link) {
  const s = String(link || '').trim();
  const m = s.match(/\/d\/([a-zA-Z0-9_-]{20,})/) || s.match(/[?&]id=([a-zA-Z0-9_-]{20,})/);
  if (m) return m[1];
  if (/^[a-zA-Z0-9_-]{20,}$/.test(s)) return s;
  throw new Error('Link tidak dikenali. Tempel link Google Sheets / Drive.');
}

/**
 * Pastikan file berformat Google Sheets.
 * Jika .xlsx (link dengan rtpof=true), otomatis buat salinan Google Sheets di folder yang sama
 * (atau pakai salinan yang sudah pernah dibuat), lalu kerja di salinan itu.
 */
function resolveSheet_(id) {
  let file;
  try { file = DriveApp.getFileById(id); }
  catch (e) { throw new Error('File tidak ditemukan, atau akun pemilik script tidak punya akses ke file ini.'); }

  const mime = file.getMimeType();
  if (mime === MimeType.GOOGLE_SHEETS) return { id: id, converted: false };

  if (mime === MimeType.MICROSOFT_EXCEL || mime === MimeType.MICROSOFT_EXCEL_LEGACY) {
    const name = file.getName().replace(/\.xlsx?$/i, '') + ' [Sheets]';
    const parents = file.getParents();
    const folder = parents.hasNext() ? parents.next() : DriveApp.getRootFolder();

    // Sudah pernah dikonversi? Pakai yang lama supaya data tidak terpecah.
    const it = folder.getFilesByName(name);
    while (it.hasNext()) {
      const f = it.next();
      if (!f.isTrashed() && f.getMimeType() === MimeType.GOOGLE_SHEETS) return { id: f.getId(), converted: true };
    }
    if (typeof Drive === 'undefined') {
      throw new Error('File ini .xlsx. Aktifkan "Drive API" di Services Apps Script agar bisa dikonversi otomatis.');
    }
    let copy;
    try {
      copy = Drive.Files.copy({ name: name, mimeType: MimeType.GOOGLE_SHEETS, parents: [folder.getId()] }, id, { supportsAllDrives: true });
    } catch (e) {
      // Tidak bisa menulis di folder asal → simpan di My Drive
      copy = Drive.Files.copy({ name: name, mimeType: MimeType.GOOGLE_SHEETS }, id, { supportsAllDrives: true });
    }
    return { id: copy.id, converted: true };
  }
  throw new Error('File ini bukan spreadsheet.');
}

function openSheet_(sheetId) {
  const ss = SpreadsheetApp.openById(sheetId);
  const sh = ss.getSheetByName(SHEET_NAME) || ss.getSheets()[0];
  return { ss: ss, sh: sh };
}

function scan_(link) {
  const r = resolveSheet_(extractId_(link));
  const o = openSheet_(r.id);
  const sh = o.sh;
  const info = sh.getRange('B1:B3').getDisplayValues();
  const last = sh.getLastRow();
  const students = [];
  if (last >= FIRST_ROW) {
    const vals = sh.getRange(FIRST_ROW, 1, last - FIRST_ROW + 1, LAST_COL).getDisplayValues();
    vals.forEach(function (row, i) {
      const nama = String(row[1]).trim();
      if (!nama) return;
      const filled = row.slice(COL_START - 1, LAST_COL).filter(function (v) { return String(v).trim() !== ''; }).length;
      students.push({ row: FIRST_ROW + i, no: row[0], nama: nama, jk: row[2], tgl: row[3], filled: filled, total: NUM_FIELDS });
    });
  }
  return {
    sheetId: r.id, converted: r.converted, file: o.ss.getName(),
    sekolah: info[0][0], ruang: info[1][0], tglTes: info[2][0],
    students: students
  };
}

function getStudent_(sheetId, row) {
  const sh = openSheet_(sheetId).sh;
  const r = sh.getRange(row, 1, 1, LAST_COL).getDisplayValues()[0];
  return { row: row, no: r[0], nama: String(r[1]).trim(), jk: r[2], tgl: r[3], values: r.slice(COL_START - 1, LAST_COL) };
}

function save_(sheetId, row, expectedName, values) {
  if (!Array.isArray(values) || values.length !== NUM_FIELDS) throw new Error('Data tidak lengkap.');
  const sh = openSheet_(sheetId).sh;
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const nama = String(sh.getRange(row, COL_NAMA).getDisplayValue()).trim();
    if (nama !== String(expectedName).trim()) {
      throw new Error('Nama di baris ' + row + ' sudah berubah (sekarang: "' + nama + '"). Muat ulang daftar.');
    }
    const out = values.map(function (v, i) {
      v = String(v == null ? '' : v).trim();
      if (v === '') return '';
      if (/^[=+@]/.test(v)) v = "'" + v;           // cegah rumus tak sengaja
      if (i === IDX_DISC || i === IDX_LS) return v;
      const n = Number(v);
      return isNaN(n) ? v : n;
    });
    sh.getRange(row, COL_START, 1, NUM_FIELDS).setValues([out]);
    SpreadsheetApp.flush();
  } finally {
    lock.releaseLock();
  }
  return {};
}
