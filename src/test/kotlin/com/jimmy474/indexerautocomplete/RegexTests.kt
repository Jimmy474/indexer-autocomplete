package com.jimmy474.indexerautocomplete

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jimmy474.indexerautocomplete.plugin.IndexReference
import com.jimmy474.indexerautocomplete.plugin.LibraryIndexCompletionProvider
import com.jimmy474.indexerautocomplete.plugin.toIndexReference

class RegexTests: BasePlatformTestCase() {
    val regex = LibraryIndexCompletionProvider.INDEX_REFERENCE_FULL_REGEX

    fun `test full regex`(){
        assertNotNull(regex.matchEntire("@`net.fabricmc.fabric.api.event.Event#invoke()`"))
    }

    fun `test path with method`() {
        val match = regex.matchEntire("@`net.fabricmc.fabric.api.event.Event#invoke()`")
        assertNotNull(match)
        val indexReference = match?.toIndexReference()
        assertNotNull(indexReference)
        assertNotNull(indexReference!!.className)
        assertNotNull(indexReference.classNameRange)
        assertNotNull(indexReference.memberName)
        assertNotNull(indexReference.memberNameRange)
        assertEquals(indexReference.memberType, IndexReference.MemberType.METHOD)
        assertEquals(indexReference.fullDisplayFlag,false)
        assertEquals(indexReference.className,"Event")
        assertEquals(indexReference.classNameRange, TextRange(32, 37))
        assertEquals(indexReference.memberName,"invoke")
        assertEquals(indexReference.memberNameRange, TextRange(38, 44))
        assertSameElements(indexReference.fqn, listOf("net","fabricmc", "fabric", "api", "event", "Event"))
    }

    fun `test path with method full display flag`() {
        val match = regex.matchEntire("@`net.fabricmc.fabric.api.event.Event#invoke(...)`")
        assertNotNull(match)
        val indexReference = match?.toIndexReference()
        assertNotNull(indexReference)
        assertNotNull(indexReference!!.className)
        assertNotNull(indexReference.classNameRange)
        assertNotNull(indexReference.memberName)
        assertNotNull(indexReference.memberNameRange)
        assertEquals(indexReference.memberType, IndexReference.MemberType.METHOD)
        assertEquals(indexReference.fullDisplayFlag,true)
        assertEquals(indexReference.className,"Event")
        assertEquals(indexReference.classNameRange, TextRange(32, 37))
        assertEquals(indexReference.memberName,"invoke")
        assertEquals(indexReference.memberNameRange, TextRange(38, 44))
        assertSameElements(indexReference.fqn, listOf("net","fabricmc", "fabric", "api", "event", "Event"))
    }

    fun `test path with field`() {
        val match = regex.matchEntire("@`net.fabricmc.fabric.api.event.Event#invoke`")
        assertNotNull(match)
        val indexReference = match?.toIndexReference()
        assertNotNull(indexReference)
        assertNotNull(indexReference!!.className)
        assertNotNull(indexReference.classNameRange)
        assertNotNull(indexReference.memberName)
        assertNotNull(indexReference.memberNameRange)
        assertEquals(indexReference.memberType, IndexReference.MemberType.FIELD)
        assertEquals(indexReference.fullDisplayFlag,false)
        assertEquals(indexReference.className,"Event")
        assertEquals(indexReference.classNameRange, TextRange(32, 37))
        assertEquals(indexReference.memberName,"invoke")
        assertEquals(indexReference.memberNameRange, TextRange(38, 44))
        assertSameElements(indexReference.fqn, listOf("net","fabricmc", "fabric", "api", "event", "Event"))
    }

    fun `test path with class`() {
        val match = regex.matchEntire("@`net.fabricmc.fabric.api.event.Event`")
        assertNotNull(match)
        val indexReference = match?.toIndexReference()
        assertNotNull(indexReference)
        assertNotNull(indexReference!!.className)
        assertNotNull(indexReference.classNameRange)
        assertNull(indexReference.memberName)
        assertNull(indexReference.memberNameRange)
        assertEquals(indexReference.memberType, IndexReference.MemberType.NONE)
        assertEquals(indexReference.fullDisplayFlag,false)
        assertEquals(indexReference.className,"Event")
        assertEquals(indexReference.classNameRange, TextRange(32, 37))
        assertSameElements(indexReference.fqn, listOf("net","fabricmc", "fabric", "api", "event", "Event"))
    }

    fun `test malformed path syntax does not match`() {
        assertNull(regex.matchEntire("@`hyphen.not-allowed`"))
    }

    fun `test malformed member syntax does not match`() {
        assertNull(regex.matchEntire("@`net.fabricmc.SomeClass#missing(`"))
    }

}
