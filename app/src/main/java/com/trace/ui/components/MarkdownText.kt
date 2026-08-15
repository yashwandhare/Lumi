package com.trace.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle

/**
 * Markdown, rendered with Trace's own type and colours.
 *
 * The model emits markdown whether or not anyone asked it to — numbered lists, `**bold**`, fenced code,
 * inline backticks — so a plain `Text` showed users the syntax instead of the formatting. That is worse
 * than either extreme: a reply full of literal asterisks reads as broken rather than as plain text.
 *
 * **Body text stays serif; code goes monospace.** The design language puts prose in serif and reserves
 * sans for labels, and neither can render code — column alignment and the difference between `l`, `1`,
 * and `I` are the whole point of a monospace face. So code is the one place the type scale is departed
 * from, and only for code.
 *
 * Sizes are passed in rather than read here, because the transcript sets its own reading size and this
 * has to match the surrounding turn exactly — a reply that changed size the moment it contained a list
 * would be its own bug.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    ProvideTextStyle(value = style.copy(color = color)) {
        RichText(
            modifier = modifier,
            style = RichTextStyle(
                codeBlockStyle = CodeBlockStyle(
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        // A step down from body text: code lines are long, and wrapping a shell command
                        // mid-flag is harder to read than slightly smaller type.
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.45f,
                        color = color,
                    ),
                ),
                stringStyle = RichTextStringStyle(
                    // Inline `code` gets the monospace face too, without a background — a highlighted
                    // span inside a serif sentence fragments the line more than it clarifies it.
                    codeStyle = SpanStyle(fontFamily = FontFamily.Monospace),
                    linkStyle = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ),
        ) {
            Markdown(content = text)
        }
    }
}
