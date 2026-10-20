package com.basira.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KnowledgeScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var memTitle by rememberSaveable { mutableStateOf("") }
    var memText by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar("المعرفة والتجارب", "${vm.sources.size} شي متعلَّم", leading = "→", onLeading = { vm.screen = Screen.CHAT })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("رابط", "ابحث وتعلّم", "تجربة").forEachIndexed { i, t ->
                            val on = tab == i
                            Text(
                                t, fontSize = 13.sp, color = if (on) C.text else C.muted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (on) C.panel2 else C.panel)
                                    .border(1.dp, C.line, RoundedCornerShape(20.dp))
                                    .clickable { tab = i }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    when (tab) {
                        0 -> {
                            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), placeholder = { Text("https://...") }, singleLine = true)
                            ActionButton("اقرأ وتعلّم", vm.learnBusy) { vm.learnUrl(url); url = "" }
                        }
                        1 -> {
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                                placeholder = { Text("موضوع، مثلاً: أساسيات تداول الذهب") }, singleLine = true)
                            ActionButton("ابحث واقرأ 5 صفحات", vm.learnBusy) { vm.learnSearch(query) }
                        }
                        else -> {
                            OutlinedTextField(memTitle, { memTitle = it }, Modifier.fillMaxWidth(),
                                placeholder = { Text("عنوان التجربة (اختياري)") }, singleLine = true)
                            OutlinedTextField(memText, { memText = it }, Modifier.fillMaxWidth(), minLines = 3,
                                placeholder = { Text("شنو جربت؟ شنو كانت النتيجة؟ وشنو تعلمت؟") })
                            ActionButton("احفظ التجربة", vm.learnBusy) { vm.learnMemory(memTitle, memText); memTitle = ""; memText = "" }
                        }
                    }
                    vm.learnStatus?.let {
                        Text(it, fontSize = 13.sp, color = when {
                            it.startsWith("✓") -> C.ok; it.startsWith("✗") -> C.bad; else -> C.muted
                        })
                    }
                }
            }
            item { Text("اللي تعلمته", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            if (vm.sources.isEmpty()) item { Text("ما تعلمت شي بعد", color = C.muted, fontSize = 13.sp) }
            items(vm.sources, key = { it.id }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.panel)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(when (s.kind) { "memory" -> "🧪"; "file" -> "📄"; else -> "🌐" }, fontSize = 15.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(s.title, color = C.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.chunks} جزء", color = C.muted, fontSize = 11.sp)
                    }
                    Text("✕", color = C.muted, modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.deleteSource(s.id) }
                        .padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.accentInk),
        shape = RoundedCornerShape(10.dp)
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}
