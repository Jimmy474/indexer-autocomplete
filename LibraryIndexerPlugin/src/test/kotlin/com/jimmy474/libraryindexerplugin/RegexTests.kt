package com.jimmy474.libraryindexerplugin

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jimmy474.libraryindexerplugin.plugin.*

class RegexTests: BasePlatformTestCase() {
    val regex = LibraryIndex.INDEX_REFERENCE_REGEX

    fun `test full regex`(){
        assertNotNull(regex.matchEntire("@`package.Class`"))
        assertNotNull(regex.matchEntire("@`package.Class()`"))
        assertNotNull(regex.matchEntire("@`package.Class#field`"))
        assertNotNull(regex.matchEntire("@`package.Class#method()`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(java.lang.String, int)`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(java.util.List<java.lang.String>)`"))

        assertNotNull(regex.matchEntire("@`package.Class+`"))
        assertNotNull(regex.matchEntire("@`package.Class()+`"))
        assertNotNull(regex.matchEntire("@`package.Class#field+`"))
        assertNotNull(regex.matchEntire("@`package.Class#method()+`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(java.lang.String, int)+`"))

        assertNotNull(regex.matchEntire("@`package.Class-`"))
        assertNotNull(regex.matchEntire("@`package.Class()-`"))
        assertNotNull(regex.matchEntire("@`package.Class#field-`"))
        assertNotNull(regex.matchEntire("@`package.Class#method()-`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(java.lang.String, int)-`"))
    }

    fun `test malformed path syntax does not match`() {
        assertNull(regex.matchEntire("@`hyphen.not-allowed`"))
    }

    fun `test malformed member syntax does not match`() {
        assertNull(regex.matchEntire("@`net.fabricmc.SomeClass#missing(`"))
    }

    fun `test dots placeholder syntax does not match`() {
        assertNull(regex.matchEntire("@`package.Class#method(...)`"))
    }

    fun testClassReference() {
        val input = "@`package.Class`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 16),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.NONE,
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testConstructorReference() {
        val input = "@`package.Class()`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 18),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 17)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.METHOD,
                params = emptyList(),
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = true
                )
            ),
            result
        )
    }

    fun testFieldReference() {
        val input = "@`package.Class#field`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 22),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "field",
                    relativeRange = TextRange(16, 21)
                ),
                memberType = IndexReference.MemberType.FIELD,
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testMethodReference() {
        val input = "@`package.Class#method()`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 25),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "method",
                    relativeRange = TextRange(16, 24)
                ),
                memberType = IndexReference.MemberType.METHOD,
                params = emptyList(),
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testMethodOverloadReference() {
        val input = "@`package.Class#method(java.lang.String, int)`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 46),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "method",
                    relativeRange = TextRange(16, 45)
                ),
                memberType = IndexReference.MemberType.METHOD,
                params = listOf(
                    GroupInfo("java.lang.String", TextRange(23, 39)),
                    GroupInfo("int", TextRange(41, 44))
                ),
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = true,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testMethodGenericOverloadReference() {
        val input = "@`package.Class#method(java.util.List<java.lang.String>)`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 57),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "method",
                    relativeRange = TextRange(16, 56)
                ),
                memberType = IndexReference.MemberType.METHOD,
                params = listOf(
                    GroupInfo("java.util.List<java.lang.String>", TextRange(23, 55))
                ),
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = true,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testShortClassReference() {
        val input = "@`package.Class-`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 17),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.NONE,
                flags = Flags(
                    relativeRange = TextRange(15, 16),
                    shortName = true,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testShortConstructorReference() {
        val input = "@`package.Class()-`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 19),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 17)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.METHOD,
                params = emptyList(),
                flags = Flags(
                    relativeRange = TextRange(17, 18),
                    shortName = true,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = true
                )
            ),
            result
        )
    }

    fun testShortFieldReference() {
        val input = "@`package.Class#field-`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 23),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "field",
                    relativeRange = TextRange(16, 21)
                ),
                memberType = IndexReference.MemberType.FIELD,
                flags = Flags(
                    relativeRange = TextRange(21, 22),
                    shortName = true,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testShortMethodReference() {
        val input = "@`package.Class#method()-`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 26),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "method",
                    relativeRange = TextRange(16, 24)
                ),
                memberType = IndexReference.MemberType.METHOD,
                params = emptyList(),
                flags = Flags(
                    relativeRange = TextRange(24, 25),
                    shortName = true,
                    fullName = false,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testFullClassReference() {
        val input = "@`package.Class+`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 17),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.NONE,
                flags = Flags(
                    relativeRange = TextRange(15, 16),
                    shortName = false,
                    fullName = true,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testFullConstructorReference() {
        val input = "@`package.Class()+`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 19),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 17)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.METHOD,
                params = emptyList(),
                flags = Flags(
                    relativeRange = TextRange(17, 18),
                    shortName = false,
                    fullName = true,
                    methodWithParams = false,
                    isConstructor = true
                )
            ),
            result
        )
    }

    fun testFullFieldReference() {
        val input = "@`package.Class#field+`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 23),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "field",
                    relativeRange = TextRange(16, 21)
                ),
                memberType = IndexReference.MemberType.FIELD,
                flags = Flags(
                    relativeRange = TextRange(21, 22),
                    shortName = false,
                    fullName = true,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testFullMethodReference() {
        val input = "@`package.Class#method()+`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 26),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 15)
                ),
                memberName = GroupInfo(
                    value = "method",
                    relativeRange = TextRange(16, 24)
                ),
                memberType = IndexReference.MemberType.METHOD,
                params = emptyList(),
                flags = Flags(
                    relativeRange = TextRange(24, 25),
                    shortName = false,
                    fullName = true,
                    methodWithParams = false,
                    isConstructor = false
                )
            ),
            result
        )
    }

}
