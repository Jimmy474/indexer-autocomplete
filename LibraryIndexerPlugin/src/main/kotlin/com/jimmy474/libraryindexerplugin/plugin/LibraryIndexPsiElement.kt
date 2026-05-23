package com.jimmy474.libraryindexerplugin.plugin

import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCompositePsiElementBase

class LibraryIndexPsiElement(node: ASTNode): MarkdownCompositePsiElementBase(node), ContributedReferenceHost {
    override fun getPresentableTagName(): String = "LibraryIndexPsiElement"
    override fun getReferences(): Array<out PsiReference> {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this)
    }
}