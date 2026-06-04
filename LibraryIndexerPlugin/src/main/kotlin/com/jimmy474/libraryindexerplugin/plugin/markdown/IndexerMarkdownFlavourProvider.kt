package com.jimmy474.libraryindexerplugin.plugin.markdown

import com.intellij.psi.FileViewProvider
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.CodeSnippetBlockProvider
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.LibraryIndexSequentialParser
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.URI
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultFlavour
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultMarkerProcessor
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourProvider

val LIBRARY_INDEX_REFERENCE: IElementType = MarkdownElementType("INDEX_REFERENCE")
val CODE_SNIPPET_REFERENCE: IElementType = MarkdownElementType("CODE_SNIPPET_REFERENCE")
val CODE_SNIPPET_REFERENCE_START: IElementType = MarkdownElementType("CODE_SNIPPET_REFERENCE_START", true)
val CODE_SNIPPET_REFERENCE_CONTENT: IElementType = MarkdownElementType("CODE_SNIPPET_REFERENCE_CONTENT", true)
val CODE_SNIPPET_REFERENCE_END: IElementType = MarkdownElementType("CODE_SNIPPET_REFERENCE_END", true)

class IndexerMarkdownFlavourProvider: MarkdownFlavourProvider {

    override fun provideFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor = IndexerMarkdownFlavour()
}

class IndexerMarkdownFlavour : MarkdownDefaultFlavour() {
    val parserManager = super.sequentialParserManager
    override val sequentialParserManager: SequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> {
            return listOf(LibraryIndexSequentialParser()) + parserManager.getParserSequence()
        }
    }

    override val markerProcessorFactory = object : MarkerProcessorFactory {
        override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> = object : MarkdownDefaultMarkerProcessor(productionHolder, GFMConstraints.BASE) {
            override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> {
                return listOf(CodeSnippetBlockProvider()) + super.getMarkerBlockProviders()
            }
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
}
