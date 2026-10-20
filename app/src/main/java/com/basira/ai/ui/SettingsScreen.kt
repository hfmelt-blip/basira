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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basira.ai.data.Prefs

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val p = vm.prefs
    var name by remember { mutableStateOf(p.appName) }
    var prompt by remember { mutableStateOf(p.systemPrompt) }
    var temp by remember { mutableFloatStateOf(p.temperature) }
    var ctx by remember { mutableIntStateOf(p.contextSize) }
    var threads by remember { mutableIntStateOf(p.threads) }
    var maxAnswer by remember { mutableIntStateOf(p.maxAnswer) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar("الإعدادات", leading = "→", onLeading = { vm.screen = Screen.CHAT })
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                Label("اسم البرنامج")
                OutlinedTextField(name, { name = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true)
                Label("الشخصية (التعليمات الأساسية)")
                OutlinedTextField(prompt, { prompt = it; saved = false }, Modifier.fillMaxWidth(), minLines = 5)
                TextButton(onClick = { prompt = Prefs.DEFAULT_PROMPT }) { Text("رجّع الافتراضي", color = C.muted) }
            }
            SectionCard {
                Label("الإبداع: ${"%.2f".format(temp)}")
                Text("قليل = أجوبة دقيقة وثابتة، عالي = أجوبة متنوعة وإبداعية", color = C.muted, fontSize = 12.sp)
                Slider(temp, { temp = it; saved = false }, valueRange = 0f..1.5f, colors = sliderColors())

                Label("حجم الذاكرة (السياق)")
                Text("أكبر = يتذكر محادثة أطول بس يستهلك رام أكثر", color = C.muted, fontSize = 12.sp)
                Choices(listOf(2048, 4096, 8192), ctx) { ctx = it; saved = false }

                Label("أقصى طول للجواب")
                Choices(listOf(512, 1024, 2048), maxAnswer) { maxAnswer = it; saved = false }

                Label("عدد الأنوية: ${if (threads == 0) "تلقائي" else threads}")
                Slider(threads.toFloat(), { threads = it.toInt(); saved = false }, valueRange = 0f..8f, steps = 7, colors = sliderColors())
            }
            Button(
                onClick = { vm.saveSettings(name, prompt, temp, ctx, threads, maxAnswer); saved = true },
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.accentInk),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saved) "انحفظ ✓" else "حفظ", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.panel2)

@Composable
private fun Choices(options: List<Int>, value: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == value
            Text(
                "$o", fontSize = 13.sp, color = if (on) C.accentInk else C.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) C.accent else C.panel)
                    .border(1.dp, if (on) C.accent else C.line, RoundedCornerShape(20.dp))
                    .clickable { onPick(o) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
