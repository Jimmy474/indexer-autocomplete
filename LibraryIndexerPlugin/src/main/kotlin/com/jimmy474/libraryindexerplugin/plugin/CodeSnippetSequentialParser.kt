package com.jimmy474.libraryindexerplugin.plugin

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

class CodeSnippetSequentialParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            if(iterator.type == MarkdownTokenTypes.LT){
                val iterator2 = iterator.advance()
                val iterator3 = iterator2.advance()
                if(iterator2.type == MarkdownTokenTypes.LT && iterator3.type == MarkdownTokenTypes.LT){
                    val endIterator = findEOLToken(iterator3.advance())
                    result.withNode(SequentialParser.Node(iterator.index..endIterator.index + 1, CODE_SNIPPET_REFERENCE))
                    iterator = endIterator.advance()
                    continue
                }
                iterator = iterator3
            }
            delegateIndices.put(iterator.index)
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }

    private fun findEOLToken(it: TokensCache.Iterator): TokensCache.Iterator {
        var iterator = it
        while (true) {
            if (iterator.type == MarkdownTokenTypes.EOL) return iterator
            val next = iterator.advance()
            if(next.type == null) return iterator
            else iterator = next
        }
    }
}