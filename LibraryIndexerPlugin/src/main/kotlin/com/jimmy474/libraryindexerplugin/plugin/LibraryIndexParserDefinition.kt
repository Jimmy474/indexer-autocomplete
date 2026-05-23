package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition

class LibraryIndexParserDefinition: MarkdownParserDefinition() {
    override fun createElement(node: ASTNode): PsiElement {
        if(node.elementType.toString().endsWith(LIBRARY_INDEX_REFERENCE.toString())){
            return LibraryIndexPsiElement(node)
        }else if(node.elementType.toString().endsWith(CODE_SNIPPET_REFERENCE.toString())){
            return CodeSnippetPsiElement(node)
        }
        return super.createElement(node)
    }
}