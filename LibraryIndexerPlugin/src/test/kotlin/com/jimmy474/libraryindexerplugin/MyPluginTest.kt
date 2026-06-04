package com.jimmy474.libraryindexerplugin

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.CodeSnippetInspection
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.CodeSnippetPsiElement
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.CodeSnippetSyntax
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.CodeSnippetUsageTypeProvider
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.RegionLightElement
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.getSnippet
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.LibraryIndexPsiElement
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.LibraryIndexUsageTypeProvider
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.LibraryIndexInspection
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.getFoldedStringFromElement

@TestDataPath($$"$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("library-index-dependency", "library-index-dependency")
        myFixture.enableInspections(LibraryIndexInspection(), CodeSnippetInspection())
        VfsUtil.markDirtyAndRefresh(false, true, true, myFixture.project.guessProjectDir())
    }

    private fun addResolvableLibraryClass() {
        myFixture.addFileToProject(
            "net/fabricmc/SomeClass.java",
            """
                package net.fabricmc;

                public class SomeClass {
                    public String name;
                    public int version;

                    public SomeClass() {}
                    public SomeClass(String value) {}

                    public String getName() { return name; }
                    public String getName(String prefix) { return prefix + name; }
                    public void setName(String value) { name = value; }
                    public void setItems(java.util.List<String> items) {}
                    public int getVersion() { return version; }
                }
            """.trimIndent()
        )
    }

    private fun libraryElement(): LibraryIndexPsiElement {
        return PsiTreeUtil.findChildOfType(myFixture.file, LibraryIndexPsiElement::class.java)
            ?: error("Library index PSI element not found")
    }

    private fun snippetElement(): CodeSnippetPsiElement {
        return PsiTreeUtil.findChildOfType(myFixture.file, CodeSnippetPsiElement::class.java)
            ?: error("Code snippet PSI element not found")
    }

    private fun snippetBlock(openingLine: String, body: String? = null): String {
        return buildString {
            append(openingLine)
            body?.let {
                appendLine()
                append(it)
            }
            appendLine()
            append("<<<")
        }
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

    fun `test Member Symbol Suggestion`() {
        myFixture.configureByText("symbols.md", "@`net.fabricmc.SomeClass<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(suggestions, "#", "()")
    }

    fun `test Method Completion`() {
        myFixture.configureByText("method_completion.md", "@`net.fabricmc.SomeClass#<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "getName(java.lang.String)", "setName()", "setName(java.lang.String)", "getVersion()")
    }

    fun `test Method Completion With Prefix`() {
        myFixture.configureByText("method_prefix.md", "@`net.fabricmc.SomeClass#get<caret>`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "getName(java.lang.String)", "getVersion()")
    }

    fun `test Field Completion`() {
        myFixture.configureByText("field_completion.md", "@`net.fabricmc.SomeClass#<caret>`")
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

    fun `test Dollar Does Not Trigger Field Suggestions`() {
        myFixture.configureByText("field_trigger.md", "@`net.fabricmc.SomeClass$<caret>`")
        myFixture.completeBasic()

        assertNullOrEmpty(myFixture.lookupElementStrings)
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
        myFixture.configureByText("method_select.md", "@`net.fabricmc.SomeClass#getVer<caret>`")
        myFixture.completeBasic()
        myFixture.checkResult("@`net.fabricmc.SomeClass#getVersion()`")
    }

    fun `test Method Parameter Completion`() {
        myFixture.configureByText("method_param.md", "@`net.fabricmc.SomeClass#getName(<caret>)`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "java.lang.String")
        assertDoesntContain(suggestions, "...")
    }

    fun `test Method Parameter Selection Keeps Closing Paren`() {
        myFixture.configureByText("method_param_select.md", "@`net.fabricmc.SomeClass#getName(<caret>)`")
        myFixture.completeBasic()
        myFixture.finishLookup('\n')
        myFixture.checkResult("@`net.fabricmc.SomeClass#getName(java.lang.String)`")
    }

    fun `test Constructor Parameter Completion`() {
        myFixture.configureByText("constructor_param.md", "@`net.fabricmc.SomeClass(<caret>)`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "java.lang.String")
        assertDoesntContain(suggestions, "...")
    }

    fun `test Completion In Middle Of Text`() {
        myFixture.configureByText("middle.md", "hello @`net.fa<caret>` world")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "fabricmc")
    }

    fun `test Library Index Class Reference Resolves`() {
        addResolvableLibraryClass()
        myFixture.configureByText("class_reference.md", "@`net.fabricmc.SomeClass`")

        val resolved = libraryElement().references.single().resolve()

        assertTrue(resolved is PsiClass)
        assertEquals("net.fabricmc.SomeClass", (resolved as PsiClass).qualifiedName)
    }

    fun `test Library Index Field Reference Resolves`() {
        addResolvableLibraryClass()
        myFixture.configureByText("field_reference.md", "@`net.fabricmc.SomeClass#name`")

        val resolved = libraryElement().references.first().resolve()

        assertTrue(resolved is PsiField)
        assertEquals("name", (resolved as PsiField).name)
    }

    fun `test Library Index Method Overload Reference Resolves`() {
        addResolvableLibraryClass()
        myFixture.configureByText("method_reference.md", "@`net.fabricmc.SomeClass#getName(java.lang.String)`")

        val resolved = libraryElement().references.first().resolve()

        assertTrue(resolved is PsiMethod)
        val method = resolved as PsiMethod
        assertEquals("getName", method.name)
        assertEquals(1, method.parameterList.parametersCount)
    }

    fun `test Library Index Constructor Overload Reference Resolves`() {
        addResolvableLibraryClass()
        myFixture.configureByText("constructor_reference.md", "@`net.fabricmc.SomeClass(java.lang.String)`")

        val resolved = libraryElement().references.first().resolve()

        assertTrue(resolved is PsiMethod)
        val constructor = resolved as PsiMethod
        assertTrue(constructor.isConstructor)
        assertEquals(1, constructor.parameterList.parametersCount)
    }

    fun `test Library Index Folding Uses Resolved Method Parameter Names`() {
        addResolvableLibraryClass()
        myFixture.configureByText("fold_method.md", "@`net.fabricmc.SomeClass#getName(java.lang.String);`")

        assertEquals("SomeClass.getName(String prefix)", getFoldedStringFromElement(libraryElement()))
    }

    fun `test Library Index Folding Uses Custom Name`() {
        addResolvableLibraryClass()
        myFixture.configureByText("fold_custom.md", "@`net.fabricmc.SomeClass#getName(java.lang.String)|Display Name`")

        assertEquals("Display Name", getFoldedStringFromElement(libraryElement()))
    }

    fun `test Library Index Usage Type Provider`() {
        myFixture.configureByText("library_usage.md", "@`net.fabricmc.SomeClass`")

        val usageType = LibraryIndexUsageTypeProvider().getUsageType(libraryElement())

        assertNotNull(usageType)
        assertEquals("Library Index", usageType.toString())
    }

    fun `test Library Index Quick Fix Removes Invalid Method Flag From Field`() {
        myFixture.configureByText("field_flag_fix.md", "@`net.fabricmc.SomeClass#name:`")
        myFixture.doHighlighting()

        val fix = myFixture.getAvailableIntention("Remove ':'")
        assertNotNull(fix)
        myFixture.launchAction(fix!!)

        myFixture.checkResult("@`net.fabricmc.SomeClass#name`")
    }

    fun `test Code Snippet Root Completion`() {
        myFixture.addFileToProject("src/Main.java", "class Main {}")
        myFixture.configureByText("snippet_root.md", snippetBlock("<<< @/<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "src")
    }

    fun `test Compact Code Snippet File Reference Resolves`() {
        myFixture.addFileToProject("docs/sample.txt", "hello")
        myFixture.configureByText("compact_snippet.md", "<<< @/docs/sample.txt <<<")

        val element = snippetElement()
        assertTrue("Compact snippet PSI text should match syntax: '${element.text}'", CodeSnippetSyntax.REGEX.matches(element.text))
        val resolvedFile = element.references.firstOrNull()?.resolve()

        assertNotNull("Should parse and resolve a compact snippet", resolvedFile)
        assertEquals("sample.txt", resolvedFile!!.containingFile.name)
    }

    fun `test Code Snippet Typing After Prefix Does Not Erase Text`() {
        myFixture.configureByText("snippet_typing.md", snippetBlock("<<< <caret>"))
        myFixture.type("@")

        myFixture.checkResult(snippetBlock("<<< @"))
    }

    fun `test Code Snippet Typing From Bare Prefix Does Not Throw`() {
        myFixture.configureByText("snippet_bare_prefix.md", snippetBlock("<<<<caret>"))
        myFixture.type(" @")

        myFixture.checkResult(snippetBlock("<<< @"))
    }

    fun `test Code Snippet Directory Selection Inserts Slash`() {
        myFixture.addFileToProject("src/Main.java", "class Main {}")
        myFixture.configureByText("snippet_dir.md", snippetBlock("<<< @/sr<caret>"))
        myFixture.completeBasic()
        myFixture.finishLookup('\n')

        myFixture.checkResult(snippetBlock("<<< @/src/"))
    }

    fun `test Code Snippet File Completion`() {
        myFixture.addFileToProject("src/Main.java", "class Main {}")
        myFixture.configureByText("snippet_file.md", snippetBlock("<<< @/src/Ma<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "Main.java")
    }

    fun `test Code Snippet Region Completion`() {
        myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region setup
                    void setup() {}
                    // #endregion setup
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_region.md", snippetBlock("<<< @/src/Main.java#<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "setup")
    }

    fun `test Code Snippet Excluded Region Completion`() {
        myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region details
                    void details() {}
                    // #endregion details
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_excluded_region.md", snippetBlock("<<< @/src/Main.java!<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "details")
    }

    fun `test Code Snippet Suggests Another Region After Completed Region`() {
        myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region setup
                    void setup() {}
                    // #endregion setup
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_multiple_regions.md", snippetBlock("<<< @/src/Main.java#setup<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "#", "!")
    }

    fun `test Code Snippet Does Not Suggest Region Already Used As Include Or Exclude`() {
        myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region setup
                    void setup() {}
                    // #endregion setup
                    // #region details
                    void details() {}
                    // #endregion details
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_unique_regions.md", snippetBlock("<<< @/src/Main.java#setup!<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "details")
        assertDoesntContain(suggestions, "setup")
    }

    fun `test Code Snippet Region Completion Reads Unsaved Psi Text`() {
        val file = myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region oldName
                    void setup() {}
                    // #endregion oldName
                }
            """.trimIndent()
        )
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(file.text.replace("oldName", "newName"))
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        myFixture.configureByText("snippet_unsaved_region.md", snippetBlock("<<< @/src/Main.java#<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "newName")
        assertDoesntContain(suggestions, "oldName")
    }

    fun `test Code Snippet Does Not Complete Inside Annotation Body`() {
        myFixture.addFileToProject("src/Main.java", "class Main {}")
        myFixture.configureByText("snippet_annotation_completion.md", snippetBlock("<<< @/src/Main.java", "1. @/<caret>"))
        myFixture.completeBasic()

        assertTrue(myFixture.lookupElementStrings.isNullOrEmpty())
    }

    fun `test Code Snippet File Selection Does Not Insert Region Symbol`() {
        myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region setup
                    void setup() {}
                    // #endregion setup
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_file_region.md", snippetBlock("<<< @/src/Ma<caret>"))
        myFixture.completeBasic()
        myFixture.finishLookup('\n')

        myFixture.checkResult(snippetBlock("<<< @/src/Main.java"))
    }

    fun `test Code Snippet Suggests Region Symbol After File With Regions`() {
        myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                    // #region setup
                    void setup() {}
                    // #endregion setup
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_region_symbol.md", snippetBlock("<<< @/src/Main.java<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "#", "!")
    }

    fun `test Code Snippet Does Not Suggest Region Symbol After File Without Regions`() {
        myFixture.addFileToProject("src/Main.java", "class Main {}")
        myFixture.configureByText("snippet_no_region_symbol.md", snippetBlock("<<< @/src/Main.java<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertDoesntContain(suggestions, "#", "!")
    }

    fun `test Code Snippet Region Completion Works For Text Files`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                // #region intro
                hello
                // #endregion intro
            """.trimIndent()
        )
        myFixture.configureByText("snippet_text_region.md", snippetBlock("<<< @/docs/sample.txt#<caret>"))
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "intro")
    }

    fun `test Code Snippet Missing File Shows Error`() {
        myFixture.configureByText("missing_snippet_file.md", snippetBlock("<<< @/src/Missing.java<caret>"))

        val error = myFixture.doHighlighting().find { it.description == "Code snippet reference file not found" }

        assertNotNull("Should find an error squiggle for missing snippet file", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Code Snippet Missing File Name Shows Error`() {
        myFixture.addFileToProject("docs/sample.txt", "hello")
        myFixture.configureByText("missing_snippet_filename.md", snippetBlock("<<< @/docs<caret>"))

        val error = myFixture.doHighlighting().find { it.description == "Code snippet reference must end with a file" }

        assertNotNull("Should find an error squiggle for missing snippet file name", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Code Snippet Missing Region Shows Error For Text File`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                // #region intro
                hello
                // #endregion intro
            """.trimIndent()
        )
        myFixture.configureByText("missing_snippet_region.md", snippetBlock("<<< @/docs/sample.txt#missing<caret>"))

        val error = myFixture.doHighlighting().find { it.description == "Code snippet region not found in file sample.txt" }

        assertNotNull("Should find an error squiggle for missing snippet region", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Code Snippet Unclosed Region Shows Error`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                // #region intro
                hello
            """.trimIndent()
        )
        myFixture.configureByText("unclosed_snippet_region.md", snippetBlock("<<< @/docs/sample.txt#intro<caret>"))

        val error = myFixture.doHighlighting().find { it.description?.startsWith("Code snippet region did not end") == true }

        assertNotNull("Should find an error squiggle for unclosed snippet region", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Code Snippet Empty Region Marker Shows Error`() {
        myFixture.addFileToProject("docs/sample.txt", "hello")
        myFixture.configureByText("empty_snippet_region.md", snippetBlock("<<< @/docs/sample.txt#<caret>"))

        val error = myFixture.doHighlighting().find { it.description == "Code snippet region name not provided" }

        assertNotNull("Should find an error squiggle for empty snippet region marker", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Code Snippet Duplicate Region Across Include And Exclude Shows Error`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                // #region intro
                hello
                // #endregion intro
            """.trimIndent()
        )
        myFixture.configureByText("duplicate_snippet_region.md", snippetBlock("<<< @/docs/sample.txt#intro!intro<caret>"))

        val error = myFixture.doHighlighting().find { it.description == "Code snippet region intro is used multiple times" }

        assertNotNull("Should reject a region reused across include and exclude", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Code Snippet Region Reference Resolves In Text File`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                // #region intro
                hello
                // #endregion intro
            """.trimIndent()
        )
        myFixture.configureByText("resolve_text_region.md", snippetBlock("<<< @/docs/sample.txt#intro"))

        val snippetElement = PsiTreeUtil.findChildOfType(myFixture.file, CodeSnippetPsiElement::class.java)
        val resolvedRegion = snippetElement?.references?.lastOrNull()?.resolve()

        assertNotNull("Should resolve region references outside Java files", resolvedRegion)
    }

    fun `test Code Snippet File Reference Resolves`() {
        myFixture.addFileToProject("docs/sample.txt", "hello")
        myFixture.configureByText("resolve_file.md", snippetBlock("<<< @/docs/sample.txt"))

        val resolvedFile = snippetElement().references.firstOrNull()?.resolve()

        assertNotNull("Should resolve snippet file reference", resolvedFile)
        assertEquals("sample.txt", resolvedFile!!.containingFile.name)
    }

    fun `test Code Snippet Inlay Returns Whole File Content`() {
        myFixture.addFileToProject("docs/sample.txt", "hello\nworld")
        myFixture.configureByText("snippet_inlay_file.md", snippetBlock("<<< @/docs/sample.txt"))

        val inlayContent = getSnippet(snippetElement())?.content

        assertEquals("hello\nworld", inlayContent)
    }

    fun `test Code Snippet Inlay Returns Region Content Only`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                before
                // #region intro
                    hello
                // #endregion intro
                after
            """.trimIndent()
        )
        myFixture.configureByText("snippet_inlay_region.md", snippetBlock("<<< @/docs/sample.txt#intro"))

        val inlayContent = getSnippet(snippetElement())?.content

        assertEquals("hello", inlayContent)
    }

    fun `test Code Snippet Inlay Excludes Selected Region`() {
        myFixture.addFileToProject(
            "docs/sample.txt",
            """
                before
                // #region details
                hidden
                // #endregion details
                after
            """.trimIndent()
        )
        myFixture.configureByText("snippet_inlay_excluded_region.md", snippetBlock("<<< @/docs/sample.txt!details"))

        val inlayContent = getSnippet(snippetElement())?.content

        assertEquals("before\nafter", inlayContent)
    }

    fun `test Code Snippet Usage Type Provider`() {
        myFixture.configureByText("snippet_usage.md", snippetBlock("<<< @/docs/sample.txt"))

        val usageType = CodeSnippetUsageTypeProvider().getUsageType(snippetElement())

        assertNotNull(usageType)
        assertEquals("Code Snippet", usageType.toString())
    }

    fun `test Region Comment Reference Rename Updates Comment`() {
        myFixture.configureByText("RegionOwner.java", "class RegionOwner {\n// #region oldName<caret>\n}")
        val comment = PsiTreeUtil.findChildOfType(myFixture.file, com.intellij.psi.PsiComment::class.java)!!
        val reference = comment.references.firstOrNull()

        assertNotNull("Should create a reference for region comments", reference)
        WriteCommandAction.runWriteCommandAction(project) {
            reference!!.handleElementRename("newName")
        }

        myFixture.checkResult("class RegionOwner {\n// #region newName\n}")
    }

    fun `test Code Snippet Region Reference IsReferenceTo Region Element`() {
        val owner = myFixture.addFileToProject(
            "src/Main.java",
            """
                class Main {
                // #region intro
                void hello() {}
                // #endregion intro
                }
            """.trimIndent()
        )
        myFixture.configureByText("snippet_is_reference_to.md", snippetBlock("<<< @/src/Main.java#intro"))

        val regionReference = snippetElement().references.last()
        val comment = PsiTreeUtil.findChildOfType(owner, com.intellij.psi.PsiComment::class.java)!!
        val regionElement = RegionLightElement(project, owner, "intro", comment)

        assertTrue(regionReference.isReferenceTo(regionElement))
    }

    fun `test Multiple Backticks`() {
        myFixture.configureByText("multi_backtick.md", "@`net.fa<caret>` something `code`")
        myFixture.completeBasic()

        val suggestions = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(suggestions, "fabricmc")
    }

    fun `test Invalid Path Shows Error`() {
        myFixture.configureByText("test.md", "@`does.not.exist<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Package or Class 'does' not found" }

        assertNotNull("Should find an error squiggle for invalid path", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Malformed Reference Shows Error`() {
        myFixture.configureByText("test.md", "@`invalid^path<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Malformed library reference syntax" }

        assertNotNull("Should find an error squiggle for malformed syntax", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Empty Member Reference Shows Error`() {
        myFixture.configureByText("test.md", "@`net.fabricmc.SomeClass#<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Reference cannot end with '#', Expected a member name" }

        assertNotNull("Should find an error squiggle for an empty member reference", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Missing Method Shows Error`() {
        myFixture.configureByText("test.md", "@`net.fabricmc.SomeClass#missing()<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Method 'missing' not found" }

        assertNotNull("Should find an error squiggle for a missing method", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Ambiguous Method Overload Shows Error`() {
        myFixture.configureByText("test.md", "@`net.fabricmc.SomeClass#getName()<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Ambiguous reference for Method overload, to reference Method with multiple overloads you must provide the types of parameters in parenthesis" }

        assertNotNull("Should find an error squiggle for an ambiguous method overload", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Missing Method Overload Shows Error`() {
        myFixture.configureByText("test.md", "@`net.fabricmc.SomeClass#getName(int)<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Method overload with given types not found" }

        assertNotNull("Should find an error squiggle for a missing method overload", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Missing Field Shows Error`() {
        myFixture.configureByText("test.md", "@`net.fabricmc.SomeClass#missing<caret>`")

        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description == "Field 'missing' not found" }

        assertNotNull("Should find an error squiggle for a missing field", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Method Only Flag On Class Shows Error`() {
        myFixture.configureByText("class_method_flag.md", "@`net.fabricmc.SomeClass:<caret>`")

        val error = myFixture.doHighlighting().find { it.description == "Method only type flag is only allowed for method or constructor reference" }

        assertNotNull("Should find an error squiggle for method-only flag on class reference", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Return Type Flag On Constructor Shows Error`() {
        myFixture.configureByText("constructor_return_flag.md", "@`net.fabricmc.SomeClass()>`")

        val error = myFixture.doHighlighting().find { it.description == "Method return type flag is only allowed for method reference" }

        assertNotNull("Should find an error squiggle for return type flag on constructor", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    fun `test Custom Library Name Shows Warning`() {
        myFixture.configureByText("custom_name_warning.md", "@`net.fabricmc.SomeClass|Custom Label<caret>`")

        val warning = myFixture.doHighlighting().find { it.description == "It is not recommended to use custom names, Unless it is an emergency" }

        assertNotNull("Should warn for custom library reference names", warning)
        assertEquals(HighlightSeverity.WARNING, warning?.severity)
    }

    fun `test Hyphenated Reference Show Error`() {
        myFixture.configureByText("test.md", "@`special.my-class<caret>`")

        val error = myFixture.doHighlighting().find { it.description == "Malformed library reference syntax" }

        assertNotNull("Should find an error squiggle for using hyphen", error)
        assertEquals(HighlightSeverity.ERROR, error?.severity)
    }

    override fun getTestDataPath(): String = "src/test/testData"
}
