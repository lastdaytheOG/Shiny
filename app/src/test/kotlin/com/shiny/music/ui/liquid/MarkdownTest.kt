package com.shiny.music.ui.liquid

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    private fun span(text: MarkdownText, style: MarkdownStyle) =
        text.spans.filter { it.style == style }.map { text.text.substring(it.start, it.end) }

    @Test
    fun `plain text is left alone`() {
        assertEquals(MarkdownText("Nothing special here.", emptyList()), parseMarkdown("Nothing special here."))
        assertEquals(MarkdownText("", emptyList()), parseMarkdown(""))
    }

    @Test
    fun `bold in either spelling`() {
        val parsed = parseMarkdown("A **big** change and a __second__ one")
        assertEquals("A big change and a second one", parsed.text)
        assertEquals(listOf("big", "second"), span(parsed, MarkdownStyle.BOLD))
    }

    @Test
    fun `italics in either spelling`() {
        val parsed = parseMarkdown("Now *faster* and _calmer_")
        assertEquals("Now faster and calmer", parsed.text)
        assertEquals(listOf("faster", "calmer"), span(parsed, MarkdownStyle.ITALIC))
    }

    @Test
    fun `emphasis can be nested`() {
        val parsed = parseMarkdown("**bold with *italic* inside** and ***both***")
        assertEquals("bold with italic inside and both", parsed.text)
        assertEquals(listOf("bold with italic inside", "both"), span(parsed, MarkdownStyle.BOLD))
        assertEquals(listOf("italic", "both"), span(parsed, MarkdownStyle.ITALIC))

        val reversed = parseMarkdown("*italic with **bold** inside*")
        assertEquals("italic with bold inside", reversed.text)
        assertEquals(listOf("italic with bold inside"), span(reversed, MarkdownStyle.ITALIC))
        assertEquals(listOf("bold"), span(reversed, MarkdownStyle.BOLD))
    }

    @Test
    fun `marks that are not emphasis stay as written`() {
        listOf("2 * 3 * 4", "snake_case_name", "a ** b", "50% *", "unclosed *star", "unclosed **stars").forEach {
            assertEquals(MarkdownText(it, emptyList()), parseMarkdown(it))
        }
    }

    @Test
    fun `inline code is taken literally`() {
        val parsed = parseMarkdown("Run `adb *shell*` first")
        assertEquals("Run adb *shell* first", parsed.text)
        assertEquals(listOf("adb *shell*"), span(parsed, MarkdownStyle.CODE))
        assertEquals(emptyList<String>(), span(parsed, MarkdownStyle.ITALIC))
        assertEquals("a lone ` tick", parseMarkdown("a lone ` tick").text)
    }

    @Test
    fun `a link keeps its label and carries its address`() {
        val parsed = parseMarkdown("See [the **site**](https://shinymusic.in/x) now")
        assertEquals("See the site now", parsed.text)
        val link = parsed.spans.single { it.style == MarkdownStyle.LINK }
        assertEquals("the site", parsed.text.substring(link.start, link.end))
        assertEquals("https://shinymusic.in/x", link.url)
        assertEquals(listOf("site"), span(parsed, MarkdownStyle.BOLD))
    }

    @Test
    fun `a bare address becomes a link without the sentence's punctuation`() {
        val parsed = parseMarkdown("Details: https://example.com/a?b=1. Thanks (see http://x.org).")
        assertEquals("Details: https://example.com/a?b=1. Thanks (see http://x.org).", parsed.text)
        assertEquals(listOf("https://example.com/a?b=1", "http://x.org"), parsed.spans.map { it.url })
        assertEquals(listOf("https://example.com/a?b=1", "http://x.org"), span(parsed, MarkdownStyle.LINK))
    }

    @Test
    fun `brackets that are not a link stay as written`() {
        listOf("[x] done", "array[0](1", "[]()", "[label]()").forEach {
            assertEquals(it, parseMarkdown(it).text)
            assertEquals(emptyList<MarkdownSpan>(), parseMarkdown(it).spans)
        }
    }

    @Test
    fun `list items get a bullet, with any marker and with indentation kept`() {
        val parsed = parseMarkdown("- one\n* two **bold**\n  + nested\nnot - a list")
        assertEquals("•  one\n•  two bold\n  •  nested\nnot - a list", parsed.text)
        assertEquals(listOf("bold"), span(parsed, MarkdownStyle.BOLD))
    }

    @Test
    fun `headings lose their hashes and are marked`() {
        val parsed = parseMarkdown("## What's new\nBody\n#hashtag\n### Fixes ###")
        assertEquals("What's new\nBody\n#hashtag\nFixes", parsed.text)
        assertEquals(listOf("What's new", "Fixes"), span(parsed, MarkdownStyle.HEADING))
    }

    @Test
    fun `a backslash keeps a mark literal`() {
        val parsed = parseMarkdown("""5 \* 3 and \_private\_ and C:\Users""")
        assertEquals("""5 * 3 and _private_ and C:\Users""", parsed.text)
        assertEquals(emptyList<MarkdownSpan>(), parsed.spans)
    }

    @Test
    fun `windows line endings are handled`() {
        assertEquals("•  one\n•  two", parseMarkdown("- one\r\n- two").text)
    }

    @Test
    fun `spans are ordered outermost first`() {
        val parsed = parseMarkdown("**a *b* c**")
        assertEquals(listOf(MarkdownStyle.BOLD, MarkdownStyle.ITALIC), parsed.spans.map { it.style })
    }
}
