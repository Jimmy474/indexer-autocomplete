package com.jimmy474.indexerautocomplete

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("library-index-dependency", "library-index-dependency")
        VfsUtil.markDirtyAndRefresh(false, true, true, myFixture.project.guessProjectDir())
    }

    fun `test Autocomplete Depth 0`() {
        myFixture.configureByText("depth0.md","@`<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(suggestions, "net", "a", "broken", "special")
    }

    fun `test Autocomplete Depth 0_Text`() {
        myFixture.configureByText("depth0_text.md","@`net<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings
        assertNotNull(suggestions)
        assertSameElements(suggestions!!, "net")
    }

    fun `test Autocomplete Depth 1`() {
        myFixture.configureByText("depth1.md","@`net.<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(suggestions, "fabricmc", "minecraft")
    }

    fun `test Autocomplete Depth 1_Text`() {
        myFixture.configureByText("depth1_text.md","@`net.fa<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(suggestions, "fabricmc")
    }

    fun `test No Completion In Python`() {
        myFixture.configureByText("main.py", "hello @`<caret>`")
        val result = myFixture.completeBasic()
        assertEmpty(result)
    }

    fun `test Non Markdown File`() {
        myFixture.configureByText("test.txt", "@`net.<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings
        assertNullOrEmpty(suggestions)
    }

    fun `test Method and Field Symbols Suggestions`() {
        myFixture.configureByText("symbols.md", "@`net.fabricmc.SomeClass<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "#", "$")
    }

    fun `test Method Completion`() {
        myFixture.configureByText("method_completion.md", "@`net.fabricmc.SomeClass#<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "getName", "setName")
    }

    fun `test Method Completion With Prefix`() {
        myFixture.configureByText("method_prefix.md", "@`net.fabricmc.SomeClass#get<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(suggestions, "getName", "getVersion")
    }

    fun `test Field Completion`() {
        myFixture.configureByText("field_completion.md", "@`net.fabricmc.SomeClass$<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "name", "version")
    }

    fun `test Invalid Path`() {
        myFixture.configureByText("invalid_path.md", "@`does.not.exist<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings
        assertNullOrEmpty(suggestions)
    }

    fun `test Invalid Method Syntax`() {
        myFixture.configureByText("invalid_method.md", "@`net.fabricmc.#<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings
        assertNullOrEmpty(suggestions)
    }

    fun `test Empty Input`() {
        myFixture.configureByText("empty.md", "@`<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertTrue(suggestions.isNotEmpty())
    }

    fun `test Directory Selection Inserts Dot`() {
        myFixture.configureByText("dot_insert.md", "@`net<caret>`")
        myFixture.completeBasic()
        myFixture.finishLookup('\n')
        myFixture.checkResult("@`net.`")
    }

    fun `test File Selection Does Not Insert Dot`() {
        myFixture.configureByText("file_insert.md", "@`net.fabricmc.SomeCla<caret>`")
        myFixture.completeBasic()
        myFixture.finishLookup('\n')
        myFixture.checkResult("@`net.fabricmc.SomeClass`")
    }

    fun `test Case Sensitive Completion`() {
        myFixture.configureByText("case.md", "@`NET.<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings
        assertNullOrEmpty(suggestions)
    }

    fun `test Ignore Invalid Prefix`() {
        myFixture.configureByText("invalid_prefix.md", "net.<caret>")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings
        assertNullOrEmpty(suggestions)
    }

    fun `test Multiple References`() {
        myFixture.configureByText(
            "multiple.md",
            """
                @`net.fabricmc`
                
                something
                
                @`net.<caret>`
            """.trimIndent()
        )
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "fabricmc")
    }

    fun `test Deep Navigation`() {
        myFixture.configureByText("deep.md", "@`a.b.c.d.e.f.g.<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "target")
    }

    fun `test No Duplicate Suggestions`() {
        myFixture.configureByText("duplicates.md", "@`net.<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertEquals(suggestions.toSet().size, suggestions.size)
    }

    fun `test Space Stops Completion`() {
        myFixture.configureByText("space.md", "@`net fabric<caret>`")
        myFixture.completeBasic()
        assertNullOrEmpty(myFixture.lookupElementStrings)
    }

    fun `test Invalid Character`() {
        myFixture.configureByText("invalid_char.md", "@`net!*<caret>`")
        myFixture.completeBasic()
        assertNullOrEmpty(myFixture.lookupElementStrings)
    }

    fun `test Hyphen Support`() {
        myFixture.configureByText("hyphen.md", "@`special.my-<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "my-class")
    }

    fun `test Underscore Support`() {
        myFixture.configureByText("underscore.md", "@`special.my_<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "my_class")
    }

    fun `test Does Not Auto Complete`() {
        myFixture.configureByText("no_auto.md", "@`net.fa<caret>`")
        myFixture.completeBasic()
        myFixture.checkResult("@`net.fa`")
    }

    fun `test Dollar Triggers Field Suggestions`() {
        myFixture.configureByText("field_trigger.md", "@`net.fabricmc.SomeClass$<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "name")
    }

    fun `test Missing Intermediate Folder`() {
        myFixture.configureByText("missing_folder.md", "@`net.invalid.path<caret>`")
        myFixture.completeBasic()
        assertNullOrEmpty(myFixture.lookupElementStrings)
    }

    fun `test Empty Folder`() {
        myFixture.configureByText("empty_folder.md", "@`special.empty.<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertTrue(suggestions.isEmpty())
    }

    fun `test Directory And File Same Name`() {
        myFixture.configureByText("same_name.md", "@`net.fabricmc.Te<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "Test", "Test")
    }

    fun `test Method Selection Does Not Add Dot`() {
        myFixture.configureByText("method_select.md", "@`net.fabricmc.SomeClass#get<caret>`")
        myFixture.completeBasic()
        myFixture.finishLookup('\n')
        myFixture.checkResult("@`net.fabricmc.SomeClass#getName`")
    }

    fun `test Completion In Middle Of Text`() {
        myFixture.configureByText("middle.md", "hello @`net.fa<caret>` world")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "fabricmc")
    }

    fun `test Multiple Backticks`() {
        myFixture.configureByText("multi_backtick.md", "@`net.fa<caret>` something `code`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "fabricmc")
    }

    fun `test Invalid Path Shows Error`() {
        myFixture.configureByText("test.md", "@`invalid.path<caret>`")
        myFixture.doHighlighting()

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Path segment 'invalid' not found" }

        assertNotNull("Should find an error squiggle for invalid path", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    override fun getTestDataPath(): String = "src/test/testData"
}
