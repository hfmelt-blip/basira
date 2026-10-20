package com.basira.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(vm: MainViewModel, onMenu: () -> Unit) {
    val (label, color) = engineLabel(vm)
    val listState = rememberLazyListState()
    val lastText = vm.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(vm.messages.size, lastText, vm.status) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex, Int.MAX_VALUE / 2)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(
            title = vm.conversations.firstOrNull { it.id == vm.currentConv }?.title ?: vm.prefs.appName,
            leading = "☰",
            onLeading = onMenu,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .padding(end = 8.dp)
                    .widthIn(max = 170.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { vm.refreshModels(); vm.screen = Screen.MODELS }
                    .padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Dot(color)
                    Text("  $label", color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )

        Box(Modifier.weight(1f)) {
            if (vm.messages.isEmpty()) {
                Welcome()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(vm.messages) { i, m ->
                        val isLast = i == vm.messages.lastIndex
                        MessageItem(m, if (isLast && vm.generating) vm.status else null)
                    }
                }
            }
        }

        Composer(vm)
    }
}

@Composable
private fun Welcome() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("◈", color = C.accent, fontSize = 52.sp)
        Spacer(Modifier.height(10.dp))
        Text("هلا، شنو بالك اليوم؟", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "أشتغل على تلفونك بالكامل وبدون نت. فعّل 🌐 حتى أبحث وأتعلم من الإنترنت، وعلّمني تجاربك من قسم المعرفة.",
            color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageItem(m: UiMessage, status: String?) {
    val ctx = LocalContext.current
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    m.text, color = C.text, fontSize = 16.sp, lineHeight = 25.sp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.panel2)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth()) {
        Text("◈", color = C.accent, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m.text.isEmpty() && status != null) {
                Text(status, color = C.muted, fontSize = 13.sp)
            }
            if (m.text.isNotEmpty()) {
                SelectionContainer { MarkdownText(m.text) }
            }
            m.error?.let {
                Text(
                    it, color = C.bad, fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.bad.copy(alpha = 0.08f))
                        .border(1.dp, C.bad.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                )
            }
            if (m.sources.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.sources.forEachIndexed { i, s ->
                        val icon = when (s.kind) { "memory" -> "🧪"; "file" -> "📄"; else -> "🌐" }
                        Text(
                            "[${i + 1}] $icon ${s.title}", color = C.muted, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(C.panel2)
                                .clickable(enabled = s.kind == "web") {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url))) }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(vm: MainViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Box {
            if (text.isEmpty()) Text("اكتب رسالتك...", color = C.muted, fontSize = 16.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = C.text, fontSize = 16.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(C.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp, max = 160.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val on = vm.webEnabled
            Text(
                "🌐 بحث بالنت", fontSize = 13.sp,
                color = if (on) C.accentInk else C.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) C.accent else C.panel)
                    .border(1.dp, if (on) C.accent else C.line, RoundedCornerShape(20.dp))
                    .clickable { vm.toggleWeb() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            val busy = vm.generating
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (busy) C.panel2 else C.accent)
                    .clickable {
                        if (busy) vm.stop() else if (text.isNotBlank()) { vm.send(text); text = "" }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (busy) "■" else "↑", color = if (busy) C.text else C.accentInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
