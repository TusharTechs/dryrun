package com.dryrun.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.dryrun.app.coach.HedgeDetector

/**
 * Underlines hedges live, as the user types.
 *
 * Marks, never blocks. Offsets are unchanged, so the caret and selection
 * behave exactly as they would with no transformation at all -- the identity
 * offset mapping below is what guarantees that.
 */
class HedgeUnderline(private val colour: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightHedges(text.text, colour), OffsetMapping.Identity)
}

/** Shared by the input field and the transcript, so a hedge looks the same in both. */
fun highlightHedges(text: String, colour: Color): AnnotatedString {
    val hits = HedgeDetector.analyse(text).hits
    if (hits.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        hits.forEach { hit ->
            addStyle(
                SpanStyle(color = colour, textDecoration = TextDecoration.Underline),
                hit.range.first,
                (hit.range.last + 1).coerceAtMost(text.length)
            )
        }
    }
}

@Composable
fun rememberHedgeUnderline(colour: Color): VisualTransformation =
    remember(colour) { HedgeUnderline(colour) }

private typealias OffsetMapping = androidx.compose.ui.text.input.OffsetMapping
