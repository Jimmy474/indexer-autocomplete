package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSnippetSyntaxTest {
    @Test
    fun `parses fenced file reference without regions`() {
        val snippet = parse("<<< @/src/main/Main.java\n<<<")

        assertEquals("@/src/main/Main.java", snippet.path.value)
        assertEquals("Main.java", snippet.fileName!!.value)
        assertTrue(snippet.regions.isEmpty())
        assertTrue(snippet.annotations.isEmpty())
    }

    @Test
    fun `parses compact single line reference without annotations`() {
        val snippet = parse("<<< @/src/main/Main.java#intro <<<")

        assertEquals("@/src/main/Main.java", snippet.path.value)
        assertEquals(listOf("#intro"), snippet.regions.map { it.value })
        assertTrue(snippet.annotations.isEmpty())
    }

    @Test
    fun `parses include and exclude regions`() {
        val snippet = parse("<<< @/docs/sample.txt#intro!details\n<<<")

        assertEquals(listOf("#intro", "!details"), snippet.regions.map { it.value })
    }

    @Test
    fun `parses multiline annotations without treating list items as markdown blocks`() {
        val snippet = parse(
            """
                <<< @/docs/sample.txt#intro
                1. first line
                   second line
                2. another annotation
                <<<
            """.trimIndent()
        )

        assertEquals(listOf("1", "2"), snippet.annotations.map { it.index.value })
        assertEquals("first line\n   second line", snippet.annotations.first().text!!.value)
    }

    @Test
    fun `builds canonical fenced markdown reference`() {
        val snippet = parse(
            """
                <<< @/docs/sample.txt!details#intro
                2. second
                1. first
                <<<
            """.trimIndent()
        )

        assertEquals(
            """
                <<< @/docs/sample.txt#intro!details
                	1. first
                	2. second
                <<<
            """.trimIndent(),
            snippet.toMarkdownReference()
        )
    }

    @Test
    fun `allows partial include and exclude markers for inspections`() {
        assertTrue(CodeSnippetSyntax.REGEX.matches("<<< @/docs/sample.txt#\n<<<"))
        assertTrue(CodeSnippetSyntax.REGEX.matches("<<< @/docs/sample.txt!\n<<<"))
    }

    @Test
    fun `rejects unclosed single line reference`() {
        assertFalse(CodeSnippetSyntax.REGEX.matches("<<< @/docs/sample.txt#intro"))
    }

    @Test
    fun `rejects references without root symbol`() {
        assertFalse(CodeSnippetSyntax.REGEX.matches("<<< docs/sample.txt\n<<<"))
    }

    @Test
    fun `rejects references with spaces in path`() {
        assertFalse(CodeSnippetSyntax.REGEX.matches("<<< @/docs/my file.txt\n<<<"))
    }

    private fun parse(text: String): CodeSnippet {
        return CodeSnippetSyntax.REGEX.matchEntire(text)!!.toCodeSnippetReference()
    }
}
