package com.shiny.music.ui.liquid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * The small part of Markdown that release notes use, turned into styled text: bold, italics,
 * inline code, links, headings and bullet lists. Anything else is shown as written.
 *
 * Parsing ([parseMarkdown]) produces plain data, so it is tested without Compose;
 * [markdownToAnnotatedString] dresses that data in the current theme.
 */
data class MarkdownText(val text: String, val spans: List<MarkdownSpan>)

/** [start] inclusive, [end] exclusive, as offsets into [MarkdownText.text]. [url] is set for links. */
data class MarkdownSpan(val start: Int, val end: Int, val style: MarkdownStyle, val url: String? = null)

enum class MarkdownStyle { BOLD, ITALIC, CODE, LINK, HEADING }

/** The tag of the string annotation that carries a link's address. */
const val MARKDOWN_URL_TAG = "URL"

private const val BULLET = "•  "

fun parseMarkdown(source: String): MarkdownText {
    val out = StringBuilder()
    val spans = mutableListOf<MarkdownSpan>()
    source.replace("\r\n", "\n").split('\n').forEachIndexed { index, line ->
        if (index > 0) out.append('\n')
        val heading = headingText(line)
        val bullet = if (heading == null) bulletText(line) else null
        val lineStart = out.length
        when {
            heading != null -> {
                parseInline(heading, out, spans)
                if (out.length > lineStart) spans += MarkdownSpan(lineStart, out.length, MarkdownStyle.HEADING)
            }
            bullet != null -> {
                out.append(bullet.first).append(BULLET)
                parseInline(bullet.second, out, spans)
            }
            else -> parseInline(line, out, spans)
        }
    }
    return MarkdownText(out.toString(), spans.sortedWith(compareBy({ it.start }, { -it.end })))
}

/** `## Title` gives "Title"; a line that is not a heading gives null. */
private fun headingText(line: String): String? {
    val hashes = line.takeWhile { it == '#' }.length
    if (hashes !in 1..6 || line.getOrNull(hashes)?.isWhitespace() != true) return null
    return line.substring(hashes).trim().trimEnd('#').trimEnd()
}

/** `  - item` gives the indentation and "item"; a line that is not a list item gives null. */
private fun bulletText(line: String): Pair<String, String>? {
    val indent = line.takeWhile { it == ' ' || it == '\t' }
    val marker = line.getOrNull(indent.length) ?: return null
    if (marker !in "-*+" || line.getOrNull(indent.length + 1)?.isWhitespace() != true) return null
    return indent to line.substring(indent.length + 2).trimStart()
}

private fun parseInline(source: String, out: StringBuilder, spans: MutableList<MarkdownSpan>) {
    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            // A backslash keeps the next punctuation mark as it is.
            c == '\\' && source.getOrNull(i + 1)?.let { !it.isLetterOrDigit() && !it.isWhitespace() } == true -> {
                out.append(source[i + 1])
                i += 2
            }

            c == '`' -> {
                val close = source.indexOf('`', i + 1)
                if (close > i + 1) {
                    val start = out.length
                    out.append(source, i + 1, close)
                    spans += MarkdownSpan(start, out.length, MarkdownStyle.CODE)
                    i = close + 1
                } else {
                    out.append(c)
                    i++
                }
            }

            c == '[' -> {
                val link = linkAt(source, i)
                if (link != null) {
                    val start = out.length
                    parseInline(link.label, out, spans)
                    if (out.length > start) spans += MarkdownSpan(start, out.length, MarkdownStyle.LINK, link.url)
                    i = link.end
                } else {
                    out.append(c)
                    i++
                }
            }

            c == '*' || c == '_' -> {
                val double = source.getOrNull(i + 1) == c
                val width = if (double) 2 else 1
                val close = closingDelimiter(source, i, c, width)
                if (close != null) {
                    val start = out.length
                    parseInline(source.substring(i + width, close), out, spans)
                    spans += MarkdownSpan(start, out.length, if (double) MarkdownStyle.BOLD else MarkdownStyle.ITALIC)
                    i = close + width
                } else {
                    out.append(source, i, i + width)
                    i += width
                }
            }

            source.startsWith("https://", i) || source.startsWith("http://", i) -> {
                var end = i
                while (end < source.length && !source[end].isWhitespace() && source[end] !in "<>\"") end++
                // Punctuation that ends the sentence is not part of the address.
                while (end > i && source[end - 1] in ".,;:!?)]}'") end--
                val url = source.substring(i, end)
                val start = out.length
                out.append(url)
                spans += MarkdownSpan(start, out.length, MarkdownStyle.LINK, url)
                i = end
            }

            else -> {
                out.append(c)
                i++
            }
        }
    }
}

private class Link(val label: String, val url: String, val end: Int)

/** `[label](url)` starting at [open], or null if that is not what is there. */
private fun linkAt(source: String, open: Int): Link? {
    var depth = 0
    var close = -1
    for (j in open until source.length) {
        when (source[j]) {
            '[' -> depth++
            ']' -> if (--depth == 0) { close = j; break }
        }
    }
    if (close < 0 || source.getOrNull(close + 1) != '(') return null
    val urlEnd = source.indexOf(')', close + 2)
    if (urlEnd < 0) return null
    val url = source.substring(close + 2, urlEnd).trim().substringBefore(' ')
    val label = source.substring(open + 1, close)
    if (url.isEmpty() || label.isEmpty()) return null
    return Link(label, url, urlEnd + 1)
}

/**
 * Where the emphasis opened at [open] closes, or null if it is not emphasis: the text inside must
 * be non-empty and must not begin or end with a space, and an underscore inside a word
 * (`snake_case`) is only an underscore.
 */
private fun closingDelimiter(source: String, open: Int, mark: Char, width: Int): Int? {
    val contentStart = open + width
    if (source.getOrNull(contentStart)?.isWhitespace() != false) return null
    if (mark == '_' && source.getOrNull(open - 1)?.isLetterOrDigit() == true) return null
    var j = contentStart
    while (j < source.length) {
        when {
            source[j] == '\\' -> j += 2
            source[j] == '`' -> {
                val codeEnd = source.indexOf('`', j + 1)
                j = if (codeEnd < 0) j + 1 else codeEnd + 1
            }
            source[j] == mark -> {
                val run = source.substring(j).takeWhile { it == mark }.length
                val closes = run >= width && j > contentStart && !source[j - 1].isWhitespace() &&
                    !(mark == '_' && source.getOrNull(j + run)?.isLetterOrDigit() == true)
                // "*a **b** c*": a longer run inside single emphasis belongs to the inner bold.
                if (closes && (width == 2 || run == 1 || run == 3)) return j + (run - width)
                j += run
            }
            else -> j++
        }
    }
    return null
}

/** [parseMarkdown] as text for a `Text`; links carry a [MARKDOWN_URL_TAG] annotation. */
fun MarkdownText.toAnnotatedString(linkColor: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { span ->
        val style = when (span.style) {
            MarkdownStyle.BOLD, MarkdownStyle.HEADING -> SpanStyle(fontWeight = FontWeight.SemiBold)
            MarkdownStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            MarkdownStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            MarkdownStyle.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        }
        addStyle(style, span.start, span.end)
        if (span.url != null) addStringAnnotation(MARKDOWN_URL_TAG, span.url, span.start, span.end)
    }
}

@Composable
fun markdownToAnnotatedString(source: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return remember(source, linkColor, codeBackground) {
        parseMarkdown(source).toAnnotatedString(linkColor, codeBackground)
    }
}
