package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
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
                    val snippet = getSnippet(element) ?: return true
                    val multiLinePresentation = MultiLinePresentation(snippet, editor)
                    val presentation = factory.roundWithBackground(factory.inset(multiLinePresentation, left = 5, right = 5, top = 5, down = 5))
                    sink.addBlockElement(
                        offset = element.firstChild.textRange.endOffset,
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

}

fun getSnippet(element: CodeSnippetPsiElement): Snippet? {
    val match = CodeSnippetSyntax.REGEX.matchEntire(element.text) ?: return null
    val projectDir = element.project.guessProjectDir() ?: return null

    val codeSnippet = match.toCodeSnippetReference()
    val annotations = codeSnippet.annotations.associate{ it.index.value to (it.text?.value ?: "") }
    val file = projectDir.findFileByRelativePath(codeSnippet.path.value.removePrefix("${CodeSnippetSyntax.ROOT_SYMBOL}/")) ?: return null

    val psiFile = PsiManager.getInstance(element.project).findFile(file) ?: return null
    val fileText = psiFile.text

    if(codeSnippet.regions.isEmpty()) return Snippet(fileText.replace("\t", "    "), psiFile.fileType, annotations)
    val includedRegions = codeSnippet.regions.filter { it.value.startsWith(CodeSnippetSyntax.INCLUDE_SYMBOL) }.map { it.value.substring(1) }
    val excludedRegions = codeSnippet.regions.filter { it.value.startsWith(CodeSnippetSyntax.EXCLUDE_SYMBOL) }.map { it.value.substring(1) }

    val lines = fileText.lines()
    val regionLines = mutableListOf<String>()
    var inInclude = 0
    var inExclude = 0

    for (originalLine in lines) {
        val trimmedLine = originalLine.trim()
        val regionMatch = CodeSnippetSyntax.REGION_REGEX.find(trimmedLine)
        if (regionMatch != null) {
            val regionName = regionMatch.groups["region"]!!.value
            val isEnd = regionMatch.groups["endMarker"] != null
            when {
                includedRegions.contains(regionName) -> inInclude += if (isEnd) -1 else 1
                excludedRegions.contains(regionName) -> inExclude += if (isEnd) -1 else 1
            }
        }else if ((includedRegions.isEmpty() || inInclude > 0) && inExclude == 0) {
            regionLines.add(originalLine)
        }
    }

    return Snippet(regionLines.joinToString("\n").trimIndent().replace("\t", "    "), psiFile.fileType, annotations)
}

/*@Suppress("UnstableApiUsage")
class MultiLinePresentation(
    private val lines: List<String>,
    private val annotations: Map<String,String>,
    private val editor: Editor
) : BasePresentation() {

    override val width: Int get() {
        val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        return lines.maxOfOrNull { metrics.stringWidth(formatLine(it)) } ?: 0
    }

    override val height: Int get() = lines.size * editor.lineHeight

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)

        val normalColor = attributes.foregroundColor ?: editor.colorsScheme.defaultForeground
        val metrics = g.fontMetrics
        val lineHeight = editor.lineHeight

        lines.forEachIndexed { lineIndex, line ->
            var x = 0
            val y = (lineIndex * lineHeight) + metrics.ascent
            var lastIndex = 0
            CodeSnippetSyntax.CODE_ANNOTATION_MARKER.findAll(line).forEach { match ->
                val beforeText = line.substring(lastIndex, match.range.first)
                g.color = normalColor
                g.drawString(beforeText, x, y)
                x += metrics.stringWidth(beforeText)

                val padX = 4
                val number = match.groups["index"]!!.value.substring(1)
                val tw = metrics.stringWidth(number)
                val h = metrics.height
                val w = maxOf(tw + 10, h)

                g.color = JBColor.BLUE
                g.fillRoundRect(x + padX/2, y - metrics.ascent, w, h, h, h)
                g.color = JBColor.white
                g.drawString(number, x + padX/2 + (w - tw) / 2, y)

                x += w + padX
                lastIndex = match.range.last + 1
            }
            val remaining = line.substring(lastIndex)
            g.color = normalColor
            g.drawString(remaining, x, y)
        }
    }

    private fun formatLine(line: String): String = CodeSnippetSyntax.CODE_ANNOTATION_MARKER.replace(line," $1 ")

    override fun toString(): String = "MultiLinePresentation(${lines.size} lines, ${annotations.size} annotations)"
}*/
sealed class DrawElement {
    data class Text(val text: String, val color: Color, val fontType: Int) : DrawElement()
    data class Annotation(val number: String) : DrawElement()
}

@Suppress("UnstableApiUsage")
class MultiLinePresentation(
    snippet: Snippet,
    private val editor: Editor
) : BasePresentation() {

    private val parsedLines: List<List<DrawElement>>

    init {
        val scheme = editor.colorsScheme
        val defaultForeground = scheme.defaultForeground

        val fullText = snippet.content.trimEnd().replace("\r\n", "\n")
        val colors = arrayOfNulls<Color>(fullText.length)
        val fontTypes = IntArray(fullText.length) { Font.PLAIN }

        if (snippet.fileType != null) {
            val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(snippet.fileType, editor.project, null)
            if (highlighter != null) {
                val lexer = highlighter.highlightingLexer
                lexer.start(fullText)

                while (lexer.tokenType != null) {
                    val keys = highlighter.getTokenHighlights(lexer.tokenType)
                    var tokenColor: Color? = null
                    var tokenFontType = Font.PLAIN

                    for (key in keys.reversed()) {
                        val attrs = scheme.getAttributes(key)
                        if (attrs != null) {
                            if (tokenColor == null && attrs.foregroundColor != null) {
                                tokenColor = attrs.foregroundColor
                            }
                            if (tokenFontType == Font.PLAIN && attrs.fontType != Font.PLAIN) {
                                tokenFontType = attrs.fontType
                            }
                        }
                    }

                    for (i in lexer.tokenStart until lexer.tokenEnd) {
                        if (i < colors.size) {
                            colors[i] = tokenColor
                            fontTypes[i] = tokenFontType
                        }
                    }
                    lexer.advance()
                }
            }
        }

        val lines = fullText.lines()
        var globalOffset = 0
        val tempParsedLines = mutableListOf<List<DrawElement>>()

        for (line in lines) {
            val lineElements = mutableListOf<DrawElement>()
            var lastMatchedOffset = 0

            CodeSnippetSyntax.CODE_ANNOTATION_MARKER.findAll(line).forEach { match ->
                val beforeText = line.substring(lastMatchedOffset, match.range.first)
                if (beforeText.isNotEmpty()) {
                    lineElements.addAll(buildTextChunks(beforeText, globalOffset + lastMatchedOffset, colors, fontTypes, defaultForeground))
                }

                val number = match.groups["index"]!!.value.substring(1)
                lineElements.add(DrawElement.Annotation(number))

                lastMatchedOffset = match.range.last + 1
            }

            val remaining = line.substring(lastMatchedOffset)
            if (remaining.isNotEmpty()) {
                lineElements.addAll(buildTextChunks(remaining, globalOffset + lastMatchedOffset, colors, fontTypes, defaultForeground))
            }

            tempParsedLines.add(lineElements)
            globalOffset += line.length + 1
        }
        parsedLines = tempParsedLines
    }

    private fun buildTextChunks(
        text: String,
        startOffset: Int,
        colors: Array<Color?>,
        fontTypes: IntArray,
        defaultColor: Color
    ): List<DrawElement.Text> {
        if (text.isEmpty()) return emptyList()
        val chunks = mutableListOf<DrawElement.Text>()

        var currentChunkStart = 0
        var currentColor = colors.getOrNull(startOffset) ?: defaultColor
        var currentFontType = fontTypes.getOrElse(startOffset) { Font.PLAIN }

        for (i in 1 until text.length) {
            val color = colors.getOrNull(startOffset + i) ?: defaultColor
            val fontType = fontTypes.getOrElse(startOffset + i) { Font.PLAIN }

            if (color != currentColor || fontType != currentFontType) {
                chunks.add(DrawElement.Text(text.substring(currentChunkStart, i), currentColor, currentFontType))
                currentChunkStart = i
                currentColor = color
                currentFontType = fontType
            }
        }
        chunks.add(DrawElement.Text(text.substring(currentChunkStart), currentColor, currentFontType))
        return chunks
    }

    override val width: Int get() {
        val baseFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        return parsedLines.maxOfOrNull { line ->
            line.sumOf { element ->
                when (element) {
                    is DrawElement.Text -> {
                        val metrics = editor.contentComponent.getFontMetrics(baseFont.deriveFont(element.fontType))
                        metrics.stringWidth(element.text)
                    }
                    is DrawElement.Annotation -> {
                        val metrics = editor.contentComponent.getFontMetrics(baseFont)
                        val tw = metrics.stringWidth(element.number)
                        maxOf(tw + 10, metrics.height) + 4
                    }
                }
            }
        } ?: 0
    }

    override val height: Int get() = parsedLines.size * editor.lineHeight

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val baseFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val lineHeight = editor.lineHeight

        parsedLines.forEachIndexed { lineIndex, lineElements ->
            var x = 0
            val y = (lineIndex * lineHeight) + editor.contentComponent.getFontMetrics(baseFont).ascent

            for (element in lineElements) {
                when (element) {
                    is DrawElement.Text -> {
                        g.font = baseFont.deriveFont(element.fontType)
                        val metrics = g.fontMetrics
                        g.color = element.color
                        g.drawString(element.text, x, y)
                        x += metrics.stringWidth(element.text)
                    }
                    is DrawElement.Annotation -> {
                        g.font = baseFont
                        val metrics = g.fontMetrics
                        val padX = 4
                        val tw = metrics.stringWidth(element.number)
                        val h = metrics.height
                        val w = maxOf(tw + 10, h)

                        g.color = JBColor.BLUE
                        g.fillRoundRect(x + padX / 2, y - metrics.ascent, w, h, h, h)
                        g.color = JBColor.white
                        g.drawString(element.number, x + padX / 2 + (w - tw) / 2, y)

                        x += w + padX
                    }
                }
            }
        }
    }

    override fun toString(): String = "MultiLinePresentation(${parsedLines.size} lines)"
}
data class Snippet(
    val content: String,
    val fileType: FileType? = null,
    val annotations: Map<String,String> = emptyMap()
)