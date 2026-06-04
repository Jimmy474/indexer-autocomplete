package com.jimmy474.libraryindexerplugin.plugin.libraryindexer

import com.jimmy474.libraryindexerplugin.plugin.markdown.LIBRARY_INDEX_REFERENCE
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

class LibraryIndexSequentialParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            if(iterator.type == MarkdownTokenTypes.TEXT && tokens.originalText.subSequence(iterator.start,iterator.end).contentEquals(LibraryIndex.PREFIX)){
                val nextIterator = iterator.advance()
                if (nextIterator.type == MarkdownTokenTypes.BACKTICK || nextIterator.type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
                    if(iterator.end == nextIterator.start) {
                        val endIterator = findOfSize(nextIterator.advance(), getLength(nextIterator, true))
                        if (endIterator != null) {
                            result.withNode(SequentialParser.Node(nextIterator.index - 1..endIterator.index + 1, LIBRARY_INDEX_REFERENCE))
                            iterator = endIterator.advance()
                            continue
                        }
                    }
                }
                iterator = nextIterator
            }
            delegateIndices.put(iterator.index)
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }

    private fun findOfSize(it: TokensCache.Iterator, length: Int): TokensCache.Iterator? {
        var iterator = it
        while (iterator.type != null) {
            if (iterator.type == MarkdownTokenTypes.BACKTICK || iterator.type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
                if (getLength(iterator, false) == length) {
                    return iterator
                }
            }

            iterator = iterator.advance()
        }
        return null
    }

    private fun getLength(info: TokensCache.Iterator, canEscape: Boolean): Int = info.length - when (info.type) {
        MarkdownTokenTypes.ESCAPED_BACKTICKS -> if (canEscape) 2 else 1
        else -> 0
    }
}
