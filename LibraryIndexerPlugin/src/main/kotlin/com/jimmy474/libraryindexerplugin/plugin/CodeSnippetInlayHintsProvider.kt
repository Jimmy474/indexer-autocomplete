package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class CodeSnippetInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("code.snippet.inlay.hints")

    override val name: String = "Code Snippet Previews"

    override val previewText: String? = null

    override fun createSettings(): NoSettings = NoSettings()
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent {
                return JPanel()
            }
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element is CodeSnippetPsiElement) {
                    val snippetText = getInlayContent(element) ?: return true

                    val lines = snippetText.trimEnd().lines()
                    val multiLinePresentation = MultiLinePresentation(lines, editor)

                    val presentation = factory.roundWithBackground(
                        factory.inset(multiLinePresentation, left = 5, right = 5, top = 5, down = 5)
                    )

                    sink.addBlockElement(
                        offset = element.textRange.endOffset,
                        relatesToPrecedingText = true,
                        showAbove = false,
                        priority = 1,
                        presentation = presentation
                    )
                }

                return true
            }
        }
    }

    fun getInlayContent(element: CodeSnippetPsiElement): String? {
        val match = LibraryIndex.CODE_SNIPPET_REGEX.matchEntire(element.text) ?: return null
        val projectDir = element.project.guessProjectDir() ?: return null

        val codeSnippet = match.toCodeSnippetReference()
        val file = projectDir.findFileByRelativePath(codeSnippet.path.value.removePrefix("${LibraryIndex.ROOT_SYMBOL}/")) ?: return null

        val fileText = file.readText()
        val region = codeSnippet.region?.value ?: return fileText

        val lines = fileText.lines()
        val regionLines = mutableListOf<String>()
        var isInRegion = false

        for (originalLine in lines) {
            val trimmedLine = originalLine.trim()

            if (trimmedLine == "// #region $region") {
                isInRegion = true
            } else if (trimmedLine == "// #endregion $region") {
                isInRegion = false
            } else if (isInRegion && !trimmedLine.startsWith("// #")) {
                regionLines.add(originalLine)
            }
        }

        return regionLines.joinToString("\n").trimIndent().replace("\t", "    ")
    }
}

@Suppress("UnstableApiUsage")
class MultiLinePresentation(
    private val lines: List<String>,
    private val editor: Editor
) : BasePresentation() {

    override val width: Int get() {
        val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        return lines.maxOfOrNull { metrics.stringWidth(it) } ?: 0
    }

    override val height: Int get() = lines.size * editor.lineHeight

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        g.color = attributes.foregroundColor ?: editor.colorsScheme.defaultForeground

        val metrics = g.fontMetrics
        val lineHeight = editor.lineHeight

        for ((index, line) in lines.withIndex()) {
            val y = (index * lineHeight) + metrics.ascent
            g.drawString(line, 0, y)
        }
    }

    override fun toString(): String = "MultiLinePresentation(${lines.size} lines)"
}