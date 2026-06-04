package com.jimmy474.libraryindexerplugin.plugin.codesnippet

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class CodeSnippetReferenceContributor: PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar){
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(CodeSnippetPsiElement::class.java), CodeSnippetReferenceProvider())
    }
}

class CodeSnippetReferenceProvider: PsiReferenceProvider(){
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element.containingFile !is MarkdownFile) return emptyArray()
        if (element !is CodeSnippetPsiElement) return emptyArray()

        val match = CodeSnippetSyntax.REGEX.matchEntire(element.text) ?: return emptyArray()
        val codeSnippet = match.toCodeSnippetReference()
        return buildList {
            val path = codeSnippet.path.value.removePrefix("${CodeSnippetSyntax.ROOT_SYMBOL}/")
            codeSnippet.fileName?.let { add(CodeSnippetReference(element, it.relativeRange, path)) }
            codeSnippet.regions.forEach { add(CodeSnippetReference(element, it.relativeRange, path, it.value.substring(1))) }
        }.toTypedArray()
    }
}
