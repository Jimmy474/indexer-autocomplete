package com.jimmy474.libraryindexerplugin.plugin.markdown

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jimmy474.libraryindexerplugin.plugin.codesnippet.CodeSnippetPsiElement
import com.jimmy474.libraryindexerplugin.plugin.libraryindexer.LibraryIndexPsiElement
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer
import org.intellij.plugins.markdown.lang.parser.MarkdownParserAdapter
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition

class IndexerMarkdownParserDefinition: MarkdownParserDefinition() {
    override fun createLexer(project: Project?): Lexer {
        return MarkdownToplevelLexer(IndexerMarkdownFlavour())
    }

    override fun createParser(project: Project?): PsiParser {
        return MarkdownParserAdapter(IndexerMarkdownFlavour())
    }

    override fun createElement(node: ASTNode): PsiElement {
        if(node.elementType.toString().endsWith(LIBRARY_INDEX_REFERENCE.toString())){
            return LibraryIndexPsiElement(node)
        }else if(node.elementType.toString().endsWith(CODE_SNIPPET_REFERENCE.toString())){
            return CodeSnippetPsiElement(node)
        }
        return super.createElement(node)
    }
}
