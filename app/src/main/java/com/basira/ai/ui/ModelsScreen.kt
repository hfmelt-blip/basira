package com.basira.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basira.ai.data.DlState
import com.basira.ai.data.MODEL_CATALOG
import com.basira.ai.llm.EngineState
import java.io.File

@Composable
fun ModelsScreen(vm: MainViewModel) {
    val engine by vm.engineState.collectAsState()
    val activePath = (engine as? EngineState.Ready)?.path
    val loadingName = (engine as? EngineState.Loading)?.name
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar("النماذج", "العقل اللي يشتغل على تلفونك", leading = "→", onLeading = { vm.screen = Screen.CHAT })
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (engine is EngineState.Failed) {
                Text((engine as EngineState.Failed).message, color = C.bad, fontSize = 14.sp)
            }
            Text("المساحة الفارغة: ${"%.1f".format(vm.models.freeSpaceMb() / 1024.0)} GB", color = C.muted, fontSize = 12.sp)

            MODEL_CATALOG.forEach { m ->
                val file = File(vm.models.dir, m.file)
                val exists = vm.localModels.any { it.name == m.file }
                val dl = vm.downloads[m.file] ?: DlState.None
                SectionCard {
                    Text(m.name, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(m.description, color = C.muted, fontSize = 13.sp)
                    Text("الحجم: ${m.sizeMb} MB", color = C.muted, fontSize = 12.sp)
                    when {
                        exists -> ModelActions(vm, file, active = activePath == file.absolutePath,
                            loading = loadingName == file.nameWithoutExtension)
                        dl is DlState.Running -> {
                            LinearProgressIndicator(
                                progress = { dl.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = C.accent, trackColor = C.panel2
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("جاري التنزيل ${(dl.progress * 100).toInt()}%  (${dl.doneMb} MB)",
                                    color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { vm.cancelDownload(m) }) { Text("إلغاء", color = C.muted) }
                            }
                            Text("تقدر تطلع من التطبيق، التنزيل يكمل بالخلفية.", color = C.muted, fontSize = 11.sp)
                        }
                        else -> {
                            if (dl is DlState.Failed) Text(dl.reason, color = C.bad, fontSize = 13.sp)
                            Button(
                                onClick = { vm.download(m) },
                                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.accentInk),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("تنزيل") }
                        }
                    }
                }
            }

            val others = vm.localModels.filter { f -> MODEL_CATALOG.none { it.file == f.name } }
            if (others.isNotEmpty()) {
                Text("نماذج مستوردة", color = C.muted, fontSize = 13.sp)
                others.forEach { f ->
                    SectionCard {
                        Text(f.nameWithoutExtension, color = C.text, fontSize = 15.sp)
                        Text("${f.length() / (1024 * 1024)} MB", color = C.muted, fontSize = 12.sp)
                        ModelActions(vm, f, active = activePath == f.absolutePath, loading = loadingName == f.nameWithoutExtension)
                    }
                }
            }

            SectionCard {
                Text("استيراد نموذج من التلفون", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("إذا عندك ملف بصيغة .gguf (مثلاً نزّلته من Hugging Face بالمتصفح)، تكدر تضيفه من هنا.",
                    color = C.muted, fontSize = 13.sp)
                vm.importStatus?.let { Text(it, color = C.muted, fontSize = 13.sp) }
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("اختار ملف GGUF", color = C.text) }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun ModelActions(vm: MainViewModel, file: File, active: Boolean, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            active -> Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(C.ok); Text("  شغّال حالياً", color = C.ok, fontSize = 13.sp)
            }
            loading -> Text("أحمّل...", color = C.accent, fontSize = 13.sp)
            else -> Button(
                onClick = { vm.loadModel(file); vm.screen = Screen.CHAT },
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.accentInk),
                shape = RoundedCornerShape(10.dp)
            ) { Text("استخدمه") }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { vm.deleteModel(file) }) { Text("حذف", color = C.bad) }
    }
}
