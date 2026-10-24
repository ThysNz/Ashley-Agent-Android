package nz.thys.ashley.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.thys.ashley.ui.components.SmallButton
import nz.thys.ashley.ui.theme.Ashley

/** Small markdown renderer, same coverage as md() in app.js: code blocks, inline code, bold, headings, lists. */
@Composable
fun MarkdownText(text: String) {
    val fence = Regex("```[a-zA-Z0-9_+-]*\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
    SelectionContainer {
        Column {
            var last = 0
            for (m in fence.findAll(text)) {
                Prose(text.substring(last, m.range.first))
                CodeBlock(m.groupValues[1].trimEnd())
                last = m.range.last + 1
            }
            Prose(text.substring(last))
        }
    }
}

@Composable
private fun Prose(chunk: String) {
    val c = Ashley.colors
    val blocks = chunk.trim().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    for (b in blocks) {
        val lines = b.lines()
        val isList = lines.all { it.trimStart().matches(Regex("^([-*+]|\\d+[.)])\\s+.*")) }
        when {
            lines.size == 1 && b.trimStart().startsWith("#") ->
                Text(inline(b.trimStart().trimStart('#').trim()), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.text, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            isList -> Column(Modifier.padding(vertical = 4.dp)) {
                lines.forEach { l ->
                    val ordered = Regex("^\\s*(\\d+)[.)]\\s+").find(l)
                    val bullet = ordered?.groupValues?.get(1)?.let { "$it." } ?: "•"
                    val body = l.trimStart().replaceFirst(Regex("^([-*+]|\\d+[.)])\\s+"), "")
                    Row(Modifier.padding(vertical = 1.dp)) {
                        Text(bullet, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(inline(body), color = c.text, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
            }
            else -> Text(inline(b), color = c.text, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    val c = Ashley.colors
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(c.codeBg)) {
        Text(
            code, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = c.text, lineHeight = 18.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, top = 10.dp, end = 64.dp, bottom = 10.dp),
        )
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            SmallButton("Copy") { clipboard.setText(AnnotatedString(code)) }
        }
    }
}

/** `code` and **bold** inside a line. */
@Composable
private fun inline(s: String): AnnotatedString {
    val c = Ashley.colors
    val token = Regex("`([^`]+)`|\\*\\*([^*]+)\\*\\*")
    return buildAnnotatedString {
        var last = 0
        for (m in token.findAll(s)) {
            append(s.substring(last, m.range.first))
            if (m.groupValues[1].isNotEmpty()) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg, fontSize = 12.5.sp)) { append(m.groupValues[1]) }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[2]) }
            }
            last = m.range.last + 1
        }
        append(s.substring(last))
    }
}
