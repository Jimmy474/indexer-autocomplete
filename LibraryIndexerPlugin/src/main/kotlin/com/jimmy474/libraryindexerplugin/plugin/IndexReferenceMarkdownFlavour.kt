package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.URI
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultFlavour
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourProvider
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCompositePsiElementBase

val LIBRARY_INDEX_REFERENCE: IElementType = MarkdownElementType("INDEX_REFERENCE")

class IndexReferenceMarkdownFlavour: MarkdownFlavourProvider {

    override fun provideFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor = object : MarkdownDefaultFlavour() {
        val flavour = MarkdownDefaultFlavour()
        override val markerProcessorFactory = flavour.markerProcessorFactory
        override val sequentialParserManager: SequentialParserManager = object : SequentialParserManager() {
            override fun getParserSequence(): List<SequentialParser> {
                return listOf(IndexReferenceSequentialParser()) + flavour.sequentialParserManager.getParserSequence()
            }
        }

        override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
            return super.createHtmlGeneratingProviders(linkMap, baseURI) + hashMapOf(
                LIBRARY_INDEX_REFERENCE to object : GeneratingProvider {
                    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
                        val inlineStyle = "color: #00627a; background-color: #ebf5f7; padding: 2px 4px; border-radius: 4px; font-family: monospace;"
                        visitor.consumeTagOpen(node, "span", "style=\"$inlineStyle\" class=\"index-reference\"")
                        val rawText = node.getTextInNode(text).toString()
                        if (rawText.length > 3) {
                            visitor.consumeHtml(rawText.substring(2, rawText.length - 1))
                        }
                        visitor.consumeTagClose("span")
                    }
                }
            )
        }

        override fun createInlinesLexer(): MarkdownLexer {
            return flavour.createInlinesLexer()
        }
    }
}

class IndexReferenceSequentialParser : SequentialParser {
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


    private fun getLength(info: TokensCache.Iterator, canEscape: Boolean): Int {
        var toSubtract = 0
        if (info.type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
            toSubtract = if (canEscape) {
                2
            } else {
                1
            }
        }

        return info.length - toSubtract
    }
}

class LibraryIndexParserDefinition: MarkdownParserDefinition() {
    override fun createElement(node: com.intellij.lang.ASTNode): PsiElement {
        if(node.elementType.toString().endsWith(LIBRARY_INDEX_REFERENCE.toString())){
            return LibraryIndexPsiElement(node)
        }
        return super.createElement(node)
    }
}

class LibraryIndexPsiElement(node: com.intellij.lang.ASTNode): MarkdownCompositePsiElementBase(node), ContributedReferenceHost{
    override fun getPresentableTagName(): String = "LibraryIndexPsiElement"
    override fun getReferences(): Array<out PsiReference> {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this)
    }
}

val LIBRARY_USAGE_TYPE = UsageType { "LibraryIndex" }

class LibraryIndexUsageTypeProvider: UsageTypeProvider{
    override fun getUsageType(element: PsiElement): UsageType? {
        return if(element !is LibraryIndexPsiElement) null else LIBRARY_USAGE_TYPE
    }
}
