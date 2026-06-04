package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.jimmy474.libraryindexerplugin.plugin.markdown.CODE_SNIPPET_REFERENCE
import com.jimmy474.libraryindexerplugin.plugin.markdown.CODE_SNIPPET_REFERENCE_CONTENT
import com.jimmy474.libraryindexerplugin.plugin.markdown.CODE_SNIPPET_REFERENCE_END
import com.jimmy474.libraryindexerplugin.plugin.markdown.CODE_SNIPPET_REFERENCE_START
import org.intellij.markdown.IElementType
import org.intellij.markdown.lexer.Compat
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.*
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import kotlin.math.min

class CodeSnippetBlockProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
    override fun createMarkerBlocks(
        pos: LookaheadText.Position,
        productionHolder: ProductionHolder,
        stateInfo: MarkerProcessor.StateInfo
    ): List<MarkerBlock> {
        if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, stateInfo.currentConstraints)) {
            return emptyList()
        }
        val currentLine = pos.currentLineFromPosition.trimStart()
        if (CodeSnippetSyntax.REGEX.matches(currentLine.trimEnd())) {
            val block = CompactCodeSnippetMarkerBlock(stateInfo.currentConstraints, productionHolder)
            productionHolder.addProduction(listOf(SequentialParser.Node(pos.offset..pos.nextLineOrEofOffset, CODE_SNIPPET_REFERENCE_START)))
            return listOf(block)
        }
        if (currentLine.startsWith(CodeSnippetSyntax.PREFIX)) {
            productionHolder.addProduction(listOf(SequentialParser.Node(pos.offset..pos.nextLineOrEofOffset, CODE_SNIPPET_REFERENCE_START)))
            return listOf(CodeSnippetMarkerBlock(stateInfo.currentConstraints, productionHolder))
        }

        return emptyList()
    }

    override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
        return pos.currentLineFromPosition.trimStart().startsWith(CodeSnippetSyntax.PREFIX)
    }
}

class CompactCodeSnippetMarkerBlock(
    myConstraints: MarkdownConstraints,
    productionHolder: ProductionHolder,
) : MarkerBlockImpl(myConstraints, productionHolder.mark()) {
    override fun allowsSubBlocks(): Boolean = false
    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = false
    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset
    override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE
    override fun doProcessToken(pos: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
        return MarkerBlock.ProcessingResult.DEFAULT
    }

    override fun getDefaultNodeType(): IElementType = CODE_SNIPPET_REFERENCE
}

class CodeSnippetMarkerBlock(
    myConstraints: MarkdownConstraints,
    private val productionHolder: ProductionHolder,
) : MarkerBlockImpl(myConstraints, productionHolder.mark()) {

    private val endLineRegex = Regex("^ {0,3}${CodeSnippetSyntax.PREFIX} *$")
    private var realInterestingOffset = -1

    override fun allowsSubBlocks(): Boolean = false
    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = true
    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset
    override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE
    override fun doProcessToken(pos: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
        if (pos.offset < realInterestingOffset) return MarkerBlock.ProcessingResult.CANCEL
        if (pos.offsetInCurrentLine != -1) return MarkerBlock.ProcessingResult.CANCEL

        Compat.assert(pos.offsetInCurrentLine == -1)

        val nextLineConstraints = constraints.applyToNextLineAndAddModifiers(pos)
        if (!nextLineConstraints.extendsPrev(constraints)) return MarkerBlock.ProcessingResult.DEFAULT

        val nextLineOffset = pos.nextLineOrEofOffset
        realInterestingOffset = nextLineOffset

        val currentLine = nextLineConstraints.eatItselfFromString(pos.currentLine)
        if (endsThisFence(currentLine)) {
            productionHolder.addProduction(listOf(SequentialParser.Node(pos.offset + 1..pos.nextLineOrEofOffset, CODE_SNIPPET_REFERENCE_END)))
            scheduleProcessingResult(nextLineOffset, MarkerBlock.ProcessingResult.DEFAULT)
        } else {
            val contentRange = min(pos.offset + 1 + constraints.getCharsEaten(pos.currentLine), nextLineOffset)..nextLineOffset
            if (contentRange.first < contentRange.last) {
                productionHolder.addProduction(listOf(SequentialParser.Node(contentRange, CODE_SNIPPET_REFERENCE_CONTENT)))
            }
        }

        return MarkerBlock.ProcessingResult.CANCEL
    }

    private fun endsThisFence(line: CharSequence): Boolean = endLineRegex.matches(line)
    override fun getDefaultNodeType(): IElementType = CODE_SNIPPET_REFERENCE
}
