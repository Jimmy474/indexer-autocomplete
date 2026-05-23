package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.psi.FileViewProvider
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.URI
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultFlavour
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourProvider

val LIBRARY_INDEX_REFERENCE: IElementType = MarkdownElementType("INDEX_REFERENCE")
val CODE_SNIPPET_REFERENCE: IElementType = MarkdownElementType("CODE_SNIPPET_REFERENCE")

class IndexReferenceMarkdownFlavour: MarkdownFlavourProvider {

    override fun provideFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor = object : MarkdownDefaultFlavour() {
        val parserManager = super.sequentialParserManager
        override val sequentialParserManager: SequentialParserManager = object : SequentialParserManager() {
            override fun getParserSequence(): List<SequentialParser> {
                return listOf(IndexReferenceSequentialParser(), CodeSnippetSequentialParser()) + parserManager.getParserSequence()
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
}
