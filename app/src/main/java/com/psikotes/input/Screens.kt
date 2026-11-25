package com.psikotes.input

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF1F6FEB)
private val Green = Color(0xFF1A8A4A)
private val Orange = Color(0xFFC27C0E)
private val Gray = Color(0xFF6B7385)
private val Red = Color(0xFFC62828)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            background = Color(0xFFF4F5F7),
            surface = Color(0xFFF4F5F7),
        ),
        content = content,
    )
}

@Composable
fun App(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val canGoBack = vm.screen == Screen.LIST || vm.screen == Screen.FORM ||
        (vm.screen == Screen.SETUP && vm.apiUrl.isNotBlank())
    BackHandler(enabled = canGoBack) { vm.back() }

    Scaffold(
        topBar = { TopBar(vm, canGoBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (vm.screen == Screen.FORM) FormBottomBar(vm) },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            when (vm.screen) {
                Screen.SETUP -> SetupScreen(vm)
                Screen.LINK -> LinkScreen(vm)
                Screen.LIST -> ListScreen(vm)
                Screen.FORM -> FormScreen(vm)
            }
            if (vm.loading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(vm: AppViewModel, canGoBack: Boolean) {
    val title = when (vm.screen) {
        Screen.SETUP -> "Pengaturan"
        Screen.LINK -> "Input Psikotes"
        Screen.LIST -> vm.scan?.let { "Ruang ${it.ruang.ifBlank { "-" }}" } ?: "Daftar Siswa"
        Screen.FORM -> vm.detail?.let { "Input No ${it.no}" } ?: "Memuat…"
    }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        },
        actions = {
            when (vm.screen) {
                Screen.LINK -> IconButton(onClick = { vm.error = null; vm.screen = Screen.SETUP }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Pengaturan")
                }
                Screen.LIST -> IconButton(onClick = { vm.doScan(goToList = false) }, enabled = !vm.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                }
                else -> {}
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Blue,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}

// ---------------- Pengaturan ----------------
@Composable
private fun SetupScreen(vm: AppViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.apiUrl) }
    var tok by rememberSaveable { mutableStateOf(vm.token) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Hubungkan ke Google Sheets") {
            Text(
                "Cukup diisi sekali. URL Web App didapat setelah deploy Code.gs (berakhiran /exec).",
                style = MaterialTheme.typography.bodySmall, color = Gray,
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL Web App") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tok, onValueChange = { tok = it },
                label = { Text("Kode rahasia (TOKEN)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(vm.error)
            Button(
                onClick = { vm.testAndSaveConfig(url, tok) },
                enabled = !vm.loading && url.isNotBlank() && tok.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (vm.loading) "Menghubungkan…" else "Simpan & Tes Koneksi") }
        }
    }
}

// ---------------- Halaman 1: Link ----------------
@Composable
private fun LinkScreen(vm: AppViewModel) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Link Google Sheets") {
            OutlinedTextField(
                value = vm.link, onValueChange = { vm.link = it },
                label = { Text("Tempel link di sini") },
                placeholder = { Text("https://docs.google.com/spreadsheets/d/…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.getText()?.text?.let { vm.link = it.trim() } },
                    modifier = Modifier.weight(1f),
                ) { Text("Tempel") }
                OutlinedButton(
                    onClick = { vm.link = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("Hapus") }
            }
            ErrorText(vm.error)
            Button(
                onClick = { vm.doScan() },
                enabled = !vm.loading && vm.link.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (vm.loading) "Memindai…" else "Scan", fontSize = 16.sp) }
        }
        Text(
            "Tips: di aplikasi Google Sheets/Drive tekan Bagikan → Input Psikotes, link langsung dipindai otomatis.\n" +
                "Link file .xlsx juga bisa; aplikasi otomatis membuat salinan Google Sheets dan menyimpan data di sana.",
            style = MaterialTheme.typography.bodySmall, color = Gray,
        )
    }
}

// ---------------- Halaman 2: Daftar siswa ----------------
@Composable
private fun ListScreen(vm: AppViewModel) {
    val sc = vm.scan
    if (sc == null) {
        Text("Belum ada data.", Modifier.padding(16.dp))
        return
    }
    val q = vm.query.trim().lowercase()
    val shown = remember(sc, q) { sc.students.filter { q.isEmpty() || it.nama.lowercase().contains(q) } }
    val full = sc.students.count { it.filled >= it.total }
    val part = sc.students.count { it.filled in 1 until it.total }
    val none = sc.students.size - full - part

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "${sc.sekolah.ifBlank { sc.file }} · Tes ${sc.tglTes.ifBlank { "-" }}",
                color = Gray, style = MaterialTheme.typography.bodyMedium,
            )
            if (sc.converted) {
                Text("Disimpan ke: ${sc.file}", color = Orange, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                Pill("Lengkap $full", Color(0xFFE3F5EA), Green)
                Pill("Sebagian $part", Color(0xFFFDF1DC), Orange)
                Pill("Kosong $none", Color(0xFFECEEF2), Gray)
            }
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it },
                placeholder = { Text("Cari nama…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(vm.error)
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (sc.students.isEmpty()) "Tidak ada nama siswa ditemukan (data dibaca mulai baris 6)." else "Tidak ada yang cocok.",
                    color = Gray, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(shown, key = { it.row }) { s ->
            StudentRow(s) { vm.openStudent(s) }
            HorizontalDivider()
        }
    }
}

@Composable
private fun StudentRow(s: Student, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.no, Modifier.width(36.dp), textAlign = TextAlign.Center, color = Gray, fontSize = 14.sp)
        Column(Modifier.weight(1f)) {
            Text(s.nama, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(s.jk, s.tgl).filter { it.isNotBlank() }.joinToString(" · "),
                color = Gray, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            s.filled >= s.total -> Pill("✓ Lengkap", Color(0xFFE3F5EA), Green)
            s.filled > 0 -> Pill("${s.filled}/${s.total}", Color(0xFFFDF1DC), Orange)
            else -> Pill("Belum", Color(0xFFECEEF2), Gray)
        }
    }
}

// ---------------- Halaman 3: Input ----------------
@Composable
private fun FormScreen(vm: AppViewModel) {
    val d = vm.detail
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (d == null) {
            if (vm.error == null) Text("Memuat data…", color = Gray)
            ErrorText(vm.error)
        } else {
            Section(null) {
                Text(d.nama, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    "No ${d.no} · ${d.jk.ifBlank { "-" }} · ${d.tgl.ifBlank { "-" }}",
                    color = Gray, style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("IST (skor)") {
                FieldGrid(IST_LABELS.indices.toList(), 3, { IST_LABELS[it] }, vm)
            }

            Section("DISC") {
                OutlinedTextField(
                    value = vm.form[IDX_DISC],
                    onValueChange = { vm.setField(IDX_DISC, it.uppercase()) },
                    label = { Text("Urutan, pisah dengan strip (contoh C-S-D)") },
                    isError = IDX_DISC in vm.invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DISC_LETTERS.forEach { l ->
                        OutlinedButton(
                            onClick = { vm.addDisc(l) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(l, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                    TextButton(onClick = { vm.setField(IDX_DISC, "") }) { Text("Hapus") }
                }
            }

            Section("Career Cluster") {
                FieldGrid((0 until 16).map { IDX_CC + it }, 4, { (it - IDX_CC + 1).toString() }, vm)
            }

            Section("Learning Style") {
                LS_OPTIONS.chunked(2).forEach { opts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        opts.forEach { o ->
                            FilterChip(
                                selected = vm.form[IDX_LS] == o,
                                onClick = { vm.setField(IDX_LS, if (vm.form[IDX_LS] == o) "" else o) },
                                label = { Text(o, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                val cur = vm.form[IDX_LS]
                if (cur.isNotEmpty() && cur !in LS_OPTIONS) {
                    Text("Nilai di sheet: $cur", color = Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
            ErrorText(vm.error)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FieldGrid(indices: List<Int>, cols: Int, label: (Int) -> String, vm: AppViewModel) {
    indices.chunked(cols).forEach { rowIdx ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowIdx.forEach { i ->
                OutlinedTextField(
                    value = vm.form[i],
                    onValueChange = { v -> vm.setField(i, v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) },
                    label = { Text(label(i)) },
                    singleLine = true,
                    isError = i in vm.invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(cols - rowIdx.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun FormBottomBar(vm: AppViewModel) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Button(
            onClick = { vm.save() },
            enabled = !vm.loading && vm.detail != null,
            colors = ButtonDefaults.buttonColors(containerColor = Green),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(54.dp),
        ) { Text(if (vm.loading) "Menyimpan…" else "Selesai", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------- Komponen kecil ----------------
@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title.uppercase(), color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            content()
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text,
            color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = Red, style = MaterialTheme.typography.bodyMedium)
}
