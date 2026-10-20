package com.basira.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** عرض مبسط للماركداون: عناوين، قوائم، غامق، كود. */
@Composable
fun MarkdownText(src: String) {
    val parts = src.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1) {
                val nl = part.indexOf('\n')
                val lang = if (nl >= 0) part.substring(0, nl).trim() else ""
                val code = (if (nl >= 0) part.substring(nl + 1) else part).trimEnd('\n')
                CodeBlock(lang, code)
            } else if (part.isNotBlank()) {
                Text(
                    text = renderBlock(part.trim('\n')),
                    color = C.text,
                    fontSize = 16.sp,
                    lineHeight = 27.sp,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CodeBlock(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(C.code)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(C.panel)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lang, color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { clip.setText(AnnotatedString(code)) }) {
                    Text("نسخ", color = C.muted, fontSize = 12.sp)
                }
            }
            Text(
                code,
                color = C.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

private fun renderBlock(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split('\n')
    lines.forEachIndexed { i, raw ->
        val heading = Regex("^(#{1,4})\\s+(.*)").find(raw)
        val bullet = Regex("^\\s*[-*•]\\s+(.*)").find(raw)
        val numbered = Regex("^\\s*(\\d+)[.)]\\s+(.*)").find(raw)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (22 - heading.groupValues[1].length * 2).sp, color = C.text)) {
                inline(heading.groupValues[2])
            }
            bullet != null -> { append("  •  "); inline(bullet.groupValues[1]) }
            numbered != null -> { append("  ${numbered.groupValues[1]}.  "); inline(numbered.groupValues[2]) }
            else -> inline(raw)
        }
        if (i < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.inline(s: String) {
    val re = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var last = 0
    for (m in re.findAll(s)) {
        append(s.substring(last, m.range.first))
        if (m.groupValues[1].isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        } else {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = C.code, fontSize = 14.sp)) {
                append(" ${m.groupValues[2]} ")
            }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}
