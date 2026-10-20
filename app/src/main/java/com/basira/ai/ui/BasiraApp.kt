@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.basira.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basira.ai.data.Conversation
import com.basira.ai.llm.EngineState
import kotlinx.coroutines.launch

@Composable
fun BasiraApp(vm: MainViewModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawer: () -> Unit = { scope.launch { drawer.close() } }

    BackHandler(enabled = vm.screen != Screen.CHAT) { vm.screen = Screen.CHAT }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = C.panel, modifier = Modifier.width(300.dp)) {
                DrawerContent(vm, closeDrawer)
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(C.bg)
        ) {
            when (vm.screen) {
                Screen.CHAT -> ChatScreen(vm, onMenu = { scope.launch { drawer.open() } })
                Screen.MODELS -> ModelsScreen(vm)
                Screen.KNOWLEDGE -> KnowledgeScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun DrawerContent(vm: MainViewModel, close: () -> Unit) {
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◈", color = C.accent, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(vm.prefs.appName, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { vm.newChat(); close() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.accentInk),
            shape = RoundedCornerShape(12.dp)
        ) { Text("＋  محادثة جديدة", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        Text("المحادثات", color = C.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(vm.conversations, key = { it.id }) { c ->
                val active = c.id == vm.currentConv
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) C.panel2 else Color.Transparent)
                        .combinedClickable(
                            onClick = { vm.openConversation(c.id); close() },
                            onLongClick = { renaming = c }
                        )
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.title, color = C.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text("🗑", fontSize = 13.sp, modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(onClick = { deleting = c })
                        .padding(4.dp))
                }
            }
        }
        HorizontalDivider(color = C.line)
        Spacer(Modifier.height(6.dp))
        DrawerItem("🧠  المعرفة والتجارب") { vm.refreshSources(); vm.screen = Screen.KNOWLEDGE; close() }
        DrawerItem("📦  النماذج") { vm.refreshModels(); vm.screen = Screen.MODELS; close() }
        DrawerItem("⚙  الإعدادات") { vm.screen = Screen.SETTINGS; close() }
        Spacer(Modifier.height(16.dp))
    }

    renaming?.let { c ->
        var t by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("اسم المحادثة") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameConversation(c.id, t); renaming = null }) { Text("حفظ") } },
            dismissButton = { TextButton({ renaming = null }) { Text("إلغاء") } },
            containerColor = C.panel
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("تحذف هالمحادثة؟") },
            text = { Text(c.title) },
            confirmButton = { TextButton({ vm.deleteConversation(c.id); deleting = null }) { Text("حذف", color = C.bad) } },
            dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } },
            containerColor = C.panel
        )
    }
}

@Composable
private fun DrawerItem(label: String, onClick: () -> Unit) {
    Text(
        label, color = C.text, fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    )
}

/** شريط علوي بسيط يُستخدم بكل الشاشات */
@Composable
fun TopBar(title: String, subtitle: String? = null, leading: String, onLeading: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.bg)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onLeading) { Text(leading, color = C.text, fontSize = 20.sp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
    HorizontalDivider(color = C.line)
}

@Composable
fun engineLabel(vm: MainViewModel): Pair<String, Color> {
    val st by vm.engineState.collectAsState()
    return when (val s = st) {
        is EngineState.Ready -> s.name to C.ok
        is EngineState.Loading -> "أحمّل ${s.name}..." to C.accent
        is EngineState.Failed -> s.message to C.bad
        EngineState.Empty -> "ماكو نموذج، روح للنماذج" to C.bad
    }
}

@Composable
fun Dot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}
