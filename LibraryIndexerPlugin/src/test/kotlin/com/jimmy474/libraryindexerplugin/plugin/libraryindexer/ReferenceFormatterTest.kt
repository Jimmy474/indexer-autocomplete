package com.jimmy474.libraryindexerplugin.plugin.libraryindexer

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceFormatterTest {
    private val target = TargetInfo(
        classFqn = "net.fabricmc.SomeClass",
        memberName = "getName",
        returnTypeFqn = "java.lang.String",
        parameters = listOf(ParameterData("prefix", "java.lang.String"))
    )

    @Test
    fun `formats custom name before any target data`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass|Display name`")!!.toIndexReference()

        assertEquals("Display name", ReferenceFormatter.formatFoldedText(reference, target, "fallback"))
    }

    @Test
    fun `formats method with parameter types`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass#getName(java.lang.String):`")!!.toIndexReference()

        assertEquals("SomeClass.getName(String)", ReferenceFormatter.formatFoldedText(reference, target, "fallback"))
    }

    @Test
    fun `formats method with parameter names`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass#getName(java.lang.String),`")!!.toIndexReference()

        assertEquals("SomeClass.getName(prefix)", ReferenceFormatter.formatFoldedText(reference, target, "fallback"))
    }

    @Test
    fun `formats method with full parameter type and name`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass#getName(java.lang.String)*;`")!!.toIndexReference()

        assertEquals("net.fabricmc.SomeClass.getName(java.lang.String prefix)", ReferenceFormatter.formatFoldedText(reference, target, "fallback"))
    }

    @Test
    fun `formats return type using simple names by default`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass#getName(java.lang.String)>`")!!.toIndexReference()

        assertEquals("String", ReferenceFormatter.formatFoldedText(reference, target, "fallback"))
    }

    @Test
    fun `formats return type using full names when requested`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass#getName(java.lang.String)+>`")!!.toIndexReference()

        assertEquals("java.lang.String", ReferenceFormatter.formatFoldedText(reference, target, "fallback"))
    }

    @Test
    fun `uses fallback when target is absent`() {
        val reference = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire("@`net.fabricmc.SomeClass#getName(java.lang.String)`")!!.toIndexReference()

        assertEquals("fallback", ReferenceFormatter.formatFoldedText(reference, null, "fallback"))
    }
}
