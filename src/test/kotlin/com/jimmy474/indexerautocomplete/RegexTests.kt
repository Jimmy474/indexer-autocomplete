package com.jimmy474.indexerautocomplete

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jimmy474.indexerautocomplete.plugin.*

class RegexTests: BasePlatformTestCase() {
    val regex = LibraryIndex.INDEX_REFERENCE_REGEX

    fun `test full regex`(){
        assertNotNull(regex.matchEntire("@`package.Class`"))
        assertNotNull(regex.matchEntire("@`package.Class()`"))
        assertNotNull(regex.matchEntire("@`package.Class(...)`"))
        assertNotNull(regex.matchEntire("@`package.Class#field`"))
        assertNotNull(regex.matchEntire("@`package.Class#method()`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(...)`"))

        assertNotNull(regex.matchEntire("@`package.Class>`"))
        assertNotNull(regex.matchEntire("@`package.Class()>`"))
        assertNotNull(regex.matchEntire("@`package.Class(...)>`"))
        assertNotNull(regex.matchEntire("@`package.Class#field>`"))
        assertNotNull(regex.matchEntire("@`package.Class#method()>`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(...)>`"))

        assertNotNull(regex.matchEntire("@`package.Class<`"))
        assertNotNull(regex.matchEntire("@`package.Class()<`"))
        assertNotNull(regex.matchEntire("@`package.Class(...)<`"))
        assertNotNull(regex.matchEntire("@`package.Class#field<`"))
        assertNotNull(regex.matchEntire("@`package.Class#method()<`"))
        assertNotNull(regex.matchEntire("@`package.Class#method(...)<`"))
    }

    fun `test malformed path syntax does not match`() {
        assertNull(regex.matchEntire("@`hyphen.not-allowed`"))
    }

    fun `test malformed member syntax does not match`() {
        assertNull(regex.matchEntire("@`net.fabricmc.SomeClass#missing(`"))
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

    fun testConstructorWithParamsReference() {
        val input = "@`package.Class(...)`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 21),
                fqn = GroupInfo(
                    value = "package.Class",
                    relativeRange = TextRange(2, 15)
                ),
                className = GroupInfo(
                    value = "Class",
                    relativeRange = TextRange(10, 20)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.METHOD,
                flags = Flags(
                    relativeRange = TextRange.EMPTY_RANGE,
                    shortName = false,
                    fullName = false,
                    methodWithParams = true,
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

    fun testMethodWithParamsReference() {
        val input = "@`package.Class#method(...)`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 28),
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
                    relativeRange = TextRange(16, 27)
                ),
                memberType = IndexReference.MemberType.METHOD,
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
        val input = "@`package.Class<`"

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
        val input = "@`package.Class()<`"

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

    fun testShortConstructorWithParamsReference() {
        val input = "@`package.Class(...)<`"

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
                    relativeRange = TextRange(10, 20)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.METHOD,
                flags = Flags(
                    relativeRange = TextRange(20, 21),
                    shortName = true,
                    fullName = false,
                    methodWithParams = true,
                    isConstructor = true
                )
            ),
            result
        )
    }

    fun testShortFieldReference() {
        val input = "@`package.Class#field<`"

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
        val input = "@`package.Class#method()<`"

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

    fun testShortMethodWithParamsReference() {
        val input = "@`package.Class#method(...)<`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 29),
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
                    relativeRange = TextRange(16, 27)
                ),
                memberType = IndexReference.MemberType.METHOD,
                flags = Flags(
                    relativeRange = TextRange(27, 28),
                    shortName = true,
                    fullName = false,
                    methodWithParams = true,
                    isConstructor = false
                )
            ),
            result
        )
    }

    fun testFullClassReference() {
        val input = "@`package.Class>`"

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
        val input = "@`package.Class()>`"

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

    fun testFullConstructorWithParamsReference() {
        val input = "@`package.Class(...)>`"

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
                    relativeRange = TextRange(10, 20)
                ),
                memberName = null,
                memberType = IndexReference.MemberType.METHOD,
                flags = Flags(
                    relativeRange = TextRange(20, 21),
                    shortName = false,
                    fullName = true,
                    methodWithParams = true,
                    isConstructor = true
                )
            ),
            result
        )
    }

    fun testFullFieldReference() {
        val input = "@`package.Class#field>`"

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
        val input = "@`package.Class#method()>`"

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

    fun testFullMethodWithParamsReference() {
        val input = "@`package.Class#method(...)>`"

        val result = regex.matchEntire(input)?.toIndexReference()

        assertEquals(
            IndexReference(
                fullRange = TextRange(0, 29),
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
                    relativeRange = TextRange(16, 27)
                ),
                memberType = IndexReference.MemberType.METHOD,
                flags = Flags(
                    relativeRange = TextRange(27, 28),
                    shortName = false,
                    fullName = true,
                    methodWithParams = true,
                    isConstructor = false
                )
            ),
            result
        )
    }

}
