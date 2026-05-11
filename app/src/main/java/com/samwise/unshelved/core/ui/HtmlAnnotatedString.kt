package com.samwise.unshelved.core.ui

import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private val TIMESTAMP_REGEX = Regex("""\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b""")

internal fun buildAnnotatedStringFromSpanned(
    spanned: Spanned,
    linkColor: Color = Color.Unspecified,
    onTimestampClick: ((Double) -> Unit)? = null,
): AnnotatedString {
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
                is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            }
        }

        if (onTimestampClick != null) {
            val text = spanned.toString()
            TIMESTAMP_REGEX.findAll(text).forEach { match ->
                val hours = match.groupValues[1].toInt()
                val minutes = match.groupValues[2].toInt()
                val seconds = match.groupValues[3].let { if (it.isEmpty()) 0 else it.toInt() }
                val totalSeconds = if (match.groupValues[3].isEmpty()) {
                    // M:SS or MM:SS format — group 1 is minutes, group 2 is seconds
                    match.groupValues[1].toInt() * 60.0 + match.groupValues[2].toInt()
                } else {
                    // H:MM:SS format
                    hours * 3600.0 + minutes * 60.0 + seconds
                }
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    match.range.first,
                    match.range.last + 1,
                )
                val cb = onTimestampClick
                addLink(
                    LinkAnnotation.Clickable("ts") { cb(totalSeconds) },
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
    }
}
