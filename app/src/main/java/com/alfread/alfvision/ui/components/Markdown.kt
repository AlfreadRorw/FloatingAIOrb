package com.alfread.alfvision.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.ChatLine

private val bulletRegex = Regex("^(\\s*)[*\\-+]\\s+(.*)$")
private val headingRegex = Regex("^#{1,6}\\s+(.*)$")
private val ruleRegex = Regex("^\\s*([-*_]\\s*){3,}$")

/** Mengubah markdown sederhana (**bold**, *italic*, `code`, bullet, heading) jadi teks berformat, tanpa simbol mentah. */
fun renderMarkdown(source: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var started = false
    var inFence = false
    for (raw in source.replace("\r\n", "\n").split("\n")) {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("```")) {
            inFence = !inFence
            continue
        }
        if (!inFence && ruleRegex.matches(raw)) continue
        if (started) append("\n")
        started = true
        if (inFence) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(raw) }
            continue
        }
        val heading = headingRegex.matchEntire(trimmed)
        val bullet = bulletRegex.matchEntire(raw)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.08.em)) {
                appendInline(heading.groupValues[1], codeBackground)
            }
            bullet != null -> {
                val indent = " ".repeat((bullet.groupValues[1].length / 2) * 2)
                append(indent + "• ")
                appendInline(bullet.groupValues[2], codeBackground)
            }
            else -> appendInline(raw, codeBackground)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String, codeBackground: Color) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(text.substring(i + 2, end), codeBackground) }
                i = end + 2
            } else {
                i += 2
            }
        } else if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(text.substring(i + 1, end)) }
                i = end + 1
            } else {
                i += 1
            }
        } else if (c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1 && !text[end - 1].isWhitespace()) {
                flush()
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(text.substring(i + 1, end), codeBackground) }
                i = end + 1
            } else {
                i += 1
            }
        } else {
            plain.append(c)
            i += 1
        }
    }
    flush()
}

/** Isi bubble chat: thumbnail gambar layar (jika ada) + teks yang sudah dirender dari markdown. */
@Composable
fun MessageContent(line: ChatLine, textColor: Color, codeBackground: Color, textSize: Int = 14) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val thumb = line.imageBytes
        if (thumb != null) {
            val bitmap = remember(thumb) { BitmapFactory.decodeByteArray(thumb, 0, thumb.size)?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Gambar layar terlampir",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
        val rendered = remember(line.content, codeBackground) { renderMarkdown(line.content, codeBackground) }
        Text(rendered, color = textColor, fontSize = textSize.sp, lineHeight = (textSize + 6).sp)
    }
}
