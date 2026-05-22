package com.jimmy474.libraryindexerplugin

import com.jimmy474.libraryindexerplugin.plugin.*
import org.junit.Assert.*
import org.junit.Test

class LibraryIndexRegexTest {

    private val prefix = LibraryIndex.PREFIX.unescapeRegex()
    private val memberPrefix = LibraryIndex.MEMBER_PREFIX.unescapeRegex()
    private val shortNameSym = LibraryIndex.SHORT_NAME_SYMBOL.unescapeRegex()
    private val longNameSym = LibraryIndex.LONG_NAME_SYMBOL.unescapeRegex()
    private val fullNameSym = LibraryIndex.FULL_NAME_SYMBOL.unescapeRegex()
    private val methodOnlyTypeSym = LibraryIndex.METHOD_ONLY_TYPE_SYMBOL.unescapeRegex()
    private val methodOnlyNameSym = LibraryIndex.METHOD_ONLY_NAME_SYMBOL.unescapeRegex()
    private val methodBothSym = LibraryIndex.METHOD_BOTH_SYMBOL.unescapeRegex()
    private val methodReturnTypeSym = LibraryIndex.METHOD_RETURN_TYPE_SYMBOL.unescapeRegex()
    private val customNameSym = LibraryIndex.CUSTOM_NAME_SYMBOL.unescapeRegex()

    data class PathOption(
        val name: String,
        val fqnPart: String,
        val classNamePart: String?,
        val memberPart: String?,
        val methodFlagPart: String?,
        val expectedMemberType: IndexReference.MemberType,
        val expectedParams: List<String>?
    )

    sealed class SuffixOption {
        data class Flags(
            val nameFlag: String?,
            val methodFlag: String?
        ) : SuffixOption() {
            override fun toString(): String = "Flags(name=$nameFlag, method=$methodFlag)"
        }

        data class CustomName(
            val label: String
        ) : SuffixOption() {
            override fun toString(): String = "CustomName($label)"
        }
    }

    @Test
    fun testAllCombinatorialCombinations() {
        val pathOptions = listOf(
            PathOption("SingleFQN", "com", null, null, null, IndexReference.MemberType.NONE, null),
            PathOption("DottedFQN", "com.example.Foo", "Foo", null, null, IndexReference.MemberType.NONE, null),
            PathOption("FQN_With_Field", "com.example.Foo", "Foo", "myField", null, IndexReference.MemberType.FIELD, null),

            PathOption("FQN_With_Method_NoParams", "com.example.Foo", "Foo", "myMethod", "()", IndexReference.MemberType.METHOD, emptyList()),
            PathOption("FQN_With_Method_OneParam", "com.example.Foo", "Foo", "myMethod", "(int)", IndexReference.MemberType.METHOD, listOf("int")),
            PathOption("FQN_With_Method_MultipleParams", "com.example.Foo", "Foo", "myMethod", "(int, String)", IndexReference.MemberType.METHOD, listOf("int", "String")),
            PathOption("FQN_With_Method_GenericParam", "com.example.Foo", "Foo", "myMethod", "(List<String>)", IndexReference.MemberType.METHOD, listOf("List<String>")),
            PathOption("FQN_With_Method_DeepGenericParam", "com.example.Foo", "Foo", "myMethod", "(Map<String,List<Integer>>)", IndexReference.MemberType.METHOD, listOf("Map<String,List<Integer>>")),

            PathOption("Constructor_NoParams", "com.example.Foo", "Foo", null, "()", IndexReference.MemberType.METHOD, emptyList()),
            PathOption("Constructor_OneParam", "com.example.Foo", "Foo", null, "(int)", IndexReference.MemberType.METHOD, listOf("int")),
            PathOption("Constructor_MultipleParams", "com.example.Foo", "Foo", null, "(int, String)", IndexReference.MemberType.METHOD, listOf("int", "String")),
            PathOption("Constructor_GenericParam", "com.example.Foo", "Foo", null, "(List<String>)", IndexReference.MemberType.METHOD, listOf("List<String>")),
            PathOption("Constructor_DeepGenericParam", "com.example.Foo", "Foo", null, "(Map<String,List<Integer>>)", IndexReference.MemberType.METHOD, listOf("Map<String,List<Integer>>"))
        )

        val suffixOptions = mutableListOf<SuffixOption>()

        val nameFlags = listOf(null, shortNameSym, longNameSym, fullNameSym)
        val methodFlags = listOf(null, methodOnlyTypeSym, methodOnlyNameSym, methodBothSym, methodReturnTypeSym)

        for (nf in nameFlags) {
            for (mf in methodFlags) {
                suffixOptions.add(SuffixOption.Flags(nf, mf))
            }
        }

        suffixOptions.add(SuffixOption.CustomName("MyCustomLabel"))

        val failures = mutableListOf<String>()
        var successCount = 0

        for (path in pathOptions) {
            for (suffix in suffixOptions) {
                try {
                    runCombinatorialTest(path, suffix)
                    successCount++
                } catch (e: Throwable) {
                    failures.add("FAIL: Path[${path.name}] + Suffix[$suffix]\nReason: ${e.message}")
                }
            }
        }

        if (failures.isNotEmpty()) {
            val failureReport = StringBuilder()
            failureReport.append("Regex Combinatorial Test failed with ${failures.size} failing combinations out of 273 total paths evaluated:\n\n")
            failures.forEachIndexed { idx, error ->
                failureReport.append("${idx + 1}) ").append(error).append("\n\n-----------------------------\n\n")
            }
            fail(failureReport.toString())
        }
    }

    private fun runCombinatorialTest(path: PathOption, suffix: SuffixOption) {
        val input = buildInputString(path, suffix)
        val matchResult = LibraryIndex.INDEX_REFERENCE_REGEX.matchEntire(input)

        assertNotNull("Regex failed to match input string '$input'", matchResult)

        val indexReference = matchResult!!.toIndexReference()

        assertEquals(0, indexReference.fullRange.startOffset)
        assertEquals(input.length, indexReference.fullRange.endOffset)

        assertEquals(path.fqnPart, indexReference.fqn.value)

        if (path.classNamePart != null) {
            assertNotNull("Expected class name parsed for $input", indexReference.className)
            assertEquals(path.classNamePart, indexReference.className!!.value)
        } else {
            assertNull("Expected class name to be null for $input", indexReference.className)
        }

        if (path.memberPart != null) {
            assertNotNull("Expected non-null member name for $input", indexReference.memberName)
            assertEquals(path.memberPart, indexReference.memberName!!.value)
        } else {
            assertNull("Expected null member name for $input", indexReference.memberName)
        }

        assertEquals(path.expectedMemberType, indexReference.memberType)

        if (path.expectedParams != null) {
            assertNotNull("Expected non-null parameter list for $input", indexReference.params)
            assertEquals(path.expectedParams.size, indexReference.params!!.size)
            path.expectedParams.forEachIndexed { idx, expectedParam ->
                assertEquals(expectedParam, indexReference.params[idx].value)
            }
        } else {
            assertNull("Expected null parameters list for $input", indexReference.params)
        }

        when (suffix) {
            is SuffixOption.CustomName -> {
                assertNotNull(indexReference.customName)
                assertEquals(suffix.label, indexReference.customName!!.value)
                assertEquals(Flags(isConstructor = path.name.startsWith("Constructor_")), indexReference.flags)
            }
            is SuffixOption.Flags -> {
                assertNull(indexReference.customName)
                val expectedFlags = indexReference.flags

                assertEquals(suffix.nameFlag == shortNameSym, expectedFlags.shortName)
                assertEquals(suffix.nameFlag == longNameSym, expectedFlags.longName)
                assertEquals(suffix.nameFlag == fullNameSym, expectedFlags.fullName)

                assertEquals(suffix.methodFlag == methodOnlyTypeSym, expectedFlags.methodOnlyType)
                assertEquals(suffix.methodFlag == methodOnlyNameSym, expectedFlags.methodOnlyName)
                assertEquals(suffix.methodFlag == methodBothSym, expectedFlags.methodBoth)
                assertEquals(suffix.methodFlag == methodReturnTypeSym, expectedFlags.methodReturnType)

                val isConstructor = path.memberPart == null && path.methodFlagPart != null
                assertEquals(isConstructor, expectedFlags.isConstructor)

                val hasParams = path.expectedParams?.isNotEmpty() == true
                val methodFlagDemandsParams = suffix.methodFlag == methodOnlyTypeSym ||
                        suffix.methodFlag == methodOnlyNameSym ||
                        suffix.methodFlag == methodBothSym
                assertEquals(hasParams && methodFlagDemandsParams, expectedFlags.methodWithParams)
            }
        }
    }

    private fun buildInputString(path: PathOption, suffix: SuffixOption): String {
        val body = StringBuilder()
        body.append(path.fqnPart)
        if (path.memberPart != null) {
            body.append(memberPrefix).append(path.memberPart)
        }
        if (path.methodFlagPart != null) {
            body.append(path.methodFlagPart)
        }

        when (suffix) {
            is SuffixOption.CustomName -> {
                body.append(customNameSym).append(suffix.label)
            }
            is SuffixOption.Flags -> {
                if (suffix.nameFlag != null) body.append(suffix.nameFlag)
                if (suffix.methodFlag != null) body.append(suffix.methodFlag)
            }
        }
        return "$prefix`$body`"
    }


    @Test
    fun testRejectsMissingBackticks() {
        val invalid = "${prefix}com.example.Foo"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }

    @Test
    fun testRejectsMissingPrefix() {
        val invalid = "`com.example.Foo`"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }

    @Test
    fun testRejectsInvalidIdentifiers() {
        val invalid = "$prefix`com.ex-ample.Foo`"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }

    @Test
    fun testRejectsMultipleMemberPrefixes() {
        val invalid = "$prefix`com.example.Foo${memberPrefix}${memberPrefix}bar`"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }

    @Test
    fun testRejectsMismatchedParenthesesInMethods() {
        val invalid = "$prefix`com.example.Foo#bar(int`"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }

    @Test
    fun testRejectsSimultaneousFlagsAndCustomLabel() {
        val invalid = "$prefix`com.example.Foo#bar$shortNameSym$methodOnlyTypeSym$customNameSym`"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }

    @Test
    fun testRejectsExtraneousTrailingCharacters() {
        val invalid = "$prefix`com.example.Foo`extra"
        assertFalse(LibraryIndex.INDEX_REFERENCE_REGEX.matches(invalid))
    }
}